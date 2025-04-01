package com.example.helloworldapp.utils

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
            val intent = Intent(activity, ShowQrActivity::class.java).apply {
                putExtra("UNIQUE_ID", recordId)
            }
            activity.startActivity(intent)
            activity.finish()
        }

        // 2. Save locally
        dialogView.findViewById<Button>(R.id.btnSaveLocally).setOnClickListener {
            dialog.dismiss()
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
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                    Intent.FLAG_ACTIVITY_NEW_TASK)
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