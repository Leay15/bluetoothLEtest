package com.example.bluetoothletest.receivers

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log


typealias bluetoothCallbackAlias = (BluetoothDevice) -> Unit

class BluetoothBroadcastReceiver : BroadcastReceiver() {

    var extraDeviceCallback: bluetoothCallbackAlias? = null
    var boundingCallback: bluetoothCallbackAlias? = null
    var connectedCallback: ((connected: Boolean, bluetoothDevice: BluetoothDevice) -> Unit)? = null

    override fun onReceive(context: Context?, intent: Intent?) {

        when (intent?.action) {
            BluetoothDevice.ACTION_FOUND -> {
                extraDeviceCallback?.invoke(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)!!)
            }
            BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                boundingCallback?.invoke(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)!!)
            }
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                connectedCallback?.invoke(
                    true,
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)!!
                )
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                connectedCallback?.invoke(
                    false,
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)!!
                )
            }
            BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                try {
                    val device: BluetoothDevice =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)!!
                    val pin = intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY", 0)
                    Log.d(
                        "PIN",
                        " " + intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY", 0)
                    );
                    Log.d("Bonded", device.name)
                    val pinBytes: ByteArray
                    pinBytes = "$pin".toByteArray(Charsets.UTF_8)
                    device.setPin(pinBytes)
                    device.setPairingConfirmation(true)
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    Log.e("Bluetooth", "Paring Error")
                }
            }
        }
    }
}