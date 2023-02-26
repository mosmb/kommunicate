package io.mosmb.library.gatt

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService

sealed interface GattResponse<T : Any> {
    val status: Int
    val value: T

    fun isSuccessful(): Boolean =
        status == BluetoothGatt.GATT_SUCCESS
}

data class ConnectionStateChanged(
    override val value: Int,
    override val status: Int,
) : GattResponse<Int>

data class MtuChanged(
    override val value: Int,
    override val status: Int,
) : GattResponse<Int>

data class ServicesDiscovered(
    override val value: List<BluetoothGattService>,
    override val status: Int,
) : GattResponse<List<BluetoothGattService>>

data class RssiRead(
    override val value: Int,
    override val status: Int,
) : GattResponse<Int>

data class CharacteristicRead(
    override val value: BluetoothGattCharacteristic,
    override val status: Int,
) : GattResponse<BluetoothGattCharacteristic>

data class CharacteristicWrite(
    override val value: BluetoothGattCharacteristic,
    override val status: Int,
) : GattResponse<BluetoothGattCharacteristic>
