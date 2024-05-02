package com.firebase.io2024.whoami

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.firebase.io2024.whoami.ui.theme.WhoAmITheme
import com.google.firebase.Firebase
import com.google.firebase.vertexai.type.generationConfig
import com.google.firebase.vertexai.vertexAI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject


class MainActivity : ComponentActivity() {
  private val deviceInfo = MutableStateFlow("Device details information here...!!")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      WhoAmITheme {
        val info by deviceInfo.collectAsState()
        // A surface container using the 'background' color from the theme
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
          Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(32.dp)) // Add space between button and text
            Button(onClick = {
              promptAi { newInfo ->
                deviceInfo.value = newInfo.toString()
              }
            }) {
              Text("Current Device", modifier = Modifier.width(IntrinsicSize.Max), textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(32.dp)) // Add space between button and text
            Text(text = info, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f).padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(32.dp)) // Add space between button and text
            Button(onClick = { parseContent() }) {
              Text("Parse Content", modifier = Modifier.width(IntrinsicSize.Max), textAlign = TextAlign.Center)
            }
          }
        }
      }
    }
  }

  private fun parseContent() {
    var jsonText = deviceInfo.value
    if (jsonText.startsWith("```json") && jsonText.endsWith("```")) {
      // The json is wrapped in a code markup block
      jsonText = jsonText.substring(7, jsonText.length - 3).trim()
    }
    val jsonObject = JSONObject(jsonText)
    deviceInfo.value = jsonObject.toString(2)
  }

  private fun promptAi(callback: (Any?) -> Unit) =
    lifecycleScope.launch {
      val generativeModel =
        Firebase.vertexAI.generativeModel(
          modelName = "gemini-1.0-pro",
          generationConfig = generationConfig { temperature = 0.7f },
        )

      deviceInfo.value = "Fetching device details"
      val manufacturer = Build.MANUFACTURER
      val deviceName = Build.MODEL// Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
      val deviceDetailsPrompt = "Give me details about the phone ${manufacturer} ${deviceName} in JSON format without adding any extra details outside of json. Include information about the features that are available on the device including camera, bluetooth, size, weight and battery. Respond as just a plain json string without any markups."
      val result = generativeModel.generateContent(deviceDetailsPrompt)

      Log.i("TAG", "find me Prompt: ${deviceDetailsPrompt}")
      Log.i("TAG", "find me Response: ${result.text}")
      callback(result.text.toString())
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
