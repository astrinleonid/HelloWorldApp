// RecordManager.kt
package com.example.helloworldapp.data

import AppConfig
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.helloworldapp.R
import com.google.gson.Gson
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
import java.net.URLEncoder

object RecordManager {
    private val recordings = mutableMapOf<String, Recording>()
    private var mediaPlayer: MediaPlayer? = null

    suspend fun initializeRecording(id: String? = null): String {
        // If ID is provided, use it directly
        if (id != null) {
            recordings[id] = Recording(id)
            return id
        }

        // Generate ID based on app mode
        val recordingId = if (AppConfig.online) {
            // Fetch a unique ID from server in online mode
            fetchUniqueId(AppConfig.numChunks) ?: generateOfflineId()
        } else {
            // Generate local ID in offline mode
            generateOfflineId()
        }

        // Create new recording with the generated ID
        recordings[recordingId] = Recording(recordingId)

        return recordingId
    }

    private fun generateOfflineId(): String {
        // Generate local ID using timestamp and random number
        val timestamp = System.currentTimeMillis()
        val random = (0..999999).random().toString().padStart(6, '0')
        val deviceHash = (Build.MODEL + Build.MANUFACTURER).hashCode().toString().takeLast(4)
        return "OFF${timestamp}${deviceHash}${random}"
    }

//    private fun generateRecordingId(): String {
//        if (AppConfig.online) {
//            // This case shouldn't happen as online ID should be provided
//            throw IllegalStateException("Online mode requires server-provided ID")
//        } else {
//            // Generate local ID using timestamp and random number
//            val timestamp = System.currentTimeMillis()
//            val random = (0..999999).random().toString().padStart(6, '0')
//            return "OFF${timestamp}${random}"
//        }
//    }

    suspend fun fetchUniqueId(numChunks: Int): String? = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val deviceInfo = mapOf(
            "model" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER
        )
        if (AppConfig.online) {
            val requestBody = Gson().toJson(deviceInfo)
                .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("${AppConfig.serverIP}/getUniqueId?numChunks=$numChunks")
                .post(requestBody)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else null
            }
        } else {
            val timestamp = System.currentTimeMillis()
            val random = (0..999999).random().toString().padStart(6, '0')
            val deviceHash = (Build.MODEL + Build.MANUFACTURER).hashCode().toString().takeLast(4)
            "OFF${timestamp}${deviceHash}${random}"
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

                    // Initialize recording if not yet created
//                    if (!recordings.containsKey(recordId)) {
//                        initializeRecording(recordId)
//                    }

                    // Process each file to update point records
                    files.forEach { filename ->
                        val pointNumber = extractPointNumber(filename)
                        if (pointNumber != null) {
                            val point = getPointRecord(recordId, pointNumber) ?: return@forEach

                            // Mark as recorded and store filename
                            point.isRecorded = true
                            point.fileName = filename

                            // Update label if present
                            val labelStr = labels.optString(filename, "")
                            point.label = when(labelStr.lowercase()) {
                                "positive" -> RecordLabel.POSITIVE
                                "negative" -> RecordLabel.NEGATIVE
                                "undetermined" -> RecordLabel.UNDETERMINED
                                else -> RecordLabel.NOLABEL
                            }
                        }
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
                RecordLabel.UNDETERMINED -> RecordLabel.POSITIVE
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
        val point = recordings[recordingId]?.points?.get(pointNumber) ?: return
        val fileName = point.fileName ?: return

        // Create dialog in a simpler way with better error handling
        try {
            // Run on UI thread
            (context as? Activity)?.runOnUiThread {
                // Create and show a simpler dialog
                val builder = AlertDialog.Builder(context)
                    .setTitle("Playing Recording")
                    .setMessage("Playing point $pointNumber recording...")
                    .setCancelable(false)
                    .setPositiveButton("Stop") { dialog, _ ->
                        stopPlayback()
                        dialog.dismiss()
                    }

                playbackDialog = builder.create()
                playbackDialog?.show()
            }
        } catch (e: Exception) {
            Log.e("RecordManager", "Error creating dialog", e)
        }

        // Setup media player
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

                prepareAsync()

                setOnPreparedListener {
                    start()
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Playing point $pointNumber", Toast.LENGTH_SHORT).show()
                    }
                }

                setOnCompletionListener {
                    (context as? Activity)?.runOnUiThread {
                        playbackDialog?.dismiss()
                        playbackDialog = null
                        Toast.makeText(context, "Playback completed", Toast.LENGTH_SHORT).show()
                    }
                    release()
                    mediaPlayer = null
                }

                setOnErrorListener { mp, what, extra ->
                    (context as? Activity)?.runOnUiThread {
                        playbackDialog?.dismiss()
                        playbackDialog = null
                        Toast.makeText(context, "Error playing recording", Toast.LENGTH_SHORT).show()
                    }
                    release()
                    mediaPlayer = null
                    true
                }
            } catch (e: Exception) {
                Log.e("RecordManager", "Error playing recording", e)
                (context as? Activity)?.runOnUiThread {
                    playbackDialog?.dismiss()
                    playbackDialog = null
                    Toast.makeText(context, "Error setting up playback", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


//    private fun updatePlaybackProgress(context: Context) {
//        val handler = Handler(Looper.getMainLooper())
//        val updateRunnable = object : Runnable {
//            override fun run() {
//                val player = mediaPlayer
//                val dialog = playbackDialog
//
//                if (player != null && player.isPlaying && dialog != null && dialog.isShowing) {
//                    val progressBar = dialog.findViewById<ProgressBar>(R.id.playbackProgress)
//                    if (progressBar != null) {
//                        progressBar.max = player.duration
//                        progressBar.progress = player.currentPosition
//                    }
//                    handler.postDelayed(this, 100)
//                }
//            }
//        }
//
//        handler.post(updateRunnable)
//    }


    // Dialog reference
    private var playbackDialog: AlertDialog? = null
    private var progressHandler: Handler? = null


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
            performPointReset(recordingId, pointNumber, context, onComplete)
        }

        alertDialog.show()
    }

    private fun performPointReset(recordingId: String, pointNumber: Int, context: Context, onComplete: (Boolean) -> Unit) {
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
                        onComplete(success)  // Return success status
                    } else {
                        onComplete(true)  // File didn't exist, consider it successful
                    }
                } catch (e: Exception) {
                    Log.e("RecordManager", "Error deleting file", e)
                    onComplete(false)  // Return failure status
                }
            } else {
                onComplete(true)  // No file to delete, consider successful
            }
        } ?: onComplete(false)  // Point not found, return failure
    }


    // In RecordManager
    fun stopPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            reset()
            release()
        }
        mediaPlayer = null


    }
    fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}





