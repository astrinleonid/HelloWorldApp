package com.example.helloworldapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.material3.Text
import com.example.helloworldapp.ui.theme.HelloWorldAppTheme
import java.io.File
import java.io.IOException



class RecordActivity : ComponentActivity() {
    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }
    private var mediaRecorder: MediaRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
        }
        setContent {
            HelloWorldAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    // Extract the button number within onCreate
                    val buttonNumber = intent.getStringExtra("button_number") ?: "0"
                    RecordingScreen(buttonNumber = buttonNumber)
                    // Pass a lambda to handle the button press
                    startRecording(buttonNumber = buttonNumber)
                }
            }
        }
    }

    private fun startRecording(buttonNumber: String) {
        val audioFile = File(filesDir, "recorded_audio.3gp")

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(audioFile.absolutePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

            try {
                prepare()
                start()
                // Start the timer after starting the recording
                startTimer(buttonNumber = buttonNumber)
            } catch (e: IOException) {
                Log.e("RecordActivity", "prepare() failed: ${e.message}")
            }
        }
    }

    private fun startTimer(buttonNumber: String) {
        object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Update your progress indicator here
            }

            override fun onFinish() {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                mediaRecorder = null

                // Play a beep sound
                MediaPlayer.create(this@RecordActivity, R.raw.beep).apply {
                    start()
                    setOnCompletionListener {
                        it.release()
                        // After beep, set result and finish
                        sendResult(buttonNumber)
                        finish()
                    }
                }
            }
        }.start()
    }

    private fun sendResult(buttonNumber: String) {
        val data = Intent().apply {
            putExtra("button_number", buttonNumber)
        }
        setResult(RESULT_OK, data)
    }

    override fun onBackPressed() {
        // If the user presses back, return "0"
        sendResult("0")
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

//
//@Composable
//fun Record(buttonNumber: String, onRecordDone: (String) -> Unit) {
//    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
//        Button(
//            onClick = { onRecordDone(buttonNumber) },
//            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
//        ) {
//            Text(text = "Record")
//        }
//    }
//}