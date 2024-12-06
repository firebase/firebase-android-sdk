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

package com.google.firebase.vertexai.internal.util

import java.lang.reflect.Field

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
