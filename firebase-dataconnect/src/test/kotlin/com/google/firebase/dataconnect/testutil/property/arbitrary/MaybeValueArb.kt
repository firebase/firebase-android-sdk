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

import com.google.firebase.dataconnect.util.MaybeValue
import io.kotest.property.Arb
import io.kotest.property.arbitrary.choose
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.map
import kotlin.math.roundToInt

internal fun Arb.Companion.emptyMaybeValue(): Arb<MaybeValue.Empty> = Arb.constant(MaybeValue.Empty)

internal fun <T> Arb.Companion.nonEmptyMaybeValue(value: Arb<T>): Arb<MaybeValue.Value<T>> =
  value.map(MaybeValue<T>::Value)

internal fun Arb.Companion.nonEmptyMaybeValue(): Arb<MaybeValue.Value<Any>> =
  Arb.someValue().map { MaybeValue.Value(it.value) }

@JvmName("maybeValue_Arb_T")
internal fun <T> Arb.Companion.maybeValue(
  value: Arb<T>,
  emptyProbability: Double = 0.33,
): Arb<MaybeValue<T>> = maybeValue(nonEmptyMaybeValue(value), emptyProbability)

@JvmName("maybeValue_Arb_MaybeValue_Value_T")
internal fun <T> Arb.Companion.maybeValue(
  nonEmptyMaybeValue: Arb<MaybeValue.Value<T>>,
  emptyProbability: Double = 0.33,
): Arb<MaybeValue<T>> {
  val emptyWeight = (1000 * emptyProbability).toInt()
  val nonEmptyWeight = 1000 - emptyWeight
  return Arb.choose(
    emptyWeight to emptyMaybeValue(),
    nonEmptyWeight to nonEmptyMaybeValue,
  )
}

@JvmName("maybeValue_Arb_Any")
internal fun Arb.Companion.maybeValue(emptyProbability: Double = 0.33): Arb<MaybeValue<Any>> =
  maybeValue(Arb.someValue().map { it.value }, emptyProbability)
