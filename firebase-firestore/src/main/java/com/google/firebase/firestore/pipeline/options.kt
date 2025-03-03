// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.pipeline

import com.google.common.collect.ImmutableMap
import com.google.firebase.firestore.model.Values
import com.google.firestore.v1.ArrayValue
import com.google.firestore.v1.MapValue
import com.google.firestore.v1.Value

/**
 * Wither style Key/Value options object.
 *
 * Basic `wither` functionality built upon `ImmutableMap<String></String>, Value>`. Exposes methods
 * to construct, augment, and encode Kay/Value pairs. The wrapped collection
 * `ImmutableMap<String></String>, Value>` is an implementation detail, not to be exposed, since
 * more efficient implementations are possible.
 */
internal class InternalOptions
internal constructor(private val options: ImmutableMap<String, Value>) {
  internal fun with(key: String, value: Value): InternalOptions {
    val builder = ImmutableMap.builderWithExpectedSize<String, Value>(options.size + 1)
    builder.putAll(options)
    builder.put(key, value)
    return InternalOptions(builder.buildKeepingLast())
  }

  internal fun with(key: String, values: Iterable<Value>): InternalOptions {
    val arrayValue = ArrayValue.newBuilder().addAllValues(values).build()
    return with(key, Value.newBuilder().setArrayValue(arrayValue).build())
  }

  internal fun with(key: String, value: InternalOptions): InternalOptions {
    return with(key, value.toValue())
  }

  internal fun forEach(f: (String, Value) -> Unit) = options.forEach(f)

  private fun toValue(): Value {
    val mapValue = MapValue.newBuilder().putAllFields(options).build()
    return Value.newBuilder().setMapValue(mapValue).build()
  }

  internal companion object {
    internal val EMPTY: InternalOptions = InternalOptions(ImmutableMap.of())

    internal fun of(key: String, value: Value): InternalOptions {
      return InternalOptions(ImmutableMap.of(key, value))
    }
  }
}

abstract class AbstractOptions<T : AbstractOptions<T>>
internal constructor(internal val options: InternalOptions) {

  internal abstract fun self(options: InternalOptions): T

  protected fun with(key: String, value: Value): T = self(options.with(key, value))

  fun with(key: String, value: String): T = with(key, Values.encodeValue(value))

  fun with(key: String, value: Boolean): T = with(key, Values.encodeValue(value))

  fun with(key: String, value: Long): T = with(key, Values.encodeValue(value))

  fun with(key: String, value: Double): T = with(key, Values.encodeValue(value))

  fun with(key: String, value: Field): T = with(key, value.toProto())
}
