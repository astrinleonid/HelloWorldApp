package com.example.helloworldapp

import AppConfig
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
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
            val recordingId = intent.getStringExtra("UNIQUE_ID")

            Log.d("TakeRecordsActivity", "Button number: $buttonNumber")
            Log.d("TakeRecordsActivity", "Record ID: $recordingId")

            if (buttonNumber != null && recordingId != null) {
                RecordManager.setPointRecorded(recordingId, buttonNumber)
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
            // Create container for the button and its overlay
            val container = FrameLayout(this).apply {
                val size = resources.getDimensionPixelSize(R.dimen.grid_button_size)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    val margin = resources.getDimensionPixelSize(R.dimen.point_button_margin)
                    setMargins(margin, margin, margin, margin)
                }
            }

            // Add the main round button
            val mainButton = createRoundButton(i)
            container.addView(mainButton, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))

            // Create and add overlay buttons (initially invisible)
            val overlay = layoutInflater.inflate(R.layout.overlay_buttons, container, false).apply {
                visibility = View.GONE
                elevation = 10f  // Add elevation to ensure overlay is above the button
                z = 10f         // Also set z-index
            }
            container.addView(overlay, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER  // Center the overlay in the container
            })

            buttonGrid.addView(container)
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

        for (i in buttonRange) {
            val buttonIndex = if (isBackView) i - 5 else i - 1

            updateButtonWithOverlay(recordId, buttonIndex)
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
            intent.getStringExtra("UNIQUE_ID")?.let { recordId ->
                RecordManager.deleteRecording(recordId, this) { success ->
                    if (success) {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "Error deleting recording", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun updateButtonWithOverlay(recordingId: String, pointNumber: Int) {
        val container = buttonGrid.getChildAt(pointNumber) as? FrameLayout ?: return
        val mainButton = container.getChildAt(0) as? Button
        val overlay = container.getChildAt(1)
        val record = RecordManager.getPointRecord(recordingId, pointNumber)

        if (RecordManager.isRecorded(recordingId, pointNumber) == true) {
            // Show overlay and set up buttons
            overlay.visibility = View.VISIBLE

            overlay.findViewById<Button>(R.id.playButton).setOnClickListener {
                RecordManager.playPointRecording(recordingId, pointNumber, this) { status ->
                    // Update status somewhere in UI
                }
            }

            overlay.findViewById<Button>(R.id.labelButton).apply {
                text = record?.label.toString().first().toString()
                setOnClickListener {
                    val newLabel = RecordManager.cyclePointLabel(recordingId, pointNumber)
                    updateAllButtons()
                }
            }

            overlay.findViewById<Button>(R.id.deleteButton).setOnClickListener {
                RecordManager.resetPoint(recordingId, pointNumber, this) { success ->
                    if (success) {
                        updateAllButtons()
                    } else {
                        Toast.makeText(this, "Error resetting point", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            overlay.visibility = View.GONE
        }

        // Update main button color
        val colorRes = record?.let { RecordManager.getButtonColor(it) }
        if (colorRes != null) {
            mainButton?.setBackgroundResource(colorRes)
        }
    }


    override fun onBackPressed() {
        showConfirmationDialog()
    }
}














