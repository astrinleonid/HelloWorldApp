// RecordManager.kt
package com.example.helloworldapp.data

import AppConfig
import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.example.helloworldapp.R
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.net.URLEncoder

object RecordManager {
    private val recordings = mutableMapOf<String, Recording>()
    private var mediaPlayer: MediaPlayer? = null

    fun initializeRecording(id: String? = null): String {
        // Generate ID if not provided
        val recordingId = id ?: generateRecordingId()

        // Create new recording
        recordings[recordingId] = Recording(recordingId)

        return recordingId
    }

    private fun generateRecordingId(): String {
        if (AppConfig.online) {
            // This case shouldn't happen as online ID should be provided
            throw IllegalStateException("Online mode requires server-provided ID")
        } else {
            // Generate local ID using timestamp and random number
            val timestamp = System.currentTimeMillis()
            val random = (0..999999).random().toString().padStart(6, '0')
            return "OFF${timestamp}${random}"
        }
    }


    fun getRecording(id: String): Recording? = recordings[id]

    fun getAllRecordingIds(): List<String> = recordings.keys.toList()

    fun getFileName(recordingId: String, pointNumber: Int): String {
        val fileName = "offline_audio_rec_${recordingId}_point_${pointNumber}_${System.currentTimeMillis()}.wav"
        recordings[recordingId]?.points?.get(pointNumber)?.let { point ->
            point.fileName = fileName
        }
        return fileName
    }
    fun setPointRecorded(recordingId: String, pointNumber: Int) {
        recordings[recordingId]?.points?.get(pointNumber)?.let { point ->
            point.isRecorded = true
        }
    }

    fun setPointLabel(recordingId: String, pointNumber: Int, label: RecordLabel) {
        recordings[recordingId]?.points?.get(pointNumber)?.label = label
    }


    fun cyclePointLabel(recordingId: String, pointNumber: Int): RecordLabel? {
        return recordings[recordingId]?.points?.get(pointNumber)?.let { point ->
            val newLabel = when(point.label) {
                RecordLabel.NOLABEL -> RecordLabel.POSITIVE
                RecordLabel.POSITIVE -> RecordLabel.NEGATIVE
                RecordLabel.NEGATIVE -> RecordLabel.UNDETERMINED
                RecordLabel.UNDETERMINED -> RecordLabel.NOLABEL
            }
            point.label = newLabel
            newLabel
        }
    }

    fun isRecorded(recordingId: String, pointNumber: Int): Boolean {
        return recordings[recordingId]?.points?.get(pointNumber)?.isRecorded ?: false
    }
    fun getPointRecord(recordingId: String, pointNumber: Int): PointRecord? {
        return recordings[recordingId]?.points?.get(pointNumber)
    }

    private fun extractPointNumber(filename: String): Int? {
        return if (AppConfig.online) {
            val match = Regex("""(\d+)\.wav$""").find(filename)
            match?.groupValues?.get(1)?.toIntOrNull()
        } else {
            filename.substringAfter("btn_")
                .substringBefore("_")
                .toIntOrNull()
        }
    }

    fun getButtonColor(record: PointRecord): Int {
        return when {
            !record.isRecorded -> R.drawable.button_not_recorded
            record.label == RecordLabel.POSITIVE -> R.drawable.button_positive
            record.label == RecordLabel.NEGATIVE -> R.drawable.button_negative
            record.label == RecordLabel.UNDETERMINED -> R.drawable.button_undetermined
            else -> R.drawable.button_recorded
        }
    }
    fun deleteRecording(id: String, context: Context, onComplete: (Boolean) -> Unit) {
        val recording = recordings[id] ?: return

        // Count files that need to be deleted
        val totalFiles = recording.points.values.count { it.fileName != null }
        var deletedFiles = 0
        var hasErrors = false

        // If no files to delete, just remove the recording and complete
        if (totalFiles == 0) {
            recordings.remove(id)
            onComplete(true)
            return
        }

        // Delete each file associated with points
        recording.points.values.forEach { point ->
            point.fileName?.let { fileName ->
                    try {
                        val file = File(context.filesDir, fileName)
                        if (file.exists()) {
                            val success = file.delete()
                            if (!success) hasErrors = true
                        }
                        deletedFiles++
                    } catch (e: Exception) {
                        hasErrors = true
                        deletedFiles++
                        Log.e("RecordManager", "Error deleting file", e)
                    }

                    if (deletedFiles == totalFiles) {
                        recordings.remove(id)
                        onComplete(!hasErrors)
                    }
            }
        }
    }

    private fun deleteFromServer(uniqueId: String?, callback: (Boolean, String?) -> Unit) {
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



    fun playPointRecording(recordingId: String, pointNumber: Int, context: Context, onStatusUpdate: (String) -> Unit) {
        val point = recordings[recordingId]?.points?.get(pointNumber) ?: return
        val fileName = point.fileName ?: return

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            try {
                if (AppConfig.online) {
                    val encodedRecordingId = URLEncoder.encode(recordingId, "UTF-8")
                    val encodedFileName = URLEncoder.encode(fileName, "UTF-8")
                    val url = "${AppConfig.serverIP}/file_download?fileName=$encodedFileName&folderId=$encodedRecordingId"
                    setDataSource(url)
                } else {
                    val file = File(context.filesDir, fileName)
                    setDataSource(file.absolutePath)
                }

            } catch (e: Exception) {
                Log.e("RecordManager", "Error playing recording", e)
                onStatusUpdate("Error playing recording")
            }
        }
    }

    fun resetPoint(recordingId: String, pointNumber: Int, context: Context, onComplete: (Boolean) -> Unit) {
        recordings[recordingId]?.points?.get(pointNumber)?.let { point ->
            val fileName = point.fileName

            // Reset point data
            point.isRecorded = false
            point.label = RecordLabel.NOLABEL

            // Delete the associated file if it exists
            if (fileName != null) {
                try {
                    val file = File(context.filesDir, fileName)
                    if (file.exists()) {
                        val success = file.delete()
                        point.fileName = null
                        onComplete(success)
                    } else {
                        onComplete(true)  // File didn't exist, consider it successful
                    }
                } catch (e: Exception) {
                    Log.e("RecordManager", "Error deleting file", e)
                    onComplete(false)
                }
            } else {
                onComplete(true)  // No file to delete
            }
        } ?: onComplete(false)  // Point not found
    }

    fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}





