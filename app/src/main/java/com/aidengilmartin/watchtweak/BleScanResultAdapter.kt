package com.aidengilmartin.watchtweak

import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.ble_row_scan_result.view.*

// This is an adapter class for the RecyclerView that displays the list of ScanResults
class BleScanResultAdapter(
    private val items: List<ScanResult>,
    private val onClickListener: ((device: ScanResult) -> Unit)
) : RecyclerView.Adapter<BleScanResultAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.ble_row_scan_result, parent, false),
            onClickListener
        )
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    class ViewHolder(
        private val view: View,
        private val onClickListener: ((device: ScanResult) -> Unit)
    ) : RecyclerView.ViewHolder(view) {

        fun bind(result: ScanResult) {
            view.device_name.text = result.device.name ?: "Unnamed"
            view.mac_address.text = result.device.address
            view.signal_strength.text = "${result.rssi} dBm"
            view.setOnClickListener { onClickListener.invoke(result) }
        }
    }
}
