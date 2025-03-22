package com.example.helloworldapp.utils

import android.content.Intent
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
                RecordManager.deleteRecording(id, activity) { success ->
                    if (success) {
                        activity.startActivity(Intent(activity, MainActivity::class.java))
                        activity.finish()
                    } else {
                        Toast.makeText(activity, "Error deleting recording", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // 4. Cancel
        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}