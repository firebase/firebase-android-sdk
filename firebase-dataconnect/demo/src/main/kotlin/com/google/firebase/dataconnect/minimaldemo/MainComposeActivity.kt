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
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)

package com.google.firebase.dataconnect.minimaldemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.google.firebase.dataconnect.minimaldemo.InMemoryNotesDatabase.Note
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class MainComposeActivity : ComponentActivity() {

  private val notesDb = InMemoryNotesDatabase()
  private val notes = mutableStateListOf<Note>()

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    notes.addAll(notesDb.getAll())

    setContent {
      MaterialTheme {
        Scaffold(
          topBar = { TopAppBar(title = { Text("After Hours Data Connect") }) },
          modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
          NoteEditor(
            notes,
            Modifier.padding(innerPadding).fillMaxSize(),
            onCreateClick = ::onCreateClick,
            onUpdateClick = ::onUpdateClick,
            onDeleteClick = ::onDeleteClick,
          )
        }
      }
    }
  }

  private fun onCreateClick(info: CreateInfo) {
    notesDb.createNote(
      title = info.title,
      body = info.body,
      createdAt = Clock.System.now().toString(),
      isFavorite = info.isFavorite,
    )
    notes.clear()
    notes.addAll(notesDb.getAll())
  }

  private fun onUpdateClick(noteId: UUID, info: CreateInfo) {
    notesDb.updateNote(noteId, title = info.title, body = info.body, isFavorite = info.isFavorite)
    notes.clear()
    notes.addAll(notesDb.getAll())
  }

  private fun onDeleteClick(noteId: UUID) {
    notesDb.deleteNote(noteId)
    notes.clear()
    notes.addAll(notesDb.getAll())
  }
}

data class CreateInfo(val title: String, val body: String?, val isFavorite: Boolean)

@Composable
fun NoteEditor(
  notes: List<Note>,
  modifier: Modifier = Modifier,
  onCreateClick: (CreateInfo) -> Unit,
  onUpdateClick: (UUID, CreateInfo) -> Unit,
  onDeleteClick: (UUID) -> Unit,
) {
  var noteId: UUID? by remember { mutableStateOf(null) }
  var title by remember { mutableStateOf("") }
  var body by remember { mutableStateOf<String?>(null) }
  var createdAt by remember { mutableStateOf("") }
  var isFavorite by remember { mutableStateOf(false) }
  var showMenu by remember { mutableStateOf(false) }
  var selectedNoteId by remember { mutableStateOf<UUID?>(null) }

  Column(modifier = modifier.padding(16.dp)) {
    TextField(
      value = title,
      onValueChange = { title = it },
      label = { Text("Title") },
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(16.dp))
    TextField(
      value = body ?: "",
      onValueChange = { body = it },
      label = { Text("Body") },
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = "Created At: $createdAt",
      modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Checkbox(checked = isFavorite, onCheckedChange = { isFavorite = it })
      Text("Favorite")
    }
    Row(modifier = Modifier.fillMaxWidth()) {
      val displayedNoteId = noteId
      if (displayedNoteId === null) {
        Button(
          onClick = {
            val cleanTitle = title.trim()
            if (cleanTitle.isEmpty()) {
              return@Button
            }
            onCreateClick(
              CreateInfo(title = cleanTitle, body = body?.trimEnd(), isFavorite = isFavorite)
            )
            title = ""
            body = null
            createdAt = ""
            isFavorite = false
          },
          modifier = Modifier.weight(1f),
        ) {
          Text("Create Note")
        }
      } else {
        Button(
          onClick = {
            val cleanTitle = title.trim()
            if (cleanTitle.isEmpty()) {
              return@Button
            }
            onUpdateClick(
              displayedNoteId,
              CreateInfo(title = cleanTitle, body = body?.trimEnd(), isFavorite = isFavorite),
            )
            noteId = null
            title = ""
            body = null
            createdAt = ""
            isFavorite = false
          },
          modifier = Modifier.weight(1f),
        ) {
          Text("Save Note")
        }
      }
      Spacer(modifier = Modifier.width(8.dp))
      Button(
        onClick = {
          noteId = null
          title = ""
          body = null
          createdAt = ""
          isFavorite = false
        },
        modifier = Modifier.weight(1f),
      ) {
        Text("Clear")
      }
    }
    Spacer(modifier = Modifier.height(16.dp))
    LazyColumn {
      items(notes) { note ->
        Box {
          Text(
            text = note.title,
            style = MaterialTheme.typography.titleMedium,
            modifier =
              Modifier.fillMaxWidth().padding(vertical = 8.dp).pointerInput(Unit) {
                detectTapGestures(
                  onTap = {
                    noteId = note.id
                    title = note.title
                    body = note.body
                    createdAt = note.createdAt
                    isFavorite = note.isFavorite
                  },
                  onLongPress = {
                    selectedNoteId = note.id
                    showMenu = true
                  },
                )
              },
          )
          DropdownMenu(
            expanded = showMenu && selectedNoteId == note.id,
            onDismissRequest = { showMenu = false },
          ) {
            DropdownMenuItem(
              text = { Text("Delete") },
              onClick = {
                showMenu = false
                onDeleteClick(note.id)
                if (noteId == note.id) {
                  noteId = null
                  title = ""
                  body = null
                  createdAt = ""
                  isFavorite = false
                }
              },
            )
          }
        }
      }
    }
  }
}
