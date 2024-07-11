/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.vertexai.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

/**
 * Runs the given [block] using [runBlocking] on the current thread for side effect.
 *
 * Using this function is like [runBlocking] with default context (which runs the given block on the
 * calling thread) but forces the return type to be `Unit`, which is helpful when implementing
 * suspending tests as expression functions:
 * ```
 * @Test
 * fun myTest() = doBlocking {...}
 * ```
 */
internal fun doBlocking(block: suspend CoroutineScope.() -> Unit) {
  runBlocking(block = block)
}
