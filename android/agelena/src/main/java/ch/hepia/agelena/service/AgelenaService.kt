package ch.hepia.agelena.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import ch.hepia.agelena.Agelena
import ch.hepia.agelena.R
import ch.hepia.agelena.bluetooth.BLEManager
import ch.hepia.agelena.db.SQLite
import ch.hepia.agelena.utils.ExchangeKeyPair
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * Work types that can be called from the upper layer
 *
 * @since 0.4
 */
internal enum class WorkType(val num: Int) {
    Unknown(-1),
    Boot(0),
    Start(1),
    Send(2),
    SendAck(3),
    Stop(4),
    Intern(5);

    companion object {
        fun fromNum(num: Int): WorkType {
            return when(num) {
                0 -> Boot
                1 -> Start
                2 -> Send
                3 -> SendAck
                4 -> Stop
                5 -> Intern
                else -> Unknown
            }
        }
    }
}

/**
 * Background service managing the lower layer (BLE)
 *
 * @since 0.4
 */
internal class AgelenaService: Service() {
    private val TAG = "AgelenaService"
    private val N_ID = 1
    private class Node(val msgId: Int, val nbBlock: Int, val broadcast: Boolean, val ack: Boolean)

    private var bleManager: BLEManager? = null
    private val receptionQueue: LinkedBlockingQueue<Node> = LinkedBlockingQueue()
    private var clientStarted = false
    private val queue = LinkedBlockingQueue<Intent>()
    private var finish = false
    private var thread: Thread? = null

    var context: Context? = null

    private val runnable = {
        while (!finish) {
            try {
                queue.take().let { intent: Intent? ->
                    if (intent?.extras != null) {

                        when (WorkType.fromNum(intent.getIntExtra("type", WorkType.Unknown.num))) {
                            WorkType.Intern -> {
                                if (!createComponents()) {
                                    bleManager?.close()
                                    bleManager = null
                                }
                            }
                            WorkType.Boot -> {
                                if (!createComponents()) {
                                    bleManager?.close()
                                    bleManager = null
                                }
                            }
                            WorkType.Start -> {
                                val s = createComponents()
                                Log.d(TAG, "Started : $s")

                                if (s) {
                                    clientStarted = true
                                } else {
                                    bleManager?.close()
                                    bleManager = null
                                }

                                val i = Intent(FrontReceiver.ACTION_STARTED)
                                i.putExtra("app", Agelena.getAppUUID(context!!).toString())
                                i.putExtra("success", s)
                                context?.sendBroadcast(i)

                                if (s) {
                                    synchronized(receptionQueue) {
                                        receptionQueue.forEach {
                                            if (it.ack) {
                                                val intt = Intent(FrontReceiver.ACTION_ACK)
                                                i.putExtra("app", Agelena.getAppUUID(context!!).toString())
                                                intt.putExtra("id", it.msgId)
                                                context!!.sendBroadcast(intt)
                                            } else {
                                                receiveMessage(it.msgId, it.nbBlock, it.broadcast)
                                            }
                                        }
                                        bleManager?.getConnectedDevices()?.forEach {
                                            val intt = Intent(FrontReceiver.ACTION_DEVICE)
                                            i.putExtra("app", Agelena.getAppUUID(context!!).toString())
                                            intt.putExtra("id", it)
                                            intt.putExtra("connected", true)
                                            context!!.sendBroadcast(intt)
                                        }
                                    }
                                }
                            }
                            WorkType.Stop -> {
                                clientStarted = false
                            }
                            WorkType.Send -> {
                                val bs = intent.getBundleExtra("data")?.getByteArray("data")
                                val id = intent.getBundleExtra("data")?.getInt("receiver")

                                if (bs != null) {
                                    if (intent.getBundleExtra("data")
                                            ?.containsKey("receiver") == true && id != null
                                    ) {
                                        bleManager?.sendData(id, bs, true)
                                    } else {
                                        bleManager?.propagateData(bs, priority = true)
                                    }
                                }
                            }
                            WorkType.SendAck -> {
                                val ack = intent.getBundleExtra("data")?.getInt("message")
                                val id = intent.getBundleExtra("data")?.getInt("sender")
                                if (ack != null && id != null) {
                                    if (bleManager?.isConnected(id) == false) {
                                        SQLite.getInstance(context!!).insertAck(ack, false)
                                    }
                                    bleManager?.sendAcks(id, listOf(ack))
                                }
                            }
                            else -> {
                            }
                        }
                    } else {
                        if (!createComponents()) {
                            bleManager?.close()
                            bleManager = null
                        }
                    }
                }
            } catch (_: InterruptedException) { }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForeground(N_ID, buildNotification())

        if (intent == null) {
            val i = Intent()
            i.putExtra("type", WorkType.Intern.num)
            queue.add(i)
        } else {
            queue.add(intent)
        }

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForeground(N_ID, buildNotification())

        // Get params to start generating them because its long
        GlobalScope.launch {
            ExchangeKeyPair.params
        }

        finish = false
        thread = thread(start = true, block = runnable)

    }

    private fun buildNotification(): Notification {
        return (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, createNotificationChannel("agelena", "Agelena"))
        } else {
            Notification.Builder(this)
        })
            .setContentTitle("Agelena")
            .setContentText("Agelena is running in background")
            .setTicker("Agelena")
            .setSmallIcon(R.drawable.agelena_icon)
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String): String{
        val chan = NotificationChannel(channelId,
            channelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.RED
        chan.lockscreenVisibility = Notification.VISIBILITY_SECRET
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        finish = true
        thread?.interrupt()
    }

    /**
     * Create components if they aren't already started
     */
    private fun createComponents(): Boolean {
        if (bleManager == null) {
            if (BLEManager.checkPermissions(context!!) &&
                BLEManager.checkBluetoothSupport(context!!) &&
                BLEManager.isBluetoothEnabled(context!!)) {

                val pref = context?.getSharedPreferences(Agelena.SHARED_PREF, Context.MODE_PRIVATE)

                val id = pref?.getInt(Agelena.PREF_ID, -1)
                val key = pref?.getString(Agelena.PREF_PUB, "")
                if (id != null && id != -1 && key != null && key != "") {
                    bleManager = BLEManager(context!!, id, key) { idM, nb, br, ack ->
                        if (clientStarted) {
                            if (!ack) {
                                receiveMessage(idM, nb!!, br!!)
                            } else {
                                val i = Intent(FrontReceiver.ACTION_ACK)
                                i.putExtra("app", Agelena.getAppUUID(context!!).toString())
                                i.putExtra("id", idM)
                                context!!.sendBroadcast(i)
                            }
                        } else {
                            receptionQueue.add(
                                Node(
                                    idM,
                                    nb ?: 0,
                                    br ?: false,
                                    ack
                                )
                            )
                        }
                    }

                    return runBlocking {
                        return@runBlocking bleManager?.start() ?: false
                    }
                }
            }
            return false
        }
        return true
    }

    /**
     * Send the message to the client instance
     */
    private fun receiveMessage(msgId: Int, nbBlock: Int, broadcast: Boolean) {
        val i = Intent(FrontReceiver.ACTION_MESSAGE)
        i.putExtra("app", Agelena.getAppUUID(context!!).toString())
        i.putExtra("id", msgId)
        i.putExtra("broadcast", broadcast)
        i.putExtra("nbBlock", nbBlock)
        context?.sendBroadcast(i)
    }

    companion object {
        /**
         * Enqueue a work for the Agelena background service
         */
        fun enqueueWork(context: Context, type: WorkType, data: Bundle? = null) {
            val i = Intent(context, AgelenaService::class.java)
            i.putExtra("type", type.num)
            if (data != null) i.putExtra("data", data)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(i) ?: Log.e("AgelenaService", "Service not started")
            else
                context.startService(i)
        }
    }
}