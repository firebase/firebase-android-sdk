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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenSource
import com.google.firebase.firestore.MetadataChanges
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
class InternalOptions internal constructor(private val options: ImmutableMap<String, Value>) {
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

  internal fun forEach(f: (String, Value) -> Unit) {
    for (entry in options.entries) {
      f(entry.key, entry.value)
    }
  }

  private fun toValue(): Value {
    val mapValue = MapValue.newBuilder().putAllFields(options).build()
    return Value.newBuilder().setMapValue(mapValue).build()
  }

  companion object {
    @JvmField val EMPTY: InternalOptions = InternalOptions(ImmutableMap.of())

    fun of(key: String, value: Value): InternalOptions {
      return InternalOptions(ImmutableMap.of(key, value))
    }
  }
}

abstract class AbstractOptions<T : AbstractOptions<T>>
internal constructor(internal val options: InternalOptions) {

  internal abstract fun self(options: InternalOptions): T

  protected fun with(key: String, value: InternalOptions): T = self(options.with(key, value))

  protected fun with(key: String, value: Value): T = self(options.with(key, value))

  /**
   * Specify generic [String] option
   *
   * @param key The option key
   * @param value The [String] value of option
   * @return A new options object.
   */
  fun with(key: String, value: String): T = with(key, Values.encodeValue(value))

  /**
   * Specify generic [Boolean] option
   *
   * @param key The option key
   * @param value The [Boolean] value of option
   * @return A new options object.
   */
  fun with(key: String, value: Boolean): T = with(key, Values.encodeValue(value))

  /**
   * Specify generic [Long] option
   *
   * @param key The option key
   * @param value The [Long] value of option
   * @return A new options object.
   */
  fun with(key: String, value: Long): T = with(key, Values.encodeValue(value))

  /**
   * Specify generic [Double] option
   *
   * @param key The option key
   * @param value The [Double] value of option
   * @return A new options object.
   */
  fun with(key: String, value: Double): T = with(key, Values.encodeValue(value))

  /**
   * Specify generic [Field] option
   *
   * @param key The option key
   * @param value The [Field] value of option
   * @return A new options object.
   */
  fun with(key: String, value: Field): T = with(key, value.toProto())

  /**
   * Specify [RawOptions] object
   *
   * @param key The option key
   * @param value The [RawOptions] object
   * @return A new options object.
   */
  fun with(key: String, value: RawOptions): T = with(key, value.options)
}

class RawOptions private constructor(options: InternalOptions) :
  AbstractOptions<RawOptions>(options) {
  override fun self(options: InternalOptions) = RawOptions(options)

  companion object {
    @JvmField val DEFAULT: RawOptions = RawOptions(InternalOptions.EMPTY)
  }
}

class PipelineOptions private constructor(options: InternalOptions) :
  AbstractOptions<PipelineOptions>(options) {

  override fun self(options: InternalOptions) = PipelineOptions(options)

  companion object {
    @JvmField val DEFAULT: PipelineOptions = PipelineOptions(InternalOptions.EMPTY)
  }

  class IndexMode private constructor(internal val value: String) {
    companion object {
      @JvmField val RECOMMENDED = IndexMode("recommended")
    }
  }

  fun withIndexMode(indexMode: IndexMode): PipelineOptions = with("index_mode", indexMode.value)
}

class RealtimePipelineOptions
private constructor(
  internal val source: ListenSource,
  internal val serverTimestampBehavior: DocumentSnapshot.ServerTimestampBehavior,
  internal val metadataChanges: MetadataChanges,
  options: InternalOptions
) : AbstractOptions<RealtimePipelineOptions>(options) {

  override fun self(options: InternalOptions) =
    RealtimePipelineOptions(source, serverTimestampBehavior, metadataChanges, options)

  companion object {
    @JvmField
    val DEFAULT: RealtimePipelineOptions =
      RealtimePipelineOptions(
        ListenSource.DEFAULT,
        DocumentSnapshot.ServerTimestampBehavior.NONE,
        MetadataChanges.EXCLUDE,
        InternalOptions.EMPTY
      )
  }

  fun withSource(source: ListenSource): RealtimePipelineOptions =
    RealtimePipelineOptions(source, serverTimestampBehavior, metadataChanges, options)


  fun withServerTimestampBehavior(
    serverTimestampBehavior: DocumentSnapshot.ServerTimestampBehavior
  ): RealtimePipelineOptions =
    RealtimePipelineOptions(source, serverTimestampBehavior, metadataChanges, options)

  fun withMetadataChanges(metadataChanges: MetadataChanges): RealtimePipelineOptions =
    RealtimePipelineOptions(source, serverTimestampBehavior, metadataChanges, options)
}
