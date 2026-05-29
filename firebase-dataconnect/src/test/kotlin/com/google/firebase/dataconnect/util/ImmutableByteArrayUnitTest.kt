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

import com.google.firebase.dataconnect.testutil.property.arbitrary.distinctPair
import com.google.firebase.dataconnect.util.StringUtil.to0xHexString
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.shuffle
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.subsequence
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

@Suppress("ReplaceCallWithBinaryOperator")
class ImmutableByteArrayUnitTest {

  @Test
  fun `ImmutableByteArray size`() = runTest {
    checkAll(propTestConfig, immutableByteArrayArb()) { sample ->
      sample.immutableByteArray.size shouldBe sample.byteArray.size
    }
  }

  @Test
  fun `ImmutableByteArray hashCode`() = runTest {
    checkAll(propTestConfig, immutableByteArrayArb()) { sample ->
      val hashCode = sample.immutableByteArray.hashCode()

      hashCode shouldBe sample.byteArray.contentHashCode()
    }
  }

  @Test
  fun `ImmutableByteArray toString`() = runTest {
    checkAll(propTestConfig, immutableByteArrayArb()) { sample ->
      val toStringResult = sample.immutableByteArray.toString()

      toStringResult shouldBe sample.byteArray.contentToString()
    }
  }

  @Test
  fun `ImmutableByteArray to0xHexString`() = runTest {
    checkAll(propTestConfig, immutableByteArrayArb()) { sample ->
      val toStringResult = sample.immutableByteArray.to0xHexString()

      toStringResult shouldBe sample.byteArray.to0xHexString()
    }
  }

  @Test
  fun `ImmutableByteArray equals should return true to same instance`() = runTest {
    checkAll(propTestConfig, immutableByteArrayArb()) { sample ->
      val immutableByteArray = sample.immutableByteArray

      immutableByteArray.equals(immutableByteArray) shouldBe true
    }
  }

  @Test
  fun `ImmutableByteArray equals should return true for equal, but distinct, instance`() = runTest {
    checkAll(propTestConfig, immutableByteArrayArb()) { sample ->
      val immutableByteArray1 = sample.immutableByteArray
      val immutableByteArray2 = immutableByteArray1.copy()

      immutableByteArray1.equals(immutableByteArray2) shouldBe true
    }
  }

  @Test
  fun `ImmutableByteArray equals should return false for unequal instance`() = runTest {
    checkAll(propTestConfig, immutableByteArrayArb().distinctPair()) { (sample1, sample2) ->
      sample1.immutableByteArray.equals(sample2.immutableByteArray) shouldBe false
    }
  }

  @Test
  fun `ImmutableByteArray equals should return false for different types`() = runTest {
    val otherTypesArb: Arb<*> =
      Arb.choice(
        Arb.constant(null),
        Arb.int(),
        Arb.string(),
        Arb.byteArray(Arb.int(0..10), Arb.byte()),
      )
    checkAll(propTestConfig, immutableByteArrayArb(), otherTypesArb) { sample, other ->
      sample.immutableByteArray.equals(other) shouldBe false
    }
  }

  @Test
  fun `ImmutableByteArray equals should return false when compared to underlying byte array`() =
    runTest {
      checkAll(propTestConfig, Arb.byteArray(Arb.int(0..10), Arb.byte())) { byteArray ->
        val immutableByteArray = ImmutableByteArray.adopt(byteArray)

        immutableByteArray.equals(byteArray) shouldBe false
      }
    }

  @Test
  fun `ImmutableByteArray compareTo should return 0 when compared to itself`() = runTest {
    checkAll(propTestConfig, immutableByteArrayArb()) { sample ->
      val immutableByteArray = sample.immutableByteArray

      immutableByteArray.compareTo(immutableByteArray) shouldBe 0
    }
  }

  @Test
  fun `ImmutableByteArray compareTo should return 0 for equal, but distinct, instances`() =
    runTest {
      checkAll(propTestConfig, immutableByteArrayArb()) { sample ->
        val immutableByteArray1 = sample.immutableByteArray
        val immutableByteArray2 = immutableByteArray1.copy()
        check(immutableByteArray1 !== immutableByteArray2)
        check(immutableByteArray1 == immutableByteArray2)

        immutableByteArray1.compareTo(immutableByteArray2) shouldBe 0
      }
    }

  @Test
  fun `ImmutableByteArray compareTo should return positive for greater array of same length`() =
    runTest {
      checkAll(propTestConfig, comparedImmutableByteArraySameSizeArb()) { sample ->
        val smaller: ImmutableByteArray = sample.smallerImmutableByteArray.immutableByteArray
        val larger: ImmutableByteArray = sample.largerImmutableByteArray.immutableByteArray

        larger.compareTo(smaller) shouldBeGreaterThan 0
      }
    }

  @Test
  fun `ImmutableByteArray compareTo should return negative for smaller array of same length`() =
    runTest {
      checkAll(propTestConfig, comparedImmutableByteArraySameSizeArb()) { sample ->
        val smaller: ImmutableByteArray = sample.smallerImmutableByteArray.immutableByteArray
        val larger: ImmutableByteArray = sample.largerImmutableByteArray.immutableByteArray

        smaller.compareTo(larger) shouldBeLessThan 0
      }
    }

  @Test
  fun `ImmutableByteArray compareTo should return positive for a prefix`() = runTest {
    checkAll(propTestConfig, comparedImmutableByteArrayPrefixArb()) { sample ->
      val smaller: ImmutableByteArray = sample.smallerImmutableByteArray.immutableByteArray
      val larger: ImmutableByteArray = sample.largerImmutableByteArray.immutableByteArray
      larger.compareTo(smaller) shouldBeGreaterThan 0
    }
  }

  @Test
  fun `ImmutableByteArray compareTo should return positive when a prefix`() = runTest {
    checkAll(propTestConfig, comparedImmutableByteArrayPrefixArb()) { sample ->
      val smaller: ImmutableByteArray = sample.smallerImmutableByteArray.immutableByteArray
      val larger: ImmutableByteArray = sample.largerImmutableByteArray.immutableByteArray
      smaller.compareTo(larger) shouldBeLessThan 0
    }
  }

  @Test
  fun `ImmutableByteArray compareTo should return positive for greater array of different length`() =
    runTest {
      checkAll(propTestConfig, comparedImmutableByteArrayDifferentSizeArb()) { sample ->
        val smaller: ImmutableByteArray = sample.smallerImmutableByteArray.immutableByteArray
        val larger: ImmutableByteArray = sample.largerImmutableByteArray.immutableByteArray

        larger.compareTo(smaller) shouldBeGreaterThan 0
      }
    }

  @Test
  fun `ImmutableByteArray compareTo should return negative for smaller array of different length`() =
    runTest {
      checkAll(propTestConfig, comparedImmutableByteArrayDifferentSizeArb()) { sample ->
        val smaller: ImmutableByteArray = sample.smallerImmutableByteArray.immutableByteArray
        val larger: ImmutableByteArray = sample.largerImmutableByteArray.immutableByteArray

        smaller.compareTo(larger) shouldBeLessThan 0
      }
    }

  @Test
  fun `ImmutableByteArray copy should return a distinct instance`() = runTest {
    checkAll(propTestConfig, immutableByteArrayArb()) { sample ->
      val immutableByteArray = sample.immutableByteArray

      val immutableByteArrayCopy = immutableByteArray.copy()

      immutableByteArrayCopy shouldNotBeSameInstanceAs immutableByteArray
    }
  }

  @Test
  fun `ImmutableByteArray copy should return an equal instance`() = runTest {
    checkAll(propTestConfig, immutableByteArrayArb()) { sample ->
      val immutableByteArray = sample.immutableByteArray

      val immutableByteArrayCopy = immutableByteArray.copy()

      immutableByteArrayCopy shouldBe immutableByteArray
    }
  }

  @Test
  fun `ImmutableByteArray adopt should hold the underlying array`() = runTest {
    checkAll(propTestConfig, Arb.byteArray(Arb.int(1..10), Arb.byte())) { byteArray ->
      val indexToChange = Arb.int(byteArray.indices).bind()
      val newValueAtIndex = Arb.byte().filterNot { it == byteArray[indexToChange] }.bind()
      val immutableByteArray = ImmutableByteArray.adopt(byteArray)
      val contentToStringBefore = byteArray.contentToString()
      byteArray[indexToChange] = newValueAtIndex
      val contentToStringAfter = byteArray.contentToString()
      check(contentToStringBefore != contentToStringAfter)

      immutableByteArray.toString() shouldBe contentToStringAfter
    }
  }

  @Test
  fun `ImmutableByteArray disown should return the underlying array`() = runTest {
    checkAll(propTestConfig, Arb.byteArray(Arb.int(0..10), Arb.byte())) { byteArray ->
      val immutableByteArray = ImmutableByteArray.adopt(byteArray)

      immutableByteArray.disown() shouldBeSameInstanceAs byteArray
    }
  }

  @Test
  fun `ImmutableByteArray peek should return the underlying array`() = runTest {
    checkAll(propTestConfig, Arb.byteArray(Arb.int(0..10), Arb.byte())) { byteArray ->
      val immutableByteArray = ImmutableByteArray.adopt(byteArray)

      immutableByteArray.peek() shouldBeSameInstanceAs byteArray
    }
  }

  @Test
  fun `ImmutableByteArray byteArrayCopyOf should return a copy of the underlying array`() =
    runTest {
      checkAll(propTestConfig, Arb.byteArray(Arb.int(0..10), Arb.byte())) { byteArray ->
        val immutableByteArray = ImmutableByteArray.adopt(byteArray)

        immutableByteArray.byteArrayCopyOf() shouldBe byteArray
      }
    }

  @Test
  fun `ImmutableByteArray byteArrayCopyOf should return a different reference than the underlying array`() =
    runTest {
      checkAll(propTestConfig, Arb.byteArray(Arb.int(0..10), Arb.byte())) { byteArray ->
        val immutableByteArray = ImmutableByteArray.adopt(byteArray)

        immutableByteArray.byteArrayCopyOf() shouldNotBeSameInstanceAs byteArray
      }
    }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(iterations = 200, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2))

private class ImmutableByteArraySample(val byteArray: ByteArray) {

  val immutableByteArray = ImmutableByteArray.adopt(byteArray.copyOf())

  override fun equals(other: Any?) =
    other is ImmutableByteArraySample && other.immutableByteArray == immutableByteArray

  override fun hashCode() = immutableByteArray.hashCode()

  override fun toString() = "ImmutableByteArraySample(${immutableByteArray})"
}

private fun immutableByteArrayArb(
  byteArray: Arb<ByteArray> = Arb.byteArray(Arb.int(0..10), Arb.byte())
): Arb<ImmutableByteArraySample> = byteArray.map(::ImmutableByteArraySample)

private data class ComparedImmutableByteArraysSample(
  val smallerImmutableByteArray: ImmutableByteArraySample,
  val largerImmutableByteArray: ImmutableByteArraySample,
)

private fun comparedImmutableByteArraySameSizeArb(): Arb<ComparedImmutableByteArraysSample> {
  val byteArb = Arb.byte()
  val sizeArb = Arb.int(1..10)
  val byteArrayArb = Arb.byteArray(sizeArb, byteArb)

  return arbitrary {
    val array1 = byteArrayArb.bind()
    val array2 = array1.copyOf()
    val startIndex = Arb.int(array1.indices).bind()
    val array1ValueAtStartIndex = array1[startIndex]
    val array2ValueAtStartIndex = byteArb.filterNot { it == array1ValueAtStartIndex }.bind()
    array2[startIndex] = array2ValueAtStartIndex
    val candidateIndicesToRandomize = (startIndex + 1 until array2.size).toList()
    val indicesToRandomize = Arb.subsequence(Arb.shuffle(candidateIndicesToRandomize).bind()).bind()
    indicesToRandomize.forEach { array2[it] = byteArb.bind() }

    val smallerArray: ByteArray
    val largerArray: ByteArray
    if (array1ValueAtStartIndex < array2ValueAtStartIndex) {
      smallerArray = array1
      largerArray = array2
    } else if (array1ValueAtStartIndex > array2ValueAtStartIndex) {
      smallerArray = array2
      largerArray = array1
    } else {
      error(
        "internal error ckf3s8mwkv: array1ValueAtStartIndex==array2ValueAtStartIndex: " +
          "$array2ValueAtStartIndex"
      )
    }

    ComparedImmutableByteArraysSample(
      smallerImmutableByteArray = ImmutableByteArraySample(smallerArray),
      largerImmutableByteArray = ImmutableByteArraySample(largerArray),
    )
  }
}

private fun comparedImmutableByteArrayDifferentSizeArb(): Arb<ComparedImmutableByteArraysSample> {
  val arb = comparedImmutableByteArraySameSizeArb()
  val boolean = Arb.boolean()
  val moreBytes = Arb.byteArray(Arb.int(1..10), Arb.byte())

  return Arb.bind(arb, boolean, moreBytes) { sample, boolean, moreBytes ->
    val smaller: ImmutableByteArraySample
    val larger: ImmutableByteArraySample

    if (boolean) {
      smaller = sample.smallerImmutableByteArray
      larger = ImmutableByteArraySample(sample.largerImmutableByteArray.byteArray.plus(moreBytes))
    } else {
      smaller = ImmutableByteArraySample(sample.smallerImmutableByteArray.byteArray.plus(moreBytes))
      larger = sample.largerImmutableByteArray
    }

    ComparedImmutableByteArraysSample(
      smallerImmutableByteArray = smaller,
      largerImmutableByteArray = larger,
    )
  }
}

private fun comparedImmutableByteArrayPrefixArb(): Arb<ComparedImmutableByteArraysSample> {
  val byteArb = Arb.byte()
  val sizeArb = Arb.int(1..10)
  val byteArrayArb = Arb.byteArray(sizeArb, byteArb)

  return arbitrary {
    val array1 = byteArrayArb.bind()
    val array2Size = Arb.int(array1.indices).bind()
    val array2 = array1.copyOfRange(0, array2Size)
    ComparedImmutableByteArraysSample(
      smallerImmutableByteArray = ImmutableByteArraySample(array2),
      largerImmutableByteArray = ImmutableByteArraySample(array1),
    )
  }
}
