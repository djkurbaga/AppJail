package com.resat.appjail

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.time.LocalDate

/**
 * Limitlerin ve günlük engel durumunun saklandığı tek kaynak.
 * SharedPreferences kullanır; servis ve arayüz aynı dosyayı okur.
 */
object LimitStore {

    private const val PREFS = "appjail_prefs"
    private const val KEY_ACTIVE = "active_limits"    // bugün geçerli limitler (paket -> dakika)
    private const val KEY_PENDING = "pending_limits"  // yarından itibaren geçerli olacak limitler
    private const val KEY_BLOCKED = "blocked_today"   // bugün kilitlenmiş paketler
    private const val KEY_DAY = "last_day"            // son sıfırlama günü (epochDay)

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Gün değiştiyse: engel listesini temizler ve bekleyen limitleri yürürlüğe alır.
     * Her okuma öncesi çağrılır, böylece ayrı bir zamanlayıcıya ihtiyaç kalmaz.
     */
    fun ensureFreshDay(ctx: Context) {
        val p = prefs(ctx)
        val today = LocalDate.now().toEpochDay()
        if (p.getLong(KEY_DAY, -1L) != today) {
            val pending = p.getString(KEY_PENDING, null)
            p.edit()
                .putLong(KEY_DAY, today)
                .putString(KEY_BLOCKED, "[]")
                .apply {
                    // Bekleyen değişiklikler yeni günün başında aktif hale gelir
                    if (pending != null) putString(KEY_ACTIVE, pending)
                }
                .apply()
        }
    }

    /** Bugün geçerli limitleri paket -> dakika biçiminde döner. */
    fun activeLimits(ctx: Context): Map<String, Int> {
        ensureFreshDay(ctx)
        return parse(prefs(ctx).getString(KEY_ACTIVE, "{}"))
    }

    /** Kullanıcının arayüzde gördüğü (yarın geçerli olacak) limitler. */
    fun pendingLimits(ctx: Context): Map<String, Int> {
        ensureFreshDay(ctx)
        val p = prefs(ctx)
        return parse(p.getString(KEY_PENDING, p.getString(KEY_ACTIVE, "{}")))
    }

    /**
     * Limit yazar. Kural: süreyi KISALTMAK anında geçerlidir,
     * UZATMAK ise ancak ertesi gün geçerli olur — yani gün içinde kaçamak yok.
     */
    fun setLimit(ctx: Context, pkg: String, minutes: Int) {
        ensureFreshDay(ctx)
        val p = prefs(ctx)
        val pending = parse(p.getString(KEY_PENDING, p.getString(KEY_ACTIVE, "{}"))).toMutableMap()
        val active = parse(p.getString(KEY_ACTIVE, "{}")).toMutableMap()

        pending[pkg] = minutes
        val current = active[pkg]
        if (current == null || minutes < current) active[pkg] = minutes  // sadece daha sıkısı anında işler

        p.edit()
            .putString(KEY_PENDING, JSONObject(pending as Map<*, *>).toString())
            .putString(KEY_ACTIVE, JSONObject(active as Map<*, *>).toString())
            .apply()
    }

    /** Takipten çıkarma da ertesi güne ertelenir; bugünkü kilit kalkmaz. */
    fun removeLimit(ctx: Context, pkg: String) {
        ensureFreshDay(ctx)
        val p = prefs(ctx)
        val pending = parse(p.getString(KEY_PENDING, p.getString(KEY_ACTIVE, "{}"))).toMutableMap()
        pending.remove(pkg)
        p.edit().putString(KEY_PENDING, JSONObject(pending as Map<*, *>).toString()).apply()
    }

    /** Bugün kilitlenmiş paketler kümesi. */
    fun blockedToday(ctx: Context): Set<String> {
        ensureFreshDay(ctx)
        val raw = prefs(ctx).getString(KEY_BLOCKED, "[]") ?: "[]"
        return raw.trim('[', ']').split(',')
            .map { it.trim().trim('"') }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /** Bir paketi gün sonuna kadar kilitler; bu işlem geri alınamaz. */
    fun markBlocked(ctx: Context, pkg: String) {
        val set = blockedToday(ctx).toMutableSet()
        if (set.add(pkg)) {
            val serialized = set.joinToString(",", "[", "]") { "\"$it\"" }
            prefs(ctx).edit().putString(KEY_BLOCKED, serialized).apply()
        }
    }

    /** JSON metnini paket -> dakika haritasına çevirir. */
    private fun parse(json: String?): Map<String, Int> {
        if (json.isNullOrBlank()) return emptyMap()
        val obj = JSONObject(json)
        return obj.keys().asSequence().associateWith { obj.optInt(it, 0) }
    }
}
