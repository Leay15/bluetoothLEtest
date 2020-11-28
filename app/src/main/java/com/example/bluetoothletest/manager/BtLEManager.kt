package com.example.bluetoothletest.manager

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.util.Log
import com.example.bluetoothletest.BuildConfig
import com.example.bluetoothletest.viewModel.BluetoothViewModel
import no.nordicsemi.android.ble.BleManager
import java.util.*

class BtLEManager private constructor(context: Context) :
    BleManager(context) {

    constructor(
        serviceUUID: UUID,
        context: Context,
        vararg characteristicUUID: UUID
    ) : this(context) {
        this.serviceUUID = serviceUUID
        this.characteristicsUUID = characteristicUUID
    }

    lateinit var serviceUUID: UUID
    lateinit var characteristicsUUID: Array<out UUID>
    val clientCharacteristics: MutableList<BluetoothGattCharacteristic> = mutableListOf()

    private val managerGattCallback by lazy {
        Callback(
            serviceUUID,
            characteristicsUUID,
            clientCharacteristics
        )
    }


    override fun getGattCallback(): BleManagerGattCallback = managerGattCallback

    override fun log(priority: Int, message: String) {
        super.log(priority, message)
        if (BuildConfig.DEBUG || priority == Log.ERROR) {
            Log.println(priority, "MyBleManager", message);
        }

    }

    private class Callback(
        val serviceUUID: UUID,
        val characteristicsUUID: Array<out UUID>,
        val characteristicsList: MutableList<BluetoothGattCharacteristic>
    ) : BleManagerGattCallback() {

        // This method will be called when the device is connected and services are discovered.
        // You need to obtain references to the characteristics and descriptors that you will use.
        // Return true if all required services are found, false otherwise.
        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            val gattService = gatt.getService(serviceUUID)

            gattService?.run {
                //Cycling_Power_Service Characteristic
                val cyclingPowerMeasurementCharacteristic =
                    gattService.getCharacteristic(characteristicsUUID[0])

                var notify = false
                cyclingPowerMeasurementCharacteristic?.run {
                    val properties = cyclingPowerMeasurementCharacteristic.properties
                    notify = (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                    characteristicsList.add(this)
                }

                //Client Characteristic Configuration Descriptor
                var descriptorRequest = false
                val descriptor =
                    cyclingPowerMeasurementCharacteristic.getDescriptor(characteristicsUUID[1])
                        ?.apply {
                            value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        }

                descriptorRequest = gatt.setCharacteristicNotification(
                    cyclingPowerMeasurementCharacteristic, true
                ) and gatt.writeDescriptor(descriptor)

                //add characteristic for control Point

                return cyclingPowerMeasurementCharacteristic != null && descriptor != null && notify && descriptorRequest
            } ?: return false
        }

        override fun onDeviceDisconnected() {
            characteristicsList.clear()
        }

        override fun initialize() {


            super.initialize()
        }
    }
}