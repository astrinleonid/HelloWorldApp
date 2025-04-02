package com.example.helloworldapp.utils

import AppConfig
import android.app.ProgressDialog
import android.content.Intent
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.helloworldapp.MainActivity
import com.example.helloworldapp.R
import com.example.helloworldapp.ShowQrActivity
import com.example.helloworldapp.data.RecordManager

object DialogUtils {
    fun showSaveOptionsDialog(activity: AppCompatActivity, recordId: String?) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_confirmation, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        // 1. Save on server
        dialogView.findViewById<Button>(R.id.btnSaveOnServer).setOnClickListener {
            dialog.dismiss()

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

                        // Now check if we need to transfer an offline recording
                        val recording = recordId?.let { id -> RecordManager.getRecording(id) }
                        val hasOfflineFiles = recording?.points?.values?.any {
                            it.isRecorded && it.fileName != null && it.fileName!!.startsWith("offline_")
                        } ?: false

                        if (hasOfflineFiles) {
                            // Update progress dialog message
                            progressDialog.setMessage("Transferring offline recording to server...")

                            // Transfer the recording
                            RecordManager.transferOfflineRecordingToServer(
                                recordingId = recordId!!,
                                context = activity,
                                onProgress = { progress ->
                                    activity.runOnUiThread {
                                        progressDialog.setMessage("Transferring offline recording to server... $progress%")
                                    }
                                },
                                onComplete = { success ->
                                    activity.runOnUiThread {
                                        progressDialog.dismiss()

                                        if (success) {
                                            Toast.makeText(activity, "Recording transferred to server successfully", Toast.LENGTH_SHORT).show()
                                            // Continue to QR code display
                                            val intent = Intent(activity, ShowQrActivity::class.java).apply {
                                                putExtra("UNIQUE_ID", recordId)
                                            }
                                            activity.startActivity(intent)
                                            activity.finish()
                                        } else {
                                            // Show error message
                                            Toast.makeText(activity, "Failed to transfer recording to server", Toast.LENGTH_LONG).show()

                                            // Ask if user wants to try again or proceed anyway
                                            AlertDialog.Builder(activity)
                                                .setTitle("Transfer Failed")
                                                .setMessage("Would you like to try again or proceed to viewing the QR code?")
                                                .setPositiveButton("Try Again") { _, _ ->
                                                    // Re-click the button to restart the process
                                                    dialogView.findViewById<Button>(R.id.btnSaveOnServer).performClick()
                                                }
                                                .setNegativeButton("Proceed Anyway") { _, _ ->
                                                    val intent = Intent(activity, ShowQrActivity::class.java).apply {
                                                        putExtra("UNIQUE_ID", recordId)
                                                    }
                                                    activity.startActivity(intent)
                                                    activity.finish()
                                                }
                                                .setCancelable(false)
                                                .show()
                                        }
                                    }
                                }
                            )
                        } else {
                            // No transfer needed, proceed to QR code display
                            progressDialog.dismiss()
                            val intent = Intent(activity, ShowQrActivity::class.java).apply {
                                putExtra("UNIQUE_ID", recordId)
                            }
                            activity.startActivity(intent)
                            activity.finish()
                        }
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

        // 2. Save locally
        dialogView.findViewById<Button>(R.id.btnSaveLocally).setOnClickListener {
            dialog.dismiss()
            // Set to offline mode explicitly
            AppConfig.online = false
            activity.startActivity(Intent(activity, MainActivity::class.java))
            activity.finish()
        }

        // 3. Delete recording
        dialogView.findViewById<Button>(R.id.btnDeleteRecording).setOnClickListener {
            dialog.dismiss()
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
}