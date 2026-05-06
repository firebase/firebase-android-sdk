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

import com.google.protobuf.Value as ValueProto

@JvmName("expectedAnyScalarRoundTripValueOrNull")
fun expectedAnyScalarRoundTripValue(value: Any?): Any? =
  if (value === null) null else expectedAnyScalarRoundTripValue(value)

fun expectedAnyScalarRoundTripValue(value: Any): Any =
  when (value) {
    is Double -> expectedAnyScalarDoubleRoundTripValue(value).value
    is List<*> -> value.map { expectedAnyScalarRoundTripValue(it) }
    is Map<*, *> -> value.mapValues { expectedAnyScalarRoundTripValue(it.value) }
    else -> value
  }

sealed interface DoubleOrString<T : Any> {

  val value: T

  fun toValueProto(): ValueProto

  @JvmInline
  value class String(override val value: kotlin.String) : DoubleOrString<kotlin.String> {
    override fun toValueProto(): ValueProto = value.toValueProto()
  }

  @JvmInline
  value class Double(override val value: kotlin.Double) : DoubleOrString<kotlin.Double> {
    override fun toValueProto(): ValueProto = value.toValueProto()
  }
}

fun expectedAnyScalarDoubleRoundTripValue(value: Double): DoubleOrString<*> =
  when (value) {
    -0.0 -> DoubleOrString.Double(0.0)
    Double.POSITIVE_INFINITY -> DoubleOrString.String("Infinity")
    Double.NEGATIVE_INFINITY -> DoubleOrString.String("-Infinity")
    else ->
      if (value.isNaN()) {
        DoubleOrString.String("NaN")
      } else {
        DoubleOrString.Double(value)
      }
  }
