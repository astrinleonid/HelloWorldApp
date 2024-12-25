package com.example.helloworldapp

import AppConfig
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException

class TakeRecordsActivity : AppCompatActivity() {
    private val buttonStates = mutableListOf<Boolean>().apply { addAll(List(10) { false }) }
    private lateinit var buttonGrid: GridLayout
    private var isBackView: Boolean = true // Track which view we're showing

    companion object {
        const val EXTRA_VIEW_TYPE = "view_type"
        const val VIEW_TYPE_BACK = "back"
        const val VIEW_TYPE_FRONT = "front"
    }

    private val getResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val buttonNumber = result.data?.getStringExtra("button_number")?.toIntOrNull()
            buttonNumber?.let {
                if (it in 1..buttonStates.size) {
                    buttonStates[it - 1] = true
                    updateButtonColor(it - 1)
                }
            }
        }
        val uniqueId = intent.getStringExtra("UNIQUE_ID")
        fetchButtonColors(uniqueId) { states ->
            states?.let {
                runOnUiThread {
                    buttonStates.clear()
                    buttonStates.addAll(it)
                    updateAllButtonColors()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isBackView = intent.getStringExtra(EXTRA_VIEW_TYPE) != VIEW_TYPE_FRONT
        setContentView(if (isBackView) R.layout.activity_take_records_back else R.layout.activity_take_records_front)

        val uniqueId = intent.getStringExtra("UNIQUE_ID")
        setupToolbar()
        setupButtons()
        createButtonGrid()

        fetchButtonColors(uniqueId) { states ->
            states?.let {
                runOnUiThread {
                    buttonStates.clear()
                    buttonStates.addAll(it)
                    updateAllButtonColors()
                }
            }
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title =
            if (isBackView) "@string/back_recording" else "@string/breast_recording"

        findViewById<Button>(R.id.playbackButton).setOnClickListener {
            val intent = Intent(this, PlayRecordsActivity::class.java).apply {
                putExtra("UNIQUE_ID", intent.getStringExtra("UNIQUE_ID"))
            }
            getResult.launch(intent)
        }
    }

    private fun setupButtons() {
        // Change from Button to ImageButton
        findViewById<ImageButton>(R.id.changeSideButton).setOnClickListener {
            val intent = Intent(this, TakeRecordsActivity::class.java).apply {
                putExtra("UNIQUE_ID", this@TakeRecordsActivity.intent.getStringExtra("UNIQUE_ID"))
                putExtra(EXTRA_VIEW_TYPE, if (isBackView) VIEW_TYPE_FRONT else VIEW_TYPE_BACK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
            finish()
        }

        findViewById<Button>(R.id.doneButton).setOnClickListener {
            showConfirmationDialog()
        }
    }

    private fun createButtonGrid() {
        buttonGrid = findViewById(R.id.buttonGrid)
        val buttonRange = if (isBackView) 5..10 else 1..4

        for (i in buttonRange) {
            val button = createRoundButton(i)
            val buttonMargin = resources.getDimensionPixelSize(R.dimen.point_button_margin)
            val params = GridLayout.LayoutParams().apply {
                width = resources.getDimensionPixelSize(R.dimen.grid_button_size)
                height = resources.getDimensionPixelSize(R.dimen.grid_button_size)
                // Set larger margins for better spacing
                setMargins(buttonMargin, buttonMargin, buttonMargin, buttonMargin)
            }
            buttonGrid.addView(button, params)
        }
    }
    private fun createRoundButton(number: Int): Button {
        return Button(this).apply {
            text = number.toString()
            setBackgroundResource(R.drawable.round_button_background)
            setOnClickListener {
                val intent = Intent(this@TakeRecordsActivity, RecordActivity::class.java).apply {
                    putExtra("button_number", number.toString())
                    putExtra("UNIQUE_ID", this@TakeRecordsActivity.intent.getStringExtra("UNIQUE_ID"))
                }
                getResult.launch(intent)
            }
        }
    }

    private fun updateButtonColor(index: Int) {
        val buttonIndex = if (isBackView) index - 5 else index
        val button = buttonGrid.getChildAt(buttonIndex) as? Button
        button?.setBackgroundResource(
            if (buttonStates[index]) R.drawable.round_button_selected
            else R.drawable.round_button_background
        )
    }

    private fun updateAllButtonColors() {
        val range = if (isBackView) 5..10 else 1..4
        for (i in range) {
            updateButtonColor(i - 1)
        }
        if (buttonStates.all { it }) {
            showConfirmationDialog()
        }
    }

    private fun showConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirmation, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnSaveAndExit).setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, ShowQrActivity::class.java).apply {
                putExtra("UNIQUE_ID", this@TakeRecordsActivity.intent.getStringExtra("UNIQUE_ID"))
            }
            startActivity(intent)
            finish()
        }

        dialogView.findViewById<Button>(R.id.btnExitWithoutSaving).setOnClickListener {
            dialog.dismiss()
            sendDeleteCommand(intent.getStringExtra("UNIQUE_ID"), this)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onBackPressed() {
        showConfirmationDialog()
    }

    private fun sendDeleteCommand(uniqueId: String?, context: Context) {
        val url = "${AppConfig.serverIP}/record_delete?record_id=${uniqueId}"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(context, "Error deleting record", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Record deleted successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to delete record: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun fetchButtonColors(recordId: String?, callback: (List<Boolean>?) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("${AppConfig.serverIP}/get_button_states?record_id=$recordId")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    if (res.isSuccessful) {
                        val jsonData = res.body?.string()
                        val jsonArray = JSONArray(jsonData)
                        val buttonColors = mutableListOf<Boolean>()
                        for (i in 0 until jsonArray.length()) {
                            buttonColors.add(jsonArray.getBoolean(i))
                        }
                        callback(buttonColors)
                    } else {
                        callback(null)
                    }
                }
            }
        })
    }
}