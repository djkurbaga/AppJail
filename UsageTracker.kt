package com.resat.appjail

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

/**
 * Gece yarısından şu ana kadar her uygulamanın ön planda geçirdiği süreyi hesaplar.
 * queryUsageStats yerine olay akışı kullanılır; çünkü ekran kapanmalarını daha doğru yakalar.
 */
object UsageTracker {

    /** Bugünün 00:00 anını milisaniye olarak verir. */
    private fun startOfToday(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * Paket -> bugün ön planda geçen milisaniye haritası.
     * Uygulama hâlâ açıksa, açık kaldığı süre "şu ana" kadar sayılır.
     */
    fun usageTodayMillis(ctx: Context): Map<String, Long> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(startOfToday(), now)

        val totals = HashMap<String, Long>()      // biriken süreler
        val openedAt = HashMap<String, Long>()    // hâlâ ön planda olanların başlangıç anı
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED ->
                    openedAt[pkg] = event.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val start = openedAt.remove(pkg) ?: continue
                    totals[pkg] = (totals[pkg] ?: 0L) + (event.timeStamp - start)
                }
            }
        }
        // Kapanış olayı gelmemiş, yani şu anda açık olan uygulamalar
        openedAt.forEach { (pkg, start) ->
            totals[pkg] = (totals[pkg] ?: 0L) + (now - start)
        }
        return totals
    }

    /** Kullanım erişimi izninin verilip verilmediğini kontrol eder. */
    fun hasUsagePermission(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            ctx.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
