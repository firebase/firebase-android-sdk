/*
 * Copyright 2026 Google LLC
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
package com.google.firebase.dataconnect.testutil

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.rules.ExternalResource

/** A JUnit test rule that creates an sqlite database. */
class SQLiteDatabaseRule(private val location: Location) : ExternalResource() {

  private val state = AtomicReference<State>(State.New)

  val db: SQLiteDatabase
    get() {
      while (true) {
        val currentState: State.New =
          when (val currentState = state.get()) {
            State.Closed -> throw IllegalStateException("after() has been called")
            is State.Opened -> return currentState.db
            is State.New -> currentState
          }

        val db =
          when (location) {
            Location.InMemory -> SQLiteDatabase.create(null)
            is Location.TemporaryFolder -> {
              val dbFile = File(location.temporaryFolder.newFolder(), "db.sqlite")
              SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            }
          }

        if (state.compareAndSet(currentState, State.Opened(db))) {
          return db
        }

        // Close the database and try again, because another thread must have intervened.
        db.close()
      }
    }

  override fun before() {}

  override fun after() {
    while (true) {
      val currentState = state.get()
      when (currentState) {
        is State.New -> Unit
        is State.Opened -> currentState.db.close()
        State.Closed -> return
      }
      state.compareAndSet(currentState, State.Closed)
    }
  }

  sealed interface Location {
    data object InMemory : Location
    data class TemporaryFolder(val temporaryFolder: org.junit.rules.TemporaryFolder) : Location
  }

  private sealed interface State {
    object New : State
    data class Opened(val db: SQLiteDatabase) : State
    object Closed : State
  }

  companion object {

    fun inMemory() = SQLiteDatabaseRule(Location.InMemory)

    fun inDirectory(temporaryFolder: org.junit.rules.TemporaryFolder) =
      SQLiteDatabaseRule(Location.TemporaryFolder(temporaryFolder))
  }
}
