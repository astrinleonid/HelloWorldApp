package com.example.helloworldapp


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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.helloworldapp.ui.theme.HelloWorldAppTheme

class TakeRecordsActivity : ComponentActivity() {
    private val buttonColors = mutableStateListOf<Boolean>().apply { addAll(List(6) { false }) }
    private val getResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Get the data from the intent
            val data = result.data
            val buttonNumber = data?.getStringExtra("button_number")?.toIntOrNull()
            buttonNumber?.let {
                // Assuming buttonNumber is 1-based index
                if (it in 1..buttonColors.size) {
                    buttonColors[it - 1] = true // Update the color state for the button
                }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HelloWorldAppTheme {
                val buttonColorsState = remember { buttonColors }
                // Use a Box to contain the ButtonGrid
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize() // Fill the screen but adjust the size of ButtonGrid inside
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.back_background),
                        contentDescription = null, // Decorative image
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop // Fill the screen, cropping as necessary
                    )
                    ButtonGrid(
                        context = this@TakeRecordsActivity,
                        buttonColors = buttonColorsState,
                        getResult = getResult,
                        modifier = Modifier
                            .size(width = 300.dp, height = 400.dp) // Example size, adjust as needed
                    )
                }
            }
        }
    }
}



@Composable
fun ButtonGrid(context: Context,  buttonColors: List<Boolean>, getResult: ActivityResultLauncher<Intent>,  modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize()
    ) {
        (1..6).chunked(2).forEach { pair ->
            ButtonRow(buttonLabels = pair, buttonColors = buttonColors, getResult = getResult, context = context)
        }
    }
}

@Composable
fun ButtonRow(buttonLabels: List<Int>, buttonColors: List<Boolean>, getResult: ActivityResultLauncher<Intent>, context: Context) {

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        buttonLabels.forEach {label ->
            val isSelected = buttonColors[label - 1]
            RoundButton(label = "$label", isSelected = isSelected, getResult = getResult, context = context)
        }
    }
}


@Composable
fun RoundButton(label: String, isSelected: Boolean, getResult: ActivityResultLauncher<Intent>, context: Context) {
    val backgroundColor = if (isSelected) Color.Green else MaterialTheme.colorScheme.primary

    Button(
        onClick = {
            // Here, you would launch RecordActivity for a result
            val intent = Intent(context, RecordActivity::class.java).apply {
                putExtra("button_number", label)
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


