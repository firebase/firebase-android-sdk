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

package com.google.firebase.dataconnect.testutil

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.next

interface ArbIterator<T> {
  fun next(rs: RandomSource): T
}

fun <T> Arb<T>.iterator(edgeCaseProbability: Float): ArbIterator<T> {
  require(edgeCaseProbability in 0.0..1.0) { "invalid edgeCaseProbability: $edgeCaseProbability" }
  return object : ArbIterator<T> {
    override fun next(rs: RandomSource) =
      if (edgeCaseProbability == 1.0f || edgeCaseProbability < rs.random.nextFloat())
        this@iterator.edgecase(rs)!!
      else this@iterator.next(rs)
  }
}
