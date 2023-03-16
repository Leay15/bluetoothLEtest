package com.example.bluetoothletest

import android.content.Context

class BluetoothRepository(context: Context) {

    companion object {
        private const val PRIVATE_MODE = 0
        private const val NAME = "bluetooth_preferences"
        private const val POWER_METER_MAC = "power_meter_mac"
        private const val POWER_METER_NAME = "power_meter_name"
        private const val FIRST_FLOW = "power_meter_first_flow"
    }

    private val preferences by lazy {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }

    fun savePowerMeterDeviceAddress(address: String?) {
        preferences.edit().putString(POWER_METER_MAC, address).apply()
    }

    fun getPowerMeterDeviceAddress(): String? = preferences.getString(POWER_METER_MAC, null)

    fun savePowerMeterDeviceName(name: String) {
        preferences.edit().putString(POWER_METER_NAME, name).apply()
    }

    fun getPowerMeterDeviceName(): String? = preferences.getString(POWER_METER_NAME, null)

    fun setFirstFlowPassed() {
        preferences.edit().putBoolean(FIRST_FLOW, true).apply()
    }

    fun isFirstFlowPassed(): Boolean =
        preferences.getBoolean(FIRST_FLOW, false).apply { }

}