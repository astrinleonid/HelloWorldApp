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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
//import androidx.compose.ui.node.CanFocusChecker.left
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.helloworldapp.ui.theme.HelloWorldAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TakeRecordsFrontActivity : ComponentActivity() {

    private val buttonColors = mutableStateListOf<Boolean>().apply { addAll(List(4) { false }) }
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainContent(uniqueId: String?,
                    buttonColors: SnapshotStateList<Boolean>, // Adjusted type here
                    getResult: ActivityResultLauncher<Intent>) {

        val context = LocalContext.current

        if (buttonColors.all { it }) {
            showCenteredToast(context, "ЗАПИСИ СДЕЛАНЫ, ТЕПЕРЬ СПИНА")
            val intent = Intent(context, TakeRecordsActivity::class.java).apply {
                putExtra("UNIQUE_ID", uniqueId)
            }
            context.startActivity(intent)
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Запись точек на груди") },
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
                        painter = painterResource(id = R.drawable.front_background),
                        contentDescription = "Background Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    ChangeSideToBackButton(context = context, getResult = getResult, uniqueId = uniqueId)
                    ButtonGridFront(
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
}



@Composable
fun ButtonGridFront(context: Context,  buttonColors: List<Boolean>, getResult: ActivityResultLauncher<Intent>,  modifier: Modifier = Modifier, uniqueId: String?) {
    Column(
        verticalArrangement = Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(horizontal = 32.dp)
            .padding(top = 180.dp)
            .fillMaxWidth()
    ) {
        (1..4).chunked(2).forEach { pair ->
            ButtonRowFront(buttonLabels = pair, buttonColors = buttonColors, getResult = getResult, context = context, uniqueId = uniqueId)
        }
    }
}


@Composable
fun ButtonRowFront(buttonLabels: List<Int>, buttonColors: List<Boolean>, getResult: ActivityResultLauncher<Intent>, context: Context, uniqueId: String?) {

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        buttonLabels.forEach {label ->
            val isSelected = buttonColors[label - 1]
            RoundButton(label = "$label", isSelected = isSelected, getResult = getResult, context = context, uniqueId = uniqueId)
        }
    }
}

@Composable
fun ChangeSideToBackButton(context: Context, getResult: ActivityResultLauncher<Intent>, uniqueId: String?) {
    Spacer(modifier = Modifier
        .width(240.dp)
        .height(180.dp))

    Button(
        onClick = {
            // Intent to start the playback activity
            val intent = Intent(context, TakeRecordsActivity::class.java).apply {
                putExtra("UNIQUE_ID", uniqueId)
            }
            context.startActivity(intent)
        },
        modifier = Modifier
            .height(180.dp)  // Sets the height of the button
            .width(160.dp)
            .padding(32.dp) ,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, Color.Black),  // Black border
        colors = ButtonDefaults.buttonColors(containerColor = Color.White)  // White background
    ) {
        Text(text = "СПИНА", color = Color.Black)
    }
}

