package com.example.bluetoothletest

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.example.bluetoothletest.service.PowerMeterService

class PowerMeterServiceConnection(private val onServiceConnectedCallback: ((PowerMeterService) -> Unit)?) :
    ServiceConnection {

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val powerMeterBinder = service as PowerMeterService.PowerMeterBinder
        onServiceConnectedCallback?.invoke(powerMeterBinder.service)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Log.i("Service", "PowerMeterServiceDisconnected")
    }
}