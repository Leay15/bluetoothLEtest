package com.example.bluetoothletest.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetoothletest.R
import kotlinx.android.synthetic.main.bluetooth_device_item_layout.view.*
import kotlin.properties.Delegates

class DevicesAdapter(onDeviceSelected: (btId: String) -> Unit) :
    RecyclerView.Adapter<DevicesAdapter.ViewHolder>() {

    val devicesList: List<Any> by Delegates.observable(emptyList()) { _, _, _ ->

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.bluetooth_device_item_layout, parent, false)
        )


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = devicesList[position]

        holder.itemView.run {
            bluetooth_device_name.text = "DeviceName"
        }
    }

    override fun getItemCount(): Int = devicesList.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}