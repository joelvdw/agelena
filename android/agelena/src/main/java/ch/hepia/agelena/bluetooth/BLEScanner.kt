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

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.util.Log
import ch.hepia.agelena.GlobalConfig.BLEConfig
import ch.hepia.agelena.utils.ByteConverter

private const val TAG = "BLEScanner"

/**
 * Scan BLE advertisement packets containing Agelena GATT service
 *
 * @since 0.1
 */
internal class BLEScanner(private val manager: BLEManager) {

    var isScanning: Boolean = false
        private set

    private val scanner = manager.bluetoothAdapter?.bluetoothLeScanner
    private val scanCallback = object : ScanCallback() {
        private val TAG = "ScanCallback"

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed. Error : $errorCode")
        }

        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if (result?.scanRecord?.serviceUuids?.contains(ParcelUuid(MessageService.getServiceUUID(manager.context))) == false) return

            result?.let {
                onDeviceFound(it)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            Log.d(TAG, "Batch size : ${results?.size}")
            results?.forEach { onScanResult(0, it) }
        }
    }

    private fun onDeviceFound(scanResult: ScanResult) {
        val payload = scanResult.scanRecord?.getServiceData(ParcelUuid(MessageService.SERVICE_DATA_UUID))
        if (payload == null || payload.size != 4) return

        manager.onDeviceScan(scanResult.device, ByteConverter.bytesToInt32(payload)!!)
    }

    fun start(): Boolean {
        if (scanner == null) return false
        if (isScanning) return true

        val settings = ScanSettings.Builder()
            .setScanMode(BLEConfig.SCAN_MODE)
            .build()

        scanner.startScan(listOf(), settings, scanCallback)
        Log.d(TAG, "Start scanning")
        isScanning = true
        return true
    }

    fun stop() {
        scanner?.stopScan(scanCallback)
        Log.d(TAG, "Stop scanning")
        isScanning = false
    }
}