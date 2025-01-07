package com.example.helloworldapp

import AppConfig
import android.app.Activity
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.helloworldapp.adapters.FileListAdapter
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder




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










