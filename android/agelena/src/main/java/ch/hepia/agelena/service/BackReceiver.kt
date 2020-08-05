package ch.hepia.agelena.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Broadcast receiving receiving requests for the lower layer
 *
 * @since 0.4
 */
internal class BackReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                AgelenaService.enqueueWork(context!!, WorkType.Boot)
            }
        }
    }
}

