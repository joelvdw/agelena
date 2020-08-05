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