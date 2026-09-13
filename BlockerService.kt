package com.resat.appjail

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Sistemin arka planında sürekli çalışan çekirdek servis.
 * İki işi var: ön plandaki uygulamayı bilmek ve limit dolduğunda anında bariyeri açmak.
 */
class BlockerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentPkg: String? = null

    // Uygulama açıkken de süre dolabilir, bu yüzden periyodik kontrol şart
    private val ticker = object : Runnable {
        override fun run() {
            checkCurrent()
            handler.postDelayed(this, CHECK_PERIOD_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.post(ticker) // servis bağlanır bağlanmaz döngüyü başlat
    }

    /** Ekrandaki uygulama her değiştiğinde tetiklenir. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // kendi arayüzümüzü engellemiyoruz
        currentPkg = pkg
        checkCurrent()
    }

    /**
     * Ön plandaki paketi limitle karşılaştırır.
     * Bir kez kilitlenen paket gün bitene kadar kilitli kalır; tekrar açılmaya çalışılırsa
     * kullanım süresi hiç sorgulanmadan doğrudan bariyer gösterilir.
     */
    private fun checkCurrent() {
        val pkg = currentPkg ?: return
        val limits = LimitStore.activeLimits(this)
        val limitMinutes = limits[pkg] ?: return // takip edilmeyen uygulamaya karışma

        if (pkg in LimitStore.blockedToday(this)) {
            block(pkg)
            return
        }

        val usedMs = UsageTracker.usageTodayMillis(this)[pkg] ?: 0L
        if (usedMs >= limitMinutes * 60_000L) {
            LimitStore.markBlocked(this, pkg) // gün sonuna kadar mühürle
            block(pkg)
        }
    }

    /** Kullanıcıyı uygulamadan çıkarır ve kapatılamayan engel ekranını açar. */
    private fun block(pkg: String) {
        performGlobalAction(GLOBAL_ACTION_HOME) // önce uygulamayı arka plana at
        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
        }
        startActivity(intent)
    }

    override fun onInterrupt() { /* kesinti durumunda özel bir iş yok */ }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }

    companion object {
        private const val CHECK_PERIOD_MS = 3_000L // 3 saniyede bir kontrol
    }
}
