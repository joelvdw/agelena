package ch.hepia.agelena.bluetooth

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import ch.hepia.agelena.Agelena
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import ch.hepia.agelena.GlobalConfig.BLEConfig
import ch.hepia.agelena.utils.ByteConverter

private const val TAG = "BLEServer"

/**
 * Manage server part of the Bluetooth implementation
 * Provide a GATT server and manage advertisement
 *
 * @since 0.1
 */
internal class BLEServer(private val context: Context, private val manager: BLEManager) {
    private val advertiser: BluetoothLeAdvertiser? by lazy { manager.bluetoothAdapter?.bluetoothLeAdvertiser }
    private var gattServer: BluetoothGattServer? = null
    private var advCallback: AdvertiseCallback? = null
    private var lastMessage: ByteArray? = null
    private val gattCallback by lazy {
        object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    manager.onDeviceDisconnection(device)
                }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice, requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean, responseNeeded: Boolean,
                offset: Int, value: ByteArray?
            ) {
                Log.d(TAG, value?.let { "Write request from $device : ${it.size} bytes" } ?: "empty")

                if (value == null) {
                    if (responseNeeded) {
                        gattServer?.sendResponse(
                            device, requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0, null
                        )
                    }
                    return
                }

                if (characteristic.uuid == MessageService.MESSAGE_UUID) {
                    if (!manager.isDeviceKnown(device)) {
                        manager.onClientDeviceConnected(device)
                    }

                    if (responseNeeded) {
                        gattServer?.sendResponse(
                            device, requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0, null
                        )
                    }
                    manager.onBlockReceived(device, value)
                }
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (characteristic.uuid == MessageService.MESSAGE_UUID) {
                    synchronized(this@BLEServer) {
                        if (lastMessage != null) {
                            characteristic.value = lastMessage
                            gattServer?.sendResponse(
                                device, requestId,
                                BluetoothGatt.GATT_SUCCESS,
                                0, lastMessage
                            )
                            lastMessage = null
                            return
                        }
                    }
                }

                gattServer?.sendResponse(
                    device, requestId,
                    BluetoothGatt.GATT_FAILURE,
                    0, null
                )
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int, descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean, responseNeeded: Boolean,
                offset: Int, value: ByteArray?
            ) {
                if (descriptor.uuid == MessageService.CCCD_UUID) {
                    if (responseNeeded) {
                        gattServer?.sendResponse(device,
                            requestId, BluetoothGatt.GATT_SUCCESS,
                            0, null)
                    }
                } else {
                    if (responseNeeded) {
                        gattServer?.sendResponse(device,
                            requestId, BluetoothGatt.GATT_FAILURE,
                            0, null)
                    }
                }
            }
        }
    }

    var isStarted: Boolean = false
        private set

    /**
     * Indicate if the server is currently advertising
     */
    var isAdvertising: Boolean = false
        private set

    fun startAdvertising() {
        if (advCallback != null) {
            val advBuilder = AdvertiseData.Builder()
                .setIncludeTxPowerLevel(false)
                .setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid(MessageService.getServiceUUID(context)))
                .addServiceData(
                    ParcelUuid(MessageService.SERVICE_DATA_UUID),
                    ByteConverter.int32ToBytes(manager.userID))

            val settingBuilder = AdvertiseSettings.Builder()
                .setAdvertiseMode(BLEConfig.ADVERTISE_MODE)
                .setTxPowerLevel(BLEConfig.POWER_MODE)
                .setConnectable(true)

            advertiser?.startAdvertising(
                settingBuilder.build(),
                advBuilder.build(),
                advCallback
            )
            isAdvertising = true
        }
    }

    private fun startServer(): Boolean {
        gattServer = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .openGattServer(context, gattCallback)
        if (gattServer == null) {
            return false
        }

        return gattServer?.addService(MessageService.createService(context)) ?: false
    }

    /**
     * Initialize the BLE GATT server and starts to advertise its services
     * Wait until everything is started
     */
    suspend fun start(): Boolean {
        if (advertiser == null) {
            Log.e(TAG, "Failed to create advertiser")
            return false
        }
        if (!startServer()) {
            Log.e(TAG, "Failed to create GATT server")
            return false
        }

        return suspendCoroutine {
            advCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    super.onStartSuccess(settingsInEffect)
                    Log.d(TAG, "Advertising started : ${settingsInEffect ?: ""}")
                    isStarted = true
                    it.resume(true)
                }

                override fun onStartFailure(errorCode: Int) {
                    super.onStartFailure(errorCode)
                    Log.e(TAG, "Advertising failed : $errorCode")
                    isStarted = false
                    it.resume(false)
                }
            }
            startAdvertising()
        }
    }

    /**
     * Send a message to the client using a GATT notification and a client GATT read
     *
     */
    fun sendMessage(device: BluetoothDevice, bytes: ByteArray) {
        lastMessage = bytes
        val char = gattServer?.getService(MessageService.getServiceUUID(context))
            ?.getCharacteristic(MessageService.MESSAGE_UUID)
        char?.value = bytes
        gattServer?.notifyCharacteristicChanged(
            device,
            char,
            true
        )
    }

    /**
     * Disconnect a currently connected or connecting device
     */
    fun disconnectDevice(device: BluetoothDevice) {
        gattServer?.cancelConnection(device)
    }

    fun stopAdvertising() {
        advertiser?.stopAdvertising(advCallback)
        isAdvertising = false
    }

    /**
     * Stop the GATT server and the advertising
     */
    fun stop() {
        stopAdvertising()
        gattServer?.close()
    }
}