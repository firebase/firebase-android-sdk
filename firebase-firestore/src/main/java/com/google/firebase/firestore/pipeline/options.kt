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
   * Specify [GenericOptions] object
   *
   * @param key The option key
   * @param value The [GenericOptions] object
   * @return A new options object.
   */
  fun with(key: String, value: GenericOptions): T = with(key, value.options)
}

class GenericOptions private constructor(options: InternalOptions) :
  AbstractOptions<GenericOptions>(options) {
  override fun self(options: InternalOptions) = GenericOptions(options)

  companion object {
    @JvmField val DEFAULT: GenericOptions = GenericOptions(InternalOptions.EMPTY)
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

  fun withExplainOptions(options: ExplainOptions): PipelineOptions =
    with("explain_options", options.options)
}

class RealtimePipelineOptions private constructor(options: InternalOptions) :
  AbstractOptions<RealtimePipelineOptions>(options) {

  override fun self(options: InternalOptions) = RealtimePipelineOptions(options)
}

class ExplainOptions private constructor(options: InternalOptions) :
  AbstractOptions<ExplainOptions>(options) {
  override fun self(options: InternalOptions) = ExplainOptions(options)

  companion object {
    @JvmField val DEFAULT = ExplainOptions(InternalOptions.EMPTY)
  }

  fun withMode(value: ExplainMode) = with("mode", value.value)

  fun withOutputFormat(value: OutputFormat) = with("output_format", value.value)

  fun withVerbosity(value: Verbosity) = with("verbosity", value.value)

  fun withIndexRecommendation(value: Boolean) = with("index_recommendation", value)

  fun withProfiles(value: Profiles) = with("profiles", value.value)

  fun withRedact(value: Boolean) = with("redact", value)

  class ExplainMode private constructor(internal val value: String) {
    companion object {
      @JvmField val EXECUTE = ExplainMode("execute")

      @JvmField val EXPLAIN = ExplainMode("explain")

      @JvmField val ANALYZE = ExplainMode("analyze")
    }
  }

  class OutputFormat private constructor(internal val value: String) {
    companion object {
      @JvmField val TEXT = OutputFormat("text")

      @JvmField val JSON = OutputFormat("json")

      @JvmField val STRUCT = OutputFormat("struct")
    }
  }

  class Verbosity private constructor(internal val value: String) {
    companion object {
      @JvmField val SUMMARY_ONLY = Verbosity("summary_only")

      @JvmField val EXECUTION_TREE = Verbosity("execution_tree")
    }
  }

  class Profiles private constructor(internal val value: String) {
    companion object {
      @JvmField val LATENCY = Profiles("latency")

      @JvmField val RECORDS_COUNT = Profiles("records_count")

      @JvmField val BYTES_THROUGHPUT = Profiles("bytes_throughput")
    }
  }
}
