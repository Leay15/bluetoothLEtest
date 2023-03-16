package com.example.bluetoothletest.viewModel;

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.bluetoothletest.BluetoothRepository
import com.example.bluetoothletest.PowerMeterServiceConnection
import com.example.bluetoothletest.service.PowerMeterService
import com.welie.blessed.BluetoothPeripheral
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

class PowerMeterViewModel(application: Application) : AndroidViewModel(application) {

    private val gender = "male"
    private val isFemale = gender == "female"

    private val malekCalsHiggerCompensationFactor = 3.20
    private val malekCalsHighCompensationFactor = 3.00
    private val malekCalsMidCompensationFactor = 2.80
    private val malekCalsLowCompensationFactor = 0.60

    private val femaleKCalsHiggerCompensationFactor = 2.20
    private val femaleKCalsHighCompensationFactor = 2.00
    private val femaleKCalsMidCompensationFactor = 1.80
    private val femaleKCalsLowCompensationFactor = 0.50

    private val kCalsHiggerCompensationFactor =
        if (isFemale) femaleKCalsHiggerCompensationFactor else malekCalsHiggerCompensationFactor
    private val kCalsHighCompensationFactor =
        if (isFemale) femaleKCalsHighCompensationFactor else malekCalsHighCompensationFactor
    private val kCalsMidCompensationFactor =
        if (isFemale) femaleKCalsMidCompensationFactor else malekCalsMidCompensationFactor
    private val kCalsLowCompensationFactor =
        if (isFemale) femaleKCalsLowCompensationFactor else malekCalsLowCompensationFactor

    private val bluetoothRepository by lazy { BluetoothRepository(application) }

    private val powerMeterServiceConnection by lazy {
        PowerMeterServiceConnection {
            Log.e("BTBinding", "Bound")
            powerMeterService = it
            Log.e("BTServiceID", powerMeterService.hashCode().toString())
            _bounded.value = (true)
        }
    }

//    private val serialServiceSensorConnection by lazy {
//        SerialSensorServiceConnection {
//            serialSensorService = it
//        }
//    }

    @SuppressLint("StaticFieldLeak")
    private var powerMeterService: PowerMeterService? = null
//
//    @SuppressLint("StaticFieldLeak")
//    private var serialSensorService: SerialSensorService? = null

    private val bluetoothDevicesList = mutableListOf<BluetoothPeripheral>()

    val newPeripheral: LiveData<BluetoothPeripheral?>
        get() = _newPeripheral
    private val _newPeripheral = MutableLiveData<BluetoothPeripheral?>(null)

    val isDeviceConnected = MutableLiveData<Boolean?>(null)

    val firmwareVersion = MutableLiveData("")

    val connectionError = MutableLiveData(false)

    val bounded: LiveData<Boolean>
        get() = _bounded
    private val _bounded: MutableLiveData<Boolean> = MutableLiveData(false)

    val power: LiveData<Int>
        get() = _power
    private val _power: MutableLiveData<Int> = MutableLiveData(0)

    val calories: LiveData<Int>
        get() = _calories
    private val _calories: MutableLiveData<Int> = MutableLiveData(0)

    var lastCalories: Double = 0.0

    val speedKph: LiveData<Int>
        get() = _speedKph
    private val _speedKph = MutableLiveData(0)

    val cadence: LiveData<Int>
        get() = _cadence
    private val _cadence: MutableLiveData<Int> = MutableLiveData(0)

    val distance: LiveData<Double>
        get() = _distance
    private val _distance: MutableLiveData<Double> = MutableLiveData(0.0)

    var lastDistance: Double = 0.0
    var lastPower = 0

    var lastPowerAverage = 0

    val seconds: LiveData<Long>
        get() = _seconds
    private val _seconds: MutableLiveData<Long> = MutableLiveData(0L)

    private var lastCrankRev: Int = -1
    private var lastCrankTik1_1024: Int = -1

    private var readTimes = 3
    private var accumulatedSeconds: Long = 0L
    private var lastCadenceRead: Double = 0.0
    private var accumulatedCalories: Double = 0.0
    private var accumulatedDistance: Double = 0.0

    private var powerStoppedCount = 0
    private var stopped = true

    private val computingSeconds = 2.0

    fun scanForPowerMeter() {
        powerMeterService?.run {
            scanForPowerMeter()
            onPeripheralFound = { peripheral ->
                if (!bluetoothDevicesList.contains(peripheral)) {
                    bluetoothDevicesList.add(peripheral)

                    _newPeripheral.postValue(peripheral)
                }
            }
        } ?: kotlin.run {
            Log.e("BTServiceError", "NotServiceAttached")
        }
    }

    fun connectToKnownDevice(): Boolean {
        val mac = if (bluetoothRepository.getPowerMeterDeviceAddress().isNullOrEmpty()) {
            null
        } else {
            bluetoothRepository.getPowerMeterDeviceAddress()
        }
        return mac?.run {
            powerMeterService?.run {
                connectToKnownDevice(mac)
                onPeripheralConnected = {
                    subscribeToPowerData()
                }
                true
            } ?: kotlin.run {
                Log.e("BTServiceError", "NotServiceAttached")
                false
            }
        } ?: false
    }

    fun subscribeToPowerData() {
        powerMeterService?.run {
            enableDataNotificationChanges()

            if (hasCyclingService) {
                onPowerMeterDataReceived = { power, crankRevs, crankTiks1_1024 ->
                    this@PowerMeterViewModel.viewModelScope.launch(Dispatchers.IO) {
//                    Log.e("BTPower", "$power")
//                    Log.e("BTCrankRev", "$crankRevs")
//                    Log.e("BTCrankTiks", "$crankTiks1_1024")
                        computeData(power, crankRevs, crankTiks1_1024)
                    }
                }
            } else if (hasFitnessMachineService) {
                onSensorDataReceived = { power, avgCadence ->
                    Log.e("BTPower", "$power")
                    Log.e("BTAvgCadence", "$avgCadence")

                }
            }
        } ?: Log.e("BTServiceError", "NotServiceAttached")
    }

    fun connectToDevice(peripheral: BluetoothPeripheral) {
        powerMeterService?.run {
            onConnectionError = {
                connectionError.postValue(true)
            }
            connectToDevice(peripheral)
            onPeripheralConnected = { peripheral ->
                bluetoothRepository.savePowerMeterDeviceAddress(peripheral.address)
                bluetoothRepository.savePowerMeterDeviceName(peripheral.name ?: "Name Error")
                isDeviceConnected.postValue(true)
                enableDataNotificationChanges()
            }
        } ?: kotlin.run {
            Log.e("BTServiceError", "NotServiceAttached")
        }
    }

    private fun enableDataNotificationChanges() {
        powerMeterService?.run {
            Log.e("BTNotification", "Activate")
            enableDataNotificationChanges(true)
        } ?: kotlin.run {
            Log.e("BTServiceError", "NotServiceAttached")
        }
    }

    fun finishConnectProcess() = disableDataNotificationChanges()

    private fun disableDataNotificationChanges() {
        powerMeterService?.run {
            enableDataNotificationChanges(false)
        } ?: kotlin.run {
            Log.e("BTServiceError", "NotServiceAttached")
        }
    }

    fun readFirmwareVersion() {
        powerMeterService?.run {
            onFirmwareVersionReceived = { firmwareVersionValue ->
                firmwareVersion.postValue(firmwareVersionValue)
            }
            getFirmwareVersion()
        } ?: kotlin.run {
            Log.e("BTServiceError", "NotServiceAttached")
        }
    }

    //Close Binder and disable notification changes
    override fun onCleared() {
        powerMeterService = null
        super.onCleared()
    }

    private fun computeData(power: Int, crankRevs: Int, crankTiks1_1024: Int) {
        checkIfStopped(power)
        if (computeTime()) {
            lastPower = if (!stopped) {
                (power)
            } else {
                0
            }

            Log.e("BTLastPower", "$lastPower")
            Log.e("BTCrankRev", "$crankRevs")
            Log.e("BTCrankTiks", "$crankTiks1_1024")
            Log.e("BTCrankRev_Last", "$lastCrankRev")
            Log.e("BTCrankTiks_Last", "$lastCrankTik1_1024")
//            accPower += lastPower
//            Log.e("PowerAdd",lastPower.toString())
//            if (avgSeconds > (computingSeconds - 1)) {
//                avgSeconds = 0
//                Log.e("PowerAcc",accPower.toString())
            lastPowerAverage = if (!stopped) {
                ceil((lastPowerAverage + lastPower) / 2.0).toInt()
            } else {
                0
            }
//            Log.e("PowerAverage", lastPowerAverage.toString())
//            accPower = lastPowerAverage

//            Log.e("BTLastAveragePower", "$lastPowerAverage")
            viewModelScope.launch(Dispatchers.IO) {
                computeDataFromPower(lastPowerAverage)
                computeUserCadence(
                    lastPowerAverage,
                    crankRevs.toFloat(),
                    crankTiks1_1024.toFloat()
                )
            }
//            } else {
//                avgSeconds++
//            }


            accumulatedSeconds++
            _seconds.postValue(accumulatedSeconds)
        }

    }

    private fun computeTime(): Boolean {
        return if (readTimes < 3) {
            readTimes++
            false
        } else {
            readTimes = 0
            true
        }
    }

    private fun checkIfStopped(power: Int) {
        if (power != 0) {
            stopped = false
            powerStoppedCount = 0
        } else {
            powerStoppedCount++
        }

        if (powerStoppedCount >= 15) {
            stopped = true
        }

//        Log.e("BTStopped", "Stopped $stopped")
    }

    fun computeDataFromPower(power: Int) {
        val speedKms = computeSpeedFromPower(power)
        computeDistanceFromSpeed(speedKms, 1.0)
//        computeCalories(power)

        computeCaloriesV2(power, 1.0)
        _power.postValue(power)
    }

    private fun computeCaloriesFromHeartRate(heartRate: Int) {
        // Age in years
        // Weight in pounds
        // Time in min  -  NOTE: we want to calculate every second
        // Heart rate in bpm
        // kcal Burned M = [(Age x 0.2017) + (Weight x 0.1988) + (Heart Rate x 0.6309)
        // - 55.0969] x Time / 4.184
        // kcal Burned F = [(Age x 0.074) + (Weight x
        // 0.05741) + (Heart Rate x 0.4472) - 20.4022] x Time / 4.184
    }

    private fun computeSpeedFromPower(power: Int): Int {
        //speed_mph = (13.26 * power / (18.1 + power)) +
        //        (38.88 * power / (586.6 + power));
        //speed_kph =  1.60934 * speed_mph;
        val speedMph = (13.26 * power / (18.1 + power)) + ((38.88 * power) / (586.6 + power))
        val speedKph = (1.60934 * speedMph)

//        _speedMph.postValue(floor(speedMph).toInt())

        //TODO removes speed
//        _speedKph.postValue(floor(speedKph).toInt())

//        println("KM/h $speedKph")
//        println("MI/h $speedMph")
//        Log.e("SPEED:", speedKph.toString() + "KMH")

        return floor(speedKph).toInt()
    }

    private fun computeDistanceFromSpeed(speedKmh: Int, seconds: Double): Double {
        val distance = speedKmh * ((seconds) / 3600)

//        println("Distance :$distance")

        lastDistance = distance
        accumulatedDistance += distance

//        println("Acc Distance : $accumulatedDistance")

        _distance.postValue(accumulatedDistance)
//        SharedPreferencesRateClass.saveLastDistanceValue(getApplication(), accumulatedDistance)

        return lastDistance
    }

    fun computeCaloriesV2(watts: Int, elapsedSeconds: Double) {

        val kJoules = (watts.toLong() * elapsedSeconds) * 0.001

        val kcals = (kJoules * (1 / 4.184) * (1 / 0.22))

        val factor = when (watts) {
            in 0..25 -> 1 + kCalsHiggerCompensationFactor
            in 26..80 -> 1 + kCalsHighCompensationFactor
            in 81..100 -> 1 + kCalsMidCompensationFactor
            else -> 1 + kCalsLowCompensationFactor
        }

        val result = (kcals * 1.0858706511) * (factor)

        lastCalories = result
        Log.e("Calories", lastCalories.toString())

        accumulatedCalories += lastCalories

        Log.e("Calories", accumulatedCalories.toString())
//        println("Kilocalories :$calories")
//        println("Acc Kcals :$accumulatedCalories")
        _calories.postValue(floor(accumulatedCalories).toInt())
    }

    fun computeCalories(watts: Int): Double {
        //Asuming its 1 second
//        val joules = computeJoulesFromWatts(watts, accumulatedSeconds)
        val joules = watts.toLong()

//        println("Joules :$joules")
        return computeCaloriesFromKJoules(joules)

//        Log.e("KCALS:", kcals.toString())
    }

    private fun computeJoulesFromWatts(watts: Int, accumulatedTimeSeconds: Long = 1L): Long {
        return watts * accumulatedTimeSeconds
    }

    private fun computeCaloriesFromKJoules(joules: Long): Double {
        /*
        * kcal = (SCALE_BY_THOUSAND((float)total_energy) / SCALE_FACTOR_KJ_TO_KCAL) /
         HUMAN_EFFICIENCY_FACTOR;
#define HUMAN_EFFICIENCY_FACTOR \
  0.22f  // Range from 18%-25% with the more active efficient people on the
         // higher end
#define KCAL_FROM_KJ(KCAL) (1.0858706511 * (KCAL)) */

        //Assuming to multiply to 0.001 to get the actual calories rate
        val calories = joules * 0.001

        lastCalories = calories * 1.60
        Log.e("Calories", lastCalories.toString())

        accumulatedCalories += lastCalories

        Log.e("Calories", accumulatedCalories.toString())
//        println("Kilocalories :$calories")
//        println("Acc Kcals :$accumulatedCalories")
        _calories.postValue(floor(accumulatedCalories).toInt())
//        SharedPreferencesRateClass.saveLastCaloriesValue(getApplication(), floor(accumulatedCalories).toInt())
        return lastCalories
    }

    private fun computeUserCadence(power: Int, crankRevolution: Float, crankTiks1_1024: Float) {
        var cadence = try {
            if (crankTiks1_1024 < lastCrankTik1_1024) {
                lastCadenceRead
            } else {
                abs(60f * ((crankRevolution - lastCrankRev) / ((crankTiks1_1024 - lastCrankTik1_1024) / 1024f))).toDouble()
            }
        } catch (e: Exception) {
            lastCadenceRead
        }

        if (cadence >= 120.0) {
            cadence = 120.0
        }

        lastCadenceRead = if (!stopped) {
            if (cadence > 0.0) {
                cadence
            } else {
                lastCadenceRead
            }
        } else {
            0.0
        }

        _cadence.postValue(ceil(lastCadenceRead).toInt())
//        computeSpeedFromCadence(lastCadenceRead)

//        println("Cadence :$cadence to int ->${cadence.toInt()}")

        lastCrankRev = crankRevolution.toInt()
        lastCrankTik1_1024 = crankTiks1_1024.toInt()
    }

    private fun computeSpeedFromCadence(cadence: Double) {
        val wheelDiameter = 132 // cm get by 44 cm * 3.14 factor
        val speedConstant = 0.001885

        val speed = if (cadence > 0) {
            cadence * wheelDiameter * speedConstant
        } else {
            0.0
        }

        _speedKph.postValue(floor(speed).toInt())

        computeDistanceFromSpeed(floor(speed).toInt(), computingSeconds)
    }

    fun bindToService() {
        getApplication<Application>().run {
            bindService(
                Intent(this, PowerMeterService::class.java),
                powerMeterServiceConnection,
                Context.BIND_AUTO_CREATE
            )
            Log.e("BTBinding", "Start")
        }
    }

//    fun bindToServiceSensor() {
//        getApplication<Application>().run {
//            bindService(
//                Intent(this, SerialSensorService::class.java),
//                serialServiceSensorConnection,
//                Context.BIND_AUTO_CREATE
//            )
//            Log.e("BTBinding", "Start")
//        }
//    }

    fun disconnectDevice() {
        disableDataNotificationChanges()
        powerMeterService?.disconnect() ?: run {
            Log.e("BTDisconnect", "Error")
        }
        unbindToService()
//        unbindToServiceSensor()
    }

    private fun unbindToService() {
        try {
            getApplication<Application>().run {
                unbindService(powerMeterServiceConnection)
                Log.e("BTBinding", "Release")
            }
        } catch (e: Exception) {
            Log.e("BTBinding", e.message ?: "")
        }
    }

//    private fun unbindToServiceSensor() {
//        try {
//            getApplication<Application>().run {
//                unbindService(serialServiceSensorConnection)
//                Log.e("BTBinding", "Release")
//            }
//        } catch (e: Exception) {
//            Log.e("BTBinding", e.message.toString())
//        }
//    }
}