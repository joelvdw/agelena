package ch.hepia.agelena.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic

/**
 * Contain useful data about a connected BLE device
 *
 * @since 0.1
 */
internal class GattDevice private constructor(
    val device: BluetoothDevice,
    val gatt: BluetoothGatt?,
    val messageChar: BluetoothGattCharacteristic?,
    val isClient: Boolean
) {
    constructor(gatt: BluetoothGatt, messageChar: BluetoothGattCharacteristic) : this(gatt.device, gatt, messageChar, false)
    constructor(device: BluetoothDevice) : this(device, null, null, true)

    var session: Session? = null
}