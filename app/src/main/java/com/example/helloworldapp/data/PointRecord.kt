package com.example.helloworldapp.data

import AppConfig
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.helloworldapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File


enum class RecordLabel {
    NOLABEL, POSITIVE, NEGATIVE, UNDETERMINED
}

data class PointRecord(
    val pointNumber: Int,
    var isRecorded: Boolean = false,
    var offlineFile: Boolean = false,
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

    private fun updateLabelOnServer(recordingId: String, context: Context) {
        if (!AppConfig.online || fileName == null) return

        // Use a background thread for the network operation
        Thread {
            try {
                // Create the label data map
                val labels = mapOf(fileName!! to label.toString().lowercase())

                // Correctly include the folderId as a URL parameter
                val url = "/update_labels?folderId=$recordingId"

                // Use the generic postJson method from ServerApi with the URL including parameters
                val result = ServerApi.postJsonSync(url, labels, context)

                when (result) {
                    is ServerApi.ApiResult.Success -> {
                        Log.d("PointRecord", "Label updated successfully on server for point $pointNumber")
                    }
                    is ServerApi.ApiResult.Error -> {
                        Log.e("PointRecord", "Failed to update label on server: ${result.message}")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e("PointRecord", "Error updating label on server", e)
            }
        }.start()
    }
    // Update cycleLabel in PointRecord to support server updates
    fun cycleLabel(recordingId: String? = null, context: Context? = null): RecordLabel {
        label = when(label) {
            RecordLabel.NOLABEL -> RecordLabel.POSITIVE
            RecordLabel.POSITIVE -> RecordLabel.NEGATIVE
            RecordLabel.NEGATIVE -> RecordLabel.UNDETERMINED
            RecordLabel.UNDETERMINED -> RecordLabel.POSITIVE
        }

        // If online and we have recording ID and context, update label on the server
        if (AppConfig.online && recordingId != null && context != null && fileName != null) {
            updateLabelOnServer(recordingId, context)
        }

        return label
    }

    fun markAsRecorded() {
        isRecorded = true
    }

    fun markAsLocal() {
        offlineFile = true
    }

    fun isLocalFile() : Boolean {
        return offlineFile
    }
    fun reset() {
        isRecorded = false
        label = RecordLabel.NOLABEL
        fileName = null
    }

    fun deleteFile(context: Context, recordingId: String): Boolean {
        Log.d("PointRecord", "deleteFile called for point $pointNumber with recordingId=$recordingId")
        Log.d("PointRecord", "Online mode: ${AppConfig.online}, fileName: $fileName")

        if (fileName == null) {
            Log.d("PointRecord", "No filename to delete, returning success")
            // Reset state even if no file exists
            reset()
            return true
        }

        try {
            if (AppConfig.online) {
                // Online mode - delete file from server
                return deleteFileFromServer(context, recordingId)
            } else {
                // Offline mode - delete local file
                return deleteLocalFile(context)
            }
        } catch (e: Exception) {
            Log.e("PointRecord", "Error deleting file for point $pointNumber", e)
            return false
        }
    }

    private fun deleteLocalFile(context: Context): Boolean {
        Log.d("PointRecord", "Deleting local file: $fileName")

        try {
            val file = File(context.filesDir, fileName!!)
            Log.d("PointRecord", "File exists: ${file.exists()}, path: ${file.absolutePath}")

            val success = if (file.exists()) file.delete() else true
            Log.d("PointRecord", "Local file deletion result: $success")

            if (success) {
                // Reset state after successful deletion
                reset()
                Log.d("PointRecord", "Offline file deleted successfully, state reset")
            }

            return success
        } catch (e: Exception) {
            Log.e("PointRecord", "Error deleting local file for point $pointNumber", e)
            return false
        }
    }

    private fun deleteFileFromServer(context: Context, recordingId: String): Boolean {
        Log.d("PointRecord", "Deleting file from server: $fileName, recordingId: $recordingId")

        // Extract the point number
        val pointNumberMatch = "point(\\d+)".toRegex().find(fileName!!)
        val pointNumber = pointNumberMatch?.groupValues?.get(1) ?: run {
            // Try alternative pattern if the first one doesn't match
            val altPattern = "(\\d+)\\.wav$".toRegex().find(fileName!!)?.groupValues?.get(1)
                ?: this.pointNumber.toString()
            Log.d("PointRecord", "Using alternative point number: $altPattern")
            altPattern
        }

        val fileNameString = fileName ?: ""
        Log.d("PointRecord", "Using point number: $pointNumber for server deletion")

        // Use ServerApi for server communication
        val params = mapOf(
            "folderId" to recordingId,
            "fileName" to fileNameString
        )

        // Execute the request synchronously in a background thread
        return try {
            val result = runBlocking(Dispatchers.IO) {
                ServerApi.getSync("/file_delete", params, context)
            }

            when (result) {
                is ServerApi.ApiResult.Success -> {
                    Log.d("PointRecord", "Server file deletion successful")
                    // Reset state after successful deletion
                    reset()
                    Log.d("PointRecord", "Online file deleted successfully, state reset")
                    true
                }
                is ServerApi.ApiResult.Error -> {
                    Log.e("PointRecord", "Server file deletion failed: ${result.message}")
                    false
                }
                else -> {false}
            }
        } catch (e: Exception) {
            Log.e("PointRecord", "Error in server file deletion", e)
            false
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
            if (!isLocalFile()) {
                Toast.makeText(context, "Audio file stored on server, cannot play offline", Toast.LENGTH_SHORT).show()
            }
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

data class Metadata(
    val user: String,
    val age: Int,
    val sex: String,
    val height: Double,
    val weight: Double,
    val diagnosis: String,
    val comment: String
)
data class Recording(
    val id: String,
    var meta: Metadata? = null,
    val points: Map<Int, PointRecord> = (1..10).associateWith { PointRecord(it) }
) {
    fun getPointRecord(pointNumber: Int): PointRecord? {
        return points[pointNumber]
    }

    fun isPointRecorded(pointNumber: Int): Boolean {
        return points[pointNumber]?.isRecorded ?: false
    }

    fun setFileName(pointNumber: Int, filename: String) {
        points[pointNumber]?.let { point ->
            point.fileName = filename
            point.isRecorded = true
            Log.d("Recording", "Set remote filename for recordingId=$id, point=$pointNumber: $filename")
        }
    }

    fun getFileName(pointNumber: Int) : String? {
        val point = points[pointNumber]?: throw IllegalStateException("Point $pointNumber does not exist")
        return point.fileName
    }

    fun setPointRecorded(pointNumber: Int) {
        points[pointNumber]?.markAsRecorded()
    }

    fun setPointSavedLocally(pointNumber: Int) {
        points[pointNumber]?.markAsLocal()
    }

    fun resetPoint(pointNumber: Int, context: Context): Boolean {
        Log.d("Recording", "resetPoint called for recording: $id, point: $pointNumber")

        val pointRecord = getPointRecord(pointNumber)
        if (pointRecord == null) {
            Log.e("Recording", "Point record not found: $pointNumber")
            return false
        }

        // Check if point has a file to delete
        if (!pointRecord.isRecorded || pointRecord.fileName == null) {
            Log.d("Recording", "Point has no file to delete, just resetting state")
            pointRecord.reset()
            return true
        }

        // Delegate to PointRecord's deleteFile method
        val success = pointRecord.deleteFile(context, id)
        Log.d("Recording", "PointRecord.deleteFile result: $success for point: $pointNumber")

        return success
    }

    fun playPointRecording(pointNumber: Int, context: Context) {
        points[pointNumber]?.playRecording(context, id) ?: run {
            Toast.makeText(context, "Point $pointNumber not found", Toast.LENGTH_SHORT).show()
        }
    }


}