package com.example.helloworldapp

import AppConfig
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.helloworldapp.data.RecordManager
import com.example.helloworldapp.data.PointRecord  // if you need the class directly
import com.example.helloworldapp.data.RecordLabel  // if you need the enum
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException



class TakeRecordsActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_VIEW_TYPE = "view_type"
        const val VIEW_TYPE_BACK = "back"
        const val VIEW_TYPE_FRONT = "front"
        private val buttonStates = mutableListOf<Boolean>().apply { addAll(List(10) { false }) } // Make it static
    }

    private lateinit var buttonGrid: GridLayout
    private var isBackView: Boolean = true

    private val getResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Log.d("TakeRecordsActivity", "============ START RESULT CALLBACK ============")
        Log.d("TakeRecordsActivity", "Result code: ${result.resultCode}")
        Log.d("TakeRecordsActivity", "Data: ${result.data?.extras}")

        if (result.resultCode == Activity.RESULT_OK) {
            val buttonNumber = result.data?.getStringExtra("button_number")?.toIntOrNull()
            val recordId = intent.getStringExtra("UNIQUE_ID")

            Log.d("TakeRecordsActivity", "Button number: $buttonNumber")
            Log.d("TakeRecordsActivity", "Record ID: $recordId")

            if (buttonNumber != null && recordId != null) {
                RecordManager.setRecorded(recordId, buttonNumber, true)
                Log.d("TakeRecordsActivity", "Setting recorded for button $buttonNumber")

            }
        }
        updateAllButtons()
        Log.d("TakeRecordsActivity", "============ END RESULT CALLBACK ============")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isBackView = intent.getStringExtra(EXTRA_VIEW_TYPE) != VIEW_TYPE_FRONT
        setContentView(if (isBackView) R.layout.activity_take_records_back else R.layout.activity_take_records_front)

        setupToolbar()
        setupButtons()
        createButtonGrid()
        updateAllButtons()
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
                Log.e("TakeRecordsActivity", "Launching RecordActivity for button $number")  // Using Log.e
                val intent = Intent(this@TakeRecordsActivity, RecordActivity::class.java).apply {
                    putExtra("button_number", number.toString())
                    putExtra("UNIQUE_ID", this@TakeRecordsActivity.intent.getStringExtra("UNIQUE_ID"))
                }
                getResult.launch(intent)
            }
        }
    }
    private fun updateAllButtons() {
        val recordId = intent.getStringExtra("UNIQUE_ID") ?: return
        val buttonRange = if (isBackView) 5..10 else 1..4

        Log.d("TakeRecordsActivity", "Updating buttons for record: $recordId")

        for (i in buttonRange) {
            val button = buttonGrid.getChildAt(i - (if (isBackView) 5 else 1)) as? Button
            val record = RecordManager.getPointRecord(recordId, i)

            record?.let { pointRecord ->
                val colorRes = RecordManager.getButtonColor(pointRecord)
                Log.d("TakeRecordsActivity", "Setting button $i color to resource: $colorRes")
                button?.setBackgroundResource(colorRes)
            }
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
            RecordManager.deleteRecording(intent.getStringExtra("UNIQUE_ID"))
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
}









