// RecordManager.kt
package com.example.helloworldapp.data

import AppConfig
import UniqueIdGenerator
import android.app.AlertDialog
import android.content.Context
import android.os.Build
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
import java.io.IOException
import java.util.concurrent.TimeUnit

object RecordManager {
    private val recordings = mutableMapOf<String, Recording>()

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

    private fun generateOfflineId(): String {
        val timestamp = System.currentTimeMillis()
        val random = (0..999999).random().toString().padStart(6, '0')
        val deviceHash = (Build.MODEL + Build.MANUFACTURER).hashCode().toString().takeLast(4)
        return "OFF${timestamp}${deviceHash}${random}"
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

    fun setPointLabel(recordingId: String, pointNumber: Int, label: RecordLabel) {
        recordings[recordingId]?.setPointLabel(pointNumber, label)
    }

    fun cyclePointLabel(recordingId: String, pointNumber: Int): RecordLabel? {
        return recordings[recordingId]?.cyclePointLabel(pointNumber)
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

    fun deleteRecording(id: String, context: Context, onComplete: (Boolean) -> Unit) {
        val recording = recordings[id] ?: run {
            onComplete(false)
            return
        }

        // Delete all files associated with the recording
        val (success, deletedCount) = recording.deleteAllFiles(context)

        // Remove the recording from the map
        recordings.remove(id)

        // Call the completion handler with success status
        onComplete(success)
    }

    fun setRemoteFileName(recordingId: String, pointNumber: Int, filename: String) {
        recordings[recordingId]?.setRemoteFileName(pointNumber, filename)
    }

    fun deleteFromServer(uniqueId: String?, callback: (Boolean, String?) -> Unit) {
        val url = "${AppConfig.serverIP}/record_delete?record_id=${uniqueId}"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, "Error deleting record")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    callback(true, "Record deleted successfully")
                } else {
                    callback(false, "Failed to delete record: ${response.message}")
                }
            }
        })
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

    fun stopPlayback() {
        // Delegate to AudioPlaybackManager
        AudioPlaybackManager.getInstance().stopPlayback()
    }

    fun releaseMediaPlayer() {
        // Delegate to AudioPlaybackManager
        AudioPlaybackManager.getInstance().releaseResources()
    }

    fun resetPoint(recordingId: String, pointNumber: Int, context: Context, onComplete: (Boolean) -> Unit) {
        // Inflate custom dialog layout
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
            alertDialog.dismiss()
            onComplete(false)  // Operation cancelled
        }

        // Set up delete button
        dialogView.findViewById<Button>(R.id.deleteButton).setOnClickListener {
            alertDialog.dismiss()
            val success = recordings[recordingId]?.resetPoint(pointNumber, context) ?: false
            onComplete(success)
        }

        alertDialog.show()
    }
}




