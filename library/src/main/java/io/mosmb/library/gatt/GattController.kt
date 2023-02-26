package io.mosmb.library.gatt

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

interface GattController {

    enum class State {
        Connected,
        Disconnected,
        Connecting,
        Disconnecting;

        companion object {
            internal fun fromBluetoothState(state: Int) =
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> Connected
                    BluetoothProfile.STATE_CONNECTING -> Connecting
                    BluetoothProfile.STATE_DISCONNECTING -> Disconnecting
                    else -> Disconnected
                }
        }
    }

    val state: StateFlow<State>

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun connect(transport: Int, phy: Int)

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun disconnect()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun discoverServices(): List<BluetoothGattService>

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun requestMtu(value: Int): Int

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun readRssi(): Int

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun readCharacteristic(
        characteristic: BluetoothGattCharacteristic,
    ): BluetoothGattCharacteristic

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
    ): BluetoothGattCharacteristic

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun getService(uuid: UUID): BluetoothGattService?

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun observeCharacteristic(characteristic: BluetoothGattCharacteristic): Flow<BluetoothGattCharacteristic>
}

fun GattController(
    device: BluetoothDevice,
    context: Context
): GattController = GattLink(device, context)
