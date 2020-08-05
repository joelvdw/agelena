package ch.hepia.agelena.bluetooth

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import ch.hepia.agelena.Agelena
import java.util.*

/**
 * Store useful information for the GATT Message service
 *
 * @since 0.1
 */
internal object MessageService {
    val MESSAGE_UUID: UUID = UUID.fromString("f3462ac9-0b04-4aec-2442-982c3f8cbec3")
    val SERVICE_DATA_UUID: UUID = UUID.fromString("00002442-0000-1000-8000-00805f9b34fb")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    fun getServiceUUID(context: Context): UUID = Agelena.getAppUUID(context)

    /**
     * Create the Message GATT service with its characteristics
     */
    fun createService(context: Context): BluetoothGattService {
        val service = BluetoothGattService(getServiceUUID(context), BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val msg = BluetoothGattCharacteristic(
            MESSAGE_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE
                    or BluetoothGattCharacteristic.PROPERTY_READ
                    or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ)
        val desc = BluetoothGattDescriptor(CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_WRITE
                    or BluetoothGattDescriptor.PERMISSION_READ)
        msg.addDescriptor(desc)
        service.addCharacteristic(msg)

        return service
    }
}
