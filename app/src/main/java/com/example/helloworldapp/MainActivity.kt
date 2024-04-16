
package com.example.helloworldapp

import AppConfig
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.helloworldapp.ui.theme.HelloWorldAppTheme
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody


//class MainActivity : ComponentActivity() {
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContent {
//            HelloWorldAppTheme {
//                // A surface container with blue background
//                Surface(
//                    modifier = Modifier.fillMaxSize(),
//                    color = MaterialTheme.colorScheme.primary // Change this to your desired blue color
//                ) {
//                    Greeting("Android", context = this)
//                }
//            }
//        }
//    }
//}

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
        title = { Text("Welcome") },
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
    var ipText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Server IP") },
        text = {
            TextField(
                value = ipText,
                onValueChange = { ipText = it },
                label = { Text("IP Address") }
            )
        },
        confirmButton = {
            Button(onClick = {
                AppConfig.serverIP = ipText
                onDismiss()
            }) {
                Text("OK")
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


        // Column composable to stack items vertically
        Column(
            modifier = modifier.fillMaxSize(), // Fill the parent's size
            verticalArrangement = Arrangement.SpaceBetween, // Space out the children as much as possible
            horizontalAlignment = Alignment.CenterHorizontally // Center items horizontally
        ) {
            Text(
                text = "Карманный стетоскоп ТЕПРО \n ip${AppConfig.serverIP}",
                modifier = Modifier.padding(top = 32.dp) // Top padding for aesthetic spacing
            )

            // Introductory Text
            Text(
                text = "Нажми на фиолетовый круг и прижми телефон микрофоном к соответствующей области на теле. Удерживай телефон плотно прижав его к телу и не шевеля, пока не услышишь короткий двойной сигнал. Длинный сигнал означает, что запись не удалась, повтори процедуру",
                modifier = Modifier.padding(top = 32.dp) // Top padding for aesthetic spacing
            )

            // Button positioned at the bottom
            Button(
                onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        uniqueId = fetchUniqueId("10.0.2.2")
                        withContext(Dispatchers.Main) {
                            uniqueId?.let {
                                val intent = Intent(context, TakeRecordsActivity::class.java)
                                intent.putExtra("UNIQUE_ID", it)
                                context.startActivity(intent)
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                modifier = Modifier
                    .padding(16.dp) // Padding around the button for aesthetic spacing
                    .align(Alignment.CenterHorizontally) // Ensure the button is centered horizontally within the Column
            ) {
                Text(text = "ПОНЯТНО, НАЧИНАЕМ")
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



suspend fun fetchUniqueId(ip : String): String? = withContext(Dispatchers.IO) {

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
        .url("http://${AppConfig.serverIP}:5000/getUniqueId")
        .post(requestBody)
        .build()

    client.newCall(request).execute().use { response ->
        if (response.isSuccessful) {
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