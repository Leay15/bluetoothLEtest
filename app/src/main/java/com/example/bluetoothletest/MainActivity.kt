package com.example.bluetoothletest

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bluetoothletest.adapter.BluetoothDevicesAdapter
import com.example.bluetoothletest.viewModel.BluetoothViewModel
import com.example.bluetoothletest.viewModel.PowerMeterViewModel
import com.welie.blessed.BluetoothPeripheral
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    val bluetoothViewModel by lazy { ViewModelProvider(this)[PowerMeterViewModel::class.java] }

    val bluetoothAdapter by lazy {
        BluetoothDevicesAdapter(::onDeviceSelected)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothViewModel.bindToService()

        bluetooth_recycler_view?.run {
            adapter = bluetoothAdapter
            setHasFixedSize(true)
            layoutManager =
                LinearLayoutManager(this@MainActivity, LinearLayoutManager.VERTICAL, false)
        }

        bluetoothViewModel.newPeripheral.observe(this) {
            it?.run {
                bluetoothAdapter.add(it)
            }
        }

        bluetoothViewModel.isDeviceConnected.observe(this) {
            if (it == true) {
                bluetoothViewModel.subscribeToPowerData()
            }
        }

        bluetoothViewModel.power.observe(this) {
            Log.e("POWER: ", it.toString())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothViewModel.disconnectDevice()
    }

    override fun onResume() {
        super.onResume()
        bluetoothViewModel.scanForPowerMeter()
    }

    private fun onDeviceSelected(peripheral: BluetoothPeripheral) {
        bluetoothViewModel.connectToDevice(peripheral)
    }


}