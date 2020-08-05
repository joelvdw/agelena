/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2020  VON DER WEID Joël
 *
 * This file is part of Agelena.
 *
 * Agelena is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Agelena is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.hepia.agelena.bluetooth

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import ch.hepia.agelena.Agelena
import ch.hepia.agelena.Device
import ch.hepia.agelena.GlobalConfig
import ch.hepia.agelena.GlobalConfig.BLEConfig
import ch.hepia.agelena.db.FileManager
import ch.hepia.agelena.db.SQLite
import ch.hepia.agelena.message.MessageManager
import ch.hepia.agelena.message.MessageType
import ch.hepia.agelena.service.FrontReceiver
import ch.hepia.agelena.utils.*
import ch.hepia.agelena.utils.ByteConverter
import ch.hepia.agelena.utils.ExchangeKeyPair
import ch.hepia.agelena.utils.MessageKeyPair
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.math.BigInteger
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import javax.crypto.spec.DHParameterSpec
import kotlin.concurrent.thread
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.min

private const val TAG = "BLEManager"

/**
 * This class manages the Bluetooth part of the library
 * It uses a [Thread] and [Queue][LinkedBlockingQueue] to send messages one by one asynchronously
 * without blocking the main thread.
 *
 * @since 0.1
 */
internal class BLEManager(val context: Context, val userID: Int, private val publicKeyString: String, private val receiveCb: (Int, Int?, Boolean?, Boolean) -> Unit) {
    val GATT_INTERNAL_ERROR = 129

    /**
     * Device [Bluetooth adapter][BluetoothAdapter]
     */
    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private class PendingDevice(var device: GattDevice, var handshake: Handshake?, var builder: Handshake.Builder?, var keyPair: ExchangeKeyPair?)
    private class Node(val device: GattDevice, val bytes: ByteArray)
    private val sendingQueue: LinkedBlockingQueue<Node> = LinkedBlockingQueue()
    private val priorityQueue: LinkedBlockingQueue<Node> = LinkedBlockingQueue()
    private val devices: MutableMap<Int, GattDevice> = mutableMapOf()
    private val pendingDevices: MutableMap<String, PendingDevice?> = mutableMapOf()
    private val incompatibleDevices: MutableMap<String, Date> = mutableMapOf()
    private val scanner = BLEScanner(this)
    private val server = BLEServer(context, this)

    private var writeSuspend: Continuation<Unit>? = null
    @Volatile private var finish = false
    private val runnable = {
        while (!finish) {
            try {
                var it = priorityQueue.poll()
                if (it == null) {
                    it = sendingQueue.poll()
                }

                if (it != null) {
                    var data = if (it.bytes.size > BLEConfig.PAYLOAD_SIZE) {
                        it.bytes.sliceArray(0 until BLEConfig.PAYLOAD_SIZE)
                    } else it.bytes

                    val type = MessageManager.getMessageType(data)
                    data = if (it.device.session?.sessionKey != null && type != MessageType.HandshakeRequest && type != MessageType.HandshakeResponse) {
                        Encryptor.encryptSymmetric(it.device.session?.sessionKey!!, it.device.session?.sendingRandom!!, data)
                    } else data

                    if (it.device.isClient) {
                        server.sendMessage(it.device.device, data)
                    } else {
                        runBlocking<Unit> { suspendCoroutine { c ->
                            writeSuspend = c
                            it.device.messageChar?.value = data
                            it.device.gatt?.writeCharacteristic(it.device.messageChar)
                        } }
                    }
                } else {
                    Thread.sleep(50)
                }
            } catch (_: InterruptedException) {
            }
        }
    }
    private var sendThread: Thread? = null

    private val gattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to : ${gatt?.device}")
                    Thread.sleep(60)
                    gatt?.requestMtu(BLEConfig.MTU)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from : ${gatt?.device}")
                    scanner.start()
                    gatt?.device?.let {
                        onDeviceDisconnection(it)
                    }
                    gatt?.close()
                }
            } else {
                Log.e(TAG, "An error occurred : $status")
                scanner.start()
                gatt?.device?.let {
                    onDeviceDisconnection(it)
                }
                gatt?.close()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Successfully changed MTU with ${gatt?.device} to $mtu")
                Thread.sleep(60)
                gatt?.discoverServices()
            } else {
                Log.e(TAG, "Failed to change MTU with ${gatt?.device}")
                gatt?.disconnect()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == GATT_INTERNAL_ERROR) {
                Log.e(TAG, "Service discovery failed")
                gatt?.device?.let {
                    onDeviceDisconnection(it)
                }
                gatt?.close()
                return
            }

            val service = gatt?.getService(MessageService.getServiceUUID(context))
            if (service == null) {
                Log.d(TAG, "Failed to found message service in ${gatt?.device}")
            } else {
                Log.d(TAG, "Services get : OK")
                val msg: BluetoothGattCharacteristic? = service.getCharacteristic(MessageService.MESSAGE_UUID)
                msg?.let {
                    onDeviceConnected(gatt, msg)
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == MessageService.MESSAGE_UUID) {
                Log.d(TAG, "Notification request from ${gatt.device} : ${characteristic.value?.let { "${it.size} bytes" } ?: "empty"}")

                characteristic.value?.let {
                    onBlockReceived(gatt.device, it)
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            writeSuspend?.resume(Unit)
        }
    }

    var isStarted: Boolean = false
        private set

    /**
     * Return if [device] is already connected, connecting or incompatible
     */
    fun isDeviceKnown(device: BluetoothDevice) = (incompatibleDevices.contains(device.address) && (Calendar.getInstance().time.time - incompatibleDevices[device.address]!!.time) < GlobalConfig.INCOMPATIBILITY_TIMEOUT)
            || pendingDevices.containsKey(device.address)
            || devices.filter { it.value.device == device }.isNotEmpty()

    /**
     * Return if [device] is currently connected
     */
    fun isConnected(device: Int) = devices.keys.contains(device)

    /**
     * Return IDs of all connected devices
     */
    fun getConnectedDevices() = devices.keys

    /**
     * Connect to the GATT server of the scanned [device], if its ID is smaller (to avoid parallel handshakes)
     * Occurs only if the device isn't already connecting or connected
     */
    fun onDeviceScan(device: BluetoothDevice, id: Int) {
        if (!isDeviceKnown(device)) {
            if (userID < id) {
                synchronized(this) {
                    pendingDevices[device.address] = null
                }
                scanner.stop()
                Thread.sleep(100)
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            }
        }
    }

    /**
     * Called when a new device is scanned
     * Add a new device to the pending devices and start the handshake with it
     */
    fun onDeviceConnected(gatt: BluetoothGatt, msg: BluetoothGattCharacteristic) {
        val dev = GattDevice(gatt, msg)

        // Start handshake
        val key = ExchangeKeyPair.generateKeyPair()
        val h = Handshake(userID, publicKeyString, key)

        synchronized(this) {
            pendingDevices[gatt.device.address] = PendingDevice(dev, h, null, key)
        }
        gatt.setCharacteristicNotification(msg, true)
        Thread.sleep(80)

        sendHandshake(dev, h, true)
    }

    /**
     * Add a new pending device as a client device.
     */
    fun onClientDeviceConnected(device: BluetoothDevice) {
        synchronized(this) {
            pendingDevices[device.address] = PendingDevice(GattDevice(device), null, null, null)
        }
    }

    /**
     * Remove [device] from the connected devices and cancel all pending messages
     */
    @Synchronized fun onDeviceDisconnection(device: BluetoothDevice) {
        pendingDevices.remove(device.address)

        devices.entries.filter { it.value.device == device }.map { it.key }.firstOrNull()?.let {
            devices.remove(it)

            val i = Intent(FrontReceiver.ACTION_DEVICE)
            i.putExtra("app", Agelena.getAppUUID(context).toString())
            i.putExtra("id", it)
            i.putExtra("connected", false)
            context.sendBroadcast(i)
        }

        val list = mutableListOf<Node>()
        sendingQueue.forEach {
            if (it.device.device == device) {
                list.add(it)
            }
        }
        list.forEach {
            sendingQueue.remove(it)
        }

        list.clear()
        priorityQueue.forEach {
            if (it.device.device == device) {
                list.add(it)
            }
        }
        list.forEach {
            priorityQueue.remove(it)
        }
    }

    /**
     * Add [data] to the sending queue for the sending [Thread] to send it.
     * @param priority If the data have a high priority
     * @return False if the device isn't found
     */
    fun sendData(device: Int, data: ByteArray, priority: Boolean): Boolean {
        devices[device]?.let {
            if (priority) {
                priorityQueue.put(Node(it, data))
            } else {
                sendingQueue.put(Node(it, data))
            }
            return true
        }
        return false
    }

    /**
     * Send [data] to all connected devices using the sending [Thread]
     * @param priority If the data have a high priority
     */
    fun propagateData(data: ByteArray, exceptDevice: BluetoothDevice? = null, priority: Boolean): Boolean {
        devices.forEach { d ->
            if (d.value.device != exceptDevice) {
                if (priority) {
                    priorityQueue.put(Node(d.value, data))
                } else {
                    sendingQueue.put(Node(d.value, data))
                }
            }
        }
        return true
    }

    /**
     * Fragment and send a [handshake] to the [device]
     * @param isRequest Indicate if it's a handshake request or response
     */
    private fun sendHandshake(device: GattDevice, handshake: Handshake, isRequest: Boolean) {
        val bytes = handshake.toJson().toByteArray(BLEConfig.CHARSET)

        var i = 0
        while (i < bytes.size) {
            val block = bytes.sliceArray(
                i until min(i+(BLEConfig.PAYLOAD_SIZE-MessageType.HandshakeRequest.headerSize), bytes.size)
            )
            i += block.size

            priorityQueue.put(Node(device,
                MessageManager.addHandshakeHeader(block, isRequest, i >= bytes.size)))
        }
    }

    /**
     * Process a [packet][bytes] received from [sender]
     */
    fun onBlockReceived(sender: BluetoothDevice, bytes: ByteArray) {
        val device = devices.values.firstOrNull { it.device == sender }
        val data = if (device?.session?.sessionKey != null) {
            Encryptor.decryptSymmetric(device.session?.sessionKey!!, device.session?.receivingRandom!!, bytes)
        } else bytes

        when (MessageManager.getMessageType(data)) {
            MessageType.Unknown -> return
            MessageType.HandshakeRequest -> {
                val builder = pendingDevices[sender.address]?.builder ?: run {
                    val b = Handshake.Builder()
                    pendingDevices[sender.address]?.builder = b
                    b
                }
                builder.addMessagePart(MessageManager.getPayload(data))

                if (MessageManager.isLast(data)) {
                    val h = builder.build() ?: run { pendingDevices[sender.address]?.builder = null; return }
                    pendingDevices[sender.address]?.builder = null

                    // Manage the case when the handshake response failed and the client resend a handshake request
                    if (pendingDevices[sender.address] == null) {
                        onDeviceDisconnection(sender)
                        onClientDeviceConnected(sender)
                    }

                    // Generate DH key pair using given g and p
                    val keyPair = if (h.g != null && h.p != null) {
                        ExchangeKeyPair.generateKeyPair(DHParameterSpec(BigInteger(h.p), BigInteger(h.g)))
                    } else null

                    pendingDevices[sender.address]?.keyPair = keyPair
                    val response = Handshake(h.sessionID, userID, publicKeyString, keyPair)
                    sendHandshake(pendingDevices[sender.address]?.device!!, response, false)

                    // Verify version compatibility
                    if (h.isVersionCompatible() && h.sessionKey != null) {
                        endHandshake(sender, h)
                    } else {
                        incompatibleDevices[sender.address] = Calendar.getInstance().time
                        pendingDevices.remove(sender.address)
                        Log.w(TAG, " v${Agelena.PROTOCOL_VERSION} incompatible with ${sender.address}: v${h.version}")
                    }
                }
            }
            MessageType.HandshakeResponse -> {
                val builder = pendingDevices[sender.address]?.builder ?: run {
                    val b = Handshake.Builder()
                    pendingDevices[sender.address]?.builder = b
                    b
                }
                builder.addMessagePart(MessageManager.getPayload(data))

                if (MessageManager.isLast(data)) {
                    val h = builder.build()
                    pendingDevices[sender.address]?.builder = null
                    if (h == null || h.sessionID != pendingDevices[sender.address]?.handshake?.sessionID) {
                        return
                    }

                    // Verify compatibility
                    if (h.isVersionCompatible() && h.sessionKey != null) {
                        endHandshake(sender, h)
                    } else {
                        incompatibleDevices[sender.address] = Calendar.getInstance().time
                        disconnectDevice(pendingDevices[sender.address]!!.device)
                        pendingDevices.remove(sender.address)
                        Log.w(TAG, " v${Agelena.PROTOCOL_VERSION} incompatible with ${sender.address}: v${h.version}")
                    }
                }
            }
            MessageType.Acknowledgment -> {
                val resend = mutableListOf<Int>()

                val bs = ByteArray(4)
                var cpt = 0
                MessageManager.getPayload(data).forEach {
                    bs[cpt] = it
                    cpt++
                    if (cpt == 4) {
                        val id = ByteConverter.bytesToInt32(bs)!!
                        if (SQLite.getInstance(context).isAckWaited(id)) {
                            SQLite.getInstance(context).removeAck(id)

                            receiveCb(id, null, null, true)
                        } else {
                            if (SQLite.getInstance(context).insertAck(id, false)) {
                                resend.add(id)
                            }
                        }
                        cpt = 0
                    }
                }

                sendAcks(-1, resend, device)
            }
            else -> {
                if (device == null) return

                MessageManager.processBlock(this, sender, data, device.session!!, receiveCb)
            }
        }
    }

    /**
     * Validate handshake, create session and inform user that a new device is connected
     *
     * Then send all stored blocks to this device to propagate broadcast and indirect messages
     */
    private fun endHandshake(device: BluetoothDevice, handshake: Handshake) {
        scanner.start()

        val gd = pendingDevices[device.address]!!
        pendingDevices.remove(device.address)
        gd.device.session = Session(
            context,
            handshake.sessionID,
            handshake.userID,
            gd.keyPair?.generateSecretKey(handshake.sessionKey!!),
            userID
        )
        devices[handshake.userID] = gd.device
        val d = Device(handshake.userID, MessageKeyPair.publicKeyFromString(handshake.userPubKey), true)
        SQLite.getInstance(context).insertDevice(d)
        Log.d(TAG, "Session ${gd.device.session!!.id} initialized with ${device.address}")

        val i = Intent(FrontReceiver.ACTION_DEVICE)
        i.putExtra("app", Agelena.getAppUUID(context).toString())
        i.putExtra("id", d.id)
        i.putExtra("connected", true)
        context.sendBroadcast(i)

        // Send all stored acks
        sendAcks(handshake.userID, SQLite.getInstance(context).getSendableAcks())

        GlobalScope.launch {
            // Send all stored blocks
            SQLite.getInstance(context).getAllSendableBlocks(userID).forEach {
                FileManager.getInstance(context).readBlock(it.messageID, it.seqNumber)?.let { data ->
                    MessageManager.sendBlock(
                        this@BLEManager,
                        d.id,
                        it,
                        data
                    )

                    // If the receiver is reached, remove the block to save space
                    if (it.receiverID == d.id) {
                        SQLite.getInstance(context).removeBlock(it.messageID, it.seqNumber)
                        FileManager.getInstance(context).removeBlock(it.messageID, it.seqNumber)
                    }
                }
            }
        }
    }

    /**
     * Send a list of [acks] to [device] or to all devices(excepted [exceptDevice]) if [device] isn't connected
     */
    fun sendAcks(device: Int, acks: List<Int>, exceptDevice: GattDevice? = null) {
        if (acks.isNotEmpty() && devices.isNotEmpty()) {
            val d = devices[device]

            val ds = if (d != null) listOf(d) else devices.values
            val blocks = mutableListOf<ByteArray>()

            // Build blocks
            val max = BLEConfig.PAYLOAD_SIZE - MessageType.Acknowledgment.headerSize / 4
            val bytes = ByteArray(max * 4)
            var cpt = 0
            for (i in acks.indices) {
                val s = cpt * 4
                val b = ByteConverter.int32ToBytes(acks[i])
                bytes[s] = b[0]
                bytes[s + 1] = b[1]
                bytes[s + 2] = b[2]
                bytes[s + 3] = b[3]

                cpt++
                if (i == acks.indices.last || cpt == max) {
                    blocks.add(MessageManager.addAckHeader(bytes.sliceArray(0 until (cpt * 4))))
                    cpt = 0
                }
            }

            // Send blocks
            ds.filter { it != exceptDevice }.forEach {
                blocks.forEach { b ->
                    sendingQueue.put(Node(it, b))
                }
            }
        }
    }

    private fun disconnectDevice(device: GattDevice) {
        if (device.isClient) {
            server.disconnectDevice(device.device)
        } else {
            device.gatt?.disconnect()
        }
    }

    /**
     * Start the BLE GATT server and the BLE scanner
     */
    suspend fun start(): Boolean {
        if (isStarted) return true

        if (!scanner.start()) {
            isStarted = false
            return false
        }

        isStarted = server.start()
        if (isStarted) {
            if (sendThread != null) {
                finish = true
                sendThread?.interrupt()
                delay(50)
            }
            finish = false
            sendThread = thread(start = true, block = runnable)
        } else {
            scanner.stop()
        }

        return isStarted
    }

    /**
     * Stop the working thread, the server and the scanner
     */
    fun close() {
        finish = true
        sendThread?.interrupt()
        sendThread?.join()
        sendThread = null
        server.stop()
        scanner.stop()

        Log.d(TAG, "Closed")
    }

    companion object {
        /**
         * Returns if Bluetooth and location (needed for BLE) are enabled
         */
        fun isBluetoothEnabled(context: Context) = (BluetoothAdapter.getDefaultAdapter()?.isEnabled ?: false) && run {
            val loc = (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
            loc.isProviderEnabled(LocationManager.GPS_PROVIDER) || loc.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
        /**
         * Check for all needed permissions of the library
         * (BLUETOOTH, BLUETOOTH_ADMIN and ACCESS_FINE_LOCATION)
         */
        fun checkPermissions(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADMIN
            ) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

        /**
         * Check if Bluetooth and Bluetooth LE are supported
         */
        fun checkBluetoothSupport(context: Context): Boolean {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                Log.e(TAG, "Bluetooth is not supported")
                return false
            }

            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                Log.e(TAG, "Bluetooth LE is not supported")
                return false
            }

            if (bluetoothAdapter.bluetoothLeAdvertiser == null) {
                Log.e(TAG, "Bluetooth LE advertising is not supported")
                return false
            }

            if (bluetoothAdapter.bluetoothLeScanner == null) {
                Log.e(TAG, "Bluetooth LE advertising is not supported")
                return false
            }

            return true
        }
    }
}