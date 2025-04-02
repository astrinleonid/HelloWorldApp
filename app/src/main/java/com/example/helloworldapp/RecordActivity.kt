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
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.helloworldapp.data.RecordManager
import com.example.helloworldapp.data.WavConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class RecordActivity : ComponentActivity() {

    private val sampleRate = 22050 // Sample rate in Hz
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private var bufferSize: Int =
        AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private var audioRecord: AudioRecord? = null
    private val recordingScope = CoroutineScope(Dispatchers.IO + Job())
    //private var result = "0"
    private var recordingId = "111111"
    private var buttonNumber = "0"
    private var isRecording = false
    private var buttonRecordingsNumber = 0
    private val activeRequests = AtomicInteger(0)
    private var recording_result = "fail"
    private var recordingOnServerId = "0"
    private var recordingModeStr = "offline"
    private var stopRecordingCallback: (String, String) -> Unit = { _, _ -> }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record)
        buttonNumber = intent.getStringExtra("button_number") ?: "0"
        recordingId = intent.getStringExtra("UNIQUE_ID") ?: "000000"
        if (AppConfig.online) {
            recordingModeStr = "online"
        }
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
            buttonNumber = intent.getStringExtra("button_number") ?: "0"
            recordingId = intent.getStringExtra("UNIQUE_ID") ?: "000000"
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
                val buttonNumber = intent.getStringExtra("button_number") ?: "0"
                startRecording()
            } else {
                // Permission denied, handle the failure
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
            getRecordingId()
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
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("${AppConfig.serverIP}/start_point_recording?record_id=$recordingId")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                // Handle the failure case
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful) {
                    val jsonObject = JSONObject(responseBody)
                    val pointRecordId = jsonObject.getString("pointRecordId")
                    recordingOnServerId = pointRecordId
                    // Use the recordingId for further operations
                } else {
                    val errorMessage = JSONObject(responseBody).getString("error")
                    // Handle the error case
                    println("Error: $errorMessage")
                }
            }
        })
    }

    private fun sendAudioDataToServer(audioData: ByteArray) {

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", "audio_record.3gp",
                audioData.toRequestBody("audio/3gp".toMediaTypeOrNull(), 0, audioData.size)
            )
            .addFormDataPart("button_number", buttonNumber)
            .addFormDataPart("record_id", recordingId)
            .addFormDataPart("pointRecordId", recordingOnServerId)
            .build()

        val request = Request.Builder()
            .url("${AppConfig.serverIP}/upload")
            .post(requestBody)
            .build()

        activeRequests.incrementAndGet()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("RecordActivity", "Failed to upload audio data", e)
                activeRequests.decrementAndGet()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    activeRequests.decrementAndGet()
                    if (!it.isSuccessful) {
                        Log.e("RecordActivity", "Server error: ${response.message}")
                        println("Request /upload is unsuccessfull, ${response.message}, code ${response.code}")
                    } else {
                        // Parse the JSON response
                        val responseBody = it.body?.string()

                        val message = JSONObject(responseBody).getString("message")
                        if (message == "Record sucsessfull" && isRecording) {
                            recording_result = "success"
                            //result = buttonNumber
                            stopRecording(recording_result, buttonNumber)
                        } else {
                        }
                    }

                }
            }
        })
    }

    private fun sendSaveCommandToServer(result: String, buttonNumber: String) {
        val client = OkHttpClient()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("result", result)
            .addFormDataPart("button_number", buttonNumber)
            .addFormDataPart("record_id", recordingId)
            .build()

        val request = Request.Builder()
            .url("${AppConfig.serverIP}/save_record")
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d("RecordActivity", "Server success: ${response.message}, body: $responseBody")

                    // Parse the JSON response to get the filename
                    responseBody?.let {
                        try {
                            val jsonObject = JSONObject(it)
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
                } else {
                    Log.e("RecordActivity", "Server error: ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("RecordActivity", "Error sending save command to server", e)
        }
    }
    private fun saveAudioDataLocally(audioData: ByteArray) {
        // Create a filename that includes button_number, record_id, and pointRecordId
        //val fileName = "offline_audio_btn_${buttonNumber}_rec_${recordingId}_point_${recordingId}_${System.currentTimeMillis()}.wav"
        val pointNumber = buttonNumber.toIntOrNull() ?: 0
        val fileName = RecordManager.getFileName(recordingId, pointNumber)
        val file = File(filesDir, fileName)
        file.writeBytes(audioData)
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
        Log.d("RecordActivity", "RESULT_CANCELED value: ${Activity.RESULT_CANCELED}")  // Should be 0
        Log.d("RecordActivity", "RESULT_OK value: ${Activity.RESULT_OK}")  // Should be -1

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



