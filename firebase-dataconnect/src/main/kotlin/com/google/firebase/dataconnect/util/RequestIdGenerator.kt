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

package com.google.firebase.dataconnect.util

import com.google.firebase.util.nextAlphanumericString
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class RequestIdGenerator(
  private val secureRandom: Random,
  private val ioDispatcher: CoroutineDispatcher,
) {

  suspend fun nextQueryRequestId(): String = nextRequestId("qry")

  suspend fun nextMutationRequestId(): String = nextRequestId("mut")

  suspend fun nextStreamId(): String = nextRequestId("str")

  private suspend fun nextRequestId(prefix: String): String =
    withContext(ioDispatcher) { prefix + secureRandom.nextAlphanumericString(length = 10) }
}
