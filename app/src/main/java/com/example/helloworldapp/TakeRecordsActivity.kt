package com.example.helloworldapp

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.helloworldapp.data.RecordManager


class TakeRecordsActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_VIEW_TYPE = "view_type"
        const val VIEW_TYPE_BACK = "back"
        const val VIEW_TYPE_FRONT = "front"
        private val buttonStates = mutableListOf<Boolean>().apply { addAll(List(10) { false }) } // Make it static
    }

    private lateinit var buttonGrid: GridLayout
    private var isBackView: Boolean = false
    private var buttonRange = 1..4
    private var inGridInexCorrector = 1

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
        if (isBackView)  {
            buttonRange = 5..10
            inGridInexCorrector = 5
        }

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

            val overlay = layoutInflater.inflate(R.layout.overlay_icons, container, false).apply {
                visibility = View.GONE
                elevation = 10f
            }
            container.addView(overlay, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))


            buttonGrid.addView(container)
        }
    }

    private fun createRoundButton(number: Int): Button {
        return Button(this).apply {
            text = number.toString()

            // Make the text bold
            setTypeface(typeface, Typeface.BOLD)

            // Center the text
            gravity = Gravity.CENTER

            // Set background resource
            setBackgroundResource(R.drawable.round_button_background)

            // This will be set in onLayout to adjust to button size
            post {
                // Calculate font size based on button width (90% of width)
                val fontSize = (width * 0.55).toFloat() // Using 45% of width rather than 90% to avoid overflow
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            }

            setTextColor(Color.WHITE)
            alpha = 0.8f

           setOnClickListener { handleButtonClick(number)

            }
        }
    }


    private fun handleButtonClick(pointNumber: Int) {
        val recordId = intent.getStringExtra("UNIQUE_ID")

        // Check if this point already has a recording
        if (recordId != null && RecordManager.isRecorded(recordId, pointNumber)) {
            // Point already recorded - cycle through labels
            RecordManager.cyclePointLabel(recordId, pointNumber)
            updateAllButtons()
        } else {
            // Point not recorded yet - launch recording activity
            Log.e("TakeRecordsActivity", "Launching RecordActivity for button $pointNumber")
            val intent = Intent(this, RecordActivity::class.java).apply {
                putExtra("button_number", pointNumber.toString())
                putExtra("UNIQUE_ID", recordId)
            }
            getResult.launch(intent)
        }
    }

    private fun updateAllButtons() {
        val recordId = intent.getStringExtra("UNIQUE_ID") ?: return
        for (i in buttonRange) {
            updateButtonWithOverlay(recordId, i)
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
        val container = buttonGrid.getChildAt(pointNumber - inGridInexCorrector) as? FrameLayout ?: return
        val mainButton = container.getChildAt(0) as? Button
        val overlay = container.getChildAt(1)
        val record = RecordManager.getPointRecord(recordingId, pointNumber)




        if (RecordManager.isRecorded(recordingId, pointNumber) == true) {

            // Show overlay and set up buttons
            overlay.visibility = View.VISIBLE

// Set up play button
            // Set up play button
            overlay.findViewById<ImageButton>(R.id.playButton).setOnClickListener {
                RecordManager.playPointRecording(recordingId, pointNumber, this)
            }

            overlay.findViewById<ImageButton>(R.id.deleteButton).setOnClickListener {
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
            val semiTransparentWhite = Color.argb(70, 255, 255, 255)  // 180/255 opacity white
            mainButton?.setTextColor(semiTransparentWhite)
        }
    }


    override fun onBackPressed() {
        showConfirmationDialog()
    }
}


