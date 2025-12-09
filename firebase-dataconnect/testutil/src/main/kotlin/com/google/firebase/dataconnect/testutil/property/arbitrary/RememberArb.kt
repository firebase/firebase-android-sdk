/*
 * Copyright 2025 Google LLC
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
import io.kotest.property.Sample

class RememberArb<T>(val delegate: Arb<T>) : Arb<T>() {

  private val _generatedValues: MutableList<T> = mutableListOf()

  val generatedValues: List<T>
    get() = _generatedValues.toList()

  override fun sample(rs: RandomSource): Sample<T> {
    val sample = delegate.sample(rs)
    _generatedValues.add(sample.value)
    return sample
  }

  override fun edgecase(rs: RandomSource): T? {
    val generatedValue = delegate.edgecase(rs)
    if (generatedValue !== null) {
      _generatedValues.add(generatedValue)
    }
    return generatedValue
  }
}
