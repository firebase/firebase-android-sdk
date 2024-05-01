package com.firebase.io2024.whoami

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.firebase.io2024.whoami.ui.theme.WhoAmITheme
import com.google.firebase.Firebase
import com.google.firebase.vertexai.type.generationConfig
import com.google.firebase.vertexai.vertexAI
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      WhoAmITheme {
        // A surface container using the 'background' color from the theme
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          Button(
            onClick = {
              promptAi("")
            }
          ) {
            Text(text = "Click me to do AI", modifier = Modifier.fillMaxSize())
          }
        }
      }
    }
  }

  fun promptAi(query: String) =
    lifecycleScope.launch {
      val generativeModel =
        Firebase.vertexAI.generativeModel(
          modelName = "gemini-1.0-pro",
          generationConfig = generationConfig { temperature = 0.7f },
        )


      val manufacturer = Build.MANUFACTURER
      val model = Build.MODEL
       val result = generativeModel.generateContent("Tell me about the phone: ${manufacturer} ${model} parsable in json")

      Log.i("TAG", "find me: ${result.text}")
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Button(
    onClick = {
      val generativeModel =
        Firebase.vertexAI.generativeModel(
          modelName = "gemini-1.0-pro",
          generationConfig = generationConfig { temperature = 0.7f },
        )

      // val result = generativeModel.generateContent("who am i?")

      // Log.i("TAG", "find me:")
    }
  ) {
    Text(text = "Hello $name!", modifier = modifier)
  }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  WhoAmITheme { Greeting("Android") }
}
