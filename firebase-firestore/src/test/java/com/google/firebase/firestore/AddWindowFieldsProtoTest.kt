// Copyright 2026 Google LLC
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

package com.google.firebase.firestore

import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.model.DatabaseId
import com.google.firebase.firestore.pipeline.AggregateFunction.Companion.arrayAgg
import com.google.firebase.firestore.pipeline.AggregateFunction.Companion.arrayAggDistinct
import com.google.firebase.firestore.pipeline.AggregateFunction.Companion.average
import com.google.firebase.firestore.pipeline.AggregateFunction.Companion.count
import com.google.firebase.firestore.pipeline.AggregateFunction.Companion.countAll
import com.google.firebase.firestore.pipeline.AggregateFunction.Companion.countDistinct
import com.google.firebase.firestore.pipeline.AggregateFunction.Companion.countIf
import com.google.firebase.firestore.pipeline.AggregateFunction.Companion.first
import com.google.firebase.firestore.pipeline.AggregateFunction.Companion.last
import com.google.firebase.firestore.pipeline.AggregateFunction.Companion.maximum
import com.google.firebase.firestore.pipeline.AggregateFunction.Companion.minimum
import com.google.firebase.firestore.pipeline.AggregateFunction.Companion.sum
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.WindowBound
import com.google.firebase.firestore.pipeline.WindowFunction
import com.google.firebase.firestore.pipeline.WindowSpec
import com.google.firestore.v1.ArrayValue
import com.google.firestore.v1.Function as ProtoFunction
import com.google.firestore.v1.MapValue
import com.google.firestore.v1.Pipeline as ProtoPipeline
import com.google.firestore.v1.Value
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Wire-format tests for the `add_window_fields` stage.
 *
 * Ported from the JS SDK's `packages/firestore/test/unit/lite-api/add_window_fields.test.ts`, and
 * deliberately kept in the same order and grouping so the two can be diffed against each other.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AddWindowFieldsProtoTest {

  // --------------------------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------------------------

  private fun basePipeline(): Pipeline {
    val databaseId = DatabaseId.forDatabase("new-project", "(default)")
    return FirebaseFirestoreIntegrationTestFactory(databaseId)
      .firestore
      .pipeline()
      .collection("sales")
  }

  /** Serializes [pipeline] the way `execute()` does and returns its `add_window_fields` stage. */
  private fun windowStage(pipeline: Pipeline): ProtoPipeline.Stage {
    val stages =
      pipeline.toExecutePipelineRequest(null).structuredPipeline.pipeline.stagesList.filter {
        it.name == "add_window_fields"
      }
    assertThat(stages).hasSize(1)
    return stages[0]
  }

  /** The `window_spec` argument (args[0]) of the `add_window_fields` stage. */
  private fun windowSpecArg(pipeline: Pipeline): Value {
    val stage = windowStage(pipeline)
    assertThat(stage.argsList).hasSize(2)
    return stage.argsList[0]
  }

  /** The `fields` argument (args[1]) of the `add_window_fields` stage. */
  private fun fieldsArg(pipeline: Pipeline): Value {
    val stage = windowStage(pipeline)
    assertThat(stage.argsList).hasSize(2)
    return stage.argsList[1]
  }

  private fun fieldRef(name: String): Value =
    Value.newBuilder().setFieldReferenceValue(name).build()

  private fun int(value: Long): Value = Value.newBuilder().setIntegerValue(value).build()

  private fun dbl(value: Double): Value = Value.newBuilder().setDoubleValue(value).build()

  private fun str(value: String): Value = Value.newBuilder().setStringValue(value).build()

  private fun map(vararg fields: Pair<String, Value>): Value =
    Value.newBuilder().setMapValue(MapValue.newBuilder().putAllFields(fields.toMap())).build()

  private fun array(vararg values: Value): Value =
    Value.newBuilder().setArrayValue(ArrayValue.newBuilder().addAllValues(values.toList())).build()

  private fun fn(name: String, vararg args: Value): Value =
    Value.newBuilder()
      .setFunctionValue(ProtoFunction.newBuilder().setName(name).addAllArgs(args.toList()))
      .build()

  private fun ordering(expression: Value, direction: String): Value =
    map("direction" to str(direction), "expression" to expression)

  // --------------------------------------------------------------------------------------------
  // Stage shape
  // --------------------------------------------------------------------------------------------

  @Test
  fun usesTheAddWindowFieldsStageNameAnd2Args() {
    val stage =
      windowStage(
        basePipeline().addWindowFields(WindowSpec.partition("product"), countAll().alias("c"))
      )

    assertThat(stage.name).isEqualTo("add_window_fields")
    assertThat(stage.argsList).hasSize(2)
  }

  @Test
  fun doesNotSendAnyStageOptions() {
    val stage =
      windowStage(
        basePipeline().addWindowFields(WindowSpec.partition("product"), countAll().alias("c"))
      )

    assertThat(stage.optionsMap).isEmpty()
  }

  // --------------------------------------------------------------------------------------------
  // window_spec
  // --------------------------------------------------------------------------------------------

  @Test
  fun serializesAnEmptyWindowSpecAsAnEmptyMap() {
    assertThat(windowSpecArg(basePipeline().addWindowFields(WindowSpec(), countAll().alias("c"))))
      .isEqualTo(map())
  }

  @Test
  fun serializesAPartitionOfFieldNameStrings() {
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(WindowSpec.partition("product", "region"), countAll().alias("c"))
        )
      )
      .isEqualTo(map("partition" to array(fieldRef("product"), fieldRef("region"))))
  }

  @Test
  fun serializesAPartitionOfExpressions() {
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.partition(field("product"), field("region").toLower()),
              countAll().alias("c")
            )
        )
      )
      .isEqualTo(map("partition" to array(fieldRef("product"), fn("to_lower", fieldRef("region")))))
  }

  @Test
  fun serializesANestedFieldPathPartition() {
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(WindowSpec.partition("metadata.region"), countAll().alias("c"))
        )
      )
      .isEqualTo(map("partition" to array(fieldRef("metadata.region"))))
  }

  @Test
  fun serializesASingleSortOrdering() {
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(WindowSpec.sort(field("date").ascending()), countAll().alias("c"))
        )
      )
      .isEqualTo(map("sort" to array(ordering(fieldRef("date"), "ascending"))))
  }

  @Test
  fun serializesMultipleSortOrderingsWithMixedDirections() {
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.sort(field("date").ascending(), field("salesPrice").descending()),
              countAll().alias("c")
            )
        )
      )
      .isEqualTo(
        map(
          "sort" to
            array(
              ordering(fieldRef("date"), "ascending"),
              ordering(fieldRef("salesPrice"), "descending")
            )
        )
      )
  }

  @Test
  fun serializesPartitionAndSortTogether() {
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.partition("product").sort(field("date").ascending()),
              countAll().alias("c")
            )
        )
      )
      .isEqualTo(
        map(
          "partition" to array(fieldRef("product")),
          "sort" to array(ordering(fieldRef("date"), "ascending"))
        )
      )
  }

  @Test
  fun omitsAnUnsetPartition() {
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(WindowSpec.sort(field("date").ascending()), countAll().alias("c"))
        )
      )
      .isEqualTo(map("sort" to array(ordering(fieldRef("date"), "ascending"))))
  }

  @Test
  fun omitsAnUnsetSort() {
    assertThat(
        windowSpecArg(
          basePipeline().addWindowFields(WindowSpec.partition("product"), countAll().alias("c"))
        )
      )
      .isEqualTo(map("partition" to array(fieldRef("product"))))
  }

  @Test
  fun omitsTheFrameWhenUsingDefaultFraming() {
    val spec =
      windowSpecArg(
        basePipeline()
          .addWindowFields(WindowSpec.sort(field("date").ascending()), countAll().alias("c"))
      )

    assertThat(spec.mapValue.fieldsMap).doesNotContainKey("documents")
    assertThat(spec.mapValue.fieldsMap).doesNotContainKey("range")
  }

  // --------------------------------------------------------------------------------------------
  // Stage level documents framing
  // --------------------------------------------------------------------------------------------

  @Test
  fun serializesNumericOffsetsAsIntegers() {
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.documents(2, 1).sort(field("date").ascending()),
              countAll().alias("c")
            )
        )
      )
      .isEqualTo(
        map(
          "sort" to array(ordering(fieldRef("date"), "ascending")),
          "documents" to map("preceding" to int(2), "following" to int(1))
        )
      )
  }

  /** A zero offset is a real numeric bound and must not be coerced into the `current` sentinel. */
  @Test
  fun serializesZeroOffsets() {
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.documents(0, 0).sort(field("date").ascending()),
              countAll().alias("c")
            )
        )
      )
      .isEqualTo(
        map(
          "sort" to array(ordering(fieldRef("date"), "ascending")),
          "documents" to map("preceding" to int(0), "following" to int(0))
        )
      )
  }

  /**
   * A zero offset and [WindowBound.CURRENT] are *different* frame boundaries and must never be
   * conflated.
   *
   * In a `range` frame `current` cuts off strictly at the current document's position, whereas an
   * offset of `0` additionally admits every document whose sort value ties with the current one.
   * Given sort values `[10, 10, 10]`, evaluating at the second document with an unbounded lower
   * bound yields two documents under `current` but three under `0`. They must therefore reach the
   * backend as distinct values.
   */
  @Test
  fun distinguishesZeroOffsetFromCurrentBound() {
    fun rangeSpec(preceding: Any, following: Any) =
      windowSpecArg(
        basePipeline()
          .addWindowFields(
            WindowSpec.range(preceding, following).sort(field("date").ascending()),
            countAll().alias("c")
          )
      )

    val zeroOffset = rangeSpec(0, 0)
    val currentBound = rangeSpec(WindowSpec.CURRENT, WindowSpec.CURRENT)

    assertThat(zeroOffset)
      .isEqualTo(
        map(
          "sort" to array(ordering(fieldRef("date"), "ascending")),
          "range" to map("preceding" to int(0), "following" to int(0))
        )
      )
    assertThat(currentBound)
      .isEqualTo(
        map(
          "sort" to array(ordering(fieldRef("date"), "ascending")),
          "range" to map("preceding" to str("current"), "following" to str("current"))
        )
      )
    assertThat(zeroOffset).isNotEqualTo(currentBound)

    // The distinction must also survive canonicalization, which backs WindowSpec equality.
    assertThat(WindowSpec.range(0, 0))
      .isNotEqualTo(WindowSpec.range(WindowSpec.CURRENT, WindowSpec.CURRENT))
  }

  /** `UNBOUNDED` is likewise a symbolic bound, not a reserved numeric value. */
  @Test
  fun treatsExtremeIntegerOffsetsAsNumbers() {
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.documents(Int.MAX_VALUE, Int.MIN_VALUE).sort(field("date").ascending()),
              countAll().alias("c")
            )
        )
      )
      .isEqualTo(
        map(
          "sort" to array(ordering(fieldRef("date"), "ascending")),
          "documents" to
            map(
              "preceding" to int(Int.MAX_VALUE.toLong()),
              "following" to int(Int.MIN_VALUE.toLong())
            )
        )
      )
  }

  @Test
  fun serializesNegativeOffsetsForLookAheadWindows() {
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.documents(-1, 2).sort(field("date").ascending()),
              countAll().alias("c")
            )
        )
      )
      .isEqualTo(
        map(
          "sort" to array(ordering(fieldRef("date"), "ascending")),
          "documents" to map("preceding" to int(-1), "following" to int(2))
        )
      )
  }

  @Test
  fun serializesTheUnboundedAndCurrentSentinels() {
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.documents(WindowSpec.UNBOUNDED, WindowSpec.CURRENT)
                .sort(field("date").ascending()),
              countAll().alias("c")
            )
        )
      )
      .isEqualTo(
        map(
          "sort" to array(ordering(fieldRef("date"), "ascending")),
          "documents" to map("preceding" to str("unbounded"), "following" to str("current"))
        )
      )
  }

  @Test
  fun serializesAnUnboundedToUnboundedFrame() {
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.documents(WindowSpec.UNBOUNDED, WindowSpec.UNBOUNDED),
              countAll().alias("c")
            )
        )
      )
      .isEqualTo(
        map("documents" to map("preceding" to str("unbounded"), "following" to str("unbounded")))
      )
  }

  @Test
  fun serializesConstantExpressionBounds() {
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.documents(constant(3), constant(0)).sort(field("date").ascending()),
              countAll().alias("c")
            )
        )
      )
      .isEqualTo(
        map(
          "sort" to array(ordering(fieldRef("date"), "ascending")),
          "documents" to map("preceding" to int(3), "following" to int(0))
        )
      )
  }

  // --------------------------------------------------------------------------------------------
  // Stage level range framing
  // --------------------------------------------------------------------------------------------

  @Test
  fun serializesNumericRangeOffsets() {
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.range(10, 10).sort(field("salesPrice").ascending()),
              countAll().alias("c")
            )
        )
      )
      .isEqualTo(
        map(
          "sort" to array(ordering(fieldRef("salesPrice"), "ascending")),
          "range" to map("preceding" to int(10), "following" to int(10))
        )
      )
  }

  /**
   * Regression test for DL-4: fractional bounds must not be truncated to integers.
   *
   * Uses the mixed `(Any, Any)` overload so that, as in JS, `2.5` encodes as a double while `0`
   * stays an integer.
   */
  @Test
  fun serializesFractionalRangeOffsetsAsDoubles() {
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.range(2.5 as Any, 0 as Any).sort(field("salesPrice").ascending()),
              countAll().alias("c")
            )
        )
      )
      .isEqualTo(
        map(
          "sort" to array(ordering(fieldRef("salesPrice"), "ascending")),
          "range" to map("preceding" to dbl(2.5), "following" to int(0))
        )
      )
  }

  /** Regression test for DL-2: `unit` belongs inside the frame, not beside it. */
  @Test
  fun serializesARangeFrameWithADayTimeUnit() {
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.range(30, WindowSpec.CURRENT, "day").sort(field("date").ascending()),
              countAll().alias("c")
            )
        )
      )
      .isEqualTo(
        map(
          "sort" to array(ordering(fieldRef("date"), "ascending")),
          "range" to
            map("preceding" to int(30), "following" to str("current"), "unit" to str("day"))
        )
      )
  }

  @Test
  fun serializesEveryTimeUnit() {
    val units =
      listOf(
        "microsecond",
        "millisecond",
        "second",
        "minute",
        "hour",
        "day",
        "week",
        "month",
        "quarter",
        "year"
      )

    for (unit in units) {
      val spec =
        windowSpecArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.range(1, WindowSpec.CURRENT, unit).sort(field("date").ascending()),
              countAll().alias("c")
            )
        )

      assertThat(spec.mapValue.fieldsMap["range"])
        .isEqualTo(map("preceding" to int(1), "following" to str("current"), "unit" to str(unit)))
    }
  }

  @Test
  fun omitsUnitWhenItIsNotSpecified() {
    val spec =
      windowSpecArg(
        basePipeline()
          .addWindowFields(
            WindowSpec.range(WindowSpec.UNBOUNDED, WindowSpec.CURRENT)
              .sort(field("salesPrice").ascending()),
            countAll().alias("c")
          )
      )

    assertThat(spec.mapValue.fieldsMap["range"])
      .isEqualTo(map("preceding" to str("unbounded"), "following" to str("current")))
  }

  // --------------------------------------------------------------------------------------------
  // Fields
  // --------------------------------------------------------------------------------------------

  @Test
  fun mapsEachAliasToItsAggregateFunction() {
    assertThat(
        fieldsArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.partition("product"),
              sum("salesPrice").alias("total"),
              countAll().alias("c")
            )
        )
      )
      .isEqualTo(map("total" to fn("sum", fieldRef("salesPrice")), "c" to fn("count")))
  }

  @Test
  fun supportsNestedOutputFieldPaths() {
    assertThat(
        fieldsArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.partition("product"),
              sum("salesPrice").alias("stats.total")
            )
        )
      )
      .isEqualTo(map("stats.total" to fn("sum", fieldRef("salesPrice"))))
  }

  @Test
  fun serializesAllSupportedAggregateFunctionNames() {
    assertThat(
        fieldsArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.partition("product"),
              countAll().alias("countAll"),
              count("salesPrice").alias("count"),
              countIf(field("salesPrice").greaterThan(10)).alias("countIf"),
              countDistinct("salesPrice").alias("countDistinct"),
              sum("salesPrice").alias("sum"),
              average("salesPrice").alias("average"),
              minimum("salesPrice").alias("minimum"),
              maximum("salesPrice").alias("maximum"),
              first("salesPrice").alias("first"),
              last("salesPrice").alias("last"),
              arrayAgg("salesPrice").alias("arrayAgg"),
              arrayAggDistinct("salesPrice").alias("arrayAggDistinct")
            )
        )
      )
      .isEqualTo(
        map(
          "countAll" to fn("count"),
          "count" to fn("count", fieldRef("salesPrice")),
          "countIf" to fn("count_if", fn("greater_than", fieldRef("salesPrice"), int(10))),
          "countDistinct" to fn("count_distinct", fieldRef("salesPrice")),
          "sum" to fn("sum", fieldRef("salesPrice")),
          "average" to fn("average", fieldRef("salesPrice")),
          "minimum" to fn("minimum", fieldRef("salesPrice")),
          "maximum" to fn("maximum", fieldRef("salesPrice")),
          "first" to fn("first", fieldRef("salesPrice")),
          "last" to fn("last", fieldRef("salesPrice")),
          "arrayAgg" to fn("array_agg", fieldRef("salesPrice")),
          "arrayAggDistinct" to fn("array_agg_distinct", fieldRef("salesPrice"))
        )
      )
  }

  @Test
  fun serializesAggregatesOverComputedExpressions() {
    assertThat(
        fieldsArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.partition("product"),
              sum(field("salesPrice").multiply(2)).alias("doubled")
            )
        )
      )
      .isEqualTo(map("doubled" to fn("sum", fn("multiply", fieldRef("salesPrice"), int(2)))))
  }

  @Test
  fun serializesTheRankingWindowFunctions() {
    assertThat(
        fieldsArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.sort(field("salesPrice").descending()),
              WindowFunction.rank().alias("rank"),
              WindowFunction.denseRank().alias("denseRank"),
              WindowFunction.rowNumber().alias("rowNumber")
            )
        )
      )
      .isEqualTo(
        map("rank" to fn("rank"), "denseRank" to fn("dense_rank"), "rowNumber" to fn("row_number"))
      )
  }

  @Test
  fun rejectsDuplicateAliases() {
    val error =
      try {
        basePipeline()
          .addWindowFields(
            WindowSpec.partition("product"),
            sum("salesPrice").alias("total"),
            average("salesPrice").alias("total")
          )
        null
      } catch (e: IllegalArgumentException) {
        e
      }

    assertThat(error).isNotNull()
    assertThat(error!!).hasMessageThat().contains("total")
  }

  // --------------------------------------------------------------------------------------------
  // Accumulator level framing (over)
  // --------------------------------------------------------------------------------------------

  @Test
  fun wrapsAnAggregateInOverWithADocumentsFrame() {
    assertThat(
        fieldsArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.sort(field("date").ascending()),
              average("salesPrice").over(WindowSpec.documents(1, 1)).alias("movingAverage")
            )
        )
      )
      .isEqualTo(
        map(
          "movingAverage" to
            fn(
              "over",
              fn("average", fieldRef("salesPrice")),
              map("documents" to map("preceding" to int(1), "following" to int(1)))
            )
        )
      )
  }

  @Test
  fun wrapsAnAggregateInOverWithARangeFrameAndTimeUnit() {
    assertThat(
        fieldsArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.sort(field("date").ascending()),
              sum("salesPrice").over(WindowSpec.range(10, 0, "day")).alias("tenDayTotal")
            )
        )
      )
      .isEqualTo(
        map(
          "tenDayTotal" to
            fn(
              "over",
              fn("sum", fieldRef("salesPrice")),
              map(
                "range" to map("preceding" to int(10), "following" to int(0), "unit" to str("day"))
              )
            )
        )
      )
  }

  @Test
  fun supportsDifferentFramesForDifferentAccumulators() {
    assertThat(
        fieldsArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.partition("product").sort(field("date").ascending()),
              sum("salesPrice")
                .over(WindowSpec.documents(WindowSpec.UNBOUNDED, WindowSpec.CURRENT))
                .alias("runningTotal"),
              average("salesPrice").over(WindowSpec.documents(1, 1)).alias("movingAverage")
            )
        )
      )
      .isEqualTo(
        map(
          "runningTotal" to
            fn(
              "over",
              fn("sum", fieldRef("salesPrice")),
              map(
                "documents" to map("preceding" to str("unbounded"), "following" to str("current"))
              )
            ),
          "movingAverage" to
            fn(
              "over",
              fn("average", fieldRef("salesPrice")),
              map("documents" to map("preceding" to int(1), "following" to int(1)))
            )
        )
      )
  }

  @Test
  fun doesNotWrapInOverWhenNoFrameIsProvided() {
    assertThat(
        fieldsArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.sort(field("date").ascending()),
              sum("salesPrice").over().alias("total")
            )
        )
      )
      .isEqualTo(map("total" to fn("sum", fieldRef("salesPrice"))))
  }

  @Test
  fun supportsOverOnARankingWindowFunction() {
    assertThat(
        fieldsArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.sort(field("date").ascending()),
              WindowFunction.rank()
                .over(WindowSpec.documents(WindowSpec.UNBOUNDED, WindowSpec.CURRENT))
                .alias("r")
            )
        )
      )
      .isEqualTo(
        map(
          "r" to
            fn(
              "over",
              fn("rank"),
              map(
                "documents" to map("preceding" to str("unbounded"), "following" to str("current"))
              )
            )
        )
      )
  }

  // The SDK does not reject `partition`/`sort` in `over()`. They are encoded into the same window
  // spec map as the frame, and the backend responds with
  // `Window frame has unexpected fields: [partition]`. Leaving this to the backend means
  // accumulator level partitioning starts working without an SDK change if the backend ever
  // supports it.
  @Test
  fun encodesAPartitionSuppliedToOver() {
    assertThat(
        fieldsArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.sort(field("date").ascending()),
              sum("salesPrice").over(WindowSpec.partition("product").documents(1, 1)).alias("total")
            )
        )
      )
      .isEqualTo(
        map(
          "total" to
            fn(
              "over",
              fn("sum", fieldRef("salesPrice")),
              map(
                "partition" to array(fieldRef("product")),
                "documents" to map("preceding" to int(1), "following" to int(1))
              )
            )
        )
      )
  }

  @Test
  fun encodesASortSuppliedToOver() {
    assertThat(
        fieldsArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.sort(field("date").ascending()),
              sum("salesPrice")
                .over(WindowSpec.documents(1, 1).sort(field("date").ascending()))
                .alias("total")
            )
        )
      )
      .isEqualTo(
        map(
          "total" to
            fn(
              "over",
              fn("sum", fieldRef("salesPrice")),
              map(
                "sort" to array(ordering(fieldRef("date"), "ascending")),
                "documents" to map("preceding" to int(1), "following" to int(1))
              )
            )
        )
      )
  }

  // --------------------------------------------------------------------------------------------
  // Frame state, and combinations left for the backend to validate
  //
  // The frame is a one-of, so `documents`/`range` cannot both be set: the builder makes that state
  // unrepresentable rather than encoding it. Everything else that is merely *invalid* — unknown
  // bound strings, out-of-order bounds — is still encoded and sent, because the backend owns
  // validation and returns a precise error. That keeps the SDK from having to change whenever the
  // backend relaxes or extends a rule.
  // --------------------------------------------------------------------------------------------

  /**
   * `documents` and `range` are mutually exclusive, so the last frame call wins and the previous
   * frame is dropped rather than both being encoded.
   */
  @Test
  fun lastFrameCallWinsWhenSwitchingFrameKind() {
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.sort(field("date").ascending()).documents(1, 1).range(2, 2),
              countAll().alias("c")
            )
        )
      )
      .isEqualTo(
        map(
          "sort" to array(ordering(fieldRef("date"), "ascending")),
          "range" to map("preceding" to int(2), "following" to int(2))
        )
      )

    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.sort(field("date").ascending()).range(2, 2).documents(1, 1),
              countAll().alias("c")
            )
        )
      )
      .isEqualTo(
        map(
          "sort" to array(ordering(fieldRef("date"), "ascending")),
          "documents" to map("preceding" to int(1), "following" to int(1))
        )
      )
  }

  /** Repeating the same frame kind also takes the last call. */
  @Test
  fun lastFrameCallWinsWhenRepeatingFrameKind() {
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.sort(field("date").ascending()).documents(1, 1).documents(3, 4),
              countAll().alias("c")
            )
        )
      )
      .isEqualTo(
        map(
          "sort" to array(ordering(fieldRef("date"), "ascending")),
          "documents" to map("preceding" to int(3), "following" to int(4))
        )
      )
  }

  /**
   * `unit` belongs to the range frame, so replacing the frame must not leak a unit from an earlier
   * call into a frame that never specified one.
   */
  @Test
  fun replacingAFrameDropsAStaleUnit() {
    // range -> range without a unit
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.sort(field("date").ascending()).range(1, 2, "day").range(3, 4),
              countAll().alias("c")
            )
        )
      )
      .isEqualTo(
        map(
          "sort" to array(ordering(fieldRef("date"), "ascending")),
          "range" to map("preceding" to int(3), "following" to int(4))
        )
      )

    // range -> documents; the backend rejects a unit on a documents frame outright
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.sort(field("date").ascending()).range(1, 2, "day").documents(3, 4),
              countAll().alias("c")
            )
        )
      )
      .isEqualTo(
        map(
          "sort" to array(ordering(fieldRef("date"), "ascending")),
          "documents" to map("preceding" to int(3), "following" to int(4))
        )
      )
  }

  /** Regression test for DL-5: unknown bound strings go to the backend, not an exception. */
  @Test
  fun passesThroughAnUnrecognizedFrameBoundString() {
    assertThat(
        windowSpecArg(
          basePipeline()
            .addWindowFields(
              WindowSpec.documents("infinite" as Any, "current" as Any)
                .sort(field("date").ascending()),
              countAll().alias("c")
            )
        )
      )
      .isEqualTo(
        map(
          "sort" to array(ordering(fieldRef("date"), "ascending")),
          "documents" to map("preceding" to str("infinite"), "following" to str("current"))
        )
      )
  }
}
