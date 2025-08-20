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

package com.google.firebase.datastorage

import android.content.Context
import android.os.Process
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.annotations.concurrent.Background
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

/**
 * Wrapper around [DataStore] for easier migration from `SharedPreferences` in Java code.
 *
 * Automatically migrates data from any `SharedPreferences` that share the same context and name.
 *
 * There should only ever be _one_ instance of this class per context and name variant.
 *
 * Note that most of the methods in this class **block** on the _current_ thread, as to help keep
 * parity with existing Java code. Typically, you'd want to dispatch this work to another thread
 * like [@Background][Background].
 *
 * > Do **NOT** use this _unless_ you're bridging Java code. If you're writing new code, or your
 * code is in Kotlin, then you should create your own singleton that uses [DataStore] directly.
 *
 * Example:
 * ```java
 * JavaDataStorage heartBeatStorage = new JavaDataStorage(applicationContext, "FirebaseHeartBeat");
 * ```
 *
 * @property context The [Context] that this data will be saved under.
 * @property name What the storage file should be named.
 *
 * @hide
 */
class JavaDataStorage(val context: Context, val name: String) {
  /**
   * Used to ensure that there's only ever one call to [editSync] per thread; as to avoid deadlocks.
   */
  private val editLock = ThreadLocal<Boolean>()

  private val Context.dataStore: DataStore<Preferences> by
    preferencesDataStore(
      name = name,
      produceMigrations = { listOf(SharedPreferencesMigration(it, name)) },
      corruptionHandler =
        ReplaceFileCorruptionHandler { ex ->
          Log.w(
            JavaDataStorage::class.simpleName,
            "CorruptionException in ${name} DataStore running in process ${Process.myPid()}"
          )
          emptyPreferences()
        }
    )

  private val dataStore = context.dataStore

  /**
   * Get data from the datastore _synchronously_.
   *
   * Note that if the key is _not_ in the datastore, while the [defaultValue] will be returned
   * instead- it will **not** be saved to the datastore; you'll have to manually do that.
   *
   * Blocks on the currently running thread.
   *
   * Example:
   * ```java
   * Preferences.Key<Long> fireCountKey = PreferencesKeys.longKey("fire-count");
   * assert dataStore.get(fireCountKey, 0L) == 0L;
   *
   * dataStore.putSync(fireCountKey, 102L);
   * assert dataStore.get(fireCountKey, 0L) == 102L;
   * ```
   *
   * @param key The typed key of the entry to get data for.
   * @param defaultValue A value to default to, if the key isn't found.
   *
   * @see Preferences.getOrDefault
   */
  fun <T> getSync(key: Preferences.Key<T>, defaultValue: T): T = runBlocking {
    dataStore.data.firstOrNull()?.get(key) ?: defaultValue
  }

  /**
   * Checks if a key is present in the datastore _synchronously_.
   *
   * Blocks on the currently running thread.
   *
   * Example:
   * ```java
   * Preferences.Key<Long> fireCountKey = PreferencesKeys.longKey("fire-count");
   * assert !dataStore.contains(fireCountKey);
   *
   * dataStore.putSync(fireCountKey, 102L);
   * assert dataStore.contains(fireCountKey);
   * ```
   *
   * @param key The typed key of the entry to find.
   */
  fun <T> contains(key: Preferences.Key<T>): Boolean = runBlocking {
    dataStore.data.firstOrNull()?.contains(key) ?: false
  }

  /**
   * Sets and saves data in the datastore _synchronously_.
   *
   * Existing values will be overwritten.
   *
   * Blocks on the currently running thread.
   *
   * Example:
   * ```java
   * dataStore.putSync(PreferencesKeys.longKey("fire-count"), 102L);
   * ```
   *
   * @param key The typed key of the entry to save the data under.
   * @param value The data to save.
   *
   * @return The [Preferences] object that the data was saved under.
   */
  fun <T> putSync(key: Preferences.Key<T>, value: T): Preferences = runBlocking {
    dataStore.edit { it[key] = value }
  }

  /**
   * Gets all data in the datastore _synchronously_.
   *
   * Blocks on the currently running thread.
   *
   * Example:
   * ```java
   * ArrayList<String> allDates = new ArrayList<>();
   *
   * for (Map.Entry<Preferences.Key<?>, Object> entry : dataStore.getAllSync().entrySet()) {
   *   if (entry.getValue() instanceof Set) {
   *     Set<String> dates = new HashSet<>((Set<String>) entry.getValue());
   *     if (!dates.isEmpty()) {
   *       allDates.add(new ArrayList<>(dates));
   *     }
   *   }
   * }
   * ```
   *
   * @return An _immutable_ map of data currently present in the datastore.
   */
  fun getAllSync(): Map<Preferences.Key<*>, Any> = runBlocking {
    dataStore.data.firstOrNull()?.asMap() ?: emptyMap()
  }

  /**
   * Transactionally edit data in the datastore _synchronously_.
   *
   * Edits made within the [transform] callback will be saved (committed) all at once once the
   * [transform] block exits.
   *
   * Because of the blocking nature of this function, you should _never_ call [editSync] within an
   * already running [transform] block. Since this can cause a deadlock, [editSync] will instead
   * throw an exception if it's caught.
   *
   * Blocks on the currently running thread.
   *
   * Example:
   * ```java
   * dataStore.editSync((pref) -> {
   *   Long heartBeatCount = pref.get(HEART_BEAT_COUNT_TAG);
   *   if (heartBeatCount == null || heartBeatCount > 30) {
   *     heartBeatCount = 0L;
   *   }
   *   pref.set(HEART_BEAT_COUNT_TAG, heartBeatCount);
   *   pref.set(LAST_STORED_DATE, "1970-0-1");
   *
   *   return null;
   * });
   * ```
   *
   * @param transform A callback to invoke with the [MutablePreferences] object.
   *
   * @return The [Preferences] object that the data was saved under.
   * @throws IllegalStateException If you attempt to call [editSync] within another [transform]
   * block.
   *
   * @see Preferences.getOrDefault
   */
  fun editSync(transform: (MutablePreferences) -> Unit): Preferences = runBlocking {
    if (editLock.get() == true) {
      throw IllegalStateException(
        """
        Don't call JavaDataStorage.edit() from within an existing edit() callback.
        This causes deadlocks, and is generally indicative of a code smell.
        Instead, either pass around the initial `MutablePreferences` instance, or don't do everything in a single callback. 
      """
          .trimIndent()
      )
    }
    editLock.set(true)
    try {
      dataStore.edit { transform(it) }
    } finally {
      editLock.set(false)
    }
  }
}

/**
 * Helper method for getting the value out of a [Preferences] object if it exists, else falling back
 * to the default value.
 *
 * This is primarily useful when working with an instance of [MutablePreferences]
 * - like when working within an [JavaDataStorage.editSync] callback.
 *
 * Example:
 * ```java
 * dataStore.editSync((pref) -> {
 *  long heartBeatCount = DataStoreKt.getOrDefault(pref, HEART_BEAT_COUNT_TAG, 0L);
 *  heartBeatCount+=1;
 *  pref.set(HEART_BEAT_COUNT_TAG, heartBeatCount);
 *
 *  return null;
 * });
 * ```
 *
 * @param key The typed key of the entry to get data for.
 * @param defaultValue A value to default to, if the key isn't found.
 */
fun <T> Preferences.getOrDefault(key: Preferences.Key<T>, defaultValue: T) =
  get(key) ?: defaultValue
