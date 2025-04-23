// In RecordsAdapter.kt
package com.example.helloworldapp.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.helloworldapp.R

class RecordsAdapter(
    private var recordIds: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<RecordsAdapter.ViewHolder>() {
    private val TAG = "RecordsAdapter"

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.recordId)
        val container: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.record_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recordId = recordIds[position]
        holder.textView.text = recordId
        holder.container.setOnClickListener { onItemClick(recordId) }
    }

    override fun getItemCount() = recordIds.size

    /**
     * Updates the data in the adapter with a new list of record IDs
     * @param newRecordIds The new list of record IDs to display
     */
    fun updateData(newRecordIds: List<String>) {
        Log.d(TAG, "updateData called with ${newRecordIds.size} records: $newRecordIds")

        // Check if data actually changed
        val dataChanged = newRecordIds != recordIds
        Log.d(TAG, "Data changed: $dataChanged")

        // Update the data
        recordIds = newRecordIds

        // Sometimes notifyDataSetChanged doesn't work properly
        // So we'll also try with more specific notify methods if needed
        if (newRecordIds.isEmpty() && recordIds.isNotEmpty()) {
            Log.d(TAG, "Notifying removal of all items")
            notifyItemRangeRemoved(0, recordIds.size)
        } else if (recordIds.isEmpty() && newRecordIds.isNotEmpty()) {
            Log.d(TAG, "Notifying insertion of all new items")
            notifyItemRangeInserted(0, newRecordIds.size)
        } else {
            Log.d(TAG, "Using notifyDataSetChanged")
            notifyDataSetChanged()
        }
    }
}