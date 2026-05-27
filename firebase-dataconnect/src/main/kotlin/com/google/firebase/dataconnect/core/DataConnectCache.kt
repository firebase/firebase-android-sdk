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

package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.core.LoggerGlobals.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase
import com.google.firebase.dataconnect.util.ObjectLifecycleManager
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher

internal class DataConnectCache(
  private val dbFile: File?,
  val maxAge: kotlin.time.Duration,
  private val cpuDispatcher: CoroutineDispatcher,
  private val logger: Logger,
) : ObjectLifecycleManager<DataConnectCacheDatabase>(cpuDispatcher, logger) {

  val maxAgeProto: com.google.protobuf.Duration =
    maxAge.toComponents { seconds, nanos ->
      com.google.protobuf.Duration.newBuilder().setSeconds(seconds).setNanos(nanos).build()
    }

  override fun create() =
    DataConnectCacheDatabase(
      dbFile,
      cpuDispatcher,
      Logger("DataConnectCacheDatabase").apply { debug { "created by ${logger.nameWithId}" } }
    )

  override suspend fun initialize(instance: DataConnectCacheDatabase) {
    instance.initialize()
  }

  override suspend fun destroy(instance: DataConnectCacheDatabase) {
    instance.close()
  }

  override fun toString() = "DataConnectCache(dbFile=$dbFile, maxAge=$maxAge)"
}
