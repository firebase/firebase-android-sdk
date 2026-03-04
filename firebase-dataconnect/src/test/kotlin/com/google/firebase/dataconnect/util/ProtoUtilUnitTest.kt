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

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.testutil.property.arbitrary.next
import com.google.firebase.dataconnect.testutil.property.arbitrary.nonNegativeLongWithEvenNumDigitsDistribution
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.firebase.dataconnect.util.ProtoUtil.toHumanFriendlyString
import com.google.protobuf.Duration
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.int
import io.kotest.property.asSample
import io.kotest.property.checkAll
import kotlin.random.nextInt
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ProtoUtilUnitTest {

  @Before
  fun registerPrinters() {
    registerDataConnectKotestPrinters()
  }

  @Test
  fun `Duration toHumanFriendlyString with zero nanos`() = runTest {
    checkAll(propTestConfig, secondsArb()) { seconds ->
      val duration = Duration.newBuilder().setSeconds(seconds).build()

      val result = duration.toHumanFriendlyString()

      result shouldBe "$seconds seconds"
    }
  }

  @Test
  fun `Duration toHumanFriendlyString with zero seconds`() = runTest {
    checkAll(propTestConfig, NonZeroNanosArb()) { nanos ->
      val duration = Duration.newBuilder().setNanos(nanos.value).build()

      val result = duration.toHumanFriendlyString()

      result shouldBe "0.${nanos.mantissaString} seconds"
    }
  }

  @Test
  fun `Duration toHumanFriendlyString with non-zero seconds and nanos`() = runTest {
    checkAll(propTestConfig, secondsArb(), NonZeroNanosArb()) { seconds, nanos ->
      val duration = Duration.newBuilder().setSeconds(seconds).setNanos(nanos.value).build()

      val result = duration.toHumanFriendlyString()

      result shouldBe "$seconds.${nanos.mantissaString} seconds"
    }
  }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(iterations = 200, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2))

private fun secondsArb(): Arb<Long> = Arb.nonNegativeLongWithEvenNumDigitsDistribution()

private class NonZeroNanosArb : Arb<NonZeroNanosArb.Sample>() {

  data class Sample(
    val value: Int,
    val numDigits: Int,
    val mantissaString: String,
    val edgeCases: Set<EdgeCase>,
    val numDigitsEdgeCaseProbability: Float,
    val digitEdgeCaseProbability: Float,
  ) {
    enum class EdgeCase {
      NumDigits,
      Digit,
    }
  }

  private val numDigitsArb = Arb.int(1..9)
  private val digitArb = Arb.int(0..9)
  private val nonZeroDigitArb = Arb.int(1..9)

  override fun edgecase(rs: RandomSource): Sample {
    val edgeCases = run {
      val allEdgeCasesShuffled = Sample.EdgeCase.entries.shuffled(rs.random)
      val edgeCaseCount = rs.random.nextInt(1..allEdgeCasesShuffled.size)
      allEdgeCasesShuffled.take(edgeCaseCount).toSet()
    }

    return generate(
      rs,
      edgeCases = edgeCases,
      numDigitsEdgeCaseProbability = if (Sample.EdgeCase.NumDigits in edgeCases) 1.0f else 0.0f,
      digitEdgeCaseProbability = if (Sample.EdgeCase.Digit in edgeCases) 1.0f else 0.0f,
    )
  }

  override fun sample(rs: RandomSource) =
    generate(
        rs,
        edgeCases = emptySet(),
        numDigitsEdgeCaseProbability = rs.random.nextFloat(),
        digitEdgeCaseProbability = rs.random.nextFloat()
      )
      .asSample()

  private fun generate(
    rs: RandomSource,
    edgeCases: Set<Sample.EdgeCase>,
    numDigitsEdgeCaseProbability: Float,
    digitEdgeCaseProbability: Float,
  ): Sample {
    val numDigits = numDigitsArb.next(rs, numDigitsEdgeCaseProbability)
    check(numDigits in 1..9)

    val sb = StringBuilder()
    sb.append(nonZeroDigitArb.next(rs, digitEdgeCaseProbability))
    while (sb.length < numDigits - 1) {
      sb.append(digitArb.next(rs, digitEdgeCaseProbability))
    }
    if (numDigits > 1) {
      sb.append(nonZeroDigitArb.next(rs, digitEdgeCaseProbability))
    }

    val value = sb.toString().toInt()
    check(value.toString() == sb.toString())

    while (sb.length < 9) {
      sb.insert(0, '0')
    }

    return Sample(
      value = value,
      numDigits = numDigits,
      mantissaString = sb.toString(),
      edgeCases = edgeCases,
      numDigitsEdgeCaseProbability = numDigitsEdgeCaseProbability,
      digitEdgeCaseProbability = digitEdgeCaseProbability,
    )
  }
}
