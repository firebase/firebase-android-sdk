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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.google.firebase.testing.integ

import androidx.annotation.RestrictTo
import com.google.firebase.concurrent.TestOnlyExecutors
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.TestScope

/**
 * Container for type-safe access to the [CoroutineContext] of [TestOnlyExecutors].
 *
 * @property ui the instance provided by [TestOnlyExecutors.ui]
 * @property blocking the instance provided by [TestOnlyExecutors.blocking]
 * @property background the instance provided by [TestOnlyExecutors.background]
 * @property lite the instance provided by [TestOnlyExecutors.lite]
 * @see TestScope.firebaseExecutors
 */
data class FirebaseTestExecutorsContainer(
  val ui: CoroutineContext,
  val blocking: CoroutineContext,
  val background: CoroutineContext,
  val lite: CoroutineContext
)

/**
 * Provides a [CoroutineContext] of a given [TestOnlyExecutors] merged with this [TestScope].
 *
 * Your standard [TestOnlyExecutors] does not support special mechanisms that other [TestScope] may
 * provide (such as fast forwarding). To fix this, you must wrap the [CoroutineContext] of a given
 * [TestOnlyExecutors] with the inherited one in a [TestScope]. This property facilitates that
 * automatically.
 *
 * Now, you can utilize [TestOnlyExecutors] AND special methods provided to [TestScope].
 *
 * Example usage:
 * ```
 * @Test
 * fun doesStuff() = runTest {
 *   val scope = CoroutineScope(firebaseExecutors.background)
 *   scope.launch {
 *    // ... does stuff
 *   }
 *
 *   runCurrent()
 * }
 * ```
 */
@get:RestrictTo(RestrictTo.Scope.TESTS)
@get:Suppress("RestrictedApi")
val TestScope.firebaseExecutors: FirebaseTestExecutorsContainer
  get() =
    FirebaseTestExecutorsContainer(
      TestOnlyExecutors.ui().asCoroutineDispatcher() + coroutineContext,
      TestOnlyExecutors.blocking().asCoroutineDispatcher() + coroutineContext,
      TestOnlyExecutors.background().asCoroutineDispatcher() + coroutineContext,
      TestOnlyExecutors.lite().asCoroutineDispatcher() + coroutineContext
    )
