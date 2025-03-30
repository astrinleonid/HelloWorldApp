package com.example.helloworldapp.data

import AppConfig
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.helloworldapp.R
import kotlin.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File


enum class RecordLabel {
    NOLABEL, POSITIVE, NEGATIVE, UNDETERMINED
}

data class PointRecord(
    val pointNumber: Int,
    var isRecorded: Boolean = false,
    var label: RecordLabel = RecordLabel.NOLABEL,
    var fileName: String? = null
) {
    // Functions moved from RecordManager that operate on a single point

    fun getButtonColor(): Int {
        return when {
            !isRecorded -> R.drawable.button_not_recorded
            label == RecordLabel.POSITIVE -> R.drawable.button_positive
            label == RecordLabel.NEGATIVE -> R.drawable.button_negative
            label == RecordLabel.UNDETERMINED -> R.drawable.button_undetermined
            else -> R.drawable.button_recorded
        }
    }

    fun cycleLabel(): RecordLabel {
        label = when(label) {
            RecordLabel.NOLABEL -> RecordLabel.POSITIVE
            RecordLabel.POSITIVE -> RecordLabel.NEGATIVE
            RecordLabel.NEGATIVE -> RecordLabel.UNDETERMINED
            RecordLabel.UNDETERMINED -> RecordLabel.POSITIVE
        }
        return label
    }

    fun markAsRecorded() {
        isRecorded = true
    }

    fun updateLabel(newLabel: RecordLabel) {
        label = newLabel
    }

    fun reset() {
        isRecorded = false
        label = RecordLabel.NOLABEL
        fileName = null
    }

    fun deleteFile(context: Context, recordingId: String): Boolean {
        Log.d("PointRecord", "deleteFile called for point $pointNumber with recordingId=$recordingId")
        Log.d("PointRecord", "Online mode: ${AppConfig.online}, fileName: $fileName")

        return try {
            if (fileName == null) {
                Log.d("PointRecord", "No filename to delete, returning success")
                return true // No file to delete, consider it a success
            }

            var success = false

            if (AppConfig.online) {
                // Online mode - delete file from server
                Log.d("PointRecord", "Online mode detected, attempting server deletion")

                runBlocking {
                    success = deleteFileFromServer(recordingId, fileName!!)
                }

                Log.d("PointRecord", "Server deletion completed with result: $success")
            } else {
                // Offline mode code...
            }

            // Only reset state if deletion was successful
            if (success) {
                Log.d("PointRecord", "Deletion successful, resetting point state")
                fileName = null
                isRecorded = false
                label = RecordLabel.NOLABEL
            } else {
                Log.d("PointRecord", "Deletion failed, NOT resetting state")
            }

            return success
        } catch (e: Exception) {
            Log.e("PointRecord", "Error deleting file for point $pointNumber", e)
            return false
        }
    }

    // Separate method for local file deletion
    private fun performLocalDelete(context: Context): Boolean {
        if (fileName == null) {
            Log.d("PointRecord", "No filename to delete, returning success")
            return true
        }

        Log.d("PointRecord", "Deleting local file: $fileName")
        try {
            val file = File(context.filesDir, fileName!!)
            Log.d("PointRecord", "File exists: ${file.exists()}, path: ${file.absolutePath}")

            val success = if (file.exists()) file.delete() else true
            Log.d("PointRecord", "Local file deletion result: $success")

            if (success) {
                // Reset state
                fileName = null
                isRecorded = false
                label = RecordLabel.NOLABEL
            }

            return success
        } catch (e: Exception) {
            Log.e("PointRecord", "Error deleting local file", e)
            return false
        }
    }

    // Server file deletion function that will be called on a background thread
    private suspend fun deleteFileFromServer(recordingId: String, fileName: String): Boolean {
        Log.d("PointRecord", "deleteFileFromServer called with recordingId=$recordingId, fileName=$fileName")

        // Extract the point number for the server
        val pointNumberMatch = "point(\\d+)".toRegex().find(fileName)
        val pointNumber = pointNumberMatch?.groupValues?.get(1) ?: run {
            Log.e("PointRecord", "Could not extract point number from filename: $fileName")
            return false
        }

        Log.d("PointRecord", "Extracted point number: $pointNumber")

        // Use suspendCancellableCoroutine instead of CompletableDeferred
        return withContext(Dispatchers.IO) {
            try {
                suspendCancellableCoroutine { continuation ->
                    // Prepare parameters
                    val params = mapOf(
                        "folderId" to recordingId,
                        "fileName" to pointNumber
                    )

                    // Register cancellation
                    continuation.invokeOnCancellation {
                        Log.d("PointRecord", "Request was cancelled")
                    }

                    ServerApi.get("/file_delete", params) { result ->
                        when (result) {
                            is ServerApi.ApiResult.Success -> {
                                Log.d("PointRecord", "Server file deletion successful")
                                if (continuation.isActive) {
                                    continuation.resumeWith(Result.success(true))
                                }
                            }
                            is ServerApi.ApiResult.Error -> {
                                Log.e("PointRecord", "Server file deletion failed: ${result.message}")
                                if (continuation.isActive) {
                                    continuation.resumeWith(Result.success(false))
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PointRecord", "Exception in deleteFileFromServer", e)
                false
            }
        }
    }

    fun playRecording(context: Context, recordingId: String) {
        Log.d("PointRecord", "Starting playback for point: $pointNumber, online: ${AppConfig.online}")

        if (fileName == null) {
            Log.e("PointRecord", "Filename is null for point: $pointNumber")
            Toast.makeText(context, "No recording file found for this point", Toast.LENGTH_SHORT).show()
            return
        }

        // Create dialog and prepare for playback
        val playbackManager = AudioPlaybackManager.getInstance()
        playbackManager.createPlaybackDialog(context, pointNumber)

        if (AppConfig.online) {
            Log.d("PointRecord", "Playing ONLINE file, filename: $fileName")
            // For online mode, call AudioPlaybackManager with necessary info
            playbackManager.prepareOnlinePlayback(context, recordingId, fileName!!, pointNumber)
        } else {
            Log.d("PointRecord", "Playing OFFLINE file, filename: $fileName")
            // For offline mode, get the file path and pass to AudioPlaybackManager
            val file = File(context.filesDir, fileName!!)
            if (!file.exists()) {
                Log.e("PointRecord", "File does not exist: ${file.absolutePath}")
                playbackManager.dismissPlaybackDialog()
                Toast.makeText(context, "Audio file not found", Toast.LENGTH_SHORT).show()
                return
            }
            playbackManager.playLocalFile(file.absolutePath, pointNumber)
        }
    }
}

// Enhanced Recording.kt
data class Recording(
    val id: String,
    val points: Map<Int, PointRecord> = (1..10).associateWith { PointRecord(it) }
) {
    fun getPointRecord(pointNumber: Int): PointRecord? {
        return points[pointNumber]
    }

    fun isPointRecorded(pointNumber: Int): Boolean {
        return points[pointNumber]?.isRecorded ?: false
    }

    fun setPointLabel(pointNumber: Int, label: RecordLabel) {
        points[pointNumber]?.updateLabel(label)
    }

    fun cyclePointLabel(pointNumber: Int): RecordLabel? {
        return points[pointNumber]?.cycleLabel()
    }

    fun getFileName(pointNumber: Int): String? {
        return points[pointNumber]?.fileName
    }

    fun generateFileName(pointNumber: Int): String {
        val fileName = "offline_audio_rec_${id}_point_${pointNumber}_${System.currentTimeMillis()}.wav"
        points[pointNumber]?.fileName = fileName
        return fileName
    }

    fun setPointRecorded(pointNumber: Int) {
        points[pointNumber]?.markAsRecorded()
    }

    fun setRemoteFileName(pointNumber: Int, filename: String) {
        points[pointNumber]?.let { point ->
            point.fileName = filename
            point.isRecorded = true
            Log.d("Recording", "Set remote filename for recordingId=$id, point=$pointNumber: $filename")
        }
    }

    fun resetPoint(pointNumber: Int, context: Context): Boolean {
        return points[pointNumber]?.deleteFile(context, id) ?: false
    }

    fun playPointRecording(pointNumber: Int, context: Context) {
        points[pointNumber]?.playRecording(context, id) ?: run {
            Toast.makeText(context, "Point $pointNumber not found", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateFromServerData(filename: String, labelStr: String) {
        val pointNumber = extractPointNumber(filename) ?: return

        points[pointNumber]?.let { point ->
            // Mark as recorded and store filename
            point.isRecorded = true
            point.fileName = filename

            // Update label if present
            point.label = when(labelStr.lowercase()) {
                "positive" -> RecordLabel.POSITIVE
                "negative" -> RecordLabel.NEGATIVE
                "undetermined" -> RecordLabel.UNDETERMINED
                else -> RecordLabel.NOLABEL
            }
        }
    }

    private fun extractPointNumber(filename: String): Int? {
        return if (AppConfig.online) {
            val match = Regex("""(\d+)\.wav$""").find(filename)
            match?.groupValues?.get(1)?.toIntOrNull()
        } else {
            filename.substringAfter("point_")
                .substringBefore("_")
                .toIntOrNull()
        }
    }

    fun deleteAllFiles(context: Context): Pair<Boolean, Int> {
        var hasErrors = false
        var deletedCount = 0

        // Delete each file associated with points
        points.values.forEach { point ->
            if (point.fileName != null) {
                val success = point.deleteFile(context, id)
                if (!success) hasErrors = true
                deletedCount++
            }
        }

        return Pair(!hasErrors, deletedCount)
    }
}