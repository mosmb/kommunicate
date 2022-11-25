package io.mosmb.library.scanner

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

internal class ScannerImp(
    private val settings: ScanSettings,
    private val filters: List<ScanFilter> = listOf(),
) : Scanner {
    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        ?: error("Bluetooth not supported or not enabled!")

    @RequiresPermission(
        allOf = [
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ]
    )
    override fun scan(): Flow<ScanResult> = callbackFlow {
        val scanListener = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                this@callbackFlow.trySend(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(this@callbackFlow::trySend)
            }

            override fun onScanFailed(errorCode: Int) {
                this@callbackFlow.close(Throwable("Scan failed: $errorCode"))
            }
        }

        bluetoothAdapter
            .bluetoothLeScanner
            .startScan(filters, settings, scanListener)

        awaitClose {
            try {
                bluetoothAdapter.bluetoothLeScanner.stopScan(scanListener)
            } catch (e: Exception) {
                // stopScan() will throw if Bluetooth has been disabled.
                // We just ignore this Exception.
            }
        }
    }.flowOn(Dispatchers.IO)
}
