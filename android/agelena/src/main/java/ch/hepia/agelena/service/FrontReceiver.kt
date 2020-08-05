package ch.hepia.agelena.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import ch.hepia.agelena.Agelena
import ch.hepia.agelena.Device
import ch.hepia.agelena.db.SQLite
import ch.hepia.agelena.message.MessageManager

/**
 * Broadcast receiver receiving requests for the upper layer
 *
 * @since 0.4
 */
internal class FrontReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.getStringExtra("app") == Agelena.getAppUUID(context!!).toString()) {
            when (intent.action) {
                ACTION_MESSAGE -> {
                    val m = MessageManager.buildMessage(
                        context,
                        intent.getIntExtra("id", -1),
                        intent.getIntExtra("nbBlock", 0),
                        intent.getBooleanExtra("broadcast", false)
                    ) ?: return
                    Agelena.onMessageReceived(m, intent.getBooleanExtra("broadcast", false))
                }
                ACTION_DEVICE -> {
                    val d = SQLite.getInstance(context).getDevice(intent.getIntExtra("id", -1))
                    if (d != null) {
                        if (intent.getBooleanExtra("connected", true)) {
                            Agelena.onDeviceConnected(Device(d.id, d.publicKey, true))
                        } else {
                            Agelena.onDeviceLost(d.id)
                        }
                    }
                }
                ACTION_ACK -> {
                    val id = intent.getIntExtra("id", -1)
                    if (id != -1) {
                        Agelena.onAckReceived(id)
                    }
                }
                ACTION_STARTED -> {
                    val s = intent.getBooleanExtra("success", false)
                    Agelena.onStarted(s)
                }
            }
        }
    }

    companion object {
        internal const val ACTION_MESSAGE = "ch.hepia.agelena.service.ACTION_MESSAGE"
        internal const val ACTION_DEVICE = "ch.hepia.agelena.service.ACTION_DEVICE"
        internal const val ACTION_ACK = "ch.hepia.agelena.service.ACTION_ACK"
        internal const val ACTION_STARTED = "ch.hepia.agelena.service.ACTION_STARTED"

        fun createReceiver(context: Context): FrontReceiver {
            val rec = FrontReceiver()
            context.registerReceiver(rec, IntentFilter(ACTION_MESSAGE))
            context.registerReceiver(rec, IntentFilter(ACTION_DEVICE))
            context.registerReceiver(rec, IntentFilter(ACTION_ACK))
            context.registerReceiver(rec, IntentFilter(ACTION_STARTED))
            return rec
        }
    }
}
