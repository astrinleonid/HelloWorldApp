package com.example.helloworldapp

import AppConfig
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
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
                    // After collecting 1 second of audio, convert it to wav
                    val wavData = WavConverter.pcmToWav(oneSecondData.toByteArray(), sampleRate, 1, 16)
                    oneSecondData.close()

                    // After converting to WAV format, send it to the server
                    sendAudioDataToServer(wavData)
                    // send it to the server
            //        sendAudioDataToServer(oneSecondData.toByteArray())
                    oneSecondData.close()
                    chunk_index += 1
                    if (chunk_index > 10) {
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
            .url("http://${AppConfig.serverIP}:5000/upload")
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
                        if (message == "Record sucsessfull" && isRecording) {
                            stopRecording("success")
                        } else {
                        }
                    }
                }
            }
        })
    }

    private fun sendSaveCommandToServer() {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("button_number", buttonNumber)
            .addFormDataPart("record_id", recordId)
            .build()

        val request = Request.Builder()
            .url("http://${AppConfig.serverIP}:5000/save_record")
            .post(requestBody)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("RecordActivity", "Failed to invoke record saving", e)
                // Handle failure, such as by notifying the user
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.e("RecordActivity", "Server error: ${response.message}")
                        // Handle server error, e.g., update UI to show error message
                    } else {
                    }
                }
            }
        })
    }
    private fun stopRecording(recording_result: String) {
        isRecording = false // This will cause the loop in startRecording() to end

        when (recording_result) {
            "success" -> {
                result = buttonNumber // or any logic you have for successful recording
                playSuccessSound()
                sendSaveCommandToServer()
            }
            "timeout" -> {
                result = "0"
                playTimeoutSound()
            }
            // Optionally handle other conditions
        }
    }

    private fun playSuccessSound() {
        val mediaPlayer = MediaPlayer.create(this, R.raw.beep_2)
        mediaPlayer.setOnCompletionListener { mp -> mp.release() }
        mediaPlayer.start()
    }

    private fun playTimeoutSound() {
        val mediaPlayer = MediaPlayer.create(this, R.raw.beep)
        mediaPlayer.setOnCompletionListener { mp -> mp.release() }
        mediaPlayer.start()
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
//        super.onBackPressed()
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

object WavConverter {
    fun pcmToWav(pcmData: ByteArray, sampleRate: Int, channels: Int, bitDepth: Int): ByteArray {
        val out = ByteArrayOutputStream()
        writeWavHeader(out, pcmData.size, sampleRate, channels, bitDepth)
        out.write(pcmData)
        return out.toByteArray()
    }

    private fun writeWavHeader(out: ByteArrayOutputStream, pcmDataLength: Int, sampleRate: Int, channels: Int, bitDepth: Int) {
        val totalDataLen = pcmDataLength + 36
        val byteRate = sampleRate * channels * bitDepth / 8

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte();  header[1] = 'I'.code.toByte();  header[2] = 'F'.code.toByte();  header[3] = 'F'.code.toByte() // RIFF/WAVE header
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte();  header[9] = 'A'.code.toByte();  header[10] = 'V'.code.toByte();  header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte();  header[13] = 'm'.code.toByte();  header[14] = 't'.code.toByte();  header[15] = ' '.code.toByte()
        header[16] = 16;  header[17] = 0;  header[18] = 0;  header[19] = 0   // size of 'fmt ' chunk
        header[20] = 1;  header[21] = 0;  header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (2 * 16 / 8).toByte()  // block align
        header[33] = 0
        header[34] = bitDepth.toByte()  // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte();  header[37] = 'a'.code.toByte();  header[38] = 't'.code.toByte();  header[39] = 'a'.code.toByte()
        header[40] = (pcmDataLength and 0xff).toByte()
        header[41] = ((pcmDataLength shr 8) and 0xff).toByte()
        header[42] = ((pcmDataLength shr 16) and 0xff).toByte()
        header[43] = ((pcmDataLength shr 24) and 0xff).toByte()
        out.write(header, 0, header.size)
    }
}
