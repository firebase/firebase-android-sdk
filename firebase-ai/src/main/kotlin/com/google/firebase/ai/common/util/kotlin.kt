/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.ai.common.util

import java.io.ByteArrayOutputStream
import java.lang.reflect.Field
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.fold

/**
 * Removes the last character from the [StringBuilder].
 *
 * If the StringBuilder is empty, calling this function will throw an [IndexOutOfBoundsException].
 *
 * @return The [StringBuilder] used to make the call, for optional chaining.
 * @throws IndexOutOfBoundsException if the StringBuilder is empty.
 */
internal fun StringBuilder.removeLast(): StringBuilder =
  if (isEmpty()) throw IndexOutOfBoundsException("StringBuilder is empty.")
  else deleteCharAt(length - 1)

/**
 * A variant of [getAnnotation][Field.getAnnotation] that provides implicit Kotlin support.
 *
 * Syntax sugar for:
 * ```
 * getAnnotation(T::class.java)
 * ```
 */
internal inline fun <reified T : Annotation> Field.getAnnotation() = getAnnotation(T::class.java)

/**
 * Collects bytes from this flow and doesn't emit them back until [minSize] is reached.
 *
 * For example:
 * ```
 * val byteArr = flowOf(byteArrayOf(1), byteArrayOf(2, 3, 4), byteArrayOf(5, 6, 7, 8))
 * val expectedResult = listOf(byteArrayOf(1, 2, 3, 4), byteArrayOf( 5, 6, 7, 8))
 *
 * byteArr.accumulateUntil(4).toList() shouldContainExactly expectedResult
 * ```
 *
 * @param minSize The minimum about of bytes the array should have before being sent down-stream
 * @param emitLeftOvers If the flow completes and there are bytes left over that don't meet the
 * [minSize], send them anyways.
 */
internal fun Flow<ByteArray>.accumulateUntil(
  minSize: Int,
  emitLeftOvers: Boolean = false
): Flow<ByteArray> = flow {
  val remaining =
    fold(ByteArrayOutputStream()) { buffer, it ->
      buffer.apply {
        write(it, 0, it.size)
        if (size() >= minSize) {
          emit(toByteArray())
          reset()
        }
      }
    }

  if (emitLeftOvers && remaining.size() > 0) {
    emit(remaining.toByteArray())
  }
}

/**
 * Create a [Job] that is a child of the [currentCoroutineContext], if any.
 *
 * This is useful when you want a coroutine scope to be canceled when its parent scope is canceled,
 * and you don't have full control over the parent scope, but you don't want the cancellation of the
 * child to impact the parent.
 *
 * If the parent coroutine context does not have a job, an empty one will be created.
 */
internal suspend inline fun childJob() = Job(currentCoroutineContext()[Job] ?: Job())

/**
 * A constant value pointing to a cancelled [CoroutineScope].
 *
 * Useful when you want to initialize a mutable [CoroutineScope] in a canceled state.
 */
internal val CancelledCoroutineScope = CoroutineScope(EmptyCoroutineContext).apply { cancel() }
