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

import com.google.firebase.dataconnect.testutil.randomPartitions
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropertyContext
import io.kotest.property.Sample

fun <T> PropertyContext.sampleFromArb(arb: Arb<T>, edgeCaseProbability: Double): Sample<T> {
  val edgeConfig = EdgeConfig(edgecasesGenerationProbability = edgeCaseProbability)
  return arb.generate(randomSource(), edgeConfig).first()
}

fun <T> PropertyContext.randomPartitions(list: List<T>, partitionCount: Int): List<List<T>> =
  list.randomPartitions(partitionCount, randomSource().random)
