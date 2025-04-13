package com.example.helloworldapp.utils

import AppConfig
import NetworkQualityChecker
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.helloworldapp.R
import com.example.helloworldapp.data.RecordManager

object SettingsUtils {

    fun showSettingsDialog(context: Context) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_settings, null)

        val serverIpEdit: EditText = dialogView.findViewById(R.id.edit_server_ip)
        val timeoutEdit: EditText = dialogView.findViewById(R.id.edit_timeout)
        val numChunksEdit: EditText = dialogView.findViewById(R.id.edit_num_chunks)
        val chunkLengthEdit: EditText = dialogView.findViewById(R.id.edit_chunk_length)
        val onlineCheckbox: CheckBox = dialogView.findViewById(R.id.checkbox_online)

        // Set current values from AppConfig
        serverIpEdit.setText(AppConfig.serverIP)
        timeoutEdit.setText(AppConfig.timeOut.toString())
        numChunksEdit.setText(AppConfig.numChunks.toString())
        chunkLengthEdit.setText(AppConfig.segmentLength.toString())
        onlineCheckbox.isChecked = AppConfig.online

        AlertDialog.Builder(context)
            .setTitle("Settings")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                AppConfig.serverIP = serverIpEdit.text.toString()
                AppConfig.timeOut = timeoutEdit.text.toString().toIntOrNull() ?: 10
                AppConfig.numChunks = numChunksEdit.text.toString().toIntOrNull() ?: 5
                AppConfig.segmentLength = chunkLengthEdit.text.toString().toIntOrNull() ?: 3
                if (!AppConfig.online and onlineCheckbox.isChecked) {
                    val progressDialog = ProgressDialog(context).apply {
                        setMessage("Checking server connection quality...")
                        setCancelable(false)
                        show()
                    }
                    Toast.makeText(
                        context,
                        "Switching to online mode, checking connection",
                        Toast.LENGTH_SHORT
                    ).show()
                    var networkQualityChecker = NetworkQualityChecker(context = context)
                    networkQualityChecker?.checkConnectionQuality(AppConfig.serverIP) { isQualitySufficient ->
                        progressDialog.dismiss()
                        if (isQualitySufficient) {
                            AppConfig.online = true
                            RecordManager.transferAllOfflineRecordingsToServer(context)
                        } else {
                            Toast.makeText(
                                context,
                                "Switching to online mode failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    AppConfig.online = onlineCheckbox.isChecked
                }

                Toast.makeText(context, "Settings saved!", Toast.LENGTH_SHORT).show()

                // Update toolbar title after settings have been saved
                if (context is AppCompatActivity) {
                    updateToolbarTitle(context)
                }
            }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener {
                // Also update toolbar title when dialog is dismissed
                if (context is AppCompatActivity) {
                    updateToolbarTitle(context)
                }
            }
            .show()
    }

    fun updateToolbarTitle(activity: AppCompatActivity, extraInfo: String? = null) {
        val modeText = if (AppConfig.online) "Online mode" else "Offline mode"
        val title = if (extraInfo != null) {
            "$extraInfo ($modeText)"
        } else {
            "App ($modeText)"
        }
        activity.supportActionBar?.title = title
    }


}