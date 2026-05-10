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
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

internal object CoroutineUtils {

  /**
   * Creates and returns a new [CoroutineScope] with a [SupervisorJob], [CoroutineName], and
   * [CoroutineExceptionHandler].
   *
   * The [CoroutineContext] of the returned [CoroutineScope] will be the given [context], with some
   * elements unconditionally replaced:
   *
   * * The [Job] element will be a newly-created [SupervisorJob] with the given [parent]; notably,
   * if the given [parent] is null then the parent of the [SupervisorJob] will _also_ be null.
   * * The [CoroutineName] element will be a newly-created instance whose value is the string given
   * for the [coroutineName] argument, or the value returned from [Logger.nameWithId] if the given
   * [coroutineName] is null.
   * * The [CoroutineExceptionHandler] will be a newly-created instance that simply logs a warning
   * message to the given [Logger] and then drops the exception. This prevents any crashing
   * coroutines in the returned scope from propagating outside the scope.
   */
  fun createSupervisorCoroutineScope(
    context: CoroutineContext = EmptyCoroutineContext,
    logger: Logger,
    parent: Job? = null,
    coroutineName: String? = null
  ): CoroutineScope =
    CoroutineScope(
      context +
        SupervisorJob(parent) +
        CoroutineName(coroutineName ?: logger.nameWithId) +
        CoroutineExceptionHandler { exceptionContext, throwable ->
          logger.warn(throwable) {
            "uncaught exception from a coroutine named ${exceptionContext[CoroutineName]?.name}"
          }
        }
    )
}
