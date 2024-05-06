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

import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.debug
import com.google.firebase.dataconnect.util.ReferenceCountedSet

internal class TypedActiveQueries(val activeQuery: ActiveQuery, parentLogger: Logger) :
  ReferenceCountedSet<TypedActiveQueryKey<*>, TypedActiveQuery<*>>() {

  private val logger =
    Logger("TypedActiveQueries").apply { debug { "Created by ${parentLogger.nameWithId}" } }

  override fun valueForKey(key: TypedActiveQueryKey<*>) =
    TypedActiveQuery(
      activeQuery = activeQuery,
      dataDeserializer = key.dataDeserializer,
      logger = logger,
    )

  override fun onAllocate(entry: Entry<TypedActiveQueryKey<*>, TypedActiveQuery<*>>) {
    logger.debug(
      "Allocated ${entry.value.logger.nameWithId} (dataDeserializer=${entry.key.dataDeserializer})"
    )
  }

  override fun onFree(entry: Entry<TypedActiveQueryKey<*>, TypedActiveQuery<*>>) {
    logger.debug("Deallocated ${entry.value.logger.nameWithId}")
  }
}
