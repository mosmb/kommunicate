package io.mosmb.library.scanner

import android.Manifest
import android.bluetooth.le.ScanResult
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.Flow

/**
 * A BLE scanner which supports Kotlin coroutines.
 */
interface Scanner {
    /**
     * Launch a BLE scan.
     */
    @RequiresPermission(
        allOf = [
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ]
    )
    fun scan(): Flow<ScanResult>
}

/**
 * Create a [Scanner].
 */
fun Scanner(builder: ScannerBuilder.() -> Unit): Scanner =
    ScannerBuilder()
        .apply(builder)
        .build()
