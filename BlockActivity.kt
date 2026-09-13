package com.resat.appjail

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Limit dolduğunda açılan tam ekran bariyer.
 * Geri tuşu iptal, görev listesinde iz yok ve gece yarısına kalan süre sayılır.
 * "Devam et" gibi bir çıkış yolu bilinçli olarak eklenmemiştir.
 */
class BlockActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var counter: TextView

    // Kalan süreyi saniyede bir tazeleyen döngü
    private val tick = object : Runnable {
        override fun run() {
            counter.text = remainingText()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
        val label = appLabel(pkg)

        // Arayüz basit tutuldu: XML yerine doğrudan kodla kuruluyor
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(64, 64, 64, 64)
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        }
        root.addView(TextView(this).apply {
            text = "$label bugünlük kapandı"
            setTextColor(Color.WHITE)
            textSize = 26f
            gravity = Gravity.CENTER
        })
        counter = TextView(this).apply {
            setTextColor(Color.parseColor("#9E9E9E"))
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 0)
        }
        root.addView(counter)
        root.addView(TextView(this).apply {
            text = "Limitini sen koydun. Yarın görüşürüz."
            setTextColor(Color.parseColor("#616161"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 0)
        })
        setContentView(root)
        handler.post(tick)
    }

    /** Gece yarısına kalan süreyi "3 sa 12 dk" biçiminde üretir. */
    private fun remainingText(): String {
        val midnight = LocalDate.now().plusDays(1).atStartOfDay()
        val left = Duration.between(LocalDateTime.now(), midnight)
        return "Kilit açılmasına ${left.toHours()} sa ${left.toMinutesPart()} dk"
    }

    /** Paket adından kullanıcıya gösterilecek uygulama ismini bulur. */
    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        "Bu uygulama"
    }

    /** Geri tuşu bariyeri kapatmaz, kullanıcıyı ana ekrana yollar. */
    override fun onBackPressed() {
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
