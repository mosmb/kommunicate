package io.mosmb.library.gatt

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class GattLink(
    private val device: BluetoothDevice,
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : GattController {

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 10_000L
    }

    private val commandMutex: Mutex = Mutex()
    private val job: Job = Job()
    private val scope: CoroutineScope = CoroutineScope(dispatcher + job)

    private var gatt: BluetoothGatt? = null

    private val gattState: MutableStateFlow<GattController.State> =
        MutableStateFlow(GattController.State.Disconnected)
    private val responseStream: Channel<GattResponse<*>> =
        Channel(CONFLATED)
    private val notificationStream: MutableSharedFlow<BluetoothGattCharacteristic> =
        MutableSharedFlow(extraBufferCapacity = Int.MAX_VALUE)

    override val state: StateFlow<GattController.State> =
        gattState.asStateFlow()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun connect(transport: Int, phy: Int) {
        requireState(GattController.State.Disconnected)

        if (gatt != null) {
            execute<Int> { connect() }
        } else {
            gatt =
                when {
                    Build.VERSION.SDK_INT >= 26 ->
                        device.connectGatt(
                            context,
                            false,
                            gattListener,
                            transport,
                            phy,
                        )

                    Build.VERSION.SDK_INT >= 23 ->
                        device.connectGatt(
                            context,
                            false,
                            gattListener,
                            transport,
                        )

                    else ->
                        device.connectGatt(
                            context,
                            false,
                            gattListener,
                        )
                }
        }

        requireNotNull(gatt) { "Cannot instantiate BluetoothGatt. Bluetooth must be enabled!" }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun disconnect() =
        execute<Unit> {
            disconnect()
            false
        }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun discoverServices(): List<BluetoothGattService> =
        execute { discoverServices() }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun requestMtu(value: Int): Int =
        execute { requestMtu(value) }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun readRssi(): Int =
        execute { readRemoteRssi() }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun readCharacteristic(
        characteristic: BluetoothGattCharacteristic,
    ): BluetoothGattCharacteristic =
        execute { readCharacteristic(characteristic) }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
    ): BluetoothGattCharacteristic =
        execute { writeCharacteristic(characteristic) }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun getService(uuid: UUID): BluetoothGattService? =
        suspendCoroutine { coroutine ->
            coroutine.resume(requireGatt().getService(uuid))
        }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun observeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
    ): Flow<BluetoothGattCharacteristic> =
        notificationStream
            .onStart {
                val isEnabled =
                    requireGatt().setCharacteristicNotification(characteristic, true)
                if (isEnabled) {
                    error("Cannot enable notifications for characteristic $characteristic")
                }
            }
            .filter { characteristic.uuid == it.uuid }
            .flowOn(dispatcher)

    private fun requireState(state: GattController.State) =
        require(gattState.value == state) {
            "Expected state $state but found ${gattState.value}."
        }

    private fun requireGatt(): BluetoothGatt =
        requireNotNull(gatt) { "Not connected. Call connect()." }

    private fun <T : Any> notifyResponse(gattResponse: GattResponse<T>) {
        scope.launch { responseStream.send(gattResponse) }
    }

    private suspend inline fun <reified T : Any> execute(
        crossinline command: BluetoothGatt.() -> Boolean,
    ): T = commandMutex.withLock {
        withContext(dispatcher) {
            val sent = requireGatt().command()
            if (!sent) error("Cannot send command.")
        }

        val response = withTimeout(DEFAULT_TIMEOUT_MS) {
            responseStream.receive()
        }

        if (!response.isSuccessful()) error("Command failed.")
        return@withLock response.value as? T ?: error("Unexpected response type")
    }

    private val gattListener = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt?,
            status: Int,
            newState: Int,
        ) {
            val state = GattController.State.fromBluetoothState(newState)
            scope.launch { gattState.emit(state) }

            ConnectionStateChanged(
                value = newState,
                status = status,
            ).run { notifyResponse(this) }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            ServicesDiscovered(
                value = gatt?.services.orEmpty(),
                status = status,
            ).run { notifyResponse(this) }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            MtuChanged(
                value = mtu,
                status = status,
            ).run { notifyResponse(this) }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            RssiRead(
                value = rssi,
                status = status,
            ).run { notifyResponse(this) }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            CharacteristicRead(
                value = characteristic,
                status = status,
            ).run { notifyResponse(this) }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            CharacteristicWrite(
                value = characteristic,
                status = status,
            ).run { notifyResponse(this) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            scope.launch { notificationStream.emit(characteristic) }
        }
    }
}
