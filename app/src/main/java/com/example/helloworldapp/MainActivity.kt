
package com.example.helloworldapp

import AppConfig
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.helloworldapp.adapters.RecordsAdapter
import com.example.helloworldapp.data.RecordManager
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
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException



class MainActivity : AppCompatActivity() {
    private lateinit var recordsAdapter: RecordsAdapter
    private var serverErrorDialog: Boolean = false
    private var uniqueId: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.top_app_bar)
        setSupportActionBar(toolbar)

        setupRecordsList()  // Add this line

        val startButton: Button = findViewById(R.id.start_button)
        startButton.setOnClickListener {
            checkServerResponse { isSuccessful ->
                if (isSuccessful) {
                    CoroutineScope(Dispatchers.IO).launch {
                        // Get ID from server in online mode
                        uniqueId = fetchUniqueId(AppConfig.numChunks)
                        withContext(Dispatchers.Main) {
                            uniqueId?.let { id ->
                                // Initialize with server-provided ID
                                RecordManager.initializeRecording(id)
                                val intent = Intent(this@MainActivity, TakeRecordsActivity::class.java)
                                intent.putExtra("UNIQUE_ID", id)
                                intent.putExtra(TakeRecordsActivity.EXTRA_VIEW_TYPE, TakeRecordsActivity.VIEW_TYPE_FRONT)
                                this@MainActivity.startActivity(intent)
                            }
                        }
                    }
                } else {
                    // In offline mode or server error, generate ID locally
                    val newId = RecordManager.initializeRecording()
                    val intent = Intent(this@MainActivity, TakeRecordsActivity::class.java)
                    intent.putExtra("UNIQUE_ID", newId)
                    intent.putExtra(TakeRecordsActivity.EXTRA_VIEW_TYPE, TakeRecordsActivity.VIEW_TYPE_FRONT)
                    startActivity(intent)
                }
            }
        }
    }

    private fun setupRecordsList() {
        val recyclerView: RecyclerView = findViewById(R.id.recordsList)
        recyclerView.layoutManager = LinearLayoutManager(this)

        recordsAdapter = RecordsAdapter(RecordManager.getAllRecordingIds()) { recordId ->
            // Handle clicking on a record
            val intent = Intent(this, TakeRecordsActivity::class.java)
            intent.putExtra("UNIQUE_ID", recordId)
            intent.putExtra(TakeRecordsActivity.EXTRA_VIEW_TYPE, TakeRecordsActivity.VIEW_TYPE_FRONT)
            startActivity(intent)
        }
        recyclerView.adapter = recordsAdapter
    }

    override fun onResume() {
        super.onResume()
        recordsAdapter.notifyDataSetChanged()
    }
    // Inflate the menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    // Handle menu item clicks
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings_menu -> {
                showSettingsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)

        val serverIpEdit: EditText = dialogView.findViewById(R.id.edit_server_ip)
        val timeoutEdit: EditText = dialogView.findViewById(R.id.edit_timeout)
        val numChunksEdit: EditText = dialogView.findViewById(R.id.edit_num_chunks)
        val chunkLengthEdit: EditText = dialogView.findViewById(R.id.edit_chunk_length)
        val onlineCheckbox: CheckBox = dialogView.findViewById(R.id.checkbox_online)

        // Set current values from AppConfig
        serverIpEdit.setText(AppConfig.serverIP)
        timeoutEdit.setText(AppConfig.timeOut.toString())
        numChunksEdit.setText(AppConfig.numChunks.toString())
        chunkLengthEdit.setText(AppConfig.segmentLength.toString())
        onlineCheckbox.isChecked = AppConfig.online  // Set the current state

        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                AppConfig.serverIP = serverIpEdit.text.toString()
                AppConfig.timeOut = timeoutEdit.text.toString().toIntOrNull() ?: 10
                AppConfig.numChunks = numChunksEdit.text.toString().toIntOrNull() ?: 5
                AppConfig.segmentLength = chunkLengthEdit.text.toString().toIntOrNull() ?: 3
                AppConfig.online = onlineCheckbox.isChecked

                Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showServerErrorDialog() {
        AlertDialog.Builder(this)
            .setTitle("Server Error")
            .setMessage("Cannot connect to the server. Please check your network connection and try again. ${AppConfig.serverIP}")
            .setPositiveButton("OK") { _, _ -> }
            .show()
    }

    private fun checkServerResponse(callback: (Boolean) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("${AppConfig.serverIP}/checkConnection")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                callback(response.isSuccessful)
            }
        })
    }

    private suspend fun fetchUniqueId(numChunks: Int): String? = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val deviceInfo = mapOf(
            "model" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER
        )
        if (AppConfig.online) {
            val requestBody = Gson().toJson(deviceInfo)
                .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("${AppConfig.serverIP}/getUniqueId?numChunks=$numChunks")
                .post(requestBody)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else null
            }
        } else {
            val timestamp = System.currentTimeMillis()
            val random = (0..999999).random().toString().padStart(6, '0')
            val deviceHash = (Build.MODEL + Build.MANUFACTURER).hashCode().toString().takeLast(4)
            "OFF${timestamp}${deviceHash}${random}"
        }
    }
}





