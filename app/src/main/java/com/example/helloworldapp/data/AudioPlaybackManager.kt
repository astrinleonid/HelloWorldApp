package com.example.helloworldapp.data

import AppConfig
import android.app.Activity
import android.app.AlertDialog
import android.content.Context

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.example.helloworldapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class AudioPlaybackManager {
    private var mediaPlayer: MediaPlayer? = null
    private var playbackDialog: AlertDialog? = null
    private var progressBar: ProgressBar? = null
    private var durationTextView: TextView? = null
    private var currentPositionTextView: TextView? = null
    private var progressHandler: Handler? = null
    private var updateProgressTask: Runnable? = null
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Creates and shows the playback dialog
    fun createPlaybackDialog(context: Context, pointNumber: Int) {
        // Create custom dialog layout with progress bar and duration info
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_audio_playback, null)
        progressBar = dialogView.findViewById(R.id.playbackProgressBar)
        currentPositionTextView = dialogView.findViewById(R.id.currentPositionText)
        durationTextView = dialogView.findViewById(R.id.durationText)

        // Set default values
        progressBar?.progress = 0
        currentPositionTextView?.text = "00:00"
        durationTextView?.text = "00:00"

        val messageView = dialogView.findViewById<TextView>(R.id.playbackMessage)
        messageView?.text = "Preparing point $pointNumber recording..."

        // Initialize progress handler for UI updates
        progressHandler = Handler(Looper.getMainLooper())

        try {
            // Create and show dialog with custom layout on UI thread
            (context as? Activity)?.runOnUiThread {
                val builder = AlertDialog.Builder(context)
                    .setTitle("Playing Recording")
                    .setView(dialogView)
                    .setCancelable(false)
                    .setPositiveButton("Stop") { dialog, _ ->
                        stopPlayback()
                        dialog.dismiss()
                    }

                playbackDialog = builder.create()
                playbackDialog?.show()
            }
        } catch (e: Exception) {
            Log.e("AudioPlaybackManager", "Error creating dialog", e)
        }
    }

    // Handles online playback preparation
    fun prepareOnlinePlayback(context: Context, recordingId: String, fileName: String, pointNumber: Int) {
        // Start a coroutine for downloading
        downloadScope.launch {
            try {
                updatePlaybackMessage(context, "Downloading audio file...")

                // Download the file
                val audioBytes = downloadAudioFile(recordingId, fileName)
                if (audioBytes == null) {
                    withContext(Dispatchers.Main) {
                        dismissPlaybackDialog()
                        Toast.makeText(context, "Failed to download audio file", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Save to a temporary file for playback
                val tempFile = File(context.cacheDir, "temp_playback_${System.currentTimeMillis()}.wav")
                tempFile.writeBytes(audioBytes)

                // Play the file on the main thread
                withContext(Dispatchers.Main) {
                    updatePlaybackMessage(context, "Playing point $pointNumber recording...")
                    playLocalFile(tempFile.absolutePath, pointNumber)
                }
            } catch (e: Exception) {
                Log.e("AudioPlaybackManager", "Error preparing online audio", e)
                withContext(Dispatchers.Main) {
                    dismissPlaybackDialog()
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Update the message in the playback dialog
    private fun updatePlaybackMessage(context: Context, message: String) {
        (context as? Activity)?.runOnUiThread {
            playbackDialog?.findViewById<TextView>(R.id.playbackMessage)?.text = message
        }
    }

    // Download audio file from server
    private suspend fun downloadAudioFile(recordingId: String, fileName: String): ByteArray? = withContext(
        Dispatchers.IO) {
        try {
            val encodedRecordingId = URLEncoder.encode(recordingId, "UTF-8")
            val encodedFileName = URLEncoder.encode(fileName, "UTF-8")
            val url = "${AppConfig.serverIP}/file_download?fileName=$encodedFileName&folderId=$encodedRecordingId"

            Log.d("AudioPlaybackManager", "Downloading audio file: $url")

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("AudioPlaybackManager", "Failed to download file: ${response.code}")
                    return@withContext null
                }

                val bytes = response.body?.bytes()
                if (bytes == null || bytes.isEmpty()) {
                    Log.e("AudioPlaybackManager", "Downloaded file is empty")
                    return@withContext null
                }

                Log.d("AudioPlaybackManager", "Downloaded ${bytes.size} bytes")
                return@withContext bytes
            }
        } catch (e: Exception) {
            Log.e("AudioPlaybackManager", "Error downloading audio file", e)
            return@withContext null
        }
    }

    // Dismisses the playback dialog if it's showing
    fun dismissPlaybackDialog() {
        playbackDialog?.dismiss()
        playbackDialog = null
    }

    // Plays a local audio file
    fun playLocalFile(filePath: String, pointNumber: Int) {
        Log.d("AudioPlaybackManager", "Playing local file: $filePath")

        // Release any existing player
        mediaPlayer?.release()
        mediaPlayer = null

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)

                setOnPreparedListener {
                    Log.d("AudioPlaybackManager", "MediaPlayer prepared, duration: $duration ms")

                    // Update UI with duration
                    val totalDuration = duration
                    progressBar?.max = totalDuration
                    durationTextView?.text = formatTime(totalDuration)

                    // Start playback
                    start()

                    // Start progress updates
                    startProgressUpdates()
                }

                setOnCompletionListener {
                    Log.d("AudioPlaybackManager", "Playback completed")
                    stopProgressUpdates()
                    release()
                    mediaPlayer = null
                    playbackDialog?.dismiss()
                    playbackDialog = null
                }

                setOnErrorListener { mp, what, extra ->
                    Log.e("AudioPlaybackManager", "MediaPlayer error: what=$what, extra=$extra")
                    stopProgressUpdates()
                    release()
                    mediaPlayer = null
                    playbackDialog?.dismiss()
                    playbackDialog = null
                    true
                }

                // Start preparing asynchronously
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("AudioPlaybackManager", "Error setting up MediaPlayer", e)
            mediaPlayer?.release()
            mediaPlayer = null
            playbackDialog?.dismiss()
            playbackDialog = null
        }
    }

    // The rest of the methods remain the same (startProgressUpdates, stopProgressUpdates, etc.)
    private fun startProgressUpdates() {
        updateProgressTask = object : Runnable {
            override fun run() {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        val currentPosition = player.currentPosition
                        progressBar?.progress = currentPosition
                        currentPositionTextView?.text = formatTime(currentPosition)

                        // Schedule next update
                        progressHandler?.postDelayed(this, 100)
                    }
                }
            }
        }

        // Start the updates
        updateProgressTask?.let { progressHandler?.post(it) }
    }

    private fun stopProgressUpdates() {
        updateProgressTask?.let { progressHandler?.removeCallbacks(it) }
        updateProgressTask = null
    }

    private fun formatTime(milliseconds: Int): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    fun stopPlayback() {
        stopProgressUpdates()
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            reset()
            release()
        }
        mediaPlayer = null
        playbackDialog?.dismiss()
        playbackDialog = null

        // Cancel any ongoing downloads
        downloadScope.coroutineContext.cancelChildren()
    }

    fun releaseResources() {
        stopProgressUpdates()
        mediaPlayer?.release()
        mediaPlayer = null
        playbackDialog?.dismiss()
        playbackDialog = null
        progressHandler = null

        // Cancel any ongoing downloads
        downloadScope.coroutineContext.cancelChildren()
    }

    companion object {
        // Singleton instance
        private var instance: AudioPlaybackManager? = null

        fun getInstance(): AudioPlaybackManager {
            if (instance == null) {
                instance = AudioPlaybackManager()
            }
            return instance!!
        }
    }
}

// Updated RecordActivity.kt - sendSaveCommandToServer method
