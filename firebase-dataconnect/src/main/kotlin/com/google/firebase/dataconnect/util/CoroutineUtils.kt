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

import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

internal object CoroutineUtils {

  /**
   * Creates and returns a new [CoroutineScope] appropriate for a private member of a class that
   * needs a scope to perform work and will cancel the scope upon its "close" method being invoked.
   *
   * The returned scope has the following properties:
   * 1. Its job is a [SupervisorJob], with the given [parent] (if specified).
   * 2. Its dispatcher is the given [CoroutineDispatcher].
   * 3. Its [CoroutineName] is the [Logger.nameWithId] of the given [Logger], or the given [coroutineName].
   * 4. Its [CoroutineExceptionHandler] logs a warning message rather than crashing the scope.
   */
  fun createSupervisorCoroutineScope(
    dispatcher: CoroutineDispatcher,
    logger: Logger,
    parent: Job? = null,
    coroutineName: String = logger.nameWithId,
  ): CoroutineScope =
    CoroutineScope(
      SupervisorJob(parent) +
        CoroutineName(coroutineName) +
        dispatcher +
        CoroutineExceptionHandler { context, throwable ->
          logger.warn(throwable) {
            "uncaught exception from a coroutine named ${context[CoroutineName]?.name}"
          }
        }
    )
}
