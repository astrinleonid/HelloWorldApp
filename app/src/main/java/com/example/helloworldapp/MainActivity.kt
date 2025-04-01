
package com.example.helloworldapp

import AppConfig
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.helloworldapp.adapters.RecordsAdapter
import com.example.helloworldapp.data.RecordManager
import com.example.helloworldapp.data.RecordManager.checkServerResponse
import com.example.helloworldapp.utils.SettingsUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext



class MainActivity : AppCompatActivity() {
    private lateinit var recordsAdapter: RecordsAdapter
    private var serverErrorDialog: Boolean = false
    private var uniqueId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

                    withContext(Dispatchers.Main) {
                        val intent = Intent(this@MainActivity, TakeRecordsActivity::class.java)
                        intent.putExtra("UNIQUE_ID", newId)
                        intent.putExtra(TakeRecordsActivity.EXTRA_VIEW_TYPE, TakeRecordsActivity.VIEW_TYPE_FRONT)
                        startActivity(intent)
                    }
                }
            }
        }
    }

    private fun setupRecordsList() {
        val recyclerView: RecyclerView = findViewById(R.id.recordsList)
        recyclerView.layoutManager = LinearLayoutManager(this)


        recordsAdapter = RecordsAdapter(RecordManager.getAllRecordingIds()) { recordId ->
            // Handle clicking on a record
            val intent = Intent(this, PlayRecordsActivity::class.java)
            intent.putExtra("UNIQUE_ID", recordId)
            startActivity(intent)
        }



        recyclerView.adapter = recordsAdapter
    }
    private fun setupToolbar() {
        val toolbar: Toolbar = findViewById(R.id.top_app_bar)
        setSupportActionBar(toolbar)
        SettingsUtils.updateToolbarTitle(this)
    }

    override fun onResume() {
        super.onResume()
        SettingsUtils.updateToolbarTitle(this)
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
                SettingsUtils.showSettingsDialog(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
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





