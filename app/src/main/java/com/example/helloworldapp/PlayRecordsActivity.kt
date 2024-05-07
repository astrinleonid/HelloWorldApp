package com.example.helloworldapp

import AppConfig
import android.app.Activity
import android.content.Context
import android.content.Intent
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
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder

class FileListAdapter(private val context: Context,
                      private val fileList: MutableList<String>,
                      private val folderId: String,
                      private val textViewStatus: TextView,
                      private val onDeleteSuccess: () -> Unit
                      ) : ArrayAdapter<String>(context, 0, fileList) {
    private var mediaPlayer: MediaPlayer? = null
    private val fileParameters = mutableMapOf<String, String>()  // Store file parameters
    init {
        // Initialize all filenames with a default label
        fileList.forEach { fileName ->
            fileParameters[fileName] = "No Label"
        }
    }
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

        textViewFileName?.text = fileName
        if (buttonLabel != null) {
            buttonLabel.text = fileParameters[fileName] ?: "No Label"
        }

        // buttonLabel?.text = "No Label"
        buttonPlay?.setOnClickListener {
            playFile(folderId, fileName ?: "")
        }
        buttonDelete?.setOnClickListener {
            deleteFile(folderId, fileName ?: "")
            notifyDataSetChanged()
        }

        buttonLabel?.setOnClickListener {
            fileName?.let { safeFileName ->
                val currentLabel = fileParameters[safeFileName] ?: "No Label"
                val newLabel = when (currentLabel) {
                    "No Label" -> "Normal"
                    "Normal" -> "Problems"
                    else -> "No Label"
                }
                fileParameters[safeFileName] = newLabel

                // Update button color based on the new label
                val colorRes = when (newLabel) {
                    "Normal" -> R.color.colorNormal
                    "Problems" -> R.color.colorProblems
                    else -> R.color.colorNoLabel
                }
                buttonLabel.setBackgroundColor(ContextCompat.getColor(context, colorRes))
                sendFileParametersToServer(fileParameters)
                notifyDataSetChanged()
            }
        }


        return listItemView!!
    }

    private fun sendFileParametersToServer(fileParameters: Map<String, String>) {
        val json = Gson().toJson(fileParameters)
        val body = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), json)
        val request = Request.Builder()
            .url("${AppConfig.serverIP}/update_labels?folderId=$folderId")
            .post(body)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("FileListAdapter", "Failed to send file parameters", e)
                // Optionally update UI thread with failure message
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("FileListAdapter", "Server error: ${response.message}")
                } else {
                    Log.i("FileListAdapter", "Parameters updated successfully")
                }
            }
        })
    }

    private fun playFile(folderId: String, fileName: String) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            try {
                val encodedFolderId = URLEncoder.encode(folderId, "UTF-8")
                val encodedFileName = URLEncoder.encode(fileName, "UTF-8")
                val url =
                    "${AppConfig.serverIP}/file_download?fileName=$encodedFileName&folderId=$encodedFolderId"

                setDataSource(url)
                textViewStatus.text = "Loading..."
                prepareAsync()
                setOnPreparedListener {
                    start()  // Start playback once the media is prepared
                    textViewStatus.text = "Playing: $fileName"
                }
                setOnErrorListener { mp, what, extra ->
                    textViewStatus.text = "Error: $what"
                    Log.e("MediaPlayer", "Error playing file: $what, $extra")
                    mp.release()  // Ensure resources are released on error
                    true  // Handle the error and prevent further processing
                }
            } catch (e: Exception) {
                Log.e("MediaPlayer", "Error setting data source", e)
                textViewStatus.text = "Error setting data source"
            }
        }
    }

    private fun deleteFile(folderId: String, fileName: String) {
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
    private var folderId = ""
    val returnIntent = Intent()
    private lateinit var buttonOK: Button

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_list)

        listView = findViewById(R.id.listView)
        textViewStatus = findViewById(R.id.textViewStatus)
        folderId = intent.getStringExtra("UNIQUE_ID") ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            val filesJson = fetchFileList(folderId)
            val flieList = filesJson.split(" ").toList()
            withContext(Dispatchers.Main) {
                setupListView(flieList)
            }
        }
        buttonOK =  findViewById<Button>(R.id.buttonOK)
        buttonOK.setOnClickListener {
            mediaPlayer?.release()
            returnIntent.putExtra("button_number", 0)
            setResult(Activity.RESULT_OK, returnIntent)
            finish()
        }
    }

    private fun fetchFileList(folderId: String): String {
        val encodedFolderId = URLEncoder.encode(folderId, "UTF-8")
        val url = "${AppConfig.serverIP}/get_wav_files?folderId=$encodedFolderId"

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .build()

        return client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                response.body?.string() ?: "[]"
            } else {
                Log.e("FileListActivity", "Server error: ${response.message}")
                "[]"
            }
        }
    }

    private fun setupListView(fileList: List<String>) {
        val mutableFileList = fileList.toMutableList() // Convert to MutableList
        val adapter = FileListAdapter(this, mutableFileList, folderId, textViewStatus) {
            refreshFileList()  // Call refresh when a file is successfully deleted
        }
        listView.adapter = adapter
    }

    private fun refreshFileList() {
        CoroutineScope(Dispatchers.IO).launch {
            val filesJson = fetchFileList(folderId)
            val updatedFileList = filesJson.split(" ").filter { it.isNotEmpty() }.toMutableList()
            withContext(Dispatchers.Main) {
                (listView.adapter as FileListAdapter).clear()
                (listView.adapter as FileListAdapter).addAll(updatedFileList)
                (listView.adapter as FileListAdapter).notifyDataSetChanged()
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        returnIntent.putExtra("button_number", 0)
        setResult(Activity.RESULT_OK, returnIntent)
        finish()
    }
}