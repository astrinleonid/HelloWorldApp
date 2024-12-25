//package com.example.helloworldapp
//
////import androidx.compose.ui.node.CanFocusChecker.left
//import android.app.Activity
//import android.content.Context
//import android.content.Intent
//import android.os.Bundle
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.activity.result.ActivityResultLauncher
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.Row
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.padding
//import androidx.compose.material3.ExperimentalMaterial3Api
//import androidx.compose.material3.Scaffold
//import androidx.compose.material3.Surface
//import androidx.compose.material3.Text
//import androidx.compose.material3.TopAppBar
//import androidx.compose.material3.TopAppBarDefaults
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.mutableStateListOf
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.snapshots.SnapshotStateList
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.layout.ContentScale
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.res.painterResource
//import androidx.compose.ui.unit.dp
//import com.example.helloworldapp.ui.theme.HelloWorldAppTheme
//
//
//import android.view.Gravity
//import android.view.LayoutInflater
//import android.widget.Button
//import android.widget.GridLayout
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import androidx.appcompat.widget.Toolbar
//
//import okhttp3.Call
//import okhttp3.Callback
//import okhttp3.OkHttpClient
//import okhttp3.Request
//import okhttp3.Response
//import org.json.JSONArray
//import java.io.IOException
//
//
//
//class TakeRecordsActivity : AppCompatActivity() {
//    private val buttonStates = mutableListOf<Boolean>().apply { addAll(List(10) { false }) }
//    private lateinit var buttonGrid: GridLayout
//    private var isBackView: Boolean = true // Track which view we're showing
//
//    companion object {
//        const val EXTRA_VIEW_TYPE = "view_type"
//        const val VIEW_TYPE_BACK = "back"
//        const val VIEW_TYPE_FRONT = "front"
//    }
//
//
////class TakeRecordsFrontActivity : ComponentActivity() {
//
////    private val buttonColors = mutableStateListOf<Boolean>().apply { addAll(List(10) { false }) }
////    private val showDialog = mutableStateOf(false)
////    private val getResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
////        if (result.resultCode == Activity.RESULT_OK) {
////            val data = result.data
////            val buttonNumber = data?.getStringExtra("button_number")?.toIntOrNull()
////            buttonNumber?.let {
////                if (it in 1..buttonColors.size) {
////                    buttonColors[it - 1] = true // Update the color state for the button
////                }
////            }
////        }
////        val uniqueId = intent.getStringExtra("UNIQUE_ID")
////        fetchButtonColors(uniqueId) { buttonStates ->
////            buttonStates?.let {
////                runOnUiThread {
////                    buttonColors.clear()
////                    buttonColors.addAll(it)
////                }
////            } ?: run {
////                println("Failed to fetch or parse button states.")
////            }
////        }
////    }
//
//
//    private val getResult =
//        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//            if (result.resultCode == Activity.RESULT_OK) {
//                val buttonNumber = result.data?.getStringExtra("button_number")?.toIntOrNull()
//                buttonNumber?.let {
//                    if (it in 5..buttonStates.size) {
//                        buttonStates[it - 1] = true
//                        updateButtonColor(it - 1)
//                    }
//                }
//            }
//            val uniqueId = intent.getStringExtra("UNIQUE_ID")
//            fetchButtonColors(uniqueId) { states ->
//                states?.let {
//                    runOnUiThread {
//                        buttonStates.clear()
//                        buttonStates.addAll(it)
//                        updateAllButtonColors()
//                    }
//                }
//            }
//        }
//
//
////    override fun onCreate(savedInstanceState: Bundle?) {
////        super.onCreate(savedInstanceState)
////        val uniqueId = intent.getStringExtra("UNIQUE_ID")
////        fetchButtonColors(uniqueId) { buttonStates ->
////            buttonStates?.let {
////                // Update your state here
////                runOnUiThread {
////                    // Assuming buttonColors is a mutableStateListOf<Boolean>
////                    buttonColors.clear()
////                    buttonColors.addAll(it)
////                }
////            } ?: run {
////                // Handle error or null state
////                println("Failed to fetch or parse button states.")
////            }
////        }
////        setContent {
////            HelloWorldAppTheme {
////                Surface(
////                    modifier = Modifier.fillMaxSize(),
////                    color = Color.White
////                ){
////                    MainContent(uniqueId, buttonColors, getResult)
////                }
////            }
////        }
////    }
//
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        // Determine which layout to use based on the intent extra
//        isBackView = intent.getStringExtra(EXTRA_VIEW_TYPE) != VIEW_TYPE_FRONT
//        setContentView(if (isBackView) R.layout.activity_take_records_back else R.layout.activity_take_records_front)
//
//        val uniqueId = intent.getStringExtra("UNIQUE_ID")
//        setupToolbar()
//        setupButtons()
//        createButtonGrid()
//
//        fetchButtonColors(uniqueId) { states ->
//            states?.let {
//                runOnUiThread {
//                    buttonStates.clear()
//                    buttonStates.addAll(it)
//                    updateAllButtonColors()
//                }
//            }
//        }
//    }
//
//    private fun setupToolbar() {
//        val toolbar = findViewById<Toolbar>(R.id.toolbar)
//        setSupportActionBar(toolbar)
//        supportActionBar?.title =
//            if (isBackView) "Запись точек на спине" else "Запись точек на груди"
//
//        findViewById<Button>(R.id.playbackButton).setOnClickListener {
//            val intent = Intent(this, PlayRecordsActivity::class.java).apply {
//                putExtra("UNIQUE_ID", intent.getStringExtra("UNIQUE_ID"))
//            }
//            getResult.launch(intent)
//        }
//    }
//
//    private fun setupButtons() {
//        findViewById<Button>(R.id.changeSideButton).setOnClickListener {
//            // Launch the same activity but with different view type
//            val newIntent = Intent(this, TakeRecordsActivity::class.java).apply {
//                putExtra("UNIQUE_ID", intent.getStringExtra("UNIQUE_ID"))
//                putExtra(EXTRA_VIEW_TYPE, if (isBackView) VIEW_TYPE_FRONT else VIEW_TYPE_BACK)
//                // Add flags to handle the activity stack
//                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
//            }
//            startActivity(newIntent)
//            finish() // Finish current instance
//        }
//
//        findViewById<Button>(R.id.doneButton).setOnClickListener {
//            showConfirmationDialog()
//        }
//    }
//
//    private fun createButtonGrid() {
//        buttonGrid = findViewById(R.id.buttonGrid)
//
//        // Different button ranges for front and back views
//        val buttonRange = if (isBackView) 5..10 else 1..4
//
//        for (i in buttonRange) {
//            val button = createRoundButton(i)
//            val params = GridLayout.LayoutParams().apply {
//                width = resources.getDimensionPixelSize(R.dimen.grid_button_size)
//                height = resources.getDimensionPixelSize(R.dimen.grid_button_size)
//                setMargins(8, 8, 8, 8)
//            }
//            buttonGrid.addView(button, params)
//        }
//    }
//
//    private fun createRoundButton(number: Int): Button {
//        return Button(this).apply {
//            text = number.toString()
//            setBackgroundResource(R.drawable.round_button_background)
//            setOnClickListener {
//                val intent = Intent(this@TakeRecordsActivity, RecordActivity::class.java).apply {
//                    putExtra("button_number", number.toString())
//                    putExtra(
//                        "UNIQUE_ID",
//                        this@TakeRecordsActivity.intent.getStringExtra("UNIQUE_ID")
//                    )
//                }
//                getResult.launch(intent)
//            }
//        }
//    }
//
//    private fun updateButtonColor(index: Int) {
//        val buttonIndex = if (isBackView) index - 5 else index
//        val button = buttonGrid.getChildAt(buttonIndex) as? Button
//        button?.setBackgroundResource(
//            if (buttonStates[index]) R.drawable.round_button_selected
//            else R.drawable.round_button_background
//        )
//    }
//
//    private fun updateAllButtonColors() {
//        val range = if (isBackView) 5..10 else 1..4
//        for (i in range) {
//            updateButtonColor(i - 1)
//        }
//        if (buttonStates.all { it }) {
//            showConfirmationDialog()
//        }
//    }
//
//}
//
////    override fun onBackPressed() {
////        // If the user presses back, return "0"
////        showDialog.value = true
//////        super.onBackPressed()
////    }
////
////    @OptIn(ExperimentalMaterial3Api::class)
////    @Composable
////    fun MainContent(uniqueId: String?,
////                    buttonColors: SnapshotStateList<Boolean>, // Adjusted type here
////                    getResult: ActivityResultLauncher<Intent>) {
////
////        val context = LocalContext.current
////
////        if (buttonColors.take(4).all { it }) {
////            showCenteredToast(context, "ЗАПИСИ СДЕЛАНЫ, ТЕПЕРЬ СПИНА")
////            val intent = Intent(context, TakeRecordsActivity::class.java).apply {
////                putExtra("UNIQUE_ID", uniqueId)
////            }
////            context.startActivity(intent)
////        } else {
////            Scaffold(
////                containerColor = Color.White,
////                topBar = {
////                    TopAppBar(
////                        title = { Text("Запись точек на груди") },
////                        colors = TopAppBarDefaults.smallTopAppBarColors(
////                            containerColor = Color.White,  // Set the background color of the TopAppBar
////                            titleContentColor = Color.Black  // Set the color of the title text
////                        ),
////                        actions = {
////                            PlaybackButton(context = context, getResult = getResult, uniqueId = uniqueId)
////                        }
////                    )
////                },
////                bottomBar = {
////                    DoneButton {
////                        showDialog.value = true
////                    }
////                }
////            ) { paddingValues ->
////                Box(
////                    modifier = Modifier
////                        .padding(paddingValues)
////                        .fillMaxSize()
////                ) {
////                    Image(
////                        painter = painterResource(id = R.drawable.breast_background),
////                        contentDescription = "Background Image",
////                        modifier = Modifier.fillMaxSize(),
////                        contentScale = ContentScale.Crop
////                    )
////                    val intent = Intent(context, TakeRecordsActivity::class.java).apply {
////                        putExtra("UNIQUE_ID", uniqueId)
////                    }
////                    ChangeSideButton(intent = intent,
////                        context = context,
////                        getResult = getResult,
////                        uniqueId = uniqueId,
////                        iconResId = R.drawable.back_icon)
////
////                    ButtonGridFront(
////                        context = context,
////                        buttonColors = buttonColors,
////                        getResult = getResult,
////                        uniqueId = uniqueId,
////                        modifier = Modifier
////                            .align(Alignment.TopCenter)
////                            .padding(top = 12.dp)
////                    )
////
////                    if (showDialog.value) {
////                        ConfirmationDialog(
////                            onSaveAndExit = {
////                                showDialog.value = false
////                                val intent = Intent(context, ShowQrActivity::class.java).apply {
////                                    putExtra("UNIQUE_ID", uniqueId)
////                                }
////                                context.startActivity(intent)
////                                finish()
////                            },
////                            onDismiss = { showDialog.value = false },
////                            onExitWithoutSaving = { showDialog.value = false
////                                sendDeleteCommand(uniqueId, context)
////                                context.startActivity(Intent(context, MainActivity::class.java))
////                                finish()}
////                        )
////
////                    }
////                }
////            }
////        }
////    }
////}
//
//
//
////@Composable
////fun ButtonGridFront(context: Context,  buttonColors: List<Boolean>, getResult: ActivityResultLauncher<Intent>,  modifier: Modifier = Modifier, uniqueId: String?) {
////    Column(
////        verticalArrangement = Arrangement.SpaceAround,
////        horizontalAlignment = Alignment.CenterHorizontally,
////        modifier = modifier
////            .padding(horizontal = 32.dp)
////            .padding(top = 180.dp)
////            .fillMaxWidth()
////    ) {
////        (1..4).chunked(2).forEach { pair ->
////            ButtonRowFront(buttonLabels = pair, buttonColors = buttonColors, getResult = getResult, context = context, uniqueId = uniqueId)
////        }
////    }
////}
////
////
////@Composable
////fun ButtonRowFront(buttonLabels: List<Int>, buttonColors: List<Boolean>, getResult: ActivityResultLauncher<Intent>, context: Context, uniqueId: String?) {
////
////    Row(
////        horizontalArrangement = Arrangement.SpaceEvenly,
////        verticalAlignment = Alignment.CenterVertically,
////        modifier = Modifier
////            .fillMaxWidth()
////            .padding(vertical = 8.dp)
////    ) {
////        buttonLabels.forEach {label ->
////            val isSelected = buttonColors[label - 1]
////            RoundButton(label = "$label", isSelected = isSelected, getResult = getResult, context = context, uniqueId = uniqueId)
////        }
////    }
////}
