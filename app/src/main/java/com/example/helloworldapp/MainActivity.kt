
package com.example.helloworldapp

import AppConfig
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.helloworldapp.ui.theme.HelloWorldAppTheme
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HelloWorldAppTheme {
                // Wrap everything in a Surface that dictates the background color
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.secondary // Set the color as defined in your theme
                ) {
                    MyApp()
                }
            }
        }
    }
}

@Composable
fun MyApp() {
    val context = LocalContext.current
    Scaffold(
        topBar = { TopBar() }
    ) { padding ->
       Greeting("Android", context = context, modifier = Modifier.padding(padding))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar() {
    var showSettingsDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    TopAppBar(
        title = { Text("beta app, server IP ${AppConfig.serverIP}") },
        actions = {
            IconButton(onClick = { showSettingsDialog = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }
    )

    if (showSettingsDialog) {
        SettingsDialog(context = context, onDismiss = { showSettingsDialog = false })
    }
}

@Composable
fun SettingsDialog(context: Context, onDismiss: () -> Unit) {
    var ipText by remember { mutableStateOf("74.208.75.175") }
    var portText by remember { mutableStateOf("5000") }
    var timeoutText by remember { mutableStateOf("10") }
    var numChunksText by remember { mutableStateOf("3") }
    var chunkLengthText by remember { mutableStateOf("1361") }

    var protocolChecked by remember { mutableStateOf(false) } // False for HTTP, true for HTTPS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                TextField(
                    value = ipText,
                    onValueChange = { ipText = it },
                    label = { Text("Server IP Address") }
                )
                TextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Number
                            )
                )
                TextField(
                    value = timeoutText,
                    onValueChange = { timeoutText = it },
                    label = { Text("Record timeout") },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number
                    )
                )

                TextField(
                    value = numChunksText,
                    onValueChange = { numChunksText = it },
                    label = { Text("Good segments") },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number
                    )
                )

                TextField(
                    value = chunkLengthText,
                    onValueChange = { chunkLengthText = it },
                    label = { Text("Segment length") },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number
                    )
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = protocolChecked,
                        onCheckedChange = { protocolChecked = it }
                    )
                    Text("Use HTTPS")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val protocol = if (protocolChecked) "https" else "http"
                AppConfig.serverIP = "$protocol://$ipText:$portText"
                val timeout = timeoutText.toIntOrNull() ?: 10 // Default to 30 seconds if invalid or empty
                AppConfig.timeOut = timeout
                val numChunks = numChunksText.toIntOrNull() ?: 10 // Default to 30 seconds if invalid or empty
                AppConfig.numChunks = numChunks
                val chunkLength = chunkLengthText.toIntOrNull() ?: 10 // Default to 30 seconds if invalid or empty
                AppConfig.segmentLength = chunkLength
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
@Composable
fun Greeting(name: String, context: Context? = null, modifier: Modifier = Modifier) {
        var showSettingsDialog by remember { mutableStateOf(false) }

        // LocalContext is used to provide context in a composable function
        val context = LocalContext.current
        var uniqueId by remember { mutableStateOf<String?>(null) }
        val showDialog = remember { mutableStateOf(false) }
        val ipText = remember { mutableStateOf("") }
        var serverErrorDialog by remember { mutableStateOf(false) }


        // Column composable to stack items vertically
        Column(
            modifier = modifier.fillMaxSize(), // Fill the parent's size
//            verticalArrangement = Arrangement.SpaceBetween, // Space out the children as much as possible
            horizontalAlignment = Alignment.CenterHorizontally // Center items horizontally
        ) {
            Text(
                text = "ТЕПРО",
//                style = TextStyle(fontSize = 32.sp), // Set font size to 16sp
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 32.dp) // Top padding for aesthetic spacing
            )
            Text(
                text = "Карманный стетоскоп",
//                style = TextStyle(fontSize = 20.sp), // Set font size to 16sp
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp, bottom = 24.dp) // Top padding for aesthetic spacing
            )

            // Introductory Text
            Text(
                text = "Нажми на фиолетовый круг и прижми телефон микрофоном к соответствующей области на теле. Удерживай телефон плотно прижав его к телу и не шевеля, пока не услышишь короткий двойной сигнал. Длинный сигнал означает, что запись не удалась, повтори процедуру",
                textAlign = TextAlign.Center,
//                style = TextStyle(fontSize = 24.sp), // Set font size to 16sp
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)
//                modifier = Modifier.padding(top = 32.dp) // Top padding for aesthetic spacing
            )

            // Button positioned at the bottom
            Button(
                onClick = {
                    checkServerResponse() { isSuccessful ->
                        if (isSuccessful) {
                            CoroutineScope(Dispatchers.IO).launch {
                                uniqueId = fetchUniqueId()
                                withContext(Dispatchers.Main) {
                                    uniqueId?.let {
                                        val intent = Intent(context, TakeRecordsFrontActivity::class.java)
                                        intent.putExtra("UNIQUE_ID", it)
                                        context.startActivity(intent)
                                    }
                                }
                            }
                        } else {
                            serverErrorDialog = true
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Text(text = "ПОНЯТНО, НАЧИНАЕМ")

            }
            if (serverErrorDialog) {
                    AlertDialog(
                        onDismissRequest = { serverErrorDialog = false },
                        title = { Text("Server Error") },
                        text = { Text("Cannot connect to the server. Please check your network connection and try again. ${AppConfig.serverIP}") },
                        confirmButton = {
                            Button(onClick = { serverErrorDialog = false }) {
                                Text("OK")
                            }
                        }
                    )
            }
        }
    Button(
        onClick = { showDialog.value = true },
        modifier = Modifier.padding(top = 16.dp)
    ) {
        Text(text = "Settings")
    }

    if (showDialog.value) {
        AlertDialog(
            onDismissRequest = { showDialog.value = false },
            title = { Text("Enter Server IP") },
            text = {
                TextField(
                    value = ipText.value,
                    onValueChange = { ipText.value = it },
                    label = { Text("IP Address") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        AppConfig.serverIP = ipText.value
                        showDialog.value = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDialog.value = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
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
                Log.e("MainActivity", "Server connection test success: ${response.message}")
                callback(response.isSuccessful)
            }
    })
}

suspend fun fetchUniqueId(): String? = withContext(Dispatchers.IO) {

    val client = OkHttpClient()
    val deviceInfo = mapOf(
        "model" to Build.MODEL,
        "manufacturer" to Build.MANUFACTURER
    )
    val requestBody = RequestBody.create(
        "application/json; charset=utf-8".toMediaTypeOrNull(),
        Gson().toJson(deviceInfo)
    )

    val request = Request.Builder()
        .url("${AppConfig.serverIP}/getUniqueId")
        .post(requestBody)
        .build()

    client.newCall(request).execute().use { response ->
        if (response.isSuccessful) {
            Log.e("MainActivity", "Server success: ${response.message}")
            response.body?.string()
        } else null
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    HelloWorldAppTheme {
        Greeting("Android")
    }
}