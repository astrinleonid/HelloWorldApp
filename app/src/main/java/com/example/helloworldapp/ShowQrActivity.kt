package com.example.helloworldapp

import AppConfig
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class ShowQrActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var commentEditText: EditText
    private lateinit var textView: TextView
    private lateinit var commentButton: Button
    private lateinit var doneButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_show_qr)

        val uniqueId = intent.getStringExtra("UNIQUE_ID")
        imageView = findViewById(R.id.imageView)
        commentEditText = findViewById(R.id.commentEditText)
//        commentButton = findViewById(R.id.commentButton)
        doneButton = findViewById(R.id.doneButton)
        textView = findViewById(R.id.textView)
        textView.text = "Записи здесь"
        fetchAndDisplayQRCode(uniqueId)
        commentEditText.setOnKeyListener(commentEditTextKeyListener)
        doneButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

    }

    private val commentEditTextKeyListener = View.OnKeyListener { v, keyCode, event ->
        if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
            val uniqueId = intent.getStringExtra("UNIQUE_ID")
            sendComment((v as EditText).text.toString(), uniqueId)
            return@OnKeyListener true
        }
        false
    }

    private fun fetchAndDisplayQRCode(id: String?) {
        val url = "${AppConfig.serverIP}/get_full_path_to_id/$id"
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                e.printStackTrace()  // Handle the error appropriately
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (response.isSuccessful) {
                    val inputStream = response.body?.byteStream()
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    runOnUiThread {
                        imageView.setImageBitmap(bitmap)  // Display the bitmap in the ImageView
                    }
                } else {
                    runOnUiThread {
                        textView.text = "Failed to load QR Code"
                    }
                }
            }
        })
    }

    private fun sendComment(comment: String, uniqueId : String?) {
        val url = "${AppConfig.serverIP}/submit_comment"
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

        val json = JSONObject()
        json.put("comment", comment)
        json.put("record_id", uniqueId)
        val requestBody = json.toString().toRequestBody(mediaType)
        val request = Request.Builder().url(url).post(requestBody).build()

        OkHttpClient().newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    textView.text = "Error sending comment"
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        textView.text = "Comment sent successfully"
                    } else {
                        textView.text = "Failed to send comment"
                    }
                }
            }
        })
    }
    override fun onBackPressed() {
        // Create an Intent to start MainActivity
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Optionally call finish() if you want to close this activity
    }
}
