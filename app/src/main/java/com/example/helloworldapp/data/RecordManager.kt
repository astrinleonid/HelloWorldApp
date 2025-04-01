// RecordManager.kt
package com.example.helloworldapp.data

import AppConfig
import UniqueIdGenerator
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.helloworldapp.R
import com.google.gson.Gson
import getDeviceIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object RecordManager {
    private val recordings = mutableMapOf<String, Recording>()
    private var applicationContext: Context? = null

    // Add this method to RecordManager
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }
    suspend fun initializeRecording(id: String? = null): String {
        // If ID is provided, use it directly
        if (id != null) {
            recordings[id] = Recording(id)
            return id
        }

        // Always generate ID on the device
        val recordingId = UniqueIdGenerator.generateId(isOnline = AppConfig.online)

        // Create new recording with the generated ID
        recordings[recordingId] = Recording(recordingId)

        // If online, register this ID with the server
        if (AppConfig.online) {
            registerIdWithServer(recordingId, AppConfig.numChunks)
        }

        return recordingId
    }
    private suspend fun registerIdWithServer(recordingId: String, numChunks: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            // Prepare device info for the server
            val deviceInfo = mapOf(
                "id" to recordingId,
                "maker" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "deviceId" to UniqueIdGenerator.getDeviceIdentifier()
            )

            // Create the request
            val requestBody = Gson().toJson(deviceInfo)
                .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("${AppConfig.serverIP}/getUniqueId?numChunks=$numChunks")
                .post(requestBody)
                .build()

            // Execute the request
            client.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                if (success) {
                    val responseBody = response.body?.string()
                    Log.d("RecordManager", "Server accepted ID registration: $responseBody")
                } else {
                    Log.e("RecordManager", "Server rejected ID registration: ${response.message}")
                }
                return@withContext success
            }
        } catch (e: Exception) {
            Log.e("RecordManager", "Error registering ID with server", e)
            return@withContext false
        }
    }

    fun syncWithServer(recordId: String, callback: (Boolean) -> Unit) {
        if (!AppConfig.online) {
            callback(true)
            return
        }

        val client = OkHttpClient()
        val request = Request.Builder()
            .url("${AppConfig.serverIP}/get_wav_files?folderId=$recordId")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("RecordManager", "Failed to sync with server", e)
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val jsonData = response.body?.string() ?: return
                    val json = JSONObject(jsonData)
                    val files = json.getString("files").split(" ")
                    val labels = json.optJSONObject("labels") ?: JSONObject()

                    // Get or create the recording
                    val recording = recordings[recordId] ?: run {
                        val newRecording = Recording(recordId)
                        recordings[recordId] = newRecording
                        newRecording
                    }

                    // Process each file to update point records
                    files.forEach { filename ->
                        val labelStr = labels.optString(filename, "")
                        recording.updateFromServerData(filename, labelStr)
                    }
                    callback(true)
                } catch (e: Exception) {
                    Log.e("RecordManager", "Error processing server response", e)
                    callback(false)
                }
            }
        })
    }

    fun getAllPointRecords(recordId: String): Map<Int, PointRecord>? {
        return recordings[recordId]?.points
    }

    fun getRecording(id: String): Recording? = recordings[id]

    fun getAllRecordingIds(): List<String> = recordings.keys.toList()

    fun getFileName(recordingId: String, pointNumber: Int): String {
        return recordings[recordingId]?.generateFileName(pointNumber)
            ?: throw IllegalStateException("Recording $recordingId not found")
    }

    fun setPointRecorded(recordingId: String, pointNumber: Int) {
        recordings[recordingId]?.setPointRecorded(pointNumber)
    }


    // Then update cyclePointLabel to use the stored context
    fun cyclePointLabel(recordingId: String, pointNumber: Int): RecordLabel? {
        val context = applicationContext ?: return null
        return recordings[recordingId]?.getPointRecord(pointNumber)?.cycleLabel(recordingId, context)
    }


    fun isRecorded(recordingId: String, pointNumber: Int): Boolean {
        return recordings[recordingId]?.isPointRecorded(pointNumber) ?: false
    }

    fun getPointRecord(recordingId: String, pointNumber: Int): PointRecord? {
        return recordings[recordingId]?.getPointRecord(pointNumber)
    }

    fun getButtonColor(record: PointRecord): Int {
        return record.getButtonColor()
    }

    fun deleteRecordingSync(id: String, context: Context): Boolean {
        val recording = recordings[id] ?: return false

        return if (AppConfig.online) {
            // For online mode, use ServerApi
            Log.d("RecordManager", "Deleting recording from server: $id")

            val params = mapOf("record_id" to id)
            val result = ServerApi.getSync("/delete_record_folder", params, context)

            when (result) {
                is ServerApi.ApiResult.Success -> {
                    // Server deletion successful, remove from local storage
                    recordings.remove(id)
                    Log.d("RecordManager", "Successfully deleted recording from server: $id")
                    true
                }
                is ServerApi.ApiResult.Error -> {
                    Log.e("RecordManager", "Error deleting recording from server: ${result.message}")
                    false
                }
                else -> {false}
            }
        } else {
            // Offline mode - delete local files
            Log.d("RecordManager", "Deleting recording in offline mode: $id")

            try {
                // Get all points with files
                val pointsWithFiles = recording.points.values.filter { it.fileName != null }
                Log.d("RecordManager", "Found ${pointsWithFiles.size} files to delete for recording $id")

                var hasErrors = false
                var deletedCount = 0

                // Delete each file individually
                for (point in pointsWithFiles) {
                    val fileName = point.fileName ?: continue

                    try {
                        val file = File(context.filesDir, fileName)
                        if (file.exists()) {
                            val success = file.delete()
                            if (success) {
                                deletedCount++
                                Log.d("RecordManager", "Successfully deleted file: $fileName")
                            } else {
                                hasErrors = true
                                Log.e("RecordManager", "Failed to delete file: $fileName")
                            }
                        } else {
                            Log.w("RecordManager", "File doesn't exist: $fileName")
                            // Count as deleted if it doesn't exist
                            deletedCount++
                        }
                    } catch (e: Exception) {
                        hasErrors = true
                        Log.e("RecordManager", "Error deleting file: $fileName", e)
                    }
                }

                // Consider success if we deleted at least some files or if there were no files
                val success = !hasErrors || (pointsWithFiles.isEmpty()) || (deletedCount > 0)

                if (success) {
                    recordings.remove(id)
                    Log.d("RecordManager", "Successfully deleted recording locally: $id")
                }

                success
            } catch (e: Exception) {
                Log.e("RecordManager", "Error in offline deletion", e)
                false
            }
        }
    }
    fun setRemoteFileName(recordingId: String, pointNumber: Int, filename: String) {
        recordings[recordingId]?.setRemoteFileName(pointNumber, filename)
    }

    fun checkServerResponse(callback: (Boolean) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("${AppConfig.serverIP}/checkConnection")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                callback(response.isSuccessful)
            }
        })
    }

    fun playPointRecording(recordingId: String, pointNumber: Int, context: Context) {
        recordings[recordingId]?.playPointRecording(pointNumber, context) ?: run {
            Toast.makeText(context, "Recording $recordingId not found", Toast.LENGTH_SHORT).show()
        }
    }

    fun releaseMediaPlayer() {
        // Delegate to AudioPlaybackManager
        AudioPlaybackManager.getInstance().releaseResources()
    }

    fun resetPoint(recordingId: String, pointNumber: Int, context: Context, onComplete: (Boolean) -> Unit) {
        Log.d("RecordManager", "resetPoint called for recording: $recordingId, point: $pointNumber")

        // First, check if we need to show a confirmation dialog
        val dialogView = LayoutInflater.from(context).inflate(R.layout.confirm_delete_dialog, null)

        // Update message with point number
        dialogView.findViewById<TextView>(R.id.dialogMessage).text =
            "Are you sure you want to delete the recording for point $pointNumber?"

        // Create dialog
        val alertDialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Set up cancel button
        dialogView.findViewById<Button>(R.id.cancelButton).setOnClickListener {
            Log.d("RecordManager", "Reset cancelled by user")
            alertDialog.dismiss()
            onComplete(false)  // Operation cancelled
        }

        // Set up delete button
        dialogView.findViewById<Button>(R.id.deleteButton).setOnClickListener {
            Log.d("RecordManager", "Reset confirmed by user, proceeding with deletion")
            alertDialog.dismiss()

            // Show progress dialog if in online mode
            val progressDialog = if (AppConfig.online) {
                ProgressDialog(context).apply {
                    setMessage("Deleting recording from server...")
                    setCancelable(false)
                    show()
                }
            } else null

            // Use a background thread to perform the deletion
            Thread {
                try {
                    // Get the recording
                    val recording = recordings[recordingId]

                    if (recording == null) {
                        Log.e("RecordManager", "Recording not found: $recordingId")
                        Handler(Looper.getMainLooper()).post {
                            progressDialog?.dismiss()
                            onComplete(false)
                        }
                        return@Thread
                    }

                    // Get the point record
                    val pointRecord = recording.getPointRecord(pointNumber)

                    if (pointRecord == null) {
                        Log.e("RecordManager", "Point not found: $pointNumber")
                        Handler(Looper.getMainLooper()).post {
                            progressDialog?.dismiss()
                            onComplete(false)
                        }
                        return@Thread
                    }

                    // Perform the deletion using Recording.resetPoint
                    Log.d("RecordManager", "Calling Recording.resetPoint")
                    val success = recording.resetPoint(pointNumber, context)
                    Log.d("RecordManager", "Recording.resetPoint result: $success")

                    // Update UI on main thread
                    Handler(Looper.getMainLooper()).post {
                        progressDialog?.dismiss()

                        if (success) {
                            Log.d("RecordManager", "Point reset successful")
                        } else {
                            Log.e("RecordManager", "Point reset failed")
                        }

                        onComplete(success)
                    }
                } catch (e: Exception) {
                    Log.e("RecordManager", "Error resetting point", e)
                    Handler(Looper.getMainLooper()).post {
                        progressDialog?.dismiss()
                        onComplete(false)
                    }
                }
            }.start()
        }

        alertDialog.show()
    }
}




