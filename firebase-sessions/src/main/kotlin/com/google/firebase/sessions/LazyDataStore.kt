/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.sessions

import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * A [DataStore] that calls `createDelegate` to create the actual [DataStore] the first time it's
 * read or written, instead of when it's constructed.
 *
 * The Sessions SDK constructs its DataStores on the main thread while Firebase initializes, and
 * creating them can be slow on some devices: it loads the `datastore_shared_counter` native library
 * and many classes. The SDK reads and writes its DataStores in coroutines, normally on background
 * threads, so deferring creation moves that work off the main thread.
 *
 * The delegate is created at most once, even if it's first used from multiple threads at the same
 * time. If `createDelegate` throws, the read or write that called it throws, so [data] collectors
 * can handle it with `catch`, and the next read or write calls it again.
 *
 * See https://github.com/firebase/firebase-android-sdk/issues/8615
 */
internal class LazyDataStore<T>(createDelegate: () -> DataStore<T>) : DataStore<T> {
  // Synchronized so there's never more than one DataStore for the same file in a process.
  private val delegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED, createDelegate)

  override val data: Flow<T> = flow { emitAll(delegate.value.data) }

  override suspend fun updateData(transform: suspend (t: T) -> T): T =
    delegate.value.updateData(transform)

  /** Returns true if the delegate [DataStore] has been created. */
  @VisibleForTesting internal fun isInitialized(): Boolean = delegate.isInitialized()
}
