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

import com.google.firebase.dataconnect.FirebaseDataConnect
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@OptIn(ExperimentalContracts::class)
suspend inline fun <T : FirebaseDataConnect, R> T.useSuspending(block: suspend (T) -> R): R {
  contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

  var exception: Throwable? = null
  try {
    return block(this)
  } catch (e: Throwable) {
    exception = e
    throw e
  } finally {
    if (exception == null) {
      withContext(NonCancellable) { suspendingClose() }
    } else {
      try {
        withContext(NonCancellable) { suspendingClose() }
      } catch (e2: Throwable) {
        exception.addSuppressed(e2)
      }
    }
  }
}
