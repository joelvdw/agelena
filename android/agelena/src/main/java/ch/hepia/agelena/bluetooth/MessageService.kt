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
