// In RecordsAdapter.kt
package com.example.helloworldapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.helloworldapp.R
import com.example.helloworldapp.data.RecordManager

class RecordsAdapter(
private val records: List<String>,
private val onClick: (String) -> Unit
) : RecyclerView.Adapter<RecordsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val recordId: TextView = view.findViewById(R.id.recordId)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.record_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recordId = records[position]
        holder.recordId.text = recordId
        holder.itemView.setOnClickListener { onClick(recordId) }
    }

    override fun getItemCount() = records.size
}