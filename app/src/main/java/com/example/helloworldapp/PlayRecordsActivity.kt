package com.example.helloworldapp

import AppConfig
import android.app.Activity
import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.helloworldapp.data.RecordLabel
import com.example.helloworldapp.data.RecordManager
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder

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
            cycleLabel(pointNumber)
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
            playFile(recordId, fileName ?: "")
        }

        buttonDelete?.setOnClickListener {
            deleteRecording(pointNumber, fileName ?: "")
        }

        return listItemView!!
    }

    private fun cycleLabel(pointNumber: Int) {
        val currentLabel = RecordManager.getPointRecord(recordId, pointNumber)?.label ?: RecordLabel.NOLABEL
        val newLabel = when(currentLabel) {
            RecordLabel.NOLABEL -> RecordLabel.POSITIVE
            RecordLabel.POSITIVE -> RecordLabel.NEGATIVE
            RecordLabel.NEGATIVE -> RecordLabel.UNDETERMINED
            RecordLabel.UNDETERMINED -> RecordLabel.NOLABEL
        }
        RecordManager.setLabel(recordId, pointNumber, newLabel)
        notifyDataSetChanged()
    }

    private fun deleteRecording(pointNumber: Int, fileName: String) {
        // Delete the file
        if (AppConfig.online) {
            deleteFileFromServer(recordId, fileName)
        } else {
            deleteFileLocally(fileName)
        }

        // Reset the record state in RecordManager
        RecordManager.setRecorded(recordId, pointNumber, false)
        RecordManager.setLabel(recordId, pointNumber, RecordLabel.NOLABEL)

        fileList.remove(fileName)
        notifyDataSetChanged()
        onDeleteSuccess()
    }
    private fun setButtonColor(button: Button, label: String) {
        val colorRes = when (label) {
            "Normal" -> R.color.colorNegative
            "Problems" -> R.color.colorPositive
            "Undetermined" -> R.color.colorUndetermined
            else -> R.color.colorRecorded
        }
        button.setBackgroundColor(ContextCompat.getColor(context, colorRes))
    }


    private fun playFile(folderId: String, fileName: String) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            try {
                if (AppConfig.online) {
                    val encodedFolderId = URLEncoder.encode(folderId, "UTF-8")
                    val encodedFileName = URLEncoder.encode(fileName, "UTF-8")
                    val url = "${AppConfig.serverIP}/file_download?fileName=$encodedFileName&folderId=$encodedFolderId"
                    setDataSource(url)
                } else {
                    // Play local file
                    val file = File(context.filesDir, fileName)
                    setDataSource(file.absolutePath)
                }

                textViewStatus.text = "Loading..."
                prepareAsync()
                setOnPreparedListener {
                    start()
                    textViewStatus.text = "Playing: $fileName"
                }
                setOnErrorListener { mp, what, extra ->
                    textViewStatus.text = "Error: $what"
                    Log.e("MediaPlayer", "Error playing file: $what, $extra")
                    mp.release()
                    true
                }
            } catch (e: Exception) {
                Log.e("MediaPlayer", "Error setting data source", e)
                textViewStatus.text = "Error setting data source"
            }
        }
    }


    private fun deleteFileLocally(fileName: String) {
        try {
            val file = File(context.filesDir, fileName)
            if (file.exists() && file.delete()) {
                (context as Activity).runOnUiThread {
                    fileList.remove(fileName)
                    notifyDataSetChanged()
                    onDeleteSuccess()
                }
            } else {
                (context as Activity).runOnUiThread {
                    Toast.makeText(context, "Failed to delete file", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("FileListAdapter", "Error deleting local file", e)
            (context as Activity).runOnUiThread {
                Toast.makeText(context, "Error deleting file", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun deleteFileFromServer(folderId: String, fileName: String) {
        val client = OkHttpClient()
        val encodedFolderId = URLEncoder.encode(folderId, "UTF-8")
        val encodedFileName = URLEncoder.encode(fileName, "UTF-8")

        val request = Request.Builder()
            .url("${AppConfig.serverIP}/file_delete?fileName=$encodedFileName&folderId=$encodedFolderId")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("PlayRecordsActivity", "Failed to delete file: ", e)
                // Optionally, handle failure on UI thread if needed
                (context as Activity).runOnUiThread {
                    Toast.makeText(context, "Failed to delete file", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.i("PlayRecordsActivity", "File successfully deleted")
                    (context as Activity).runOnUiThread {
                        fileList.remove(fileName)  // Modify list
                        notifyDataSetChanged()  // Notify adapter
                        onDeleteSuccess()  // Refresh list, ensure this does not finish activity
                    }
                } else {
                    Log.e("PlayRecordsActivity", "Error deleting file: ${response.message}")
                }
            }
        })
    }

}


class PlayRecordsActivity : AppCompatActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var listView: ListView
    private lateinit var textViewStatus: TextView
    private var recordId = ""
    private lateinit var okButton: Button  // Changed variable name to match Kotlin conventions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_list)

        listView = findViewById(R.id.listView)
        textViewStatus = findViewById(R.id.textViewStatus)
        okButton = findViewById<Button>(R.id.buttonOK)  // Specify the type explicitly
        recordId = intent.getStringExtra("UNIQUE_ID") ?: ""

        if (AppConfig.online) {
            fetchFileListFromServer()
        } else {
            setupListView(getLocalFiles())
        }

        okButton.setOnClickListener {
            mediaPlayer?.release()
            setResult(Activity.RESULT_OK)
            finish()
        }
    }
    private fun fetchFileListFromServer() {
        val encodedFolderId = URLEncoder.encode(recordId, "UTF-8")
        val url = "${AppConfig.serverIP}/get_wav_files?folderId=$encodedFolderId"

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("FileListActivity", "Failed to fetch file list", e)
                runOnUiThread {
                    setupListView(emptyList())
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val jsonData = it.body?.string() ?: "{}"
                        val jsonObject = JSONObject(jsonData)
                        val files = jsonObject.optString("files", "")
                        val fileList = files.split(" ").filter { it.isNotEmpty() }

                        runOnUiThread {
                            setupListView(fileList)
                        }
                    } else {
                        Log.e("FileListActivity", "Server error: ${response.message}")
                        runOnUiThread {
                            setupListView(emptyList())
                        }
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        setResult(Activity.RESULT_OK)
        finish()
    }
    
    private fun getLocalFiles(): List<String> {
        return filesDir.listFiles { file ->
            file.name.startsWith("offline_audio") &&
                    file.name.contains("rec_$recordId")
        }?.map { it.name } ?: emptyList()
    }

    private fun setupListView(fileList: List<String>) {
        val adapter = FileListAdapter(
            this,
            fileList.toMutableList(),
            recordId,
            textViewStatus
        ) {
            if (AppConfig.online) {
                fetchFileListFromServer()
            } else {
                setupListView(getLocalFiles())
            }
        }
        listView.adapter = adapter
    }

}










