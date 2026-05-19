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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel

internal object CoroutineUtils {

  /**
   * Creates and returns a new [CoroutineScope] with newly-created [SupervisorJob], [CoroutineName],
   * and [CoroutineExceptionHandler] elements.
   *
   * The [CoroutineContext] of the returned [CoroutineScope] will be the given [context], with the
   * following elements unconditionally replaced:
   *
   * * The [Job] element will be a newly-created [SupervisorJob] with the given [parent]; notably,
   * if the given [parent] is null then the parent of the [SupervisorJob] will _also_ be null.
   * * The [CoroutineName] element will be a newly-created instance whose value is the string given
   * for the [coroutineName] argument, or the value returned from [Logger.nameWithId] if the given
   * [coroutineName] is null.
   * * The [CoroutineExceptionHandler] will be a newly-created instance that simply logs a warning
   * message to the given [Logger] and then drops the exception. This prevents any crashing
   * coroutines in the returned scope from propagating outside the scope.
   *
   * @param context The context elements for the returned [CoroutineScope]; note that the [Job],
   * [CoroutineName], and [CoroutineExceptionHandler] will be discarded and replaced, as documented
   * above.
   * @param logger The logger to use to log uncaught exceptions in the [CoroutineExceptionHandler]
   * element of the [CoroutineContext] of the returned [CoroutineScope]; also used to calculate the
   * [coroutineName] if the given value is null.
   * @param parent The parent for the [SupervisorJob] of the [CoroutineContext] of the returned
   * [CoroutineScope].
   * @param coroutineName The name to specify in the [CoroutineName] element of the
   * [CoroutineContext] of the returned [CoroutineScope]; if null, use the [Logger.nameWithId] value
   * of the given [logger].
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

  /**
   * Creates and returns a new [CoroutineScope] with newly-created [SupervisorJob], [CoroutineName],
   * and [CoroutineExceptionHandler] elements, that is a "child" of the given [parentScope], and
   * uses the [CoroutineContext] of the given [parentScope] as its base context.
   *
   * The [SupervisorJob] of the returned [CoroutineScope] will have its parent set to the [Job] of
   * the [CoroutineContext] of the given [parentScope]. If the parent's [Job] is found to be null
   * then the parent of the [SupervisorJob] of the returned [CoroutineScope] will also be null.
   *
   * The [CoroutineContext] of the returned [CoroutineScope] will be that of the [parentScope] plus
   * the given [childContextOverrides], plus some elements unconditionally replaced as described in
   * [createSupervisorCoroutineScope].
   *
   * This is a convenience wrapper around [createSupervisorCoroutineScope] that automatically
   * extracts the job from the [parentScope] to establish structured concurrency.
   *
   * @param parentScope The parent scope from which to inherit the context and job.
   * @param logger Passed verbatim to [createSupervisorCoroutineScope].
   * @param childContextOverrides Additional context elements to add or override from the parent.
   * @param coroutineName Passed verbatim to [createSupervisorCoroutineScope].
   */
  fun createChildSupervisorCoroutineScope(
    parentScope: CoroutineScope,
    logger: Logger,
    childContextOverrides: CoroutineContext = EmptyCoroutineContext,
    coroutineName: String? = null
  ): CoroutineScope =
    createSupervisorCoroutineScope(
      context = parentScope.coroutineContext + childContextOverrides,
      logger = logger,
      parent = parentScope.coroutineContext[Job],
      coroutineName = coroutineName,
    )

  /**
   * Convenience extension function that simply calls [createChildSupervisorCoroutineScope] with the
   * receiver [CoroutineScope] as the first argument.
   *
   * @receiver Passed to [createChildSupervisorCoroutineScope] as the `parentScope` parameter.
   * @param logger Passed to [createChildSupervisorCoroutineScope] as the `logger` parameter.
   * @param context Passed to [createChildSupervisorCoroutineScope] as the `childContextOverrides`
   * parameter.
   * @param coroutineName Passed to [createChildSupervisorCoroutineScope] as the `coroutineName`
   * parameter.
   */
  fun CoroutineScope.createChildSupervisorScope(
    logger: Logger,
    context: CoroutineContext = EmptyCoroutineContext,
    coroutineName: String? = null
  ): CoroutineScope = createChildSupervisorCoroutineScope(this, logger, context, coroutineName)

  /** Returns a "send-only" wrapper around the receiver [SendChannel]. */
  fun <T> SendChannel<T>.asSendChannel(): SendOnlySendChannel<T> =
    when (this) {
      is SendOnlySendChannel -> this
      else -> SendOnlySendChannel(this)
    }

  /**
   * A "send-only" wrapper around a [SendChannel], preventing it from being cast to another type,
   * specifically [Channel], and, thus, allowing non-send methods to be called on it.
   *
   * The purpose of this wrapper is to prevent a [Channel] instance from being passed to a method
   * that takes [SendChannel] and that [SendChannel] being cast back to [Channel] and having methods
   * not defined in [SendChannel] called on it.
   *
   * This is similar in spirit to the [kotlinx.coroutines.flow.asSharedFlow] and
   * [kotlinx.coroutines.flow.asStateFlow] extension functions that are defined in the official
   * Kotlin coroutines library.
   */
  class SendOnlySendChannel<in T>(delegate: SendChannel<T>) : SendChannel<T> by delegate
}
