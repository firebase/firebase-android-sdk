package com.google.firebase.dataconnect.demo

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
    setContent {
      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          MessageCard(Message(author = "Test Author", body = "This is the body"))
        }
      }
    }
  }
}

data class Message(val author: String, val body: String)

@Composable
fun MessageCard(message: Message) {
  Row {
    Image(
      painter = painterResource(id = R.drawable.profile_picture),
      contentDescription = "Profile picture",
      modifier = Modifier
        .size(40.dp)
        .clip(CircleShape)
        .border(1.5.dp, MaterialTheme.colorScheme.primary)
    )
    Spacer(modifier = Modifier.width(8.dp))
    Column {
      Text(text = message.author, color = MaterialTheme.colorScheme.secondary, style=MaterialTheme.typography.titleSmall)
      Spacer(modifier = Modifier.height(4.dp))
      Surface(shape = MaterialTheme.shapes.medium, shadowElevation = 1.dp) {
        Text(text = message.body, style=MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(all=4.dp))
      }
    }
  }
}

@Preview(name = "Light Mode")
@Preview(
  uiMode = Configuration.UI_MODE_NIGHT_YES,
  showBackground = true,
  name = "Dark Mode"
)
@Composable
fun PreviewMessageCard() {
  MaterialTheme {
    Surface {
      MessageCard(Message(author = "Test Author", body = "This is the body"))
    }
  }
}

@Composable
fun Conversation(messages: List<Message>) {
  LazyColumn {
    items(messages) {
      MessageCard(it)
    }
  }
}

@Preview
@Composable
fun PreviewConversation() {
  MaterialTheme {
    Conversation(sampleMessages())
  }
}

fun sampleMessages() = listOf(
  Message("Person1", "Hey, what's up?"),
  Message("Person2", "Oh not much. How about you?"),
  Message("Person1", "What Android version names do you know?"),
  Message("Person2", "Oh man, let's see... uh, Cupcake, Donut, Eclair, " + "Froyo, Gingerbread, KitKat, Lollipop, Marshmallow, Nougat."),
);