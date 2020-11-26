package com.example.bluetoothletest.adapter

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetoothletest.R
import kotlinx.android.synthetic.main.bluetooth_device_item_layout.view.*

class BluetoothDevicesAdapter(val callback: (device: BluetoothDevice) -> Unit) :
    RecyclerView.Adapter<BluetoothDevicesAdapter.ViewHolder>() {

    private var devicesList = mutableListOf<BluetoothDevice>()
    private var lastPosition = -1
    private var connectedAddress = ""
    var context: Context? = null

    fun addAll(devices: List<BluetoothDevice>) {
        devicesList.addAll(devices)
        notifyDataSetChanged()
    }

    fun add(device: BluetoothDevice) {
        devicesList.add(device)
        notifyDataSetChanged()
    }

    fun clear() {
        devicesList.clear()
        lastPosition = -1
        notifyDataSetChanged()
    }

    fun changeDeviceStatus(connectedDevice: BluetoothDevice?) {
        connectedAddress = connectedDevice?.address ?: ""
        notifyDataSetChanged()
    }

    fun contains(device: BluetoothDevice): Boolean = devicesList.contains(device)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        return ViewHolder(
            LayoutInflater.from(context)
                .inflate(R.layout.bluetooth_device_item_layout, parent, false)
        )
    }

    override fun getItemCount(): Int = devicesList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devicesList[position]
        holder.itemView.bluetooth_device_name?.text = device.name
        holder.itemView.setOnClickListener { callback(device) }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

}