

package com.example.helloworldapp

import AppConfig
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.helloworldapp.adapters.RecordsAdapter
import com.example.helloworldapp.components.CustomToolbar
import com.example.helloworldapp.data.RecordManager
import com.example.helloworldapp.utils.SettingsUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private lateinit var recordsAdapter: RecordsAdapter
    private var serverErrorDialog: Boolean = false
    private var uniqueId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        RecordManager.initialize(this)
        setContentView(R.layout.activity_main)
        setupToolbar()
        setupRecordsList()

        val startButton: Button = findViewById(R.id.start_button)
        startButton.setOnClickListener {
            checkServerResponse { isSuccessful ->
                // Launch coroutine to handle initialization
                CoroutineScope(Dispatchers.IO).launch {
                    // Get ID through initializeRecording function which handles fetching/generating
                    val newId = RecordManager.initializeRecording()
                    RecordManager.setActive(newId)
                    withContext(Dispatchers.Main) {
                        val intent = Intent(this@MainActivity, TakeRecordsActivity::class.java)
                        intent.putExtra(TakeRecordsActivity.EXTRA_VIEW_TYPE, TakeRecordsActivity.VIEW_TYPE_FRONT)
                        startActivity(intent)
                    }
                }
            }
        }

        val viewServerRecordingsButton: Button = findViewById(R.id.view_server_recordings_button)
        viewServerRecordingsButton.setOnClickListener {
            openServerRecordingsInBrowser()
        }
    }

    private fun setupToolbar() {
        val toolbar: CustomToolbar = findViewById(R.id.top_app_bar)
        setSupportActionBar(toolbar)
        SettingsUtils.updateToolbarTitle(this)
        toolbar.setOnlineMode(AppConfig.online)
    }

    private fun openServerRecordingsInBrowser() {
        // First check if we're online and can connect to the server
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Checking server connection...")
            setCancelable(false)
            show()
        }
        RecordManager.checkServerResponse { isConnected ->
            runOnUiThread {
                progressDialog.dismiss()
                if (isConnected) {
                    // Server is reachable, open the server recordings page
                    val serverUrl = "${AppConfig.serverIP}/show_all_records"
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl))
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error opening browser", e)
                        Toast.makeText(
                            this,
                            "Could not open browser. Server URL: $serverUrl",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    // Server is not reachable
                    Toast.makeText(
                        this,
                        "Cannot connect to server. Please check your network connection.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun setupRecordsList() {
        val recyclerView: RecyclerView = findViewById(R.id.recordsList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recordsAdapter = RecordsAdapter(RecordManager.getAllRecordingIds()) { recordId ->
            // Handle clicking on a record
            RecordManager.setActive(recordId)
            val intent = Intent(this, TakeRecordsActivity::class.java)
            intent.putExtra(TakeRecordsActivity.EXTRA_VIEW_TYPE, TakeRecordsActivity.VIEW_TYPE_FRONT)
//            intent.putExtra("UNIQUE_ID", recordId)
            startActivity(intent)
        }
        recyclerView.adapter = recordsAdapter
    }

    // Public method that can be called to refresh the records list
    fun refreshRecordsList() {
        Log.d(TAG, "refreshRecordsList called")
        if (::recordsAdapter.isInitialized) {
            val newRecords = RecordManager.getAllRecordingIds()
            Log.d(TAG, "Got ${newRecords.size} records from RecordManager")
            recordsAdapter.updateData(newRecords)
            recordsAdapter.notifyDataSetChanged()
            Log.d(TAG, "Records list refreshed")
        } else {
            Log.e(TAG, "recordsAdapter not initialized!")
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        SettingsUtils.updateToolbarTitle(this)
        refreshRecordsList()
    }

    private fun checkServerResponse(callback: (Boolean) -> Unit) {
        // Only try to contact server if in online mode
        if (!AppConfig.online) {
            callback(false)
            return
        }
        // Show a progress dialog while checking
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Checking server connection...")
            setCancelable(false)
            show()
        }
        // Use ServerApi to check connection
        RecordManager.checkServerResponse { isConnected ->
            progressDialog.dismiss()
            if (!isConnected && AppConfig.online) {
                // Server is not reachable but app is in online mode
                // Show the error dialog
                serverErrorDialog = true
                showServerErrorDialog()
                // Return failure
                callback(false)
            } else {
                // Server is reachable or app is in offline mode
                callback(true)
            }
        }
    }

    private fun showServerErrorDialog() {
        AlertDialog.Builder(this)
            .setTitle("Server Error")
            .setMessage("Cannot connect to the server. Please check your network connection and try again. ${AppConfig.serverIP}")
            .setPositiveButton("OK") { _, _ -> }
            .show()
    }
}