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

package com.google.firebase.dataconnect.minimaldemo

import java.util.UUID

class InMemoryNotesDatabase {

  data class Note(val id: UUID, val title: String, val body: String, val createdAt: String)

  private val notes = mutableListOf<Note>()

  fun createNote(title: String, body: String, createdAt: String) {
    notes.add(Note(id = UUID.randomUUID(), title = title, body = body, createdAt = createdAt))
  }

  fun updateNote(id: UUID, title: String, body: String) {
    val index = notes.indexOfFirst { it.id == id }
    if (index >= 0) {
      notes[index] = notes[index].copy(title = title, body = body)
    }
  }

  fun deleteNote(id: UUID) {
    val index = notes.indexOfFirst { it.id == id }
    if (index >= 0) {
      notes.removeAt(index)
    }
  }

  fun getAll(): List<Note> = notes.toList()
}
