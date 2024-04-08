package com.example.helloworldapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.helloworldapp.ui.theme.HelloWorldAppTheme
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
import java.io.IOException

class RecordActivity : ComponentActivity() {

    private val sampleRate = 44100 // Sample rate in Hz
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private var bufferSize: Int = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private var audioRecord: AudioRecord? = null
    private val recordingScope = CoroutineScope(Dispatchers.IO + Job())
    private var result = "0"
    private var recordId = "111111"
    private var buttonNumber = "0"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions if they have not been granted yet
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
        } else {
            // Permissions already granted, start recording
            buttonNumber = intent.getStringExtra("button_number") ?: "0"
            recordId = intent.getStringExtra("record_ID") ?: "111111"
            startRecording()
        }

        setContent {
            HelloWorldAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Extract the button number within onCreate
                    buttonNumber = intent.getStringExtra("button_number") ?: "0"
                    recordId = intent.getStringExtra("UNIQUE_ID") ?: "111111"
                    result = buttonNumber
                    RecordingScreen(buttonNumber = buttonNumber)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
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


    private var isRecording = false

    private fun startRecording() {


            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize)
            audioRecord?.startRecording()
            isRecording = true

            recordingScope.launch {
                var chunk_index = 0
                while (isRecording) {
                    val oneSecondData = ByteArrayOutputStream()
                    val audioData = ByteArray(bufferSize)
                    val startTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startTime < 1000) { // Capture chunks for roughly 1000 milliseconds
                        val readResult = audioRecord?.read(audioData, 0, audioData.size)
                            ?: AudioRecord.ERROR_INVALID_OPERATION
                        if (readResult > 0) {
                            oneSecondData.write(audioData, 0, readResult)
                        }
                    }
                    // After collecting 1 second of audio, send it to the server
                    sendAudioDataToServer(oneSecondData.toByteArray())
                    oneSecondData.close()
                    chunk_index += 1
                    if (chunk_index > 5) {
                        stopRecording("timeout")
                    }
                }
                audioRecord?.stop()
                audioRecord?.release()

                sendResult()
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

    private fun sendAudioDataToServer(audioData: ByteArray) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "audio_record.3gp",
                audioData.toRequestBody("audio/3gp".toMediaTypeOrNull(), 0, audioData.size))
            .addFormDataPart("button_number", buttonNumber)
            .addFormDataPart("record_id", recordId)
            .build()

        val request = Request.Builder()
            .url("http://10.0.2.2:5000/upload")
            .post(requestBody)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("RecordActivity", "Failed to upload audio data", e)
                // Handle failure, such as by notifying the user
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.e("RecordActivity", "Server error: ${response.message}")
                        // Handle server error, e.g., update UI to show error message
                    } else {
                        // Parse the JSON response
                        val responseBody = it.body?.string()
                        // Assuming the server response includes a JSON object with a "message" field
                        val message = JSONObject(responseBody).getString("message")
                        if (message == "Record sucsessfull") {
                            stopRecording("sucsess")
                        } else {
                        }
                    }
                }
            }
        })
    }

    private fun stopRecording(recording_result: String) {
        isRecording = false // This will cause the loop in startRecording() to end
        if (!(recording_result == "sucsess")) {
            result = "0"
        }
    }
    private fun sendResult() {
        val data = Intent().apply {
            putExtra("button_number", result)
        }
        setResult(RESULT_OK, data)
    }

    override fun onBackPressed() {
        // If the user presses back, return "0"
        stopRecording("return")
        sendResult()
        super.onBackPressed()
    }
}

@Composable
fun RecordingScreen(buttonNumber: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.recording_image),
                contentDescription = "Recording in progress",
                modifier = Modifier
                    .size(200.dp) // Adjust the size as needed
                    .padding(16.dp)
            )
            Text(text = "Recording...", modifier = Modifier.padding(16.dp))
            // Include a button or another UI element if needed
        }
    }
}



