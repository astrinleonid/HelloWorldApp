package com.example.helloworldapp

import AppConfig
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.helloworldapp.data.RecordManager
import com.example.helloworldapp.data.ServerApi
import com.example.helloworldapp.data.WavConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

class RecordActivity : ComponentActivity() {

    private val sampleRate = 22050 // Sample rate in Hz
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private var bufferSize: Int =
        AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private var audioRecord: AudioRecord? = null
    private val recordingScope = CoroutineScope(Dispatchers.IO + Job())
    private var recordingId = "111111"
    private var buttonNumber = "0"
    private var isRecording = false
    private var recording_result = "fail"
    private var recordingOnServerId = "0"
    private var recordingModeStr = "offline"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record)
        buttonNumber = intent.getStringExtra("button_number") ?: "0"
        recordingId = RecordManager.getActive()

        // Set recording mode string based on AppConfig.online
        recordingModeStr = if (AppConfig.online) "online" else "offline"

        findViewById<TextView>(R.id.buttonNumberText).text = "Recording point ${buttonNumber}"
        findViewById<TextView>(R.id.recordModeText).text = "App in ${recordingModeStr} mode. Record ID ${recordingId}"

        // Request permissions if they have not been granted yet
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        } else {
            // Permissions already granted, start recording
            startRecording()
        }

        // Set up the cancel button click listener
        findViewById<Button>(R.id.cancelButton).setOnClickListener {
            Log.d("RecordActivity", "Cancel button pressed")
            recording_result = "abort"
            setResult(Activity.RESULT_CANCELED)
            stopRecording("abort", "0")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // Permission was granted, start recording
                startRecording()
            } else {
                // Permission denied, handle the failure
                Toast.makeText(this, "Audio recording permission denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startRecording() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
        audioRecord?.startRecording()
        isRecording = true

        recordingScope.launch {
            // Only get recording ID from server if in online mode
            if (AppConfig.online) {
                getRecordingId()
            }

            var startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < AppConfig.setupTimeout) {
                // Wait for the initial setting of the record
            }

            if (AppConfig.online) {
                // Online mode - record and send chunks
                var chunk_index = 0
                while (isRecording) {
                    val oneSecondData = ByteArrayOutputStream()
                    val audioData = ByteArray(bufferSize)
                    startTime = System.currentTimeMillis()

                    while (System.currentTimeMillis() - startTime < AppConfig.segmentLength && isRecording) {
                        val readResult = audioRecord?.read(audioData, 0, audioData.size)
                            ?: AudioRecord.ERROR_INVALID_OPERATION
                        if (readResult > 0) {
                            oneSecondData.write(audioData, 0, readResult)
                        }
                    }

                    val wavData = WavConverter.pcmToWav(oneSecondData.toByteArray(), sampleRate, 1, 16)
                    oneSecondData.close()
                    sendAudioDataToServer(wavData)

                    chunk_index += 1
                    if (chunk_index > AppConfig.timeOut) {
                        recording_result = "timeout"
                        stopRecording(recording_result, buttonNumber)
                    }
                }
                // Send save command after recording is completed
                sendSaveCommandToServer(recording_result, buttonNumber)
            } else {
                // Offline mode - record one continuous piece
                val fullRecordingData = ByteArrayOutputStream()
                val audioData = ByteArray(bufferSize)
                startTime = System.currentTimeMillis()

                // Record for a fixed duration (AppConfig.segmentLength * AppConfig.numChunks milliseconds)
                val recordingDuration = AppConfig.segmentLength * AppConfig.numChunks

                while (System.currentTimeMillis() - startTime < recordingDuration && isRecording) {
                    val readResult = audioRecord?.read(audioData, 0, audioData.size)
                        ?: AudioRecord.ERROR_INVALID_OPERATION
                    if (readResult > 0) {
                        fullRecordingData.write(audioData, 0, readResult)
                    }
                }

                if (isRecording) {
                    val wavData = WavConverter.pcmToWav(fullRecordingData.toByteArray(), sampleRate, 1, 16)
                    fullRecordingData.close()
                    saveAudioDataLocally(wavData)
                    recording_result = "success"
                    stopRecording(recording_result, buttonNumber)
                }
            }

            audioRecord?.stop()
            audioRecord?.release()
            playSound(recording_result)
            sendResult(recording_result, buttonNumber)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recordingScope.cancel() // Cancel coroutine when Activity is destroyed
        audioRecord?.release() // Ensure AudioRecord is released
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }

    private fun getRecordingId() {
        // Use ServerApi instead of direct OkHttp
        val params = mapOf("record_id" to recordingId)

        try {
            // Use synchronous call since we're already in a background coroutine
            val result = ServerApi.getSync("/start_point_recording", params, this)

            when (result) {
                is ServerApi.ApiResult.Success -> {
                    try {
                        val jsonObject = JSONObject(result.data)
                        val pointRecordId = jsonObject.getString("pointRecordId")
                        recordingOnServerId = pointRecordId
                        Log.d("RecordActivity", "Got point record ID: $pointRecordId")
                    } catch (e: Exception) {
                        Log.e("RecordActivity", "Error parsing point record ID response", e)
                        handleServerError()
                    }
                }
                is ServerApi.ApiResult.Error -> {
                    Log.e("RecordActivity", "Error getting point record ID: ${result.message}")

                    // Check for 400 error code which means record not found
                    if (result.code == 400) {
                        Log.e("RecordActivity", "Error 400: Record not found for ID: $recordingId")
                        handleServerError()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RecordActivity", "Exception in getRecordingId", e)
            handleServerError()
        }
    }

    private fun handleServerError() {
        // Stop recording
        isRecording = false
        recording_result = "fail"

        // Post to main thread for UI operations
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                this@RecordActivity,
                "Recording failed: Record does not exist on server",
                Toast.LENGTH_LONG
            ).show()

            // Create intent to restart TakeRecordsActivity
            val restartIntent = Intent(this@RecordActivity, TakeRecordsActivity::class.java).apply {
                putExtra("UNIQUE_ID", recordingId)
                putExtra(
                    TakeRecordsActivity.EXTRA_VIEW_TYPE,
                    if (buttonNumber.toIntOrNull() ?: 0 <= 4)
                        TakeRecordsActivity.VIEW_TYPE_FRONT
                    else
                        TakeRecordsActivity.VIEW_TYPE_BACK
                )
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            startActivity(restartIntent)
            finish()
        }
    }
    private fun sendAudioDataToServer(audioData: ByteArray) {
        // Use ServerApi for file upload
        try {
            // Create a temporary file to pass to ServerApi
            val tempFile = File(cacheDir, "temp_audio_chunk.wav")
            tempFile.writeBytes(audioData)

            val fileInfo = ServerApi.FileInfo(
                path = tempFile.absolutePath,
                name = "audio_record.wav",
                mimeType = "audio/wav"
            )

            val params = mapOf(
                "button_number" to buttonNumber,
                "record_id" to recordingId,
                "pointRecordId" to recordingOnServerId
            )

            val files = mapOf("file" to fileInfo)

            // Use synchronous call since we're already in a background coroutine
            val result = ServerApi.postSync("/upload", params, files, this)

            // Delete temporary file
            tempFile.delete()

            when (result) {
                is ServerApi.ApiResult.Success -> {
                    try {
                        val jsonObject = JSONObject(result.data)
                        val message = jsonObject.optString("message", "")

                        if (message == "Record sucsessfull" && isRecording) {
                            recording_result = "success"
                            stopRecording(recording_result, buttonNumber)
                        }
                    } catch (e: Exception) {
                        Log.e("RecordActivity", "Error parsing upload response", e)
                    }
                }
                is ServerApi.ApiResult.Error -> {
                    Log.e("RecordActivity", "Error uploading audio: ${result.message}")

                    // Check for 402 error code - record not found on server
                    if (result.code == 402) {
                        Log.e("RecordActivity", "Error 402: Record does not exist on server")
                        handleServerError()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RecordActivity", "Exception in sendAudioDataToServer", e)
        }
    }

    private fun sendSaveCommandToServer(result: String, buttonNumber: String) {
        // Use ServerApi instead of direct OkHttp
        val params = mapOf(
            "result" to result,
            "button_number" to buttonNumber,
            "record_id" to recordingId
        )

        try {
            // Use synchronous call since we're already in a background coroutine
            val apiResult = ServerApi.postSync("/save_record", params, context = this)

            when (apiResult) {
                is ServerApi.ApiResult.Success -> {
                    try {
                        val jsonObject = JSONObject(apiResult.data)
                        val filename = jsonObject.optString("filename", null)

                        if (!filename.isNullOrEmpty()) {
                            Log.d("RecordActivity", "Received filename from server: $filename")

                            // Store the filename in RecordManager
                            val pointNumber = buttonNumber.toIntOrNull() ?: 0
                            RecordManager.setRemoteFileName(recordingId, pointNumber, filename)
                            RecordManager.setPointRecorded(recordingId, pointNumber)
                        } else {
                            Log.e("RecordActivity", "No filename received from server")
                        }
                    } catch (e: Exception) {
                        Log.e("RecordActivity", "Error parsing server response", e)
                    }
                }
                is ServerApi.ApiResult.Error -> {
                    Log.e("RecordActivity", "Error sending save command: ${apiResult.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("RecordActivity", "Exception in sendSaveCommandToServer", e)
        }
    }

    private fun saveAudioDataLocally(audioData: ByteArray) {
        val pointNumber = buttonNumber.toIntOrNull() ?: 0

        // Get the filename from RecordManager to ensure consistency
        val fileName = RecordManager.getFileName(recordingId, pointNumber)

        // Make sure the filename is stored consistently
        val file = File(filesDir, fileName)

        try {
            file.writeBytes(audioData)

            // Update RecordManager with recording information
            RecordManager.setPointRecorded(recordingId, pointNumber)

            Log.d("RecordActivity", "Saved local recording: $fileName")
        } catch (e: Exception) {
            Log.e("RecordActivity", "Error saving audio file locally", e)
            recording_result = "fail"
        }
    }

    private fun stopRecording(result: String, buttonNumber: String) {
        Log.d("RecordActivity", "Stopping recording - recording_result: $result")
        recording_result = result
        isRecording = false // This will cause the loop in startRecording() to end
    }

    private fun playSound(recording_result: String) {
        // Declare mediaPlayer variable outside of the if/else scope
        val mediaPlayer: MediaPlayer = if (recording_result == "success") {
            MediaPlayer.create(this, R.raw.beep_2) // Success sound
        } else {
            MediaPlayer.create(this, R.raw.beep) // Timeout or other failure sound
        }

        mediaPlayer.setOnCompletionListener { mp -> mp.release() }
        mediaPlayer.start()
    }

    private fun sendResult(recording_result: String, buttonNumber: String) {
        // Add logging
        Log.d("RecordActivity", "Sending result - recording_result: $recording_result")

        if (recording_result == "success") {
            val data = Intent().apply {
                putExtra("button_number", buttonNumber)
            }
            Log.d("RecordActivity", "Setting RESULT_OK with button: $buttonNumber")
            setResult(Activity.RESULT_OK, data)
        } else {
            Log.d("RecordActivity", "Setting RESULT_CANCELED")
            setResult(Activity.RESULT_CANCELED)
        }
    }
}