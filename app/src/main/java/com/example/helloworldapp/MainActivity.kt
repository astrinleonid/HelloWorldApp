
package com.example.helloworldapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.helloworldapp.ui.theme.HelloWorldAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HelloWorldAppTheme {
                // A surface container with blue background
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary // Change this to your desired blue color
                ) {
                    Greeting("Android", context = this)
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, context: Context? = null, modifier: Modifier = Modifier) {


        // LocalContext is used to provide context in a composable function
        val context = LocalContext.current

        // Column composable to stack items vertically
        Column(
            modifier = modifier.fillMaxSize(), // Fill the parent's size
            verticalArrangement = Arrangement.SpaceBetween, // Space out the children as much as possible
            horizontalAlignment = Alignment.CenterHorizontally // Center items horizontally
        ) {
            Text(
                text = "Welcome, $name!",
                modifier = Modifier.padding(top = 32.dp) // Top padding for aesthetic spacing
            )

            // Introductory Text
            Text(
                text = "Welcome, $name! /n/n Нажми на фиолетовый круг и прижми телефон микрофоном к соответствующей области на теле. Удерживай телефон плотно прижав его к телу и не шевеля, пока не услышишь продолжительный сигнал. Двойной сигнал означает, что запись не удалась, повтори процедуру",
                modifier = Modifier.padding(top = 32.dp) // Top padding for aesthetic spacing
            )

            // Button positioned at the bottom
            Button(
                onClick = {
                    val intent = Intent(context, TakeRecordsActivity::class.java)
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                modifier = Modifier
                    .padding(16.dp) // Padding around the button for aesthetic spacing
                    .align(Alignment.CenterHorizontally) // Ensure the button is centered horizontally within the Column
            ) {
                Text(text = "ПОНЯТНО, НАЧИНАЕМ")
            }
        }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    HelloWorldAppTheme {
        Greeting("Android")
    }
}