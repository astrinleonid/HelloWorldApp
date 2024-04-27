package com.example.helloworldapp


import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.helloworldapp.ui.theme.HelloWorldAppTheme
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class TakeRecordsActivity : ComponentActivity() {

    private val buttonColors = mutableStateListOf<Boolean>().apply { addAll(List(6) { false }) }
    private val showDialog = mutableStateOf(false)
    private val getResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val buttonNumber = data?.getStringExtra("button_number")?.toIntOrNull()
            buttonNumber?.let {
                if (it in 5..buttonColors.size + 4) {
                    buttonColors[it - 5] = true // Update the color state for the button
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uniqueId = intent.getStringExtra("UNIQUE_ID")
        setContent {
            HelloWorldAppTheme {
                MainContent(uniqueId, buttonColors, getResult)
            }
        }
    }

    override fun onBackPressed() {
        // If the user presses back, return "0"
        showDialog.value = true
//        super.onBackPressed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainContent(uniqueId: String?,
                    buttonColors: SnapshotStateList<Boolean>, // Adjusted type here
                    getResult: ActivityResultLauncher<Intent>) {

        val context = LocalContext.current

        if (buttonColors.all { it }) {
//            showCenteredToast(context, "ЗАПИСЬ СОХРАНЕНА")
//            context.startActivity(Intent(context, MainActivity::class.java))
            showDialog.value = true
        }
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Запись точек на спине") },
                        actions = {
                            PlaybackButton(context = context, getResult = getResult, uniqueId = uniqueId)
                        }
                    )
                },
                bottomBar = {
                    DoneButton {
                        showDialog.value = true
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.back_background),
                        contentDescription = "Background Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    val intent = Intent(context, TakeRecordsFrontActivity::class.java).apply {
                        putExtra("UNIQUE_ID", uniqueId)
                    }
                    ChangeSideButton(intent = intent,
                                    context = context,
                                    getResult = getResult,
                                    uniqueId = uniqueId,
                                    iconResId = R.drawable.breast_icon)
                    ButtonGrid(
                        context = context,
                        buttonColors = buttonColors,
                        getResult = getResult,
                        uniqueId = uniqueId,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 50.dp)
                    )

                    if (showDialog.value) {
                        ConfirmationDialog(
                            onSaveAndExit = {
                                showDialog.value = false
                                val intent = Intent(context, ShowQrActivity::class.java).apply {
                                    putExtra("UNIQUE_ID", uniqueId)
                                }
                                context.startActivity(intent)
                                finish()
                            },
                            onDismiss = { showDialog.value = false },
                            onExitWithoutSaving = {
                                showDialog.value = false
                                sendDeleteCommand(uniqueId, context)
                                context.startActivity(Intent(context, MainActivity::class.java))
                                finish()}

                        )
                    }
                }

        }
    }
}

fun showCenteredToast(context: Context, message: String) {
    val toast = Toast.makeText(context, message, Toast.LENGTH_LONG)
    toast.setGravity(Gravity.CENTER, 0, 0) // Center the toast
    toast.show()
}



@Composable
fun ButtonGrid(context: Context,  buttonColors: List<Boolean>, getResult: ActivityResultLauncher<Intent>,  modifier: Modifier = Modifier, uniqueId: String?) {
    Column(
        verticalArrangement = Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(horizontal = 32.dp)
            .padding(top = 96.dp)
            .fillMaxWidth()
    ) {
        (5..10).chunked(2).forEach { pair ->
            ButtonRow(buttonLabels = pair, buttonColors = buttonColors, getResult = getResult, context = context, uniqueId = uniqueId)
        }
    }
}

@Composable
fun ButtonRow(buttonLabels: List<Int>, buttonColors: List<Boolean>, getResult: ActivityResultLauncher<Intent>, context: Context, uniqueId: String?) {

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        buttonLabels.forEach {label ->
            val isSelected = buttonColors[label - 5]
            RoundButton(label = "$label", isSelected = isSelected, getResult = getResult, context = context, uniqueId = uniqueId)
        }
    }
}


@Composable
fun RoundButton(label: String, isSelected: Boolean, getResult: ActivityResultLauncher<Intent>, context: Context, uniqueId: String?) {
    val backgroundColor = if (isSelected) Color.Green else MaterialTheme.colorScheme.primary

    Button(
        onClick = {
            // Here, you would launch RecordActivity for a result
            val intent = Intent(context, RecordActivity::class.java).apply {
                putExtra("button_number", label)
                putExtra("UNIQUE_ID", uniqueId)
            }
            getResult.launch(intent)
        },
        shape = CircleShape,
        modifier = Modifier
            .padding(8.dp)
            .size(80.dp),
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor)
    ) {
        Text(text = label, color = Color.White)
    }
}

fun sendDeleteCommand(uniqueId: String?, context: Context) {
    val url = "${AppConfig.serverIP}/record_delete?record_id=${uniqueId}"

    val request = Request.Builder()
        .url(url)
        .get() // Using GET as specified
        .build()

    OkHttpClient().newCall(request).enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: IOException) {
            // Handle failure, e.g., update UI on main thread
            (context as? Activity)?.runOnUiThread {
                Toast.makeText(context,"Error deleting record", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            (context as? Activity)?.runOnUiThread {
                if (response.isSuccessful) {
                    Toast.makeText(context,"Record deleted successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context,"Failed to delete record: ${response.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    })
}


@Composable
fun ConfirmationDialog(
    onSaveAndExit: () -> Unit,
    onExitWithoutSaving: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("Подтвердить") },
        text = {
            Column {
                Text("Сохранить записи на сервере и выйти?")
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = onSaveAndExit) {
                        Text("Сохранить и выйти")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onExitWithoutSaving) {
                Text("Не сохранять")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun DoneButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp), // Adjust padding as necessary
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Text("DONE", color = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
fun PlaybackButton(context: Context, getResult: ActivityResultLauncher<Intent>, uniqueId: String?) {
    Button(
        onClick = {
            // Intent to start the playback activity
            val intent = Intent(context, PlayRecordsActivity::class.java).apply {
                putExtra("UNIQUE_ID", uniqueId)
            }
            context.startActivity(intent)
        },
        modifier = Modifier
            .height(32.dp)  // Sets the height of the button
            .width(120.dp),  // Sets the width of the button to make it more elongated
        shape = RoundedCornerShape(12.dp),  // Sets the corners to be rounded, 12.dp is a moderate roundness
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Text(text = "Play", color = MaterialTheme.colorScheme.onPrimary)  // Ensures text color contrasts with button color
    }
}

@Composable
fun ChangeSideButton(
    intent: Intent,
    context: Context,
    getResult: ActivityResultLauncher<Intent>,
    uniqueId: String?,
    iconResId: Int
) {
    val icon = painterResource(id = iconResId)

    Button(
        onClick = {
            context.startActivity(intent)
        },
        modifier = Modifier
            .height(160.dp)
            .width(140.dp)
            .padding(10.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, Color.Black),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
        contentPadding = PaddingValues(0.dp) // Add this line
    ) {
        Icon(
            painter = icon,
            contentDescription = "Button Icon",
            tint = Color.Unspecified, // Use Color.Unspecified to prevent tinting
            modifier = Modifier.size(1203.dp)
        )
    }
}