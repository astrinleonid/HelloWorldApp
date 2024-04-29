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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    private var recordId = "111111"
    private var buttonNumber = "0"
    private var isRecording = false
    private val activeRequests = AtomicInteger(0)
    private var recording_result = "fail"
    private var recordingId = "0"

    private var stopRecordingCallback: (String, String) -> Unit = { _, _ -> }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            recordId = intent.getStringExtra("UNIQUE_ID") ?: "000000"
            startRecording()
        }

        setContent {
            HelloWorldAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ) {
                    // Extract the button number within onCreate
                    buttonNumber = intent.getStringExtra("button_number") ?: "0"
                    recordId = intent.getStringExtra("UNIQUE_ID") ?: "111111"
                    //result = buttonNumber
                    RecordingScreen(buttonNumber = buttonNumber, stopRecordingCallback = { result, buttonNum ->
                        stopRecording(result, buttonNum)
                    })
                }
            }
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
            var chunk_index = 0
            while (isRecording) {
                val oneSecondData = ByteArrayOutputStream()
                val audioData = ByteArray(bufferSize)
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < AppConfig.segmentLength) { // Capture chunks for roughly 1000 milliseconds
                    val readResult = audioRecord?.read(audioData, 0, audioData.size)
                        ?: AudioRecord.ERROR_INVALID_OPERATION
                    if (readResult > 0) {
                        oneSecondData.write(audioData, 0, readResult)
                    }
                }
                // After collecting 1 second of audio, convert it to wav
                val wavData = WavConverter.pcmToWav(oneSecondData.toByteArray(), sampleRate, 1, 16)
                oneSecondData.close()

                sendAudioDataToServer(wavData)

                oneSecondData.close()
                chunk_index += 1
                if (chunk_index > AppConfig.timeOut) {
                    recording_result = "timeout"
                    stopRecording(recording_result, buttonNumber)
                }
            }

            playSound(recording_result)
            sendSaveCommandToServer(recording_result, buttonNumber)
            audioRecord?.stop()
            audioRecord?.release()
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
            .url("${AppConfig.serverIP}/start_point_recording?record_id=$recordId")
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
                    recordingId = pointRecordId
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
            .addFormDataPart("record_id", recordId)
            .addFormDataPart("pointRecordId", recordingId)
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
            .addFormDataPart("record_id", recordId)
            .build()

        val request = Request.Builder()
            .url("${AppConfig.serverIP}/save_record")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                Log.e("RecordActivity", "Server success: ${response.message}")
                response.body?.string()
            } else null
        }

    }

    private fun stopRecording(result: String, buttonNumber: String) {
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
        var result_to_return = "0"
        var result_code = RESULT_CANCELED
        if (recording_result == "success") {
            result_to_return = buttonNumber
            result_code = RESULT_OK
        }
        val data = Intent().apply {
            putExtra("button_number", result_to_return)
        }

        setResult(result_code, data)
    }
}


@Composable
fun RecordingScreen(buttonNumber: String, stopRecordingCallback: (String, String) -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center // Centers the content inside the box
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center, // Centers the column content vertically
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.recording_image),
                contentDescription = "Recording in progress",
                modifier = Modifier.size(200.dp)
            )
            Text(text = "Recording...", modifier = Modifier.padding(top = 16.dp))
        }

        Button(
            onClick = { stopRecordingCallback("abort", "0") },
            modifier = Modifier
                .align(Alignment.BottomCenter) // Position the button at the bottom center of the Box
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(text = "STOP", color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

//
//
//@Composable
//fun RecordingScreen(buttonNumber: String, stopRecordingCallback: (String, String) -> Unit) {
//    Box(
//        modifier = Modifier.fillMaxSize()
//    ) {
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(16.dp),
//            verticalArrangement = Arrangement.SpaceBetween, // This will push the button to the bottom
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            Column(
//                horizontalAlignment = Alignment.CenterHorizontally
//            ) {
//                Image(
//                    painter = painterResource(R.drawable.recording_image),
//                    contentDescription = "Recording in progress",
//                    modifier = Modifier
//                            .size(200.dp)
//                )
//                Text(text = "Recording...", modifier = Modifier.padding(top = 16.dp))
//            }
//
//            // Stop Button at the bottom
//            Button(
//                onClick = {
//                    stopRecordingCallback("abort", "0")
//                },
//                modifier = Modifier
//                    .fillMaxWidth() // Ensure the button stretches across the width
//                    .padding(8.dp),
//                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
//            ) {
//                Text(text = "STOP", color = MaterialTheme.colorScheme.onPrimary)
//            }
//        }
//    }
//}


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
