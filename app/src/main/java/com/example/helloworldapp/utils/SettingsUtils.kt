package com.example.helloworldapp.utils

import AppConfig
import NetworkQualityChecker
import android.app.ProgressDialog
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.helloworldapp.R
import com.example.helloworldapp.components.CustomToolbar
import com.example.helloworldapp.data.RecordManager

object SettingsUtils {
    private const val TAG = "SettingsUtils"
    private const val ADMIN_PASSWORD = "TeproAdmin"
    /**
     * Shows the settings dialog to the user
     */
    fun showSettingsDialog(context: Context) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_settings, null)

        val serverIpEdit: EditText = dialogView.findViewById(R.id.edit_server_ip)
        val timeoutEdit: EditText = dialogView.findViewById(R.id.edit_timeout)
        val numChunksEdit: EditText = dialogView.findViewById(R.id.edit_num_chunks)
        val chunkLengthEdit: EditText = dialogView.findViewById(R.id.edit_chunk_length)
        val onlineCheckbox: CheckBox = dialogView.findViewById(R.id.checkbox_online)
        // Add reference to the admin password field
        val adminPasswordEdit: EditText = dialogView.findViewById(R.id.admin_rights)

        // Set current values from AppConfig
        serverIpEdit.setText(AppConfig.serverIP)
        timeoutEdit.setText(AppConfig.timeOut.toString())
        numChunksEdit.setText(AppConfig.numChunks.toString())
        chunkLengthEdit.setText(AppConfig.segmentLength.toString())
        onlineCheckbox.isChecked = AppConfig.online

        // Set admin password field based on current admin status
        if (AppConfig.admin) {
            adminPasswordEdit.setText(ADMIN_PASSWORD)
        } else {
            adminPasswordEdit.setText("")
        }

        AlertDialog.Builder(context)
            .setTitle("Settings")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                AppConfig.serverIP = serverIpEdit.text.toString()
                AppConfig.timeOut = timeoutEdit.text.toString().toIntOrNull() ?: 10
                AppConfig.numChunks = numChunksEdit.text.toString().toIntOrNull() ?: 5
                AppConfig.segmentLength = chunkLengthEdit.text.toString().toIntOrNull() ?: 3

                // Handle admin mode
                val enteredPassword = adminPasswordEdit.text.toString()
                val wasAdmin = AppConfig.admin
                if (enteredPassword == ADMIN_PASSWORD) {
                    AppConfig.admin = true
                    if (!wasAdmin) {
                        // Only show toast if switching to admin mode
                        Toast.makeText(context, "Admin mode activated", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    if (enteredPassword.isEmpty()) {
                        // Empty password field - turn off admin mode
                        if (wasAdmin) {
                            AppConfig.admin = false
                            Toast.makeText(context, "Admin mode deactivated", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Incorrect password
                        AppConfig.admin = false
                        if (wasAdmin) {
                            Toast.makeText(context, "Admin mode deactivated - incorrect password", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Incorrect admin password", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // Handle online/offline mode change
                if (!AppConfig.online && onlineCheckbox.isChecked) {
                    // Switching from offline to online
                    goOnline(context)
                } else if (AppConfig.online && !onlineCheckbox.isChecked) {
                    // Switching from online to offline
                    goOffline(context)
                } else {
                    // No mode change, just update UI
                    if (context is AppCompatActivity) {
                        updateToolbarTitle(context)
                    }
                }

                Toast.makeText(context, "Settings saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener {
                // Also update toolbar title and online indicator when dialog is dismissed
                if (context is AppCompatActivity) {
                    updateToolbarTitle(context)
                }
            }
            .show()
    }

    /**
     * Attempts to switch the app to online mode
     * Shows a progress dialog, checks connection quality, and updates UI
     * @param context The current context
     * @param callback Optional callback that returns true if switched to online successfully
     */
    fun goOnline(context: Context, callback: ((Boolean) -> Unit)? = null) {
        Log.d(TAG, "Attempting to go online, current status: ${AppConfig.online}")

        val progressDialog = ProgressDialog(context).apply {
            setMessage("Checking server connection quality...")
            setCancelable(false)
            show()
        }

        Toast.makeText(
            context,
            "Checking connection to server...",
            Toast.LENGTH_SHORT
        ).show()

        val networkQualityChecker = NetworkQualityChecker(context = context)
        networkQualityChecker.checkConnectionQuality(AppConfig.serverIP) { isQualitySufficient ->
            progressDialog.dismiss()

            if (isQualitySufficient) {
                val wasOfflineBefore = !AppConfig.online
                Log.d(TAG, "Connection check successful, wasOfflineBefore: $wasOfflineBefore")

                AppConfig.online = true

                Toast.makeText(
                    context,
                    "Switched to online mode",
                    Toast.LENGTH_SHORT
                ).show()

                // Update UI
                if (context is AppCompatActivity) {
                    updateToolbarTitle(context)
                }

                // If we were previously offline, transfer recordings to server
                // and update the list when complete
                if (wasOfflineBefore) {
                    Log.d(TAG, "Status changed from offline to online, transferring recordings")
                    RecordManager.transferAllOfflineRecordingsToServer(context) { transferSuccess ->
                        // This callback will be invoked after the transfer completes
                        Log.d(TAG, "Transfer completed with success: $transferSuccess")

                        // Notify original callback
                        callback?.invoke(true)
                    }
                } else {
                    Log.d(TAG, "Already online, no transfer needed")
                    callback?.invoke(true)
                }
            } else {
                Log.d(TAG, "Connection check failed")

                Toast.makeText(
                    context,
                    "Cannot switch to online mode. Connection quality insufficient.",
                    Toast.LENGTH_SHORT
                ).show()

                callback?.invoke(false)
            }
        }
    }

    /**
     * Switches the app to offline mode
     * @param context The current context
     */
    fun goOffline(context: Context) {
        AppConfig.online = false

        Toast.makeText(
            context,
            "Switched to offline mode",
            Toast.LENGTH_SHORT
        ).show()

        // Update UI
        if (context is AppCompatActivity) {
            updateToolbarTitle(context)
        }
    }

    /**
     * Updates the toolbar title to reflect the current online/offline mode
     * @param activity The current activity
     * @param extraInfo Optional extra information to include in the title
     */
    fun updateToolbarTitle(activity: AppCompatActivity, extraInfo: String? = null) {
        val modeText = if (AppConfig.online) "Online mode" else "Offline mode"
        val title = if (extraInfo != null) {
            "$extraInfo ($modeText)"
        } else {
            "App ($modeText)"
        }
        activity.supportActionBar?.title = title

        // Update online/offline indicator
        val toolbar = activity.findViewById<CustomToolbar>(R.id.top_app_bar)
        toolbar?.setOnlineMode(AppConfig.online)
    }
}