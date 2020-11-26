package com.example.bluetoothletest.viewModel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.bluetooth.BluetoothDevice.*
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_BROADCAST
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Context.BLUETOOTH_SERVICE
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.bluetoothletest.receivers.BluetoothBroadcastReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.reflect.InvocationTargetException
import java.util.*

//The bluetooth permission is granted by PM
@SuppressLint("MissingPermission")
class BluetoothViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        //16bits UUID for BTLE services
//        const val CYCLING_POWER_SERVICE = 0x2A63
//        const val CYCLING_POWER_CONTROL_POINT = 0x2A66

        const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb"

        const val BASE_BLUETOOTH_UUID = "00000000-0000-1000-8000-00805F9B34FB"
        const val CYCLING_POWER_SERVICE_UUID = "00002A63-0000-1000-8000-00805F9B34FB"
        const val CYCLING_POWER_CONTROL_POINT_UUID = "00002A66-0000-1000-8000-00805F9B34FB"
    }

    val bluetoothLECommandQueue: Queue<Runnable> = LinkedList()
    val commandQueueBussy = false


    val newBluetoothDevice = MutableLiveData<BluetoothDevice?>(null)
    val bluetoothDevicesList = MutableLiveData<MutableList<BluetoothDevice>>(mutableListOf())
    val bondedProcessResult = MutableLiveData(-1)
    val isDeviceFound = MutableLiveData<Boolean>(null)
    val errorOccurred = MutableLiveData<Boolean>(null)

    val connectedDevice = MutableLiveData<BluetoothDevice?>(null)
    val pairedDevices = MutableLiveData<MutableList<BluetoothDevice>>(mutableListOf())

    val isDeviceConnected = MutableLiveData<Boolean>(false)

    val broadcastReceiver: BluetoothBroadcastReceiver by lazy { BluetoothBroadcastReceiver() }
    var bluetoothManager =
        (application as Context).getSystemService(BLUETOOTH_SERVICE) as BluetoothManager

    private var bluetoothA2dp: BluetoothA2dp? = null

    private var selectedDevice: BluetoothDevice? = null
    private var connected = false
    private val bluetoothProfileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.A2DP) {
                Log.e("Disconnected", "A2DP")
            }
        }

        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.A2DP) {
                bluetoothA2dp = proxy as BluetoothA2dp
//                val method = bluetoothA2dp?.javaClass?.getDeclaredMethod(
//                    "connect",
//                    BluetoothDevice::class.java
//                )
//                method?.run {
//                    invoke(bluetoothA2dp, selectedDevice)
//                }
                if (bluetoothA2dp?.getConnectionState(selectedDevice) == BluetoothProfile.STATE_CONNECTED) {
                    connectedDevice.value = selectedDevice
                    connected = true
                } else {
                    if (connect()) {
                        Log.e("BT", "Connected")
                        connectedDevice.value = selectedDevice
                        connected = true
                    } else {
                        Log.e("BT", "Failed")
                        connectedDevice.value = null
                        connected = false
                    }
                }
            }
        }
    }

    private val gattCallback2 = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (status == GATT_SUCCESS) {

                when (newState) {
                    BluetoothGatt.STATE_CONNECTED -> {
                        gatt?.run {
                            val bondState = device.bondState

                            if (bondState == BOND_NONE || bondState == BOND_BONDED) {

                                Log.e("GATT_STATUS", "CONNECTED")
                                //Check if this implementation works
                                viewModelScope.async {
                                    delay(100)
//                                    Try to get characteristic directly
                                    //                                    discoverServices()
                                    isDeviceConnected.postValue(true)
                                }
                            } else {
                                Log.i("BT", "Waiting for bonding complete")
                            }
                        }
                    }
                    BluetoothGatt.STATE_CONNECTING -> {
                        Log.e("GATT_STATUS", "CONNECTING")
                    }
                    else -> {
                        Log.e("GATT_STATUS", "$newState")
                        gatt?.close()
                    }
                }
            } else {
                gatt?.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            when (status) {
                GATT_SUCCESS -> {
                    Log.e(
                        "GATT_DISCOVERED",
                        "onServicesDiscovered received: DISCOVERED"
                    )
                    val servicesList = gatt!!.services
                    Log.e(
                        "GATT_SERVICES",
                        servicesList.map { it.characteristics.toString() }.toString()
                    )
                }
                else -> {
                    Log.e("GATT_DISCOVERED", "onServicesDiscovered received: $status")
                    gatt?.close()
                }
            }

        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.e("GATT_CHARACTERISTICS", characteristic.toString())
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            Log.e("GATT_CHARACTERISTIC", characteristic?.value.toString())
        }


    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if (status == BluetoothGatt.STATE_CONNECTED) {
                connectedDevice.value = selectedDevice
                connected = true
            } else {
                connectedDevice.value = null
                connected = false
            }
        }
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }

    init {
        bluetoothAdapter?.run {
            enable()
//            pairedDevices.value = bondedDevices.toList().toMutableList()
//        }
//        broadcastReceiver.boundingCallback = {
//            bondedProcessResult.value = it.bondState
//        }
//        broadcastReceiver.connectedCallback = { connected, device ->
//            Log.e("Connected", connected.toString())
//            Log.e("Device", device.address.toString())
//        }
//        bluetoothAdapter?.getProfileProxy(
//            (getApplication()),
//            bluetoothProfileListener,
//            BluetoothProfile.A2DP
//        )
        }
    }

    val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    var scanning = false

    //    val handler = Handler()
    val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.run {
                val name = device?.name ?: "NONAME"
                if (name.contains("Stages") && (bluetoothDevicesList.value?.contains(device) == false)) {
                    Log.e("Found", result.device.name)
                    val list = bluetoothDevicesList.value ?: mutableListOf()
                    list.add(this.device)
                    bluetoothDevicesList.value = list
                    newBluetoothDevice.value = this.device
                    isDeviceFound.value = true
                } else {
                    Log.e("Found", "NOTPOWERMETER")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("ScanError", "BluetoothScanError")
        }
    }

    fun startBluetoothLEDevicesScan() {
        if (!scanning) {
            scanning = true
            bluetoothLeScanner?.startScan(leScanCallback)
            viewModelScope.async(Dispatchers.IO) {
                delay(3000)
                bluetoothLeScanner?.stopScan(leScanCallback)
            }
        } else {
            scanning = false
            bluetoothLeScanner?.stopScan(leScanCallback)
        }
    }

    fun startBluetoothDiscovery() {
        bluetoothAdapter?.run {
            Log.e("Initializing", "True")
            if (isDiscovering) {
                cancelDiscovery()
            }
            startDiscovery()

            broadcastReceiver.extraDeviceCallback = { bluetoothDevice ->
                Log.e("Found", bluetoothDevice.name)
                val list = bluetoothDevicesList.value ?: mutableListOf()
                list.add(bluetoothDevice)
                bluetoothDevicesList.value = list
                newBluetoothDevice.value = bluetoothDevice
                isDeviceFound.value = true
            }

            viewModelScope.launch(Dispatchers.Default) {
                delay(30000)
                if (newBluetoothDevice.value == null) {
                    isDeviceFound.postValue(false)
                }
            }
        }
    }

    fun stopBluetoothDiscovery() {
        bluetoothAdapter?.cancelDiscovery()
    }

    fun pairAttempt(bluetoothDevice: BluetoothDevice) {
        if (bluetoothDevice.type == BluetoothDevice.DEVICE_TYPE_LE) {
            bluetoothDevice.connectGatt(getApplication() as Context, true, gattCallback)
        } else if (bluetoothDevice.type == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
            bluetoothDevice.createBond()
        }
    }

    fun attemptConnection(bluetoothDevice: BluetoothDevice) {
        if (selectedDevice?.address == bluetoothDevice.address) {
            connectedDevice.value = null
            selectedDevice = null
            connected = false
        } else {
            selectedDevice = bluetoothDevice
            bluetoothAdapter?.getProfileProxy(
                (getApplication()),
                bluetoothProfileListener,
                BluetoothProfile.A2DP
            )
            viewModelScope.launch {
                delay(5000)
                if (!connected) {
                    connectedDevice.value = null
                    selectedDevice = null
                    bluetoothAdapter?.closeProfileProxy(BluetoothProfile.A2DP, bluetoothA2dp)
                }
            }
        }
    }

    var bluetoothGatt2: BluetoothGatt? = null
    fun connectGattDevice(bluetoothDevice: BluetoothDevice) {
        if (scanning) {
            bluetoothLeScanner?.stopScan(leScanCallback)
        }
        bluetoothGatt2 =
            bluetoothDevice.connectGatt(getApplication(), false, gattCallback2, TRANSPORT_LE)
    }

    //Used for reconnect to known device
    fun reconnectToGattDevice(bluetoothDevice: BluetoothDevice) {
        if (scanning) {
            bluetoothLeScanner?.stopScan(leScanCallback)
        }
        bluetoothGatt2 =
            bluetoothDevice.connectGatt(getApplication(), true, gattCallback2, TRANSPORT_LE)
    }

    fun getConnectedDevice(): List<BluetoothDevice> =
        (bluetoothA2dp?.run {
            connectedDevices?.run {
                this
            }
        } ?: emptyList())

    //This App will run only on API25
    @SuppressLint("DiscouragedPrivateApi")
    fun connect(): Boolean {
        bluetoothA2dp?.run {
            try {
                Log.e("BT", "Connecting")
                val method = BluetoothA2dp::class.java.getDeclaredMethod(
                    "connect",
                    BluetoothDevice::class.java
                )
                return method.invoke(this, selectedDevice) as Boolean? ?: false
            } catch (ie: IllegalAccessException) {
                Log.e("BTError", ie.toString())
            } catch (ine: InvocationTargetException) {
                Log.e("BTError", ine.toString())
            } catch (nsme: NoSuchMethodException) {
                Log.e("BTError", nsme.toString())
            }
            return false
        } ?: return false
    }

    fun reset() {
        newBluetoothDevice.value = null
        bluetoothDevicesList.value = mutableListOf()
    }

    fun readCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        bluetoothGatt2?.run {
            //We supose the characteristic is READABLE


        }

        return true
    }

    fun enableDataNotification(enable: Boolean) {
        val characteristic = BluetoothGattCharacteristic(
            UUID.fromString(
                CYCLING_POWER_SERVICE_UUID
            ),
            PROPERTY_BROADCAST,
            0
        )

        bluetoothGatt2?.setCharacteristicNotification(
            characteristic, enable
        )

        val descriptor = characteristic.getDescriptor(UUID.fromString(CCC_DESCRIPTOR_UUID)).apply {
            value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        bluetoothGatt2?.writeDescriptor(descriptor)
    }

    //For cases when power metter has firmware updates
    private fun clearServicesCache(): Boolean {
        return try {
            val refreshMethod = bluetoothGatt2!!.javaClass.getMethod("refresh")
            refreshMethod.run {
                invoke(bluetoothGatt2) as Boolean
            } ?: false
        } catch (e: Exception) {
            Log.e("GATT_REFRESH", e.localizedMessage)
            false
        }
    }

}