package com.example.helloworldapp

//import androidx.compose.ui.node.CanFocusChecker.left
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

class TakeRecordsFrontActivity : ComponentActivity() {

    private val buttonColors = mutableStateListOf<Boolean>().apply { addAll(List(10) { false }) }
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
        val uniqueId = intent.getStringExtra("UNIQUE_ID")
        fetchButtonColors(uniqueId) { buttonStates ->
            buttonStates?.let {
                runOnUiThread {
                    buttonColors.clear()
                    buttonColors.addAll(it)
                }
            } ?: run {
                println("Failed to fetch or parse button states.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uniqueId = intent.getStringExtra("UNIQUE_ID")
        fetchButtonColors(uniqueId) { buttonStates ->
            buttonStates?.let {
                // Update your state here
                runOnUiThread {
                    // Assuming buttonColors is a mutableStateListOf<Boolean>
                    buttonColors.clear()
                    buttonColors.addAll(it)
                }
            } ?: run {
                // Handle error or null state
                println("Failed to fetch or parse button states.")
            }
        }
        setContent {
            HelloWorldAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ){
                    MainContent(uniqueId, buttonColors, getResult)
                }
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

        if (buttonColors.take(4).all { it }) {
            showCenteredToast(context, "ЗАПИСИ СДЕЛАНЫ, ТЕПЕРЬ СПИНА")
            val intent = Intent(context, TakeRecordsActivity::class.java).apply {
                putExtra("UNIQUE_ID", uniqueId)
            }
            context.startActivity(intent)
        } else {
            Scaffold(
                containerColor = Color.White,
                topBar = {
                    TopAppBar(
                        title = { Text("Запись точек на груди") },
                        colors = TopAppBarDefaults.smallTopAppBarColors(
                            containerColor = Color.White,  // Set the background color of the TopAppBar
                            titleContentColor = Color.Black  // Set the color of the title text
                        ),
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
                        painter = painterResource(id = R.drawable.breast_background),
                        contentDescription = "Background Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    val intent = Intent(context, TakeRecordsActivity::class.java).apply {
                        putExtra("UNIQUE_ID", uniqueId)
                    }
                    ChangeSideButton(intent = intent,
                        context = context,
                        getResult = getResult,
                        uniqueId = uniqueId,
                        iconResId = R.drawable.back_icon)

                    ButtonGridFront(
                        context = context,
                        buttonColors = buttonColors,
                        getResult = getResult,
                        uniqueId = uniqueId,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
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
                            onExitWithoutSaving = { showDialog.value = false
                                sendDeleteCommand(uniqueId, context)
                                context.startActivity(Intent(context, MainActivity::class.java))
                                finish()}
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
