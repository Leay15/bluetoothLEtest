package com.example.bluetoothletest.viewModel

import android.app.Application
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.le.ScanResult
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.welie.blessed.BluetoothCentral
import com.welie.blessed.BluetoothCentralCallback
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.BluetoothPeripheral.GATT_SUCCESS
import com.welie.blessed.BluetoothPeripheralCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import java.util.*

class PowerMetterViewModel(application: Application) : AndroidViewModel(application) {

    companion object {

        const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb"

        const val BASE_BLUETOOTH_UUID = "00000000-0000-1000-8000-00805F9B34FB"
        const val CYCLING_POWER_SERVICE_UUID_STRING = "00001818-0000-1000-8000-00805F9B34FB"
        const val CYCLING_POWER_MEASUREMENT_UUID_STRING = "00002A63-0000-1000-8000-00805F9B34FB"
        const val CYCLING_POWER_CONTROL_POINT_UUID_STRING = "00002A66-0000-1000-8000-00805F9B34FB"
    }

    private val CYCLING_POWER_SERVICE = UUID.fromString(CYCLING_POWER_SERVICE_UUID_STRING)
    private val CYCLING_POWER_MEASUREMENT_UUID =
        UUID.fromString(CYCLING_POWER_MEASUREMENT_UUID_STRING)

    //To Zero reset feature
    private val CYCLING_POWER_CONTROL_POINT_UUID = UUID.fromString(
        CYCLING_POWER_CONTROL_POINT_UUID_STRING
    )

    val bluetoothDevicesList = MutableLiveData<MutableList<BluetoothPeripheral>>(mutableListOf())
    val isDeviceConnected = MutableLiveData(false)

    private var powerMetterDevice: BluetoothPeripheral? = null

    private val bluetoothCentralCallback = object : BluetoothCentralCallback() {
        override fun onConnectedPeripheral(peripheral: BluetoothPeripheral) {
            super.onConnectedPeripheral(peripheral)
            Log.e("GATT_CONNECTED", peripheral.name ?: peripheral.address)
        }

        override fun onConnectionFailed(peripheral: BluetoothPeripheral, status: Int) {
            super.onConnectionFailed(peripheral, status)
            Log.e("GATT_ERROR", "Device ${peripheral.name ?: peripheral.address} Status $status")
        }

        override fun onDisconnectedPeripheral(peripheral: BluetoothPeripheral, status: Int) {
            super.onDisconnectedPeripheral(peripheral, status)
            Log.e("GATT_DISCONNECT", peripheral.name ?: peripheral.address)
        }

        override fun onDiscoveredPeripheral(
            peripheral: BluetoothPeripheral,
            scanResult: ScanResult
        ) {
            val name = peripheral.name ?: "NONAME"
            if (name.contains("Stages") && (bluetoothDevicesList.value?.contains(peripheral) == false)) {
                Log.e("Found", name)
                val list = bluetoothDevicesList.value ?: mutableListOf()
                list.add(peripheral)
                bluetoothDevicesList.value = list
            } else {
                Log.e("Found", peripheral.name ?: "NONAME")
            }

            super.onDiscoveredPeripheral(peripheral, scanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
        }

        override fun onBluetoothAdapterStateChanged(state: Int) {
            super.onBluetoothAdapterStateChanged(state)
        }
    }

    private val bluetoothCentral: BluetoothCentral by lazy {
        BluetoothCentral(
            getApplication(),
            bluetoothCentralCallback,
            Handler(Looper.getMainLooper())
        )
    }

    private val peripheralCallback = object : BluetoothPeripheralCallback() {

        override fun onServicesDiscovered(peripheral: BluetoothPeripheral) {
            super.onServicesDiscovered(peripheral)
            Log.i(
                "GATT_SERVICES",
                String.format(
                    "SUCCESS: Services Discovered for %s",
                    peripheral.name ?: peripheral.address
                )
            )
            peripheral.getService(CYCLING_POWER_SERVICE)?.run {
                Log.i(
                    "GATT_SERVICES",
                    String.format(
                        "SUCCESS: Peripheral has the service"
                    )
                )
                powerMetterDevice = peripheral
                isDeviceConnected.postValue(true)
            }
        }

        override fun onNotificationStateUpdate(
            peripheral: BluetoothPeripheral,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onNotificationStateUpdate(peripheral, characteristic, status)

            if (status == GATT_SUCCESS) {
                if (peripheral.isNotifying(characteristic)) {
                    Log.i(
                        "GATT_EVENT",
                        String.format("SUCCESS: Notify set to 'on' for %s", characteristic.uuid)
                    )
                } else {
                    Log.i(
                        "GATT_EVENT",
                        String.format("SUCCESS: Notify set to 'off' for %s", characteristic.uuid)
                    )
                }
            } else {
                Log.i(
                    "GATT_ERROR",
                    String.format(
                        "ERROR: Changing notification state failed for %s",
                        characteristic.uuid
                    )
                )
            }
        }

        //Getting when some value is modified
        override fun onCharacteristicUpdate(
            peripheral: BluetoothPeripheral,
            value: ByteArray,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicUpdate(peripheral, value, characteristic, status)

            if (status == GATT_SUCCESS) {
                when (characteristic.uuid) {
                    CYCLING_POWER_MEASUREMENT_UUID -> {
                        Log.e("GATT_EVENT", "PowerMeasurement")
//                        Log.e("GATT_EVENT_DATA", "${}")


                    }
                    CYCLING_POWER_CONTROL_POINT_UUID -> {
                        Log.e("GATT_EVENT", "ControlPointEvent")
                    }
                }
            }
        }

        override fun onCharacteristicWrite(
            peripheral: BluetoothPeripheral,
            value: ByteArray,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(peripheral, value, characteristic, status)
        }

        override fun onDescriptorRead(
            peripheral: BluetoothPeripheral,
            value: ByteArray,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorRead(peripheral, value, descriptor, status)
        }

        override fun onDescriptorWrite(
            peripheral: BluetoothPeripheral,
            value: ByteArray,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorWrite(peripheral, value, descriptor, status)
        }
    }

    fun scanForPowerMetter() {

        if (bluetoothCentral.isScanning) bluetoothCentral.stopScan()
        bluetoothCentral.scanForPeripheralsWithServices(
            arrayOf(CYCLING_POWER_SERVICE)
        )
        viewModelScope.async(Dispatchers.IO) {
            delay(5000)
            bluetoothCentral.stopScan()
        }
    }

    fun connectToDevice(peripheral: BluetoothPeripheral) {
        bluetoothCentral.connectPeripheral(peripheral, peripheralCallback)
    }

    fun enableDataNotificationChanges(enable: Boolean) {
        powerMetterDevice?.run {
            val characteristic =
                this.getCharacteristic(CYCLING_POWER_SERVICE, CYCLING_POWER_MEASUREMENT_UUID)

            characteristic?.let {
                this.setNotify(characteristic, enable)
            }

        }
    }

    //Not sure if must to do it
    override fun onCleared() {
        super.onCleared()
        bluetoothCentral.close()
    }
}