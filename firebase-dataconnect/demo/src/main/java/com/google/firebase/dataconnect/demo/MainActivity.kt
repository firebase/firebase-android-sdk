package com.google.firebase.dataconnect.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { MessageCard(Message(author = "Test Author", body = "This is the body")) }
  }
}

data class Message(val author: String, val body: String)

@Composable
fun MessageCard(message: Message) {
  Row {
    Image(
      painter = painterResource(id = R.drawable.profile_picture),
      contentDescription = "Profile picture",
      modifier = Modifier.size(40.dp).clip(CircleShape)
    )
    Spacer(modifier = Modifier.width(8.dp))
    Column {
      Text(text = message.author)
      Spacer(modifier = Modifier.height(4.dp))
      Text(text = message.body)
    }
  }
}

@Preview
@Composable
fun PreviewMessageCard() {
  MessageCard(Message(author = "Test Author", body = "This is the body"))
}
