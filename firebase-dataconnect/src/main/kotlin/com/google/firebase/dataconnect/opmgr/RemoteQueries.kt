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

package com.google.firebase.dataconnect.opmgr

import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.SuspendingWeakValueHashMap
import kotlinx.coroutines.CoroutineDispatcher

internal class RemoteQueries(
  cpuDispatcher: CoroutineDispatcher,
  private val logger: Logger,
) : AutoCloseable {

  private val map = SuspendingWeakValueHashMap<Key, Unit>(cpuDispatcher)

  override fun close() {
    logger.debug { "close() called" }
    map.close()
  }

  data class Key(
    val authUid: String?,
    val queryId: ImmutableByteArray,
  )
}
