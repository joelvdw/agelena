package ch.hepia.agelena

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import ch.hepia.agelena.bluetooth.BLEManager
import ch.hepia.agelena.bluetooth.MessageService
import ch.hepia.agelena.db.FileManager
import ch.hepia.agelena.db.SQLite
import ch.hepia.agelena.message.Message
import ch.hepia.agelena.message.MessageManager
import ch.hepia.agelena.service.AgelenaService
import ch.hepia.agelena.service.BackReceiver
import ch.hepia.agelena.service.FrontReceiver
import ch.hepia.agelena.service.WorkType
import ch.hepia.agelena.utils.ExchangeKeyPair
import ch.hepia.agelena.utils.MessageKeyPair
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Exception
import java.lang.IllegalStateException
import java.security.PublicKey
import java.util.*
import kotlin.random.Random

/**
 * Main class of the Agelena library
 *
 * This class permits to send and receive messages through BLE
 * It works as a singleton, that's why all methods are static but require the initialization.
 *
 * @since 0.1
 */
class Agelena private constructor(
    private val context: Context,
    private var msgListener: MessageListener,
    private var stateListener: StateListener,
    private val config: Config,
    private val userID: Int,
    private val keyPair: MessageKeyPair
) {
    private var isStarted: Boolean = false
    private val devices: MutableMap<Int, Device> = mutableMapOf()
    private val notifiedMessages: MutableList<Int> = mutableListOf()
    private var frontReceiver: FrontReceiver? = null

    private fun start() {
        if (isStarted) return

        frontReceiver = FrontReceiver.createReceiver(context)
        sendToService(WorkType.Start)
    }

    private fun stop() {
        Agelena.sendToService(WorkType.Stop)
        context.unregisterReceiver(frontReceiver)
        isStarted = false

        stateListener.onStopped()
    }

    private fun isMessageValid(message: Message, broadcast: Boolean): Boolean {
        if (!isStarted) {
            onMessageFailed(message, MESSAGE_ERROR_NOT_STARTED)
            return false
        }
        if (message.receiverID == userID) {
            onMessageFailed(message, MESSAGE_ERROR_SELF)
            return false
        }

        if (!broadcast) {
            if (message.receiverID == null) {
                onMessageFailed(message, MESSAGE_ERROR_RECEIVER_EMPTY)
                return false
            }
            if (message.receiverID == userID) {
                onMessageFailed(message, MESSAGE_ERROR_SELF)
                return false
            }
        }
        return true
    }

    fun addDevice(id: Int, publicKey: PublicKey) {
        SQLite.getInstance(context).insertDevice(Device(id, publicKey, false))
    }

    private fun sendMessage(message: Message) {
        if (!isMessageValid(message, false)) return

        val device: Device? = devices[message.receiverID!!]
        if (device != null && device.isConnected) {
            sendDirectMessage(message)
        } else {
            val d = SQLite.getInstance(context).getDevice(message.receiverID!!)
            if (d == null) {
                onMessageFailed(message, MESSAGE_ERROR_UNKNOWN_RECEIVER)
                return
            }

            MessageManager.sendMessage(context, null, message, d.publicKey,false)
            SQLite.getInstance(context).insertAck(message.id, true)
            onMessageSent(message)
        }
    }

    private fun sendDirectMessage(message: Message) {
        if (!isMessageValid(message, false)) return

        val device = devices[message.receiverID!!]
        if (device == null || !device.isConnected) {
            onMessageFailed(message, MESSAGE_ERROR_OUT_OF_RANGE)
        } else {
            MessageManager.sendMessage(context, device, message, device.publicKey,true)
            SQLite.getInstance(context).insertAck(message.id, true)
            onMessageSent(message)
        }
    }

    private fun sendBroadcastMessage(message: Message, ttl: Int) {
        if (!isMessageValid(message, true)) return
        if (ttl <= 0 || ttl > 255) {
            onMessageFailed(message, MESSAGE_ERROR_INVALID_TTL)
            return
        }

        message.ttl = ttl
        MessageManager.sendBroadcast(context, message)
        onMessageSent(message)
    }

    private fun onDeviceConnected(device: Device) {
        devices[device.id] = device
        stateListener.onDeviceConnected(device)
    }

    private fun onDeviceLost(id: Int) {
        devices.remove(id)?.let {
            stateListener.onDeviceLost(it)
        }
    }

    private fun onMessageReceived(message: Message, broadcast: Boolean) {
        if (broadcast) {
            msgListener.onBroadcastMessageReceived(message)
        } else {
            msgListener.onMessageReceived(message)
        }
    }

    private fun onMessageSent(message: Message) {
        if (!notifiedMessages.contains(message.id)) {
            msgListener.onMessageSent(message)
            notifiedMessages.add(message.id)
        }
    }

    private fun onMessageFailed(message: Message, errorCode: Int) {
        if (!notifiedMessages.contains(message.id)) {
            msgListener.onMessageFailed(message, errorCode)
            notifiedMessages.add(message.id)
        }
    }

    private fun onAckReceived(messageId: Int) {
        msgListener.onAckReceived(messageId)
    }

    private fun onStarted(success: Boolean) {
        if (success) stateListener.onStarted() else stateListener.onStartError()
        isStarted = success
    }

    /**
     * Remove all blocks that are too old from storage and database
     */
    private fun cleanStorage() {
        SQLite.getInstance(context).removeOldAcks()
        SQLite.getInstance(context).getAllRemovableBlocks().forEach {
            SQLite.getInstance(context).removeBlock(it.first, it.second)
            FileManager.getInstance(context).removeBlock(it.first, it.second)
        }
    }

    private fun sendToService(type: WorkType, bundle: Bundle? = null) {
        AgelenaService.enqueueWork(context, type, bundle)
    }

    companion object {
        private const val TAG = "Agelena"
        internal const val SHARED_PREF = "Agelena"
        internal const val PREF_ID = "id"
        internal const val PREF_PUB = "pub"
        private const val PREF_PRIV = "priv"

        /**
         * Agelena protocol version
         */
        const val PROTOCOL_VERSION = "1.0"

        const val SUCCESS = 0
        /**
         * Unknown error
         */
        const val ERROR_GENERAL = -1
        /**
         * One of the needed permissions is not granted
         */
        const val ERROR_MISSING_PERMISSIONS = -2
        /**
         * Bluetooth or location (needed for BLE) is not enabled
         */
        const val ERROR_BLUETOOTH_NOT_ENABLED = -3
        /**
         * Bluetooth LE is not supported
         */
        const val ERROR_BLE_NOT_SUPPORTED = -4
        /**
         * App UUID invalid or not found in app meta-data in the AndroidManifest
         * (<meta-data android:name="ch.hepia.agelena.APP_UUID" android:value="APP_UUID"/>
         */
        const val ERROR_INVALID_APP_UUID = -5

        /**
         * Message receiver was not found
         */
        const val MESSAGE_ERROR_RECEIVER_EMPTY = -10
        /**
         * Message received isn't in range
         * Occurs only with direct messages
         */
        const val MESSAGE_ERROR_OUT_OF_RANGE = -11
        /**
         * Given TTL is smaller or equal to 0
         */
        const val MESSAGE_ERROR_INVALID_TTL = -12
        /**
         * Message receiver is this device
         */
        const val MESSAGE_ERROR_SELF = -13
        /**
         * Agelena is not started
         */
        const val MESSAGE_ERROR_NOT_STARTED = -14
        /**
         * Message encryption is enabled but receiver public key is unknown.
         *
         * A receiver must have been at least one time in range to send it encrypted messages.
         */
        const val MESSAGE_ERROR_UNKNOWN_RECEIVER = -15

        /**
         * Private static Agelena instance for singleton
         */
        @Volatile private var instance: Agelena? = null

        val isInitialized: Boolean
            get() = instance != null
        val isStarted: Boolean
            get() = instance?.isStarted ?: false

        /**
         * Initialize Agelena library using the given [config], verifies permissions and enables BLE
         * @param context Context used to check permissions and get BluetoothManager
         * @param stateListener Used to notify state modifications in Agelena
         * @param messageListener Used to notify new message receptions and sending
         * @return [Agelena.SUCCESS] if succeeded or an Agelena.ERROR_* if an error occurred
         */
        fun initialize(context: Context, stateListener: StateListener, messageListener: MessageListener, config: Config): Int {
            if (isInitialized) return SUCCESS

            if (!BLEManager.checkPermissions(context)) {
                return ERROR_MISSING_PERMISSIONS
            }
            if (!BLEManager.checkBluetoothSupport(context)) {
                return ERROR_BLE_NOT_SUPPORTED
            }
            if (!BLEManager.isBluetoothEnabled(context)) {
                return ERROR_BLUETOOTH_NOT_ENABLED
            }

            val pref = context.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE)
                ?: return ERROR_GENERAL

            var id = pref.getInt(PREF_ID, -1)
            if (id == -1) {
                id = Random.nextInt(1, Int.MAX_VALUE)
                pref.edit()
                    .putInt(PREF_ID, id)
                    .apply()
            }

            val keyPair: MessageKeyPair?
            if (pref.contains(PREF_PRIV) && pref.contains(PREF_PUB)) {
                keyPair = MessageKeyPair.getFromStrings(
                    pref.getString(PREF_PUB, "")!!,
                    pref.getString(PREF_PRIV, "")!!
                )
            } else {
                keyPair = MessageKeyPair.generateKeyPair()
                pref.edit()
                    .putString(PREF_PRIV, keyPair.privateKeyString)
                    .putString(PREF_PUB, keyPair.publicKeyString)
                    .apply()
            }

            try {
                getAppUUID(context)
            } catch (_: Exception) {
                return ERROR_INVALID_APP_UUID
            }

            instance = Agelena(context, messageListener, stateListener, config, id, keyPair)
            instance?.run {
                cleanStorage()

                Log.d(TAG, "Initialized with ID : $id")
            }

            return SUCCESS
        }

        /**
         * Initialize Agelena library with default [Config], verifies permissions and enables BLE
         * @param context Context used to check permissions and get BluetoothManager
         * @param stateListener Used to notify state modifications in Agelena
         * @param messageListener Used to notify new message receptions and sending
         * @return [Agelena.SUCCESS] if succeeded or an Agelena.ERROR_* if an error occurred
         */
        fun initialize(context: Context, stateListener: StateListener, messageListener: MessageListener): Int {
            return initialize(context, stateListener, messageListener, Config.Builder().build())
        }

        fun start() {
            instance ?: throw IllegalStateException("Agelena must first be initialized")
            instance?.start()
        }

        fun stop() {
            instance?.stop()
        }

        /**
         * Stop Agelena and free memory
         */
        fun close() {
            stop()
            instance = null
        }

        fun setMessageListener(messageListener: MessageListener) {
            instance?.msgListener = messageListener
        }
        fun setStateListener(stateListener: StateListener) {
            instance?.stateListener = stateListener
        }

        /**
         * Return the unique ID for this device
         */
        fun getUserID() = instance?.userID ?: throw IllegalStateException("Agelena must first be initialized")

        /**
         * Return the public key that's sent to other users
         */
        fun getPublicKey() = instance?.keyPair?.publicKey ?: throw IllegalStateException("Agelena must first be initialized")
        /**
         * Return the private key used to decrypt messages
         */
        fun getPrivateKey() = instance?.keyPair?.privateKey ?: throw IllegalStateException("Agelena must first be initialized")

        internal fun getKeyPair() = instance?.keyPair ?: throw IllegalStateException("Agelena must first be initialized")

        /**
         * Return the [Config] used during initialization
         */
        fun getConfig() = instance?.config ?: throw IllegalStateException("Agelena must first be initialized")

        fun addDevice(id: Int, publicKey: PublicKey) {
            if (instance == null) throw IllegalStateException("Agelena must first be initialized")
            instance?.addDevice(id, publicKey)
        }

        /**
         * Send a message to another device
         *
         * If the device isn't in range, it'll try to use intermediates to reach it
         * By using this method, you can't be sure that the message will reach its recipient
         * The maximum size of an indirect message after compression is 590 kB.
         */
        fun sendMessage(message: Message) {
            if (instance == null) throw IllegalStateException("Agelena must first be initialized")
            instance?.sendMessage(message)
        }

        /**
         * Send a direct message to another device
         * The recipient must be in range otherwise the message will fail to send
         */
        fun sendDirectMessage(message: Message) {
            if (instance == null) throw IllegalStateException("Agelena must first be initialized")
            instance?.sendDirectMessage(message)
        }

        /**
         * Send a message with this [content] to all nearby devices and propagate to it to all their nearby devices
         *
         * The maximum size of a broadcast message after compression is 590 kB.
         * @param ttl The number of hops the message will do before stopping its propagation. Must be > 0
         */
        fun sendBroadcastMessage(content: Map<String, Any>, ttl: Int) {
            sendBroadcastMessage(Message.Builder().setContent(content).build(), ttl)
        }

        /**
         * Send a [message] to all nearby devices and propagate to it to all their nearby devices
         *
         * The maximum size of a broadcast message after compression is 590 kB.
         * @param ttl The number of hops the message will do before stopping its propagation. Must be > 0
         */
        fun sendBroadcastMessage(message: Message, ttl: Int) {
            if (instance == null) throw IllegalStateException("Agelena must first be initialized")
            instance?.sendBroadcastMessage(message, ttl)
        }

        internal fun getAppUUID(context: Context): UUID {
            context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).apply {
                try {
                    return UUID.fromString(
                        metaData.getString("ch.hepia.agelena.APP_UUID")
                            ?: throw Resources.NotFoundException("ch.hepia.agelena.APP_UUID not found in meta-data")
                    )
                } catch (e: IllegalArgumentException) {
                    throw Exception("ch.hepia.agelena.APP_UUID has invalid format", e)
                }
            }
        }

        /**
         * Called when a new device is connected
         */
        internal fun onDeviceConnected(device: Device) {
            instance?.onDeviceConnected(device)
        }

        /**
         * Called when a device is disconnected
         */
        internal fun onDeviceLost(id: Int) {
            instance?.onDeviceLost(id)
        }

        /**
         * Called when a message is received
         */
        internal fun onMessageReceived(message: Message, broadcast: Boolean) {
            instance?.onMessageReceived(message, broadcast)
        }

        internal fun onAckReceived(messageId: Int) {
            instance?.onAckReceived(messageId)
        }

        internal fun onStarted(success: Boolean) {
            instance?.onStarted(success)
        }

        /**
         * Send an action to the background service
         */
        internal fun sendToService(type: WorkType, bundle: Bundle? = null) {
            instance?.sendToService(type, bundle)
        }
    }
}