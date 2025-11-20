/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.google.firebase.dataconnect.minimaldemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainComposeActivity : ComponentActivity() {

  private val notesDb = InMemoryNotesDatabase()

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        Scaffold(
          topBar = { TopAppBar(title = { Text("After Hours Data Connect") }) },
          modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
          NoteEditor(notesDb, Modifier.padding(innerPadding).fillMaxSize())
        }
      }
    }
  }
}

@Composable
fun NoteEditor(notesDb: InMemoryNotesDatabase, modifier: Modifier = Modifier) {
  var title by remember { mutableStateOf("") }
  var body by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf(notesDb.getAll()) }

  Column(modifier = modifier.padding(16.dp)) {
    TextField(
      value = title,
      onValueChange = { title = it },
      label = { Text("Title") },
      modifier = Modifier.fillMaxWidth(),
    )
    TextField(
      value = body,
      onValueChange = { body = it },
      label = { Text("Body") },
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
      Button(
        onClick = {
          val cleanTitle = title.trim()
          if (!cleanTitle.isEmpty()) {
            val cleanBody = body.trimEnd()
            notesDb.createNote(title = cleanTitle, body = cleanBody)
            notes = notesDb.getAll()
            title = ""
            body = ""
          }
        },
        modifier = Modifier.weight(1f),
      ) {
        Text("Create Note")
      }
      Spacer(modifier = Modifier.width(8.dp))
      Button(
        onClick = {
          title = ""
          body = ""
        },
        modifier = Modifier.weight(1f),
      ) {
        Text("Clear")
      }
    }
    Spacer(modifier = Modifier.height(16.dp))
    LazyColumn { items(notes) { note -> Text(note.title) } }
  }
}
