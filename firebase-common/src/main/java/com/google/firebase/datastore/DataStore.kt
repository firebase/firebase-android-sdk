/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

/**
 * Don't use this unless you're bridging Java code Use your own [dataStore] directly in Kotlin code,
 * or if you're writing new code.
 */
class DataStorage(val context: Context, val name: String) {
  private val transforming = ThreadLocal<Boolean>()

  private val Context.dataStore: DataStore<Preferences> by
    preferencesDataStore(
      name = name,
      produceMigrations = { listOf(SharedPreferencesMigration(it, name)) }
    )

  private val dataStore = context.dataStore

  fun <T> getSync(key: Preferences.Key<T>, defaultValue: T): T = runBlocking {
    dataStore.data.firstOrNull()?.get(key) ?: defaultValue
  }

  fun <T> contains(key: Preferences.Key<T>): Boolean = runBlocking {
    dataStore.data.firstOrNull()?.contains(key) ?: false
  }

  fun <T> putSync(key: Preferences.Key<T>, value: T): Preferences = runBlocking {
    dataStore.edit { it[key] = value }
  }

  /** Do not modify the returned map (should be obvious, since it's immutable though) */
  fun getAllSync(): Map<Preferences.Key<*>, Any> = runBlocking {
    dataStore.data.firstOrNull()?.asMap() ?: emptyMap()
  }

  /** Edit calls should not be called within edit calls. Is there a way to prevent this? */
  fun editSync(transform: (MutablePreferences) -> Unit): Preferences = runBlocking {
    if (transforming.get() == true) {
      throw IllegalStateException(
        """
        Don't call DataStorage.edit() from within an existing edit() callback.
        This causes deadlocks, and is generally indicative of a code smell.
        Instead, either pass around the initial `MutablePreferences` instance, or don't do everything in a single callback. 
      """
          .trimIndent()
      )
    }
    transforming.set(true)
    try {
      dataStore.edit { transform(it) }
    } finally {
      transforming.set(false)
    }
  }
}

/** Helper for Java code */
fun <T> Preferences.getOrDefault(key: Preferences.Key<T>, defaultValue: T) =
  get(key) ?: defaultValue
