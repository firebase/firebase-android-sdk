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

package com.google.firebase.dataconnect.testutil.property.arbitrary

import io.kotest.common.ExperimentalKotest
import io.kotest.property.PropTestConfig

fun PropTestConfig.withIterations(iterations: Int): PropTestConfig {
  @OptIn(ExperimentalKotest::class) return copy(iterations = iterations)
}

fun PropTestConfig.withIterationsIfNotNull(iterations: Int?): PropTestConfig =
  if (iterations === null) this else withIterations(iterations)

fun PropTestConfig.withSeed(seed: Long): PropTestConfig {
  @OptIn(ExperimentalKotest::class) return copy(seed = seed)
}
