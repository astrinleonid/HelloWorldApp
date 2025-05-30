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
import java.io.File

object RecordManager {
    val recordings = mutableMapOf<String, Recording>()
    private var applicationContext: Context? = null
    private var currentRecording = ""

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

    fun setActive(id: String){
        currentRecording = id
    }

    fun resetActive(){
        currentRecording = ""
    }

    fun getActive() : String {
        return currentRecording
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

    fun getCurrentRecording(): Recording? = recordings[currentRecording]

    fun getAllRecordingIds(): List<String> = recordings.keys.toList()

    fun setPointRecorded(recordingId: String, pointNumber: Int) {
        recordings[recordingId]?.setPointRecorded(pointNumber)
    }

    fun setPointSavedLocally(recordingId: String, pointNumber: Int) {
        recordings[recordingId]?.setPointSavedLocally(pointNumber)
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

    // DELETING
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

    fun deleteRecordingFromServer(recordingId: String, context: Context): Boolean {
        Log.d("RecordManager", "Deleting recording from server: $recordingId")

        if (!AppConfig.online) {
            Log.e("RecordManager", "Cannot delete from server in offline mode")
            return false
        }

        try {
            // Use the URL with query parameter
            val url = "/delete_record_folder?record_id=$recordingId"
            val result = ServerApi.getSync(url, context = context)

            return when (result) {
                is ServerApi.ApiResult.Success -> {
                    // Server deletion successful
                    Log.d("RecordManager", "Successfully deleted recording from server: $recordingId")
                    true
                }
                is ServerApi.ApiResult.Error -> {
                    Log.e("RecordManager", "Error deleting recording from server: ${result.message}")
                    false
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e("RecordManager", "Exception deleting recording from server", e)
            return false
        }
    }

    /**
     * Deletes a recording locally
     * @param recordingId The ID of the recording to delete
     * @param pointNumber The specific point to delete, or null to delete all points
     * @param context The context to use for operations
     * @return true if deletion was successful
     */
    fun deleteRecordingLocally(recordingId: String, pointNumber: Int? = null, context: Context): Boolean {
        try {
            val recording = recordings[recordingId]
            if (recording == null) {
                Log.e("RecordManager", "Recording not found for deletion: $recordingId")
                return false
            }

            if (pointNumber != null) {
                // Delete a specific point
                val point = recording.getPointRecord(pointNumber)
                if (point == null) {
                    Log.e("RecordManager", "Point not found for deletion: $pointNumber")
                    return false
                }

                val fileName = point.fileName
                if (fileName == null) {
                    Log.d("RecordManager", "No file to delete for point $pointNumber")
                    // Reset state even if no file exists
                    point.reset()
                    return true
                }

                // Delete the file
                val file = File(context.filesDir, fileName)
                val success = if (file.exists()) file.delete() else true

                if (success) {
                    // Reset point state after successful deletion
                    point.reset()
                    Log.d("RecordManager", "Successfully deleted local file for point $pointNumber")
                } else {
                    Log.e("RecordManager", "Failed to delete local file for point $pointNumber")
                }

                return success
            } else {
                // Delete all points
                var hasErrors = false
                var deletedCount = 0

                // Get all points with files
                val pointsWithFiles = recording.points.values.filter { it.fileName != null }
                Log.d("RecordManager", "Found ${pointsWithFiles.size} files to delete for recording $recordingId")

                // Delete each file individually
                for (point in pointsWithFiles) {
                    val fileName = point.fileName ?: continue

                    try {
                        val file = File(context.filesDir, fileName)
                        if (file.exists()) {
                            val success = file.delete()
                            if (success) {
                                deletedCount++
                                point.reset()
                                Log.d("RecordManager", "Successfully deleted file: $fileName")
                            } else {
                                hasErrors = true
                                Log.e("RecordManager", "Failed to delete file: $fileName")
                            }
                        } else {
                            Log.w("RecordManager", "File doesn't exist: $fileName")
                            // Count as deleted if it doesn't exist
                            deletedCount++
                            point.reset()
                        }
                    } catch (e: Exception) {
                        hasErrors = true
                        Log.e("RecordManager", "Error deleting file: $fileName", e)
                    }
                }

                // Consider success if we deleted at least some files or if there were no files
                return !hasErrors || (pointsWithFiles.isEmpty()) || (deletedCount > 0)
            }
        } catch (e: Exception) {
            Log.e("RecordManager", "Error deleting recording locally", e)
            return false
        }
    }

    /**
     * Deletes a recording (either from server or locally based on online mode)
     * @param id The ID of the recording to delete
     * @param context The context to use for operations
     * @return true if deletion was successful
     */
    fun deleteRecordingSync(id: String, context: Context): Boolean {
        val recording = recordings[id] ?: return false

        return if (AppConfig.online) {
            // For online mode, use server API
            val success = deleteRecordingFromServer(id, context)

            if (success) {
                // If server deletion was successful, also remove local files
                deleteRecordingLocally(id, null, context)
                recordings.remove(id)
            }

            success
        } else {
            // Offline mode - delete local files only
            val success = deleteRecordingLocally(id, null, context)

            if (success) {
                recordings.remove(id)
            }

            success
        }
    }

    // Add this function to RecordManager
    fun removeRecordingFromList(recordingId: String) {
        recordings.remove(recordingId)
        Log.d("RecordManager", "Removed recording $recordingId from list")
    }

    fun generateFilename(recordingId: String, pointNumber: String) : String {
        return "${recordingId}point${pointNumber}.wav"
    }
    fun setFileName(recordingId: String, pointNumber: Int, fileName: String) : String? {
        val rec = recordings[recordingId]?: throw IllegalStateException("Recording $recordingId not found")
        rec.setFileName(pointNumber, fileName)
        return rec.getFileName(pointNumber)
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


    fun transferAllOfflineRecordingsToServer(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        Log.d("RecordManager", "Starting transfer of all offline recordings to server")

        if (!AppConfig.online) {
            Log.e("RecordManager", "Cannot transfer recordings: device is offline")
            Toast.makeText(context, "Cannot transfer recordings while offline", Toast.LENGTH_SHORT).show()
            onComplete?.invoke(false)
            return
        }

        // Get all recording IDs
        val allRecordingIds = recordings.keys.toList()
        Log.d("RecordManager", "Found ${allRecordingIds.size} recordings to evaluate for transfer")

        if (allRecordingIds.isEmpty()) {
            Log.d("RecordManager", "No recordings found to transfer")
            Toast.makeText(context, "No recordings to transfer", Toast.LENGTH_SHORT).show()
            onComplete?.invoke(true) // Success but nothing to do
            return
        } else {
            // BUG FOUND: This else block has an incorrect toast message that gets displayed even when recordings exist
            // Changed from "No recordings to transfer" to show actual count
            Log.d("RecordManager", "Found ${allRecordingIds.size} recordings for transfer")
            Toast.makeText(context, "Found ${allRecordingIds.size} recordings to transfer", Toast.LENGTH_SHORT).show()
        }

        // Create progress dialog
        val progressDialog = ProgressDialog(context).apply {
            setTitle("Transferring Recordings")
            setMessage("Preparing to transfer recordings...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setCancelable(false)
            max = 100
            progress = 0
            show()
        }
        Log.d("RecordManager", "Progress dialog created and shown")

        // Counter for completed transfers
        val totalRecordings = allRecordingIds.size
        var completedCount = 0
        var successCount = 0

        // Log which recording is currently active and will be skipped
        Log.d("RecordManager", "Current active recording ID: $currentRecording (will be skipped)")

        // Execute transfer in background thread
        Thread {
            Log.d("RecordManager", "Starting background transfer thread")
            try {
                for (recordingId in allRecordingIds) {
                    Log.d("RecordManager", "Processing recording ID: $recordingId")

                    // Update progress dialog on main thread
                    Handler(Looper.getMainLooper()).post {
                        val progressMessage = "Transferring recording $completedCount of $totalRecordings..."
                        Log.d("RecordManager", progressMessage)
                        progressDialog.setMessage(progressMessage)
                        progressDialog.progress = (completedCount * 100) / totalRecordings
                    }

                    // Log before transfer attempt
                    Log.d("RecordManager", "Attempting to transfer recording: $recordingId")

                    // Transfer individual recording
                    transferOfflineRecordingToServer(
                        recordingId = recordingId,
                        context = context,
                        onProgress = { progress ->
                            // We're using the progress of individual transfer as a sub-progress
                            // But we don't update the main progress dialog to avoid UI flickering
                            Log.v("RecordManager", "Recording $recordingId transfer progress: $progress%")
                        },
                        onComplete = { success ->
                            if (success) {
                                successCount++
                                Log.d("RecordManager", "Successfully transferred recording: $recordingId")
                            } else {
                                Log.e("RecordManager", "Failed to transfer recording: $recordingId")
                            }

                            // Count as completed regardless of success
                            completedCount++
                            Log.d("RecordManager", "Completed: $completedCount/$totalRecordings (Success: $successCount)")
                        }
                    )

                    // Small delay to prevent overwhelming the server
                    Log.d("RecordManager", "Sleeping 500ms before next transfer")
                    Thread.sleep(500)
                }

                // Wait for all transfers to complete
                Log.d("RecordManager", "All transfer requests sent, waiting for completions...")
                while (completedCount < totalRecordings) {
                    Log.d("RecordManager", "Waiting for completions: $completedCount/$totalRecordings done")
                    Thread.sleep(100)
                }

                // Update UI on main thread with final result
                Log.d("RecordManager", "All transfers completed. Updating UI on main thread")
                Handler(Looper.getMainLooper()).post {
                    try {
                        progressDialog.dismiss()
                        Log.d("RecordManager", "Progress dialog dismissed")

                        // Show completion message
                        val message = if (successCount > 0) {
                            "Successfully transferred $successCount out of ${totalRecordings - 1} recordings"
                        } else {
                            "Failed to transfer any recordings"
                        }

                        Log.d("RecordManager", message)
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        Log.d("RecordManager", "Transfer completed: $successCount/${totalRecordings - 1} recordings transferred")

                        // Call the completion callback with success if at least one recording was transferred
                        Log.d("RecordManager", "Calling onComplete with result: ${successCount > 0}")
                        onComplete?.invoke(successCount > 0)
                    } catch (e: Exception) {
                        Log.e("RecordManager", "Error in UI update after transfer", e)
                        Toast.makeText(context, "Error updating UI after transfer: ${e.message}", Toast.LENGTH_LONG).show()
                        onComplete?.invoke(false)
                    }
                }

            } catch (e: Exception) {
                Log.e("RecordManager", "Error transferring all recordings", e)
                Log.e("RecordManager", "Stack trace: ${e.stackTraceToString()}")

                // Update UI on main thread
                Handler(Looper.getMainLooper()).post {
                    try {
                        progressDialog.dismiss()
                        Log.d("RecordManager", "Progress dialog dismissed after error")
                        Toast.makeText(context, "Error transferring recordings: ${e.message}", Toast.LENGTH_LONG).show()
                        onComplete?.invoke(false)
                    } catch (innerException: Exception) {
                        Log.e("RecordManager", "Error dismissing dialog after exception", innerException)
                        Toast.makeText(context, "Multiple errors occurred during transfer", Toast.LENGTH_LONG).show()
                        onComplete?.invoke(false)
                    }
                }
            }
        }.start()

        Log.d("RecordManager", "Background transfer thread started")
    }

    fun syncMetadataToServer(recordingId: String, metadata: Metadata, context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        if (!AppConfig.online) {
            Log.d("RecordManager", "Not syncing metadata: offline mode")
            onComplete?.invoke(false)
            return
        }

        // Execute in background thread
        Thread {
            try {
                // Prepare metadata in the format expected by server
                val metadataMap = mapOf(
                    "user" to metadata.user,
                    "age" to metadata.age,
                    "sex" to metadata.sex,
                    "height" to metadata.height,
                    "weight" to metadata.weight,
                    "diagnosis" to metadata.diagnosis,
                    "comment" to metadata.comment
                )

                // Create URL with query parameter
                val url = "/update_metadata?record_id=$recordingId"

                // Use ServerApi for the request
                val result = ServerApi.postJsonSync(url, metadataMap, context)

                when (result) {
                    is ServerApi.ApiResult.Success -> {
                        Log.d("RecordManager", "Metadata synced successfully to server for recording: $recordingId")
                        Handler(Looper.getMainLooper()).post {
                            onComplete?.invoke(true)
                        }
                    }
                    is ServerApi.ApiResult.Error -> {
                        Log.e("RecordManager", "Failed to sync metadata to server: ${result.message}")
                        Handler(Looper.getMainLooper()).post {
                            onComplete?.invoke(false)
                        }
                    }
                    else -> {
                        Handler(Looper.getMainLooper()).post {
                            onComplete?.invoke(false)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RecordManager", "Error syncing metadata to server", e)
                Handler(Looper.getMainLooper()).post {
                    onComplete?.invoke(false)
                }
            }
        }.start()
    }

    // Update the existing setMetadata function or add this new one
    fun setMetadata(recordingId: String, metadata: Metadata, context: Context? = null) {
        val recording = recordings[recordingId]
        if (recording == null) {
            Log.e("RecordManager", "Recording not found: $recordingId")
            return
        }

        // Update local metadata
        recording.meta = metadata
        Log.d("RecordManager", "Metadata updated locally for recording: $recordingId")

        // If online and context available, sync to server
        if (AppConfig.online && context != null) {
            syncMetadataToServer(recordingId, metadata, context) { success ->
                if (success) {
                    Log.d("RecordManager", "Metadata successfully synced to server")
                } else {
                    Log.w("RecordManager", "Failed to sync metadata to server, but saved locally")
                }
            }
        }
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
                syncMetadataToServer(recording.id, recording.meta!!, context) { success ->
                    if (!success) {
                        Log.w("RecordManager", "Failed to sync metadata for recording: $recordingId")
                    } else {
                        Log.d("RecordManager", "Metadata synced successfully for recording: $recordingId")
                    }
                }
                // Step 2: Upload each file individually
                var successCount = 0
                val totalFiles = pointsWithFiles.size
                val successfulPoints = mutableListOf<Int>()

                for ((index, point) in pointsWithFiles.withIndex()) {
                    val fileName = point.fileName ?: continue
                    val localFile = File(context.filesDir, fileName)

                    if (!localFile.exists()) {
                        Log.w("RecordManager", "Local file not found: ${localFile.absolutePath}")
                        continue
                    }

                    // Calculate progress
                    val progress = 10 + (index * 70 / totalFiles)
                    Handler(Looper.getMainLooper()).post { onProgress(progress) }

                    // Use the point number as the server filename
                    val pointNumber = point.pointNumber

                    // Upload the file using ServerApi
                    val fileUploaded = uploadFileToServer(localFile, recordingId, fileName, pointNumber, context)

                    if (fileUploaded) {
                        successCount++
                        successfulPoints.add(pointNumber)
                        Log.d("RecordManager", "Successfully uploaded file for point $pointNumber")
                    } else {
                        Log.e("RecordManager", "Failed to upload file for point $pointNumber")
                    }
                }

                // Step 3: Delete local files for successfully transferred points
                // ONLY if this is not the active recording
                if (successCount > 0) {
                    Handler(Looper.getMainLooper()).post { onProgress(85) }

                    // Check if this is the active recording
                    val isActiveRecording = (recordingId == currentRecording)

                    if (!isActiveRecording) {
                        // Only delete local files if this is NOT the active recording
                        for (pointNumber in successfulPoints) {
                            deleteRecordingLocally(recordingId, pointNumber, context)
                        }
                        recordings.remove(recordingId)
                        Log.d("RecordManager", "Deleted local files for ${successfulPoints.size} points")
                    } else {
                        Log.d("RecordManager", "Kept local files for active recording: $recordingId")
                    }
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

    private fun uploadFileToServer(file: File, recordingId: String, serverFileName: String, pointNumber: Int, context: Context): Boolean {
        try {
            Log.d("RecordManager_upload", "Uploading file: ${file.name} to server as $serverFileName")

            // Get the point record to update
            val recording = recordings[recordingId]
            val pointRecord = recording?.getPointRecord(pointNumber)

            if (pointRecord == null) {
                Log.e("RecordManager", "Point record not found for point $pointNumber")
                return false
            }

            // Store the original label
            val originalLabel = pointRecord.label

            // Create a temporary file with server-compatible name if needed
            val tempFile = File(context.cacheDir, serverFileName)
            file.copyTo(tempFile, overwrite = true)

            // Prepare file info for ServerApi
            val fileInfo = ServerApi.FileInfo(
                path = tempFile.absolutePath,
                name = serverFileName, // Keep the server filename
                mimeType = "audio/wav"
            )

            // Prepare params for the upload using upload_file route
            val params = mapOf(
                "button_number" to pointNumber.toString(),
                "record_id" to recordingId,
                "pointRecordId" to recordingId, // Using recordingId as pointRecordId for simplicity
                "fileName" to serverFileName
            )

            // Map of files to upload
            val files = mapOf("file" to fileInfo)

            // Use ServerApi.postSync for the file upload with the correct route
            val uploadResult = ServerApi.postSync(
                route = "/upload_file",  // This is the correct route for direct file uploads
                params = params,
                files = files,
                context = context
            )

            // Clean up temp file
            tempFile.delete()

            var uploadSuccess = false

            // Process upload result
            when (uploadResult) {
                is ServerApi.ApiResult.Success -> {
                    // Update point with the new filename
                    pointRecord.fileName = serverFileName
                    uploadSuccess = true

                    // Now update the label separately
                    val labelParams = mapOf(serverFileName to originalLabel.toString().lowercase())
                    val labelUrl = "/update_labels?folderId=$recordingId"

                    // Update label on server
                    val labelResult = ServerApi.postJsonSync(labelUrl, labelParams, context)
                    when (labelResult) {
                        is ServerApi.ApiResult.Success -> {
                            Log.d("RecordManager", "Label updated for $serverFileName")
                        }
                        is ServerApi.ApiResult.Error -> {
                            Log.e("RecordManager", "Failed to update label: ${labelResult.message}")
                        }
                    }

                    Log.d("RecordManager", "File upload successful: $serverFileName")
                }
                is ServerApi.ApiResult.Error -> {
                    Log.e("RecordManager", "File upload failed: ${uploadResult.message}")
                }
            }

            return uploadSuccess

        } catch (e: Exception) {
            Log.e("RecordManager", "Error uploading file", e)
            return false
        }
    }
}



