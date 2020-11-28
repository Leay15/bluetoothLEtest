package com.example.bluetoothletest

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bluetoothletest.adapter.BluetoothDevicesAdapter
import com.example.bluetoothletest.viewModel.BluetoothViewModel
import com.example.bluetoothletest.viewModel.PowerMetterViewModel
import com.welie.blessed.BluetoothPeripheral
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    val bluetoothViewModel by lazy { ViewModelProvider(this)[PowerMetterViewModel::class.java] }

    val bluetoothAdapter by lazy {
        BluetoothDevicesAdapter(::onDeviceSelected)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetooth_recycler_view?.run {
            adapter = bluetoothAdapter
            setHasFixedSize(true)
            layoutManager =
                LinearLayoutManager(this@MainActivity, LinearLayoutManager.VERTICAL, false)
        }

//        registerReceiver(bluetoothViewModel.broadcastReceiver,BluetoothDevice.ACTION_FOUND)

        bluetoothViewModel.bluetoothDevicesList.observe(this) {
            bluetoothAdapter.addAll(it)
        }

        bluetoothViewModel.isDeviceConnected.observe(this) {
            if (it) {
                bluetoothViewModel.enableDataNotificationChanges(true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bluetoothViewModel.scanForPowerMetter()
    }

    private fun onDeviceSelected(peripheral: BluetoothPeripheral) {
        bluetoothViewModel.connectToDevice(peripheral)
    }


}