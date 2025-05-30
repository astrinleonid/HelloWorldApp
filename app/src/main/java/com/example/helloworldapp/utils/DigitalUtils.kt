package com.example.helloworldapp.utils

import AppConfig
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.helloworldapp.MainActivity
import com.example.helloworldapp.R
import com.example.helloworldapp.ShowQrActivity
import com.example.helloworldapp.data.Metadata
import com.example.helloworldapp.data.RecordManager
import com.example.helloworldapp.data.Recording
object DialogUtils {

    private fun navigateToQrActivity(activity: AppCompatActivity, recordId: String) {
        val intent = Intent(activity, ShowQrActivity::class.java).apply {
            putExtra("UNIQUE_ID", recordId)
        }
        activity.startActivity(intent)
        activity.finish()
    }

    fun showSaveOptionsDialog(activity: AppCompatActivity, recordId: String?) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_confirmation, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        // Get reference to the Save Locally button
        val btnSaveLocally = dialogView.findViewById<Button>(R.id.btnSaveLocally)

        // Check if app is in online mode and disable Save Locally button accordingly
        if (AppConfig.online) {
            btnSaveLocally.isEnabled = false
            btnSaveLocally.alpha = 0.5f
        }

        // 1. Save locally
        btnSaveLocally.setOnClickListener {
            dialog.dismiss()
            RecordManager.resetActive()
            // Set to offline mode explicitly
            AppConfig.online = false
            activity.startActivity(Intent(activity, MainActivity::class.java))
            activity.finish()
        }

        // 2. Save on server
        dialogView.findViewById<Button>(R.id.btnSaveOnServer).setOnClickListener {
            dialog.dismiss()
            RecordManager.resetActive()

            // Show progress dialog for server checks
            val progressDialog = ProgressDialog(activity).apply {
                setMessage("Checking server connection...")
                setCancelable(false)
                show()
            }

            // Check if server is responsive
            RecordManager.checkServerResponse { isServerResponsive ->
                activity.runOnUiThread {
                    if (isServerResponsive) {
                        // Server is responsive, set online mode
                        AppConfig.online = true

                        // Update progress dialog
                        progressDialog.setMessage("Transferring recordings to server...")

                        // Use RecordManager's transferAllOfflineRecordingsToServer
                        RecordManager.transferAllOfflineRecordingsToServer(
                            context = activity,
                            onComplete = { transferSuccess ->
                                activity.runOnUiThread {
                                    progressDialog.dismiss()

                                    if (transferSuccess) {
                                        Toast.makeText(activity, "Recordings transferred to server successfully", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(activity, "Some recordings could not be transferred", Toast.LENGTH_SHORT).show()
                                    }

                                    // Whether transfer was successful or not, continue to QR code if we have a recordId
                                    if (recordId != null) {
                                        // Move to QR code display
                                        val intent = Intent(activity, ShowQrActivity::class.java).apply {
                                            putExtra("UNIQUE_ID", recordId)
                                        }
                                        activity.startActivity(intent)
                                        activity.finish()
                                    } else {
                                        // Return to main activity if we don't have a recordId
                                        activity.startActivity(Intent(activity, MainActivity::class.java))
                                        activity.finish()
                                    }
                                }
                            }
                        )
                    } else {
                        // Server is not responsive
                        progressDialog.dismiss()
                        Toast.makeText(activity, "Server is not reachable. Cannot save online.", Toast.LENGTH_LONG).show()

                        // Ask if user wants to retry or save locally
                        AlertDialog.Builder(activity)
                            .setTitle("Server Unavailable")
                            .setMessage("The server is currently unavailable. Would you like to retry connecting or save locally?")
                            .setPositiveButton("Retry") { _, _ ->
                                // Re-click the button to restart the process
                                dialogView.findViewById<Button>(R.id.btnSaveOnServer).performClick()
                            }
                            .setNegativeButton("Save Locally") { _, _ ->
                                // Set offline mode and go to MainActivity
                                AppConfig.online = false
                                activity.startActivity(Intent(activity, MainActivity::class.java))
                                activity.finish()
                            }
                            .setCancelable(false)
                            .show()
                    }
                }
            }
        }

        // 3. Delete recording
        dialogView.findViewById<Button>(R.id.btnDeleteRecording).setOnClickListener {
            dialog.dismiss()
            RecordManager.resetActive()
            recordId?.let { id ->
                // Show progress dialog
                val progressDialog = ProgressDialog(activity).apply {
                    setMessage("Deleting recording...")
                    setCancelable(false)
                    show()
                }

                // Use a simple Thread for the deletion operation
                Thread {
                    try {
                        // Call the synchronous deletion method
                        val success = RecordManager.deleteRecordingSync(id, activity)
                        // Always return to UI thread for activities
                        activity.runOnUiThread {
                            progressDialog.dismiss()
                            // Log the result
                            Log.d("DialogUtils", "Delete result: $success, returning to MainActivity")
                            // Always navigate back to avoid being stuck
                            val intent = Intent(activity, MainActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                            activity.startActivity(intent)
                            activity.finish()
                        }
                    } catch (e: Exception) {
                        // Handle exceptions
                        activity.runOnUiThread {
                            progressDialog.dismiss()
                            Log.e("DialogUtils", "Error during deletion", e)
                            // Show error but still navigate back
                            Toast.makeText(activity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            val intent = Intent(activity, MainActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            activity.startActivity(intent)
                            activity.finish()
                        }
                    }
                }.start()
            }
        }

        // 4. Cancel
        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
    fun showEditMetadataDialog(context: Context, recording: Recording, onMetadataUpdated: (Metadata) -> Unit) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_metadata, null)

        val editUser = dialogView.findViewById<EditText>(R.id.editUser)
        val editAge = dialogView.findViewById<EditText>(R.id.editAge)
        val spinnerSex = dialogView.findViewById<Spinner>(R.id.spinnerSex)
        val editHeight = dialogView.findViewById<EditText>(R.id.editHeight)
        val editWeight = dialogView.findViewById<EditText>(R.id.editWeight)
        val editDiagnosis = dialogView.findViewById<EditText>(R.id.editDiagnosis)
        val editComment = dialogView.findViewById<EditText>(R.id.editComment)

        // Setup spinner with sex options
        val sexOptions = arrayOf("", "Male", "Female", "Other")
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, sexOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSex.adapter = adapter

        // Populate fields with current metadata
        recording.meta?.let { metadata ->
            editUser.setText(metadata.user)
            if (metadata.age > 0) editAge.setText(metadata.age.toString())
            if (metadata.height > 0) editHeight.setText(metadata.height.toString())
            if (metadata.weight > 0) editWeight.setText(metadata.weight.toString())
            editDiagnosis.setText(metadata.diagnosis)
            editComment.setText(metadata.comment)

            // Set sex spinner selection
            val sexIndex = sexOptions.indexOf(metadata.sex)
            if (sexIndex >= 0) {
                spinnerSex.setSelection(sexIndex)
            }
        }

        AlertDialog.Builder(context)
            .setTitle("Edit Metadata")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val updatedMetadata = Metadata(
                    user = editUser.text.toString().trim(),
                    age = editAge.text.toString().toIntOrNull() ?: 0,
                    sex = spinnerSex.selectedItem.toString(),
                    height = editHeight.text.toString().toDoubleOrNull() ?: 0.0,
                    weight = editWeight.text.toString().toDoubleOrNull() ?: 0.0,
                    diagnosis = editDiagnosis.text.toString().trim(),
                    comment = editComment.text.toString().trim()
                )

                // Use RecordManager to set metadata (this will handle sync if online)
                RecordManager.setMetadata(recording.id, updatedMetadata, context)

                // Call the callback
                onMetadataUpdated(updatedMetadata)

                // Show appropriate message based on online status
                if (AppConfig.online) {
                    Toast.makeText(context, "Metadata updated and synced to server", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Metadata updated locally", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

