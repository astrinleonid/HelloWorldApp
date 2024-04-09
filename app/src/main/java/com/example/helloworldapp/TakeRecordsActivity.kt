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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

class TakeRecordsActivity : ComponentActivity() {

    private val buttonColors = mutableStateListOf<Boolean>().apply { addAll(List(6) { false }) }
    private val showDialog = mutableStateOf(false)
    private val getResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val buttonNumber = data?.getStringExtra("button_number")?.toIntOrNull()
            buttonNumber?.let {
                if (it in 1..buttonColors.size) {
                    buttonColors[it - 1] = true // Update the color state for the button
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
    @Composable
    fun MainContent(uniqueId: String?,
                    buttonColors: SnapshotStateList<Boolean>, // Adjusted type here
                    getResult: ActivityResultLauncher<Intent>) {

        val context = LocalContext.current
//        val showDialog = remember { mutableStateOf(false) } // State for controlling the visibility of the dialog

        if (buttonColors.all { it }) {
//            onAllGreen() // If all are green, execute the passed action
            showCenteredToast(context, "ЗАПИСЬ СОХРАНЕНА")
            context.startActivity(Intent(context, MainActivity::class.java))
        } else {

            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                // Background Image
                Image(
                    painter = painterResource(id = R.drawable.back_background),
                    contentDescription = "Background Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Using Column for vertical arrangement
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp), // Apply padding to the Column for overall padding
                    verticalArrangement = Arrangement.SpaceBetween, // Space between items, pushing the button to the bottom
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ButtonGrid(
                        context = context,
                        buttonColors = buttonColors,
                        getResult = getResult,
                        uniqueId = uniqueId,
                        modifier = Modifier
                                .size(width = 300.dp, height = 400.dp)
                                .offset(y = (180).dp)
                    )

                    // Spacer to optionally add space above the button if needed
                    Spacer(modifier = Modifier.weight(1f, fill = false))

                    DoneButton { showDialog.value = true }
                }

                // Confirmation Dialog
                if (showDialog.value) {
                    ConfirmationDialog(
                        onConfirm = {
                            showDialog.value = false
                            context.startActivity(Intent(context, MainActivity::class.java))
                        },
                        onDismiss = { showDialog.value = false }
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
        modifier = modifier.fillMaxSize()
    ) {
        (1..6).chunked(2).forEach { pair ->
            ButtonRow(buttonLabels = pair, buttonColors = buttonColors, getResult = getResult, context = context, uniqueId = uniqueId)
        }
    }
}

@Composable
fun ButtonRow(buttonLabels: List<Int>, buttonColors: List<Boolean>, getResult: ActivityResultLauncher<Intent>, context: Context, uniqueId: String?) {

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        buttonLabels.forEach {label ->
            val isSelected = buttonColors[label - 1]
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
//                uniqueId?.let {
//                    putExtra("UNIQUE_ID", it) // Pass the unique ID to RecordActivity
//                }
            }
            getResult.launch(intent)
            // For demonstration, let's assume you want to toggle the color on click
            // This is where you would update the state based on the result from RecordActivity
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

@Composable
fun DoneButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp), // Adjust padding as necessary
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Text("DONE", color = MaterialTheme.colorScheme.onPrimary)
    }
}


@Composable
fun ConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Подтвердить")
        },
        text = {
            Text("Вы уверены, что хотите закончить запись?")
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Yes")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("No")
            }
        }
    )
}

