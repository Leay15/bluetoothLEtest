package com.example.bluetoothletest.service

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.os.*
import android.util.Log
import com.welie.blessed.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class PowerMeterService : ServiceScope() {

    companion object {
        const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb"

        const val BASE_BLUETOOTH_UUID = "00000000-0000-1000-8000-00805F9B34FB"

        //        Stages power meter
        const val CYCLING_POWER_SERVICE_UUID_STRING = "00001818-0000-1000-8000-00805F9B34FB"
        const val CYCLING_POWER_MEASUREMENT_UUID_STRING = "00002A63-0000-1000-8000-00805F9B34FB"
        const val CYCLING_POWER_CONTROL_POINT_UUID_STRING = "00002A66-0000-1000-8000-00805F9B34FB"

        //        Fitek bt sensor
        const val FITNESS_MACHINE_SERVICE_UUID_STRING = "00001826-0000-1000-8000-00805F9B34FB"
        const val FITNESS_MACHINE_FEATURE_UUID_STRING = "00002ACC-0000-1000-8000-00805F9B34FB"
        const val INDOOR_BIKE_DATA_CHARACTERISTIC_UUID_STRING =
            "00002AD2-0000-1000-8000-00805F9B34FB"
//        const val INDOOR_BIKE_DESCRIPTOR_UUID_STRING = "00002902-0000-1000-8000-00805F9B34FB"

        const val DEVICE_INFORMATION_SERVICE_UUID_STRING = "0000180A-0000-1000-8000-00805F9B34FB"
        const val FIRMWARE_REVISION_STRING_UUID_STRING = "00002A26-0000-1000-8000-00805F9B34FB"

        const val START_ENHANCED_OFFSET_COMPENSATION = 0x10

    }

    var onPeripheralFound: ((BluetoothPeripheral) -> Unit)? = null
    var onPeripheralConnected: ((BluetoothPeripheral) -> Unit)? = null

    var onPowerMeterDataReceived: ((power: Int, crankRevs: Int, crankPeriod1_1024: Int) -> Unit)? =
        null
    var onSensorDataReceived: ((power: Int, avgCadence: Int) -> Unit)? = null

    var onCalibrationDataReceived: ((adc: Int) -> Unit)? = null

    var onFirmwareVersionReceived: ((firmwareVersion: String) -> Unit)? = null

    var onConnectionError: (() -> Unit)? = null

    var hasCyclingService: Boolean = false
        private set

    var hasFitnessMachineService: Boolean = false
        private set

    val isPeripheralConnected
        get() = bluetoothPeripheralDevice != null

    private val CYCLING_POWER_SERVICE =
        UUID.fromString(CYCLING_POWER_SERVICE_UUID_STRING)
    private val CYCLING_POWER_MEASUREMENT_UUID =
        UUID.fromString(CYCLING_POWER_MEASUREMENT_UUID_STRING)
    private val DEVICE_INFORMATION_SERVICE =
        UUID.fromString(DEVICE_INFORMATION_SERVICE_UUID_STRING)

    //To Zero reset feature
    private val CYCLING_POWER_CONTROL_POINT_UUID = UUID.fromString(
        CYCLING_POWER_CONTROL_POINT_UUID_STRING
    )

    //To read Firmware version
    private val FIRMWARE_REVISION_STRING_UUID =
        UUID.fromString(FIRMWARE_REVISION_STRING_UUID_STRING)

    private val FITNESS_MACHINE_SERVICE_UUID =
        UUID.fromString(FITNESS_MACHINE_SERVICE_UUID_STRING)
    private val FITNESS_MACHINE_FEATURE =
        UUID.fromString(FITNESS_MACHINE_FEATURE_UUID_STRING)
    private val INDOOR_BIKE_DATA_CHARACTERISTIC_UUID =
        UUID.fromString(INDOOR_BIKE_DATA_CHARACTERISTIC_UUID_STRING)


    private val binder = PowerMeterBinder()

    private var bluetoothPeripheralDevice: BluetoothPeripheral? = null

    private val bluetoothCentralCallback = object : BluetoothCentralManagerCallback() {
        override fun onConnectedPeripheral(peripheral: BluetoothPeripheral) {
            super.onConnectedPeripheral(peripheral)
            Log.e("GATT_CONNECTED", peripheral.name ?: peripheral.address)

            bluetoothPeripheralDevice = peripheral

            onPeripheralConnected?.invoke(peripheral)
        }

        override fun onConnectionFailed(peripheral: BluetoothPeripheral, status: HciStatus) {
            super.onConnectionFailed(peripheral, status)
            Log.e("GATT_ERROR", "Device ${peripheral.name ?: peripheral.address} Status $status")

            onConnectionError?.invoke()
            bluetoothPeripheralDevice = null

        }

        override fun onDisconnectedPeripheral(peripheral: BluetoothPeripheral, status: HciStatus) {
            super.onDisconnectedPeripheral(peripheral, status)

            Log.e("GATT_DISCONNECT", peripheral.name ?: peripheral.address)

            //TODO check if it is neccesary
            bluetoothPeripheralDevice = null
        }

        override fun onDiscoveredPeripheral(
            peripheral: BluetoothPeripheral,
            scanResult: ScanResult
        ) {
            val name = peripheral.name ?: "NONAME"
//            REFACT THIS DISCOVERY
            if (name.contains("Stages") || name.contains("Bike")) {
                Log.e("Found", name)
                onPeripheralFound?.invoke(peripheral)
            } else {
                Log.e("Found", peripheral.name ?: "NONAME")
            }

            super.onDiscoveredPeripheral(peripheral, scanResult)
        }
    }

    private val powerMeterHandlerThread = HandlerThread("PowerMeterServiceHandler")

    private val bluetoothCentral: BluetoothCentralManager by lazy {
        powerMeterHandlerThread.start()
        BluetoothCentralManager(
            application,
            bluetoothCentralCallback,
            Handler(powerMeterHandlerThread.looper)
        )
    }

    private val peripheralCallback = object : BluetoothPeripheralCallback() {

        override fun onBondingSucceeded(peripheral: BluetoothPeripheral) {
            super.onBondingSucceeded(peripheral)
            Log.e("GATT_SERVICES", "BONDING_SUCCESS")
        }

        override fun onServicesDiscovered(peripheral: BluetoothPeripheral) {
            super.onServicesDiscovered(peripheral)
            Log.e(
                "GATT_SERVICES",
                String.format(
                    "SUCCESS: Services Discovered for %s",
                    peripheral.name ?: peripheral.address
                )
            )
            val peripheralServices = peripheral.services.map { it.uuid }
            when {
                peripheralServices.contains(CYCLING_POWER_SERVICE) -> {
                    peripheral.getService(CYCLING_POWER_SERVICE)?.run {
                        Log.e(
                            "GATT_SERVICES",
                            String.format(
                                "SUCCESS: Peripheral Cycling service"
                            )
                        )
                        bluetoothPeripheralDevice = peripheral
                        hasCyclingService = true
                        onPeripheralConnected?.invoke(peripheral)
                    }
                }
                peripheralServices.contains(FITNESS_MACHINE_SERVICE_UUID) -> {
                    peripheral.getService(FITNESS_MACHINE_SERVICE_UUID)?.run {
                        Log.e(
                            "GATT_SERVICES",
                            String.format(
                                "SUCCESS: Peripheral Fitness Machine service"
                            )
                        )
                        bluetoothPeripheralDevice = peripheral
                        hasFitnessMachineService = true
                        onPeripheralConnected?.invoke(peripheral)
                    }
                }
            }
        }

        override fun onNotificationStateUpdate(
            peripheral: BluetoothPeripheral,
            characteristic: BluetoothGattCharacteristic,
            status: GattStatus
        ) {
            super.onNotificationStateUpdate(peripheral, characteristic, status)

            if (status == GattStatus.SUCCESS) {
                if (peripheral.isNotifying(characteristic)) {
                    when (characteristic.uuid) {
                        CYCLING_POWER_MEASUREMENT_UUID -> {
                            Log.e(
                                "GATT_EVENT",
                                String.format(
                                    "SUCCESS: Notify set to 'on' for %s",
                                    characteristic.uuid
                                )
                            )
                        }
                        CYCLING_POWER_CONTROL_POINT_UUID -> {
                            Log.e(
                                "GATT_EVENT",
                                String.format(
                                    "SUCCESS: Indication set to 'on' for %s",
                                    characteristic.uuid
                                )
                            )
                        }
                        INDOOR_BIKE_DATA_CHARACTERISTIC_UUID -> {
                            Log.e(
                                "GATT_EVENT",
                                String.format(
                                    "SUCCESS: Notify set to 'on' for %s",
                                    characteristic.uuid
                                )
                            )
                        }
                    }


                } else {
                    when (characteristic.uuid) {
                        CYCLING_POWER_MEASUREMENT_UUID -> {
                            Log.e(
                                "GATT_EVENT",
                                String.format(
                                    "SUCCESS: Notify set to 'off' for %s",
                                    characteristic.uuid
                                )
                            )
                        }
                        CYCLING_POWER_CONTROL_POINT_UUID -> {
                            Log.e(
                                "GATT_EVENT",
                                String.format(
                                    "SUCCESS: Indication set to 'off' for %s",
                                    characteristic.uuid
                                )
                            )
                        }

                        INDOOR_BIKE_DATA_CHARACTERISTIC_UUID -> {
                            Log.e(
                                "GATT_EVENT",
                                String.format(
                                    "SUCCESS: Notify set to 'off' for %s",
                                    characteristic.uuid
                                )
                            )
                        }
                    }
                }
            } else {
                Log.e(
                    "GATT_ERROR",
                    String.format(
                        "ERROR: Changing notification state failed for %s",
                        characteristic.uuid
                    )
                )
            }
        }

        override fun onCharacteristicUpdate(
            peripheral: BluetoothPeripheral,
            value: ByteArray,
            characteristic: BluetoothGattCharacteristic,
            status: GattStatus
        ) {
            super.onCharacteristicUpdate(peripheral, value, characteristic, status)


            if (status == GattStatus.SUCCESS) {
                when (characteristic.uuid) {
                    CYCLING_POWER_MEASUREMENT_UUID -> {
                        Log.e("GATT_EVENT", "PowerMeasurement")
//                        Log.e("GATT_EVENT_DATA", "${}")
                        this@PowerMeterService.launch(Dispatchers.IO) {
                            decodePowerData(value)
                        }
                    }
                    CYCLING_POWER_CONTROL_POINT_UUID -> {
                        Log.e("GATT_EVENT", "ControlPointEvent")
                        this@PowerMeterService.launch(Dispatchers.IO) {
                            decodeIndicationData(value)
                        }
                    }
                    FIRMWARE_REVISION_STRING_UUID -> {
                        Log.e("GATT_EVENT", "DeviceFirmware")
                        this@PowerMeterService.launch(Dispatchers.IO) {
                            decodeFirmwareVersion(value)
                        }
                    }
                    INDOOR_BIKE_DATA_CHARACTERISTIC_UUID -> {
                        Log.e("GATT_EVENT", "IndoorBike")
                        this@PowerMeterService.launch(Dispatchers.IO) {
                            decodeIndoorBikeData(value)
                        }
                    }
                    else -> {
                        Log.e("GATT_EVENT", "Another Characteristic")
                    }
                }

            }
        }

    }

    fun scanForPowerMeter() {
        if (bluetoothCentral.isBluetoothEnabled) {
            if (bluetoothCentral.isScanning) bluetoothCentral.stopScan()
            bluetoothCentral.scanForPeripheralsWithServices(
                arrayOf(CYCLING_POWER_SERVICE, FITNESS_MACHINE_SERVICE_UUID)
            )
            this.async(Dispatchers.IO) {
                delay(60000)
                bluetoothCentral.stopScan()
            }
        }
    }

    fun connectToKnownDevice(mac: String) {
        if (bluetoothCentral.isBluetoothEnabled) {
            bluetoothCentral.run {
                val peripheral = getPeripheral(mac)
                Log.e("Peripheral", peripheral.name ?: "NONAME")
                autoConnectToDevice(peripheral)

                onPeripheralConnected?.invoke(peripheral)
            }
        }
    }

    private fun autoConnectToDevice(peripheral: BluetoothPeripheral) {
        if (bluetoothCentral.isBluetoothEnabled) {
            bluetoothCentral.cancelConnection(peripheral)
            bluetoothCentral.autoConnectPeripheral(peripheral, peripheralCallback)
        }
    }

    fun connectToDevice(peripheral: BluetoothPeripheral) {
        if (bluetoothCentral.isBluetoothEnabled) {
            if (bluetoothCentral.isScanning) bluetoothCentral.stopScan()
            bluetoothCentral.connectPeripheral(peripheral, peripheralCallback)
        }
    }

    fun enableDataNotificationChanges(enable: Boolean) {
        bluetoothPeripheralDevice?.run {
            val characteristic = if (hasCyclingService) {
                this.getCharacteristic(CYCLING_POWER_SERVICE, CYCLING_POWER_MEASUREMENT_UUID)
            } else if (hasFitnessMachineService) {
                this.getCharacteristic(
                    FITNESS_MACHINE_SERVICE_UUID,
                    INDOOR_BIKE_DATA_CHARACTERISTIC_UUID
                )
            } else {
                null
            }

            characteristic?.let {
                this.setNotify(characteristic, enable)
            }

        } ?: Log.e("ERROR", "NO_DEVICE_CONNECTED")
    }

    private fun decodePowerData(bytes: ByteArray) {
        val parser = BluetoothBytesParser(bytes)

        val flags = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)

        val hasPedalPowerBalance = (flags and 0x0001) > 0
        val pedalPowerBalanceKnownLeft = (flags and 0x0002) > 0
        val hasAccumulatedTorque = (flags and 0x0004) > 0
        val accumulatedTorqueFromCrank = (flags and 0x0008) > 0
        val hasCrankRevs = (flags and 0x0020) > 0
        val hasExtremeForceMagnitudes = (flags and 0x0040) > 0
        val hasExtremeTorqueMagnitudes = (flags and 0x0080) > 0
        val hasExtremeAngles = (flags and 0x0100) > 0
        val hasDeadSpotAngleTop = (flags and 0x0200) > 0
        val hasDeadSpotAngleBottom = (flags and 0x0400) > 0
        val hasAccumulatedEnergy = (flags and 0x0800) > 0

        //Process power data, Watts with resolution of 1
        val powerWatts = parser.getIntValue(BluetoothBytesParser.FORMAT_SINT16)

        //Left pedal power
        val pedalPowerBalancePercent = if (hasPedalPowerBalance) {
            val halfPercent = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8)
            halfPercent / 2f
        } else {
            0
        }

        if (hasAccumulatedTorque) {
            val accumulatedTorque_1_32NM = parser.getIntValue(BluetoothBytesParser.FORMAT_SINT16)
            val accumulatedTorqueSource = "CRANK" //Not sure what is it
        } else {
            val accumulatedTorque_1_32NM = 0
            val accumulatedTorqueSource = null //Not sure what is it
        }

        var crankRevs = 0
        var crankRevsTicks_1_1024Sec = 0

        if (hasCrankRevs) {
            crankRevs = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)
            crankRevsTicks_1_1024Sec = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)
        }

        val accumulatedEnergyKilojoules = if (hasAccumulatedEnergy) {
            parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)
        } else {
            0
        }

        onPowerMeterDataReceived?.invoke(powerWatts, crankRevs, crankRevsTicks_1_1024Sec)
    }

    private fun decodeIndicationData(bytes: ByteArray) {
        val parser = BluetoothBytesParser(bytes)

        val responseCode = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8)
        val requestCode = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8)
        val success = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8)

        if (success == 0x01) {
            //Calibration Done
            var standardErrorCode = -1
            var manufacturerErrorCode = -1

            //decode offset compensation
            var torqueOffset = parser.getIntValue(BluetoothBytesParser.FORMAT_SINT16)

            //SIG assigned number for company ID
            var companyId = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)

            //Number of bytes representing a manufacturer specific value such as ADC transmitted as an unsigned int
            var adcLength = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8)

            var strainTicks = 0
            var leftStrainTicks = -1
            var rightStrainTicks = -1

            when (adcLength) {
                1 -> {
                    strainTicks = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8)
                    leftStrainTicks = -1
                    rightStrainTicks = -1
                }
                2 -> {
                    strainTicks = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)
                    leftStrainTicks = -1
                    rightStrainTicks = -1
                }
                3 -> {
                    //NOT SURE
                    strainTicks = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT32)
                    leftStrainTicks = -1
                    rightStrainTicks = -1
                }
                4 -> { // left and right present
                    leftStrainTicks = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)
                    rightStrainTicks = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)
                    strainTicks = 0
                }
            }
            onCalibrationDataReceived?.invoke(strainTicks)
            Log.e("Calibration", "Success")
        } else {
            Log.e("Calibration", "Error")
            onCalibrationDataReceived?.invoke(-1)
        }
    }

    private fun decodeFirmwareVersion(bytes: ByteArray) {
        val parser = BluetoothBytesParser(bytes)

        val versionString = parser.getStringValue(0)
        Log.e("FirmwareVersion", versionString)

        onFirmwareVersionReceived?.invoke(versionString)
    }

    private fun decodeIndoorBikeData(bytes: ByteArray) {
        val parser = BluetoothBytesParser(bytes)

        val flags = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)

        val hasMoreData = (flags and 0x0001) > 0
        val hasAverageSpeedPresent = (flags and 0x0002) > 0
        val hasInstantaneousCadencePresent = (flags and 0x0004) > 0
        val hasAverageCadencePresent = (flags and 0x0008) > 0
        val hasTotalDistancePresent = (flags and 0x0010) > 0
        val hasResistanceLevelPresent = (flags and 0x0020) > 0
        val hasInstantaneousPowerPresent = (flags and 0x0040) > 0
        val hasAveragePowerPresent = (flags and 0x0080) > 0
        val hasExpendedEnergyPresent = (flags and 0x0100) > 0
        val hasHeartRatePresent = (flags and 0x0200) > 0
        val hasMetabolicEquivalentPresent = (flags and 0x0400) > 0
        val hasElapsedTimePresent = (flags and 0x0800) > 0
        val hasRemainingTimePresent = (flags and 0x1000) > 0
//        val hasElapsedTimePresent = (flags and 0x0800) > 0

        if (!hasMoreData) { //Value is 0
            Log.e("BTGATT", "HasMoreData")
            val instantaneousSpeed = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)
            Log.e("BTGATT", "$instantaneousSpeed")
        }
        if (hasAverageSpeedPresent) {
            Log.e("BTGATT", "hasAverageSpeedPresent")
            val avgSpeed = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)
            Log.e("BTGATT", "$avgSpeed")
        }
        if (hasInstantaneousCadencePresent) {
            Log.e("BTGATT", "hasInstantaneousCadencePresent")
            val instantaneousCadence = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)
            Log.e("BTGATT", "$instantaneousCadence")
        }
        val cadence = if (hasAverageCadencePresent) {
            Log.e("BTGATT", "hasAverageCadencePresent")
            val avgCadence = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)
            Log.e("BTGATT", "$avgCadence")
            avgCadence
        } else {
            0
        }
        if (hasTotalDistancePresent) {
            //TODO Read this data if it comes into
            Log.e("BTGATT", "hasTotalDistancePresent")
            val totalDistance = parser.getByteArray(24)

            Log.e("BTGATT", "$totalDistance")
        }
        if (hasResistanceLevelPresent) {
            Log.e("BTGATT", "hasResistanceLevelPresent")
            val resistanceLevel = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8)
            Log.e("BTGATT", "$resistanceLevel")
        }
        val instantaneousPower = if (hasInstantaneousPowerPresent) {
            Log.e("BTGATT", "hasInstantaneousPowerPresent")
            val r = parser.getIntValue(BluetoothBytesParser.FORMAT_SINT16)
            Log.e("BTGATT", "$r")
            r
        } else {
            0
        }
        if (hasAveragePowerPresent) {
            Log.e("BTGATT", "hasAveragePowerPresent")
            val avgPower = parser.getIntValue(BluetoothBytesParser.FORMAT_SINT16)
            Log.e("BTGATT", "$avgPower")
        }
        if (hasExpendedEnergyPresent) {
            Log.e("BTGATT", "hasExpendedEnergyPresent")
            val totalEnergy = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)
            val energyPerHour = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)
            val energyPerMinute = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8)
            Log.e("BTGATT", "$totalEnergy")
            Log.e("BTGATT", "$energyPerHour")
            Log.e("BTGATT", "$energyPerMinute")
        }
        if (hasHeartRatePresent) {
            Log.e("BTGATT", "hasHeartRatePresent")
            val heartRate = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8)
            Log.e("BTGATT", "$heartRate")
        }
        if (hasMetabolicEquivalentPresent) {
            Log.e("BTGATT", "hasMetabolicEquivalentPresent")
            val metabolicEquivalent = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8)
            Log.e("BTGATT", "$metabolicEquivalent")
        }
        if (hasElapsedTimePresent) {
            Log.e("BTGATT", "hasElapsedTimePresent")
            val elapsedTime = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)
            Log.e("BTGATT", "$elapsedTime")
        }
        if (hasRemainingTimePresent) {
            Log.e("BTGATT", "hasRemainingTimePresent")
            val remainingTime = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16)
            Log.e("BTGATT", "$remainingTime")
        }

        onSensorDataReceived?.invoke(instantaneousPower, cadence)
    }

    fun enableDataIndicationChanges(enable: Boolean) {
        bluetoothPeripheralDevice?.run {
            val characteristic = if (hasCyclingService) {
                this.getCharacteristic(CYCLING_POWER_SERVICE, CYCLING_POWER_CONTROL_POINT_UUID)
            } else if (hasFitnessMachineService) {
                Log.e("BTGATT", "Not Supported")
                null
            } else {
                null
            }

            characteristic?.let {
                this.setNotify(characteristic, enable)
            }
        }
    }

    fun sendStartOffsetCompensationCommand() {
        sendCommandToCharacteristic(START_ENHANCED_OFFSET_COMPENSATION)
    }

    private fun sendCommandToCharacteristic(characteristicCommand: Int) {
        bluetoothPeripheralDevice?.run {
            val characteristic =
                this.getCharacteristic(CYCLING_POWER_SERVICE, CYCLING_POWER_CONTROL_POINT_UUID)

            val parser =
                BluetoothBytesParser()
            parser.setIntValue(characteristicCommand, BluetoothBytesParser.FORMAT_UINT8)

            characteristic?.run {
                writeCharacteristic(this, parser.value, WriteType.WITH_RESPONSE)
            }
        }
    }

    fun getFirmwareVersion() {
        bluetoothPeripheralDevice?.run {
            val characteristic =
                this.getCharacteristic(DEVICE_INFORMATION_SERVICE, FIRMWARE_REVISION_STRING_UUID)

            characteristic?.run {
                readCharacteristic(characteristic)
            }
        }
    }

    fun disconnect() {
        bluetoothPeripheralDevice?.cancelConnection()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean {
        //TODO check if is neccessary
        bluetoothPeripheralDevice = null
        stopSelf()
        powerMeterHandlerThread.quit()
        Log.e("Service", "Stop")
        return true
    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        bluetoothCentral.close()
//    }

    inner class PowerMeterBinder : Binder() {
        val service: PowerMeterService
            get() = this@PowerMeterService
    }
}