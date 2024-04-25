package com.example.helloworldapp

import AppConfig
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
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class FileListAdapter(private val context: Context,
                      private val fileList: List<String>,
                      private val folderId: String,
                      private val textViewStatus: TextView
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

        textViewFileName?.text = fileName
        buttonPlay?.setOnClickListener {
            playFile(folderId, fileName ?: "")
        }
        buttonDelete?.setOnClickListener {
            deleteFile(folderId, fileName ?: "")
            notifyDataSetChanged()
        }

        return listItemView!!
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

        val encodedFolderId = URLEncoder.encode(folderId, "UTF-8")
        val encodedFileName = URLEncoder.encode(fileName, "UTF-8")
        val url =
            "${AppConfig.serverIP}/file_delete?fileName=$encodedFileName&folderId=$encodedFolderId"

    }
}



class PlayRecordsActivity : AppCompatActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var listView: ListView
    private lateinit var textViewStatus: TextView
    private var folderId = ""

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
        val adapter = FileListAdapter(this, fileList, folderId, textViewStatus)
        listView.adapter = adapter
    }


    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}