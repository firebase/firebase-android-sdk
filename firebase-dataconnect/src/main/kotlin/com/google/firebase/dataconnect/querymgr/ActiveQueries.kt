/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.dataconnect.querymgr

import com.google.firebase.dataconnect.core.FirebaseDataConnectImpl
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.debug
import com.google.firebase.dataconnect.util.ReferenceCountedSet
import com.google.firebase.dataconnect.util.toCompactString

internal class ActiveQueries(val dataConnect: FirebaseDataConnectImpl, parentLogger: Logger) :
  ReferenceCountedSet<ActiveQueryKey, ActiveQuery>() {

  private val logger =
    Logger("ActiveQueries").apply { debug { "Created by ${parentLogger.nameWithId}" } }

  override fun valueForKey(key: ActiveQueryKey) =
    ActiveQuery(
      dataConnect = dataConnect,
      operationName = key.operationName,
      variables = key.variables,
      parentLogger = logger,
    )

  override fun onAllocate(entry: Entry<ActiveQueryKey, ActiveQuery>) {
    logger.debug(
      "Registered ${entry.value.logger.nameWithId} (" +
        "operationName=${entry.key.operationName}, " +
        "variables=${entry.key.variables.toCompactString()})"
    )
  }

  override fun onFree(entry: Entry<ActiveQueryKey, ActiveQuery>) {
    logger.debug("Unregistered ${entry.value.logger.nameWithId}")
  }
}
