package com.example.helloworldapp.adapters

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.helloworldapp.R
import com.example.helloworldapp.data.RecordLabel
import com.example.helloworldapp.data.RecordManager

class FileListAdapter(
    private val context: Context,
    private val pointsList: MutableList<String>,
    private val recordId: String,
    private val textViewStatus: TextView,
    private val onRefresh: () -> Unit
) : ArrayAdapter<String>(context, 0, pointsList) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var listItemView = convertView
        if (listItemView == null) {
            listItemView = LayoutInflater.from(context).inflate(R.layout.file_list_with_buttons, parent, false)
        }

        val pointNumber = getItem(position)?.toIntOrNull() ?: return listItemView!!
        val pointRecord = RecordManager.getPointRecord(recordId, pointNumber)

        val textViewFileName = listItemView?.findViewById<TextView>(R.id.textViewFileName)
        val buttonPlay = listItemView?.findViewById<Button>(R.id.buttonPlay)
        val buttonDelete = listItemView?.findViewById<Button>(R.id.buttonDelete)
        val buttonLabel = listItemView?.findViewById<Button>(R.id.buttonLabel)

        textViewFileName?.text = "Point $pointNumber"

        // Set up label button
        buttonLabel?.text = pointRecord?.label?.toString() ?: "NOLABEL"
        buttonLabel?.setOnClickListener {
            RecordManager.cyclePointLabel(recordId, pointNumber)
            notifyDataSetChanged()
        }

        // Set label button color
        buttonLabel?.let { button ->
            val colorRes = when(pointRecord?.label) {
                RecordLabel.POSITIVE -> R.color.colorPositive
                RecordLabel.NEGATIVE -> R.color.colorNegative
                RecordLabel.UNDETERMINED -> R.color.colorUndetermined
                else -> R.color.colorRecorded
            }
            button.setBackgroundColor(ContextCompat.getColor(context, colorRes))
        }

        // Set up play button
        buttonPlay?.setOnClickListener {
            RecordManager.playPointRecording(recordId, pointNumber, context)
        }

        // Set up delete button
        buttonDelete?.setOnClickListener {
            RecordManager.resetPoint(recordId, pointNumber, context) { success ->
                if (success) {
                    (context as Activity).runOnUiThread {
                        pointsList.remove(pointNumber.toString())
                        notifyDataSetChanged()
                        onRefresh()
                    }
                } else {
                    (context as Activity).runOnUiThread {
                        Toast.makeText(context, "Failed to delete recording", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        return listItemView!!
    }
}




