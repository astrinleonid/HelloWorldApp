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
import getDeviceIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object RecordManager {
    private val recordings = mutableMapOf<String, Recording>()
    private var applicationContext: Context? = null

    // Initialize with application context
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
            // Prepare device info for the server
            val deviceInfo = mapOf(
                "id" to recordingId,
                "maker" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "deviceId" to UniqueIdGenerator.getDeviceIdentifier()
            )

            // Create URL with query parameter
            val url = "/getUniqueId?numChunks=$numChunks"

            // Use ServerApi for the request
            val result = ServerApi.postJsonSync(url, deviceInfo, applicationContext)

            when (result) {
                is ServerApi.ApiResult.Success -> {
                    Log.d("RecordManager", "Server accepted ID registration: ${result.data}")
                    true
                }
                is ServerApi.ApiResult.Error -> {
                    Log.e("RecordManager", "Server rejected ID registration: ${result.message}")
                    false
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e("RecordManager", "Error registering ID with server", e)
            false
        }
    }

    fun syncWithServer(recordId: String, callback: (Boolean) -> Unit) {
        if (!AppConfig.online) {
            callback(true)
            return
        }

        // Use the query parameter in the URL
        val url = "/get_wav_files?folderId=$recordId"

        // Use ServerApi for the request
        ServerApi.get(url, context = applicationContext) { result ->
            when (result) {
                is ServerApi.ApiResult.Success -> {
                    try {
                        val jsonData = result.data
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
                is ServerApi.ApiResult.Error -> {
                    Log.e("RecordManager", "Failed to sync with server: ${result.message}")
                    callback(false)
                }
                else -> callback(false)
            }
        }
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

            // Use the URL with query parameter
            val url = "/delete_record_folder?record_id=$id"
            val result = ServerApi.getSync(url, context = context)

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
                else -> false
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
        // Use ServerApi for the connection check
        ServerApi.get("/checkConnection", context = applicationContext) { result ->
            when (result) {
                is ServerApi.ApiResult.Success -> callback(true)
                else -> callback(false)
            }
        }
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


    fun transferOfflineRecordingToServer(
        recordingId: String,
        context: Context,
        onProgress: (Int) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        Log.d("RecordManager", "Starting transfer of offline recording: $recordingId")

        // Validate recording exists
        val recording = recordings[recordingId]
        if (recording == null) {
            Log.e("RecordManager", "Recording not found: $recordingId")
            onComplete(false)
            return
        }

        // Must be in online mode to transfer
        if (!AppConfig.online) {
            Log.e("RecordManager", "Cannot transfer recording: device is offline")
            onComplete(false)
            return
        }

        // Get all recorded points with files
        val pointsWithFiles = recording.points.values.filter { it.isRecorded && it.fileName != null }
        if (pointsWithFiles.isEmpty()) {
            Log.w("RecordManager", "No files to transfer for recording: $recordingId")
            onComplete(true) // Consider this a success since there's nothing to do
            return
        }

        // Execute transfer in background thread
        Thread {
            try {
                // Step 1: Register the recording ID with server
                onProgress(5)
                val idRegistered = runBlocking { registerIdWithServer(recordingId, AppConfig.numChunks) }
                if (!idRegistered) {
                    Log.e("RecordManager", "Failed to register recording ID with server")
                    Handler(Looper.getMainLooper()).post { onComplete(false) }
                    return@Thread
                }

                // Step 2: Upload each file
                var successCount = 0
                val totalFiles = pointsWithFiles.size

                for ((index, point) in pointsWithFiles.withIndex()) {
                    val fileName = point.fileName ?: continue
                    val localFile = File(context.filesDir, fileName)

                    if (!localFile.exists()) {
                        Log.w("RecordManager", "Local file not found: ${localFile.absolutePath}")
                        continue
                    }

                    // Calculate progress (10-90%, reserving beginning and end for registration and label sync)
                    val progress = 10 + (index * 80 / totalFiles)
                    Handler(Looper.getMainLooper()).post { onProgress(progress) }

                    // Create server filename that follows the server's convention
                    // Typically point number followed by .wav
                    val pointNumber = point.pointNumber
                    val serverFileName = "$pointNumber.wav"

                    // Upload the file using ServerApi
                    val fileUploaded = uploadFileToServer(localFile, recordingId, serverFileName, context)

                    if (fileUploaded) {
                        // Update the point with the server filename
                        point.fileName = serverFileName
                        successCount++
                        Log.d("RecordManager", "Successfully uploaded file $serverFileName for point $pointNumber")
                    } else {
                        Log.e("RecordManager", "Failed to upload file for point $pointNumber")
                    }
                }

                // Step 3: Sync labels if any files were uploaded
                if (successCount > 0) {
                    onProgress(95)
                    recording.syncLabelsWithServer(context)
                }

                // Complete with success if at least one file was uploaded
                val transferSuccess = successCount > 0
                Log.d("RecordManager", "Transfer completed: $successCount/$totalFiles files uploaded")

                Handler(Looper.getMainLooper()).post {
                    onProgress(100)
                    onComplete(transferSuccess)
                }

            } catch (e: Exception) {
                Log.e("RecordManager", "Error transferring recording", e)
                Handler(Looper.getMainLooper()).post { onComplete(false) }
            }
        }.start()
    }

    /**
     * Uploads a local file to the server using ServerApi
     * @return true if upload was successful
     */
    /**
     * Uploads a local file to the server using ServerApi
     * @return true if upload was successful
     */
    private fun uploadFileToServer(file: File, recordingId: String, serverFileName: String, context: Context): Boolean {
        try {
            Log.d("RecordManager", "Uploading file: ${file.name} to server as $serverFileName")

            // Extract the point number from the server filename
            val pointNumber = serverFileName.replace(".wav", "").toIntOrNull() ?: run {
                // Alternative extraction for different filename formats
                val match = "\\d+".toRegex().find(serverFileName)?.value?.toIntOrNull()
                match ?: return false
            }

            // Generate a point record ID for the server
            // This should match how your server validates it
            val pointRecordId = "$recordingId-$pointNumber" // Adjust this format if needed

            // Prepare file info for ServerApi
            val fileInfo = ServerApi.FileInfo(
                path = file.absolutePath,
                name = file.name, // Keep original name as file.filename for upload
                mimeType = "audio/wav"
            )

            // Prepare params for the upload according to the server endpoint
            val params = mapOf(
                "record_id" to recordingId,
                "button_number" to pointNumber.toString(),
                "pointRecordId" to pointRecordId
            )

            // Map of files to upload (field name -> file info)
            val files = mapOf("file" to fileInfo)

            // Use ServerApi.postSync for the file upload
            val result = ServerApi.postSync(
                route = "/upload_file", // The correct route based on your server
                params = params,
                files = files,
                context = context
            )

            return when (result) {
                is ServerApi.ApiResult.Success -> {
                    try {
                        // Parse the response to check for success
                        val responseJson = JSONObject(result.data)

                        if (responseJson.has("error")) {
                            Log.e("RecordManager", "Server returned error: ${responseJson.getString("error")}")
                            return false
                        }

                        val message = responseJson.optString("message", "")
                        val returnedFilename = responseJson.optString("filename", "")

                        Log.d("RecordManager", "File upload successful: $returnedFilename, message: $message")
                        return true
                    } catch (e: Exception) {
                        Log.e("RecordManager", "Error parsing upload response", e)
                        return false
                    }
                }
                is ServerApi.ApiResult.Error -> {
                    Log.e("RecordManager", "File upload failed: ${result.message}")
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e("RecordManager", "Error uploading file", e)
            return false
        }
    }
    // Add method to sync all labels with server
}



