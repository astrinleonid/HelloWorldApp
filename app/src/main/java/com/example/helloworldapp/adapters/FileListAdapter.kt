package com.example.helloworldapp.adapters

import AppConfig
import android.app.Activity
import android.content.Context
import android.media.MediaPlayer
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
    private val fileList: MutableList<String>,
    private val recordId: String,  // renamed from folderId for consistency
    private val textViewStatus: TextView,
    private val onDeleteSuccess: () -> Unit
) : ArrayAdapter<String>(context, 0, fileList) {
    private var mediaPlayer: MediaPlayer? = null

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var listItemView = convertView
        if (listItemView == null) {
            listItemView = LayoutInflater.from(context).inflate(R.layout.file_list_with_buttons, parent, false)
        }

        val fileName = getItem(position)
        val textViewFileName = listItemView?.findViewById<TextView>(R.id.textViewFileName)
        val buttonPlay = listItemView?.findViewById<Button>(R.id.buttonPlay)
        val buttonDelete = listItemView?.findViewById<Button>(R.id.buttonDelete)
        val buttonLabel = listItemView?.findViewById<Button>(R.id.buttonLabel)

        // Extract point number from filename
        val pointNumber = if (AppConfig.online) {
            fileName?.substringAfterLast("point")?.toIntOrNull() ?: 0
        } else {
            fileName?.substringAfter("btn_")?.substringBefore("_")?.toIntOrNull() ?: 0
        }

        // Get point record from RecordManager
        val record = RecordManager.getPointRecord(recordId, pointNumber)

        textViewFileName?.text = "Point $pointNumber"

        // Set up label button
        buttonLabel?.text = record?.label?.toString() ?: "NOLABEL"
        buttonLabel?.setOnClickListener {
            val newLabel = RecordManager.cyclePointLabel(recordId, pointNumber)
            notifyDataSetChanged()
        }

        // Set label button color
        buttonLabel?.let { button ->
            val colorRes = when(record?.label) {
                RecordLabel.POSITIVE -> R.color.colorPositive
                RecordLabel.NEGATIVE -> R.color.colorNegative
                RecordLabel.UNDETERMINED -> R.color.colorUndetermined
                else -> R.color.colorRecorded  // or whatever color you want for NOLABEL
            }
            button.setBackgroundColor(ContextCompat.getColor(context, colorRes))
        }

        buttonPlay?.setOnClickListener {
            RecordManager.playPointRecording(recordId, pointNumber, context) { status ->
                textViewStatus.text = status
            }
        }

        buttonDelete?.setOnClickListener {
            RecordManager.resetPoint(recordId, pointNumber, context) { success ->
                if (success) {
                    // Run on UI thread since we're modifying UI elements
                    (context as Activity).runOnUiThread {
                        // Remove the file from the list
                        fileName?.let { name ->
                            fileList.remove(name)
                            notifyDataSetChanged()  // Update the ListView
                            onDeleteSuccess()       // Call the callback to update parent activity if needed
                        }
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







