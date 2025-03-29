package com.example.helloworldapp.data

import AppConfig
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.helloworldapp.R
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

    fun deleteFile(context: Context): Boolean {
        return try {
            fileName?.let { name ->
                val file = File(context.filesDir, name)
                if (file.exists()) {
                    val success = file.delete()
                    if (success) {
                        fileName = null
                    }
                    success
                } else {
                    true // File didn't exist, consider it a success
                }
            } ?: true // No file to delete, consider it a success
        } catch (e: Exception) {
            Log.e("PointRecord", "Error deleting file for point $pointNumber", e)
            false
        }
    }

    fun getFilePath(context: Context): String? {
        return fileName?.let { name ->
            val file = File(context.filesDir, name)
            if (file.exists()) file.absolutePath else null
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
        return points[pointNumber]?.deleteFile(context) ?: false
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
                val success = point.deleteFile(context)
                if (!success) hasErrors = true
                deletedCount++
            }
        }

        return Pair(!hasErrors, deletedCount)
    }
}