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

package com.google.firebase.dataconnect.testutil.property.arbitrary

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import kotlin.random.Random

/** Generates [Random] instances with seeds from the given [Arb]. */
fun Arb.Companion.random(seed: Arb<Long> = Arb.long()): Arb<Random> =
  randomSource(seed).map { it.random }

/** Generates [RandomSource] instances with seeds from the given [Arb]. */
fun Arb.Companion.randomSource(seed: Arb<Long> = Arb.long()): Arb<RandomSource> =
  arbitrary(edgecases = emptyList()) { RandomSource.seeded(seed.bind()) }
