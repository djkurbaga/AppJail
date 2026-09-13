package com.resat.appjail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Telefon yeniden başladığında gün kontrolünü tetikler.
 * Böylece cihaz kapalıyken gün değiştiyse kilitler doğru şekilde sıfırlanır.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        LimitStore.ensureFreshDay(context)
    }
}
