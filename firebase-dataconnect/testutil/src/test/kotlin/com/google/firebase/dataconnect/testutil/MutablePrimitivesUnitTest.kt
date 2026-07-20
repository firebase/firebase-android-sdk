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

package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.testutil.property.arbitrary.any
import com.google.firebase.dataconnect.testutil.property.arbitrary.distinctPair
import com.google.firebase.dataconnect.testutil.property.arbitrary.pair
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.short
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MutablePrimitivesUnitTest {

  @Test
  fun `MutableBoolean value initialization from constructor`() = runTest {
    checkAll(propTestConfig, Arb.boolean()) { booleanValue ->
      val mutableBoolean = MutableBoolean(booleanValue)
      mutableBoolean.value shouldBe booleanValue
    }
  }

  @Test
  fun `MutableBoolean toString() initial value`() = runTest {
    checkAll(propTestConfig, Arb.boolean()) { booleanValue ->
      val mutableBoolean = MutableBoolean(booleanValue)
      mutableBoolean.toString() shouldBe booleanValue.toString()
    }
  }

  @Test
  fun `MutableBoolean toString() subsequent values`() = runTest {
    checkAll(propTestConfig, Arb.boolean(), Arb.list(Arb.boolean(), 10..10)) {
      initialBooleanValue,
      booleanValues ->
      val mutableBoolean = MutableBoolean(initialBooleanValue)
      booleanValues.forEach { booleanValue ->
        mutableBoolean.value = booleanValue
        mutableBoolean.toString() shouldBe booleanValue.toString()
      }
    }
  }

  @Test
  fun `MutableBoolean hashCode() initial value`() = runTest {
    checkAll(propTestConfig, Arb.boolean()) { booleanValue ->
      val mutableBoolean = MutableBoolean(booleanValue)
      mutableBoolean.hashCode() shouldBe booleanValue.hashCode()
    }
  }

  @Test
  fun `MutableBoolean hashCode() subsequent values`() = runTest {
    checkAll(propTestConfig, Arb.boolean(), Arb.list(Arb.boolean(), 10..10)) {
      initialBooleanValue,
      booleanValues ->
      val mutableBoolean = MutableBoolean(initialBooleanValue)
      booleanValues.forEach { booleanValue ->
        mutableBoolean.value = booleanValue
        mutableBoolean.hashCode() shouldBe booleanValue.hashCode()
      }
    }
  }

  @Test
  fun `MutableBoolean hashCode() and equals() consistency`() = runTest {
    checkAll(propTestConfig, Arb.boolean()) { booleanValue ->
      val mutableBoolean1 = MutableBoolean(booleanValue)
      val mutableBoolean2 = MutableBoolean(booleanValue)
      check(mutableBoolean1 == mutableBoolean2)
      mutableBoolean1.hashCode() shouldBe mutableBoolean2.hashCode()
    }
  }

  @Test
  fun `MutableBoolean equals(self)`() = runTest {
    checkAll(propTestConfig, Arb.boolean()) { booleanValue ->
      val mutableBoolean = MutableBoolean(booleanValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableBoolean.equals(mutableBoolean) shouldBe true
    }
  }

  @Test
  fun `MutableBoolean equals(equal, but distinct, instance)`() = runTest {
    checkAll(propTestConfig, Arb.boolean()) { booleanValue ->
      val mutableBoolean1 = MutableBoolean(booleanValue)
      val mutableBoolean2 = MutableBoolean(booleanValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableBoolean1.equals(mutableBoolean2) shouldBe true
    }
  }

  @Test
  fun `MutableBoolean equals(equal, but distinct, instance, after mutations)`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.boolean(), 10..10).pair(), Arb.boolean()) {
      (booleanValues1, booleanValues2),
      finalBooleanValue ->
      val mutableBoolean1 = MutableBoolean(booleanValues1[0])
      val mutableBoolean2 = MutableBoolean(booleanValues2[0])
      booleanValues1.drop(1).forEach { mutableBoolean1.value = it }
      booleanValues2.drop(1).forEach { mutableBoolean2.value = it }
      mutableBoolean1.value = finalBooleanValue
      mutableBoolean2.value = finalBooleanValue
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableBoolean1.equals(mutableBoolean2) shouldBe true
    }
  }

  @Test
  fun `MutableBoolean equals(unequal instance)`() = runTest {
    checkAll(propTestConfig, Arb.boolean()) { booleanValue ->
      val mutableBoolean1 = MutableBoolean(booleanValue)
      val mutableBoolean2 = MutableBoolean(!booleanValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableBoolean1.equals(mutableBoolean2) shouldBe false
    }
  }

  @Test
  fun `MutableBoolean equals(unequal instance, after mutations)`() = runTest {
    checkAll(
      propTestConfig,
      Arb.boolean(),
      Arb.list(Arb.boolean(), 10..10).pair(),
      Arb.boolean()
    ) { initialBooleanValue, (booleanValues1, booleanValues2), finalMutation ->
      val mutableBoolean1 = MutableBoolean(initialBooleanValue)
      val mutableBoolean2 = MutableBoolean(initialBooleanValue)
      booleanValues1.forEach { mutableBoolean1.value = it }
      booleanValues2.forEach { mutableBoolean2.value = it }
      if (finalMutation) {
        mutableBoolean1.value = !booleanValues2.last()
      } else {
        mutableBoolean2.value = !booleanValues1.last()
      }
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableBoolean1.equals(mutableBoolean2) shouldBe false
    }
  }

  @Test
  fun `MutableBoolean equals(value)`() = runTest {
    checkAll(propTestConfig, Arb.boolean()) { booleanValue ->
      val mutableBoolean = MutableBoolean(booleanValue)
      mutableBoolean.equals(mutableBoolean.value) shouldBe false
    }
  }

  @Test
  fun `MutableBoolean equals(null)`() = runTest {
    checkAll(propTestConfig, Arb.boolean()) { booleanValue ->
      val mutableBoolean = MutableBoolean(booleanValue)
      mutableBoolean.equals(null) shouldBe false
    }
  }

  @Test
  fun `MutableBoolean equals(different type)`() = runTest {
    checkAll(
      propTestConfig,
      Arb.boolean(),
      Arb.any(exclude = MutableBoolean::class, extra = Arb.boolean())
    ) { booleanValue, other ->
      val mutableBoolean = MutableBoolean(booleanValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableBoolean.equals(other) shouldBe false
    }
  }

  @Test
  fun `MutableByte value initialization from constructor`() = runTest {
    checkAll(propTestConfig, Arb.byte()) { byteValue ->
      val mutableByte = MutableByte(byteValue)
      mutableByte.value shouldBe byteValue
    }
  }

  @Test
  fun `MutableByte toString() initial value`() = runTest {
    checkAll(propTestConfig, Arb.byte()) { byteValue ->
      val mutableByte = MutableByte(byteValue)
      mutableByte.toString() shouldBe byteValue.toString()
    }
  }

  @Test
  fun `MutableByte toString() subsequent values`() = runTest {
    checkAll(propTestConfig, Arb.byte(), Arb.list(Arb.byte(), 10..10)) {
      initialByteValue,
      byteValues ->
      val mutableByte = MutableByte(initialByteValue)
      byteValues.forEach { byteValue ->
        mutableByte.value = byteValue
        mutableByte.toString() shouldBe byteValue.toString()
      }
    }
  }

  @Test
  fun `MutableByte hashCode() initial value`() = runTest {
    checkAll(propTestConfig, Arb.byte()) { byteValue ->
      val mutableByte = MutableByte(byteValue)
      mutableByte.hashCode() shouldBe byteValue.hashCode()
    }
  }

  @Test
  fun `MutableByte hashCode() subsequent values`() = runTest {
    checkAll(propTestConfig, Arb.byte(), Arb.list(Arb.byte(), 10..10)) {
      initialByteValue,
      byteValues ->
      val mutableByte = MutableByte(initialByteValue)
      byteValues.forEach { byteValue ->
        mutableByte.value = byteValue
        mutableByte.hashCode() shouldBe byteValue.hashCode()
      }
    }
  }

  @Test
  fun `MutableByte hashCode() and equals() consistency`() = runTest {
    checkAll(propTestConfig, Arb.byte()) { byteValue ->
      val mutableByte1 = MutableByte(byteValue)
      val mutableByte2 = MutableByte(byteValue)
      check(mutableByte1 == mutableByte2)
      mutableByte1.hashCode() shouldBe mutableByte2.hashCode()
    }
  }

  @Test
  fun `MutableByte equals(self)`() = runTest {
    checkAll(propTestConfig, Arb.byte()) { byteValue ->
      val mutableByte = MutableByte(byteValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableByte.equals(mutableByte) shouldBe true
    }
  }

  @Test
  fun `MutableByte equals(equal, but distinct, instance)`() = runTest {
    checkAll(propTestConfig, Arb.byte()) { byteValue ->
      val mutableByte1 = MutableByte(byteValue)
      val mutableByte2 = MutableByte(byteValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableByte1.equals(mutableByte2) shouldBe true
    }
  }

  @Test
  fun `MutableByte equals(equal, but distinct, instance, after mutations)`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.byte(), 10..10).pair(), Arb.byte()) {
      (byteValues1, byteValues2),
      finalByteValue ->
      val mutableByte1 = MutableByte(byteValues1[0])
      val mutableByte2 = MutableByte(byteValues2[0])
      byteValues1.drop(1).forEach { mutableByte1.value = it }
      byteValues2.drop(1).forEach { mutableByte2.value = it }
      mutableByte1.value = finalByteValue
      mutableByte2.value = finalByteValue
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableByte1.equals(mutableByte2) shouldBe true
    }
  }

  @Test
  fun `MutableByte equals(unequal instance)`() = runTest {
    checkAll(propTestConfig, Arb.byte().distinctPair()) { (byteValue1, byteValue2) ->
      val mutableByte1 = MutableByte(byteValue1)
      val mutableByte2 = MutableByte(byteValue2)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableByte1.equals(mutableByte2) shouldBe false
    }
  }

  @Test
  fun `MutableByte equals(unequal instance, after mutations)`() = runTest {
    checkAll(
      propTestConfig,
      Arb.byte(),
      Arb.list(Arb.byte(), 10..10).pair(),
      Arb.byte().distinctPair(),
    ) { initialByteValue, (byteValues1, byteValues2), (finalByteValue1, finalByteValue2) ->
      val mutableByte1 = MutableByte(initialByteValue)
      val mutableByte2 = MutableByte(initialByteValue)
      byteValues1.forEach { mutableByte1.value = it }
      byteValues2.forEach { mutableByte2.value = it }
      mutableByte1.value = finalByteValue1
      mutableByte2.value = finalByteValue2
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableByte1.equals(mutableByte2) shouldBe false
    }
  }

  @Test
  fun `MutableByte equals(value)`() = runTest {
    checkAll(propTestConfig, Arb.byte()) { byteValue ->
      val mutableByte = MutableByte(byteValue)
      mutableByte.equals(mutableByte.value) shouldBe false
    }
  }

  @Test
  fun `MutableByte equals(null)`() = runTest {
    checkAll(propTestConfig, Arb.byte()) { byteValue ->
      val mutableByte = MutableByte(byteValue)
      mutableByte.equals(null) shouldBe false
    }
  }

  @Test
  fun `MutableByte equals(different type)`() = runTest {
    checkAll(
      propTestConfig,
      Arb.byte(),
      Arb.any(exclude = MutableByte::class, extra = Arb.byte())
    ) { byteValue, other ->
      val mutableByte = MutableByte(byteValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableByte.equals(other) shouldBe false
    }
  }

  @Test
  fun `MutableShort value initialization from constructor`() = runTest {
    checkAll(propTestConfig, Arb.short()) { shortValue ->
      val mutableShort = MutableShort(shortValue)
      mutableShort.value shouldBe shortValue
    }
  }

  @Test
  fun `MutableShort toString() initial value`() = runTest {
    checkAll(propTestConfig, Arb.short()) { shortValue ->
      val mutableShort = MutableShort(shortValue)
      mutableShort.toString() shouldBe shortValue.toString()
    }
  }

  @Test
  fun `MutableShort toString() subsequent values`() = runTest {
    checkAll(propTestConfig, Arb.short(), Arb.list(Arb.short(), 10..10)) {
      initialShortValue,
      shortValues ->
      val mutableShort = MutableShort(initialShortValue)
      shortValues.forEach { shortValue ->
        mutableShort.value = shortValue
        mutableShort.toString() shouldBe shortValue.toString()
      }
    }
  }

  @Test
  fun `MutableShort hashCode() initial value`() = runTest {
    checkAll(propTestConfig, Arb.short()) { shortValue ->
      val mutableShort = MutableShort(shortValue)
      mutableShort.hashCode() shouldBe shortValue.hashCode()
    }
  }

  @Test
  fun `MutableShort hashCode() subsequent values`() = runTest {
    checkAll(propTestConfig, Arb.short(), Arb.list(Arb.short(), 10..10)) {
      initialShortValue,
      shortValues ->
      val mutableShort = MutableShort(initialShortValue)
      shortValues.forEach { shortValue ->
        mutableShort.value = shortValue
        mutableShort.hashCode() shouldBe shortValue.hashCode()
      }
    }
  }

  @Test
  fun `MutableShort hashCode() and equals() consistency`() = runTest {
    checkAll(propTestConfig, Arb.short()) { shortValue ->
      val mutableShort1 = MutableShort(shortValue)
      val mutableShort2 = MutableShort(shortValue)
      check(mutableShort1 == mutableShort2)
      mutableShort1.hashCode() shouldBe mutableShort2.hashCode()
    }
  }

  @Test
  fun `MutableShort equals(self)`() = runTest {
    checkAll(propTestConfig, Arb.short()) { shortValue ->
      val mutableShort = MutableShort(shortValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableShort.equals(mutableShort) shouldBe true
    }
  }

  @Test
  fun `MutableShort equals(equal, but distinct, instance)`() = runTest {
    checkAll(propTestConfig, Arb.short()) { shortValue ->
      val mutableShort1 = MutableShort(shortValue)
      val mutableShort2 = MutableShort(shortValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableShort1.equals(mutableShort2) shouldBe true
    }
  }

  @Test
  fun `MutableShort equals(equal, but distinct, instance, after mutations)`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.short(), 10..10).pair(), Arb.short()) {
      (shortValues1, shortValues2),
      finalShortValue ->
      val mutableShort1 = MutableShort(shortValues1[0])
      val mutableShort2 = MutableShort(shortValues2[0])
      shortValues1.drop(1).forEach { mutableShort1.value = it }
      shortValues2.drop(1).forEach { mutableShort2.value = it }
      mutableShort1.value = finalShortValue
      mutableShort2.value = finalShortValue
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableShort1.equals(mutableShort2) shouldBe true
    }
  }

  @Test
  fun `MutableShort equals(unequal instance)`() = runTest {
    checkAll(propTestConfig, Arb.short().distinctPair()) { (shortValue1, shortValue2) ->
      val mutableShort1 = MutableShort(shortValue1)
      val mutableShort2 = MutableShort(shortValue2)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableShort1.equals(mutableShort2) shouldBe false
    }
  }

  @Test
  fun `MutableShort equals(unequal instance, after mutations)`() = runTest {
    checkAll(
      propTestConfig,
      Arb.short(),
      Arb.list(Arb.short(), 10..10).pair(),
      Arb.short().distinctPair(),
    ) { initialShortValue, (shortValues1, shortValues2), (finalShortValue1, finalShortValue2) ->
      val mutableShort1 = MutableShort(initialShortValue)
      val mutableShort2 = MutableShort(initialShortValue)
      shortValues1.forEach { mutableShort1.value = it }
      shortValues2.forEach { mutableShort2.value = it }
      mutableShort1.value = finalShortValue1
      mutableShort2.value = finalShortValue2
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableShort1.equals(mutableShort2) shouldBe false
    }
  }

  @Test
  fun `MutableShort equals(value)`() = runTest {
    checkAll(propTestConfig, Arb.short()) { shortValue ->
      val mutableShort = MutableShort(shortValue)
      mutableShort.equals(mutableShort.value) shouldBe false
    }
  }

  @Test
  fun `MutableShort equals(null)`() = runTest {
    checkAll(propTestConfig, Arb.short()) { shortValue ->
      val mutableShort = MutableShort(shortValue)
      mutableShort.equals(null) shouldBe false
    }
  }

  @Test
  fun `MutableShort equals(different type)`() = runTest {
    checkAll(
      propTestConfig,
      Arb.short(),
      Arb.any(exclude = MutableShort::class, extra = Arb.short())
    ) { shortValue, other ->
      val mutableShort = MutableShort(shortValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableShort.equals(other) shouldBe false
    }
  }
  @Test
  fun `MutableInt value initialization from constructor`() = runTest {
    checkAll(propTestConfig, Arb.int()) { intValue ->
      val mutableInt = MutableInt(intValue)
      mutableInt.value shouldBe intValue
    }
  }

  @Test
  fun `MutableInt toString() initial value`() = runTest {
    checkAll(propTestConfig, Arb.int()) { intValue ->
      val mutableInt = MutableInt(intValue)
      mutableInt.toString() shouldBe intValue.toString()
    }
  }

  @Test
  fun `MutableInt toString() subsequent values`() = runTest {
    checkAll(propTestConfig, Arb.int(), Arb.list(Arb.int(), 10..10)) { initialIntValue, intValues ->
      val mutableInt = MutableInt(initialIntValue)
      intValues.forEach { intValue ->
        mutableInt.value = intValue
        mutableInt.toString() shouldBe intValue.toString()
      }
    }
  }

  @Test
  fun `MutableInt hashCode() initial value`() = runTest {
    checkAll(propTestConfig, Arb.int()) { intValue ->
      val mutableInt = MutableInt(intValue)
      mutableInt.hashCode() shouldBe intValue.hashCode()
    }
  }

  @Test
  fun `MutableInt hashCode() subsequent values`() = runTest {
    checkAll(propTestConfig, Arb.int(), Arb.list(Arb.int(), 10..10)) { initialIntValue, intValues ->
      val mutableInt = MutableInt(initialIntValue)
      intValues.forEach { intValue ->
        mutableInt.value = intValue
        mutableInt.hashCode() shouldBe intValue.hashCode()
      }
    }
  }

  @Test
  fun `MutableInt hashCode() and equals() consistency`() = runTest {
    checkAll(propTestConfig, Arb.int()) { intValue ->
      val mutableInt1 = MutableInt(intValue)
      val mutableInt2 = MutableInt(intValue)
      check(mutableInt1 == mutableInt2)
      mutableInt1.hashCode() shouldBe mutableInt2.hashCode()
    }
  }

  @Test
  fun `MutableInt equals(self)`() = runTest {
    checkAll(propTestConfig, Arb.int()) { intValue ->
      val mutableInt = MutableInt(intValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableInt.equals(mutableInt) shouldBe true
    }
  }

  @Test
  fun `MutableInt equals(equal, but distinct, instance)`() = runTest {
    checkAll(propTestConfig, Arb.int()) { intValue ->
      val mutableInt1 = MutableInt(intValue)
      val mutableInt2 = MutableInt(intValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableInt1.equals(mutableInt2) shouldBe true
    }
  }

  @Test
  fun `MutableInt equals(equal, but distinct, instance, after mutations)`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.int(), 10..10).pair(), Arb.int()) {
      (intValues1, intValues2),
      finalIntValue ->
      val mutableInt1 = MutableInt(intValues1[0])
      val mutableInt2 = MutableInt(intValues2[0])
      intValues1.drop(1).forEach { mutableInt1.value = it }
      intValues2.drop(1).forEach { mutableInt2.value = it }
      mutableInt1.value = finalIntValue
      mutableInt2.value = finalIntValue
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableInt1.equals(mutableInt2) shouldBe true
    }
  }

  @Test
  fun `MutableInt equals(unequal instance)`() = runTest {
    checkAll(propTestConfig, Arb.int().distinctPair()) { (intValue1, intValue2) ->
      val mutableInt1 = MutableInt(intValue1)
      val mutableInt2 = MutableInt(intValue2)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableInt1.equals(mutableInt2) shouldBe false
    }
  }

  @Test
  fun `MutableInt equals(unequal instance, after mutations)`() = runTest {
    checkAll(
      propTestConfig,
      Arb.int(),
      Arb.list(Arb.int(), 10..10).pair(),
      Arb.int().distinctPair(),
    ) { initialIntValue, (intValues1, intValues2), (finalIntValue1, finalIntValue2) ->
      val mutableInt1 = MutableInt(initialIntValue)
      val mutableInt2 = MutableInt(initialIntValue)
      intValues1.forEach { mutableInt1.value = it }
      intValues2.forEach { mutableInt2.value = it }
      mutableInt1.value = finalIntValue1
      mutableInt2.value = finalIntValue2
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableInt1.equals(mutableInt2) shouldBe false
    }
  }

  @Test
  fun `MutableInt equals(value)`() = runTest {
    checkAll(propTestConfig, Arb.int()) { intValue ->
      val mutableInt = MutableInt(intValue)
      mutableInt.equals(mutableInt.value) shouldBe false
    }
  }

  @Test
  fun `MutableInt equals(null)`() = runTest {
    checkAll(propTestConfig, Arb.int()) { intValue ->
      val mutableInt = MutableInt(intValue)
      mutableInt.equals(null) shouldBe false
    }
  }

  @Test
  fun `MutableInt equals(different type)`() = runTest {
    checkAll(propTestConfig, Arb.int(), Arb.any(exclude = MutableInt::class, extra = Arb.int())) {
      intValue,
      other ->
      val mutableInt = MutableInt(intValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableInt.equals(other) shouldBe false
    }
  }

  @Test
  fun `MutableLong value initialization from constructor`() = runTest {
    checkAll(propTestConfig, Arb.long()) { longValue ->
      val mutableLong = MutableLong(longValue)
      mutableLong.value shouldBe longValue
    }
  }

  @Test
  fun `MutableLong toString() initial value`() = runTest {
    checkAll(propTestConfig, Arb.long()) { longValue ->
      val mutableLong = MutableLong(longValue)
      mutableLong.toString() shouldBe longValue.toString()
    }
  }

  @Test
  fun `MutableLong toString() subsequent values`() = runTest {
    checkAll(propTestConfig, Arb.long(), Arb.list(Arb.long(), 10..10)) {
      initialLongValue,
      longValues ->
      val mutableLong = MutableLong(initialLongValue)
      longValues.forEach { longValue ->
        mutableLong.value = longValue
        mutableLong.toString() shouldBe longValue.toString()
      }
    }
  }

  @Test
  fun `MutableLong hashCode() initial value`() = runTest {
    checkAll(propTestConfig, Arb.long()) { longValue ->
      val mutableLong = MutableLong(longValue)
      mutableLong.hashCode() shouldBe longValue.hashCode()
    }
  }

  @Test
  fun `MutableLong hashCode() subsequent values`() = runTest {
    checkAll(propTestConfig, Arb.long(), Arb.list(Arb.long(), 10..10)) {
      initialLongValue,
      longValues ->
      val mutableLong = MutableLong(initialLongValue)
      longValues.forEach { longValue ->
        mutableLong.value = longValue
        mutableLong.hashCode() shouldBe longValue.hashCode()
      }
    }
  }

  @Test
  fun `MutableLong hashCode() and equals() consistency`() = runTest {
    checkAll(propTestConfig, Arb.long()) { longValue ->
      val mutableLong1 = MutableLong(longValue)
      val mutableLong2 = MutableLong(longValue)
      check(mutableLong1 == mutableLong2)
      mutableLong1.hashCode() shouldBe mutableLong2.hashCode()
    }
  }

  @Test
  fun `MutableLong equals(self)`() = runTest {
    checkAll(propTestConfig, Arb.long()) { longValue ->
      val mutableLong = MutableLong(longValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableLong.equals(mutableLong) shouldBe true
    }
  }

  @Test
  fun `MutableLong equals(equal, but distinct, instance)`() = runTest {
    checkAll(propTestConfig, Arb.long()) { longValue ->
      val mutableLong1 = MutableLong(longValue)
      val mutableLong2 = MutableLong(longValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableLong1.equals(mutableLong2) shouldBe true
    }
  }

  @Test
  fun `MutableLong equals(equal, but distinct, instance, after mutations)`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.long(), 10..10).pair(), Arb.long()) {
      (longValues1, longValues2),
      finalLongValue ->
      val mutableLong1 = MutableLong(longValues1[0])
      val mutableLong2 = MutableLong(longValues2[0])
      longValues1.drop(1).forEach { mutableLong1.value = it }
      longValues2.drop(1).forEach { mutableLong2.value = it }
      mutableLong1.value = finalLongValue
      mutableLong2.value = finalLongValue
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableLong1.equals(mutableLong2) shouldBe true
    }
  }

  @Test
  fun `MutableLong equals(unequal instance)`() = runTest {
    checkAll(propTestConfig, Arb.long().distinctPair()) { (longValue1, longValue2) ->
      val mutableLong1 = MutableLong(longValue1)
      val mutableLong2 = MutableLong(longValue2)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableLong1.equals(mutableLong2) shouldBe false
    }
  }

  @Test
  fun `MutableLong equals(unequal instance, after mutations)`() = runTest {
    checkAll(
      propTestConfig,
      Arb.long(),
      Arb.list(Arb.long(), 10..10).pair(),
      Arb.long().distinctPair(),
    ) { initialLongValue, (longValues1, longValues2), (finalLongValue1, finalLongValue2) ->
      val mutableLong1 = MutableLong(initialLongValue)
      val mutableLong2 = MutableLong(initialLongValue)
      longValues1.forEach { mutableLong1.value = it }
      longValues2.forEach { mutableLong2.value = it }
      mutableLong1.value = finalLongValue1
      mutableLong2.value = finalLongValue2
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableLong1.equals(mutableLong2) shouldBe false
    }
  }

  @Test
  fun `MutableLong equals(value)`() = runTest {
    checkAll(propTestConfig, Arb.long()) { longValue ->
      val mutableLong = MutableLong(longValue)
      mutableLong.equals(mutableLong.value) shouldBe false
    }
  }

  @Test
  fun `MutableLong equals(null)`() = runTest {
    checkAll(propTestConfig, Arb.long()) { longValue ->
      val mutableLong = MutableLong(longValue)
      mutableLong.equals(null) shouldBe false
    }
  }

  @Test
  fun `MutableLong equals(different type)`() = runTest {
    checkAll(
      propTestConfig,
      Arb.long(),
      Arb.any(exclude = MutableLong::class, extra = Arb.long())
    ) { longValue, other ->
      val mutableLong = MutableLong(longValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableLong.equals(other) shouldBe false
    }
  }

  @Test
  fun `MutableChar value initialization from constructor`() = runTest {
    checkAll(propTestConfig, Arb.char()) { charValue ->
      val mutableChar = MutableChar(charValue)
      mutableChar.value shouldBe charValue
    }
  }

  @Test
  fun `MutableChar toString() initial value`() = runTest {
    checkAll(propTestConfig, Arb.char()) { charValue ->
      val mutableChar = MutableChar(charValue)
      mutableChar.toString() shouldBe charValue.toString()
    }
  }

  @Test
  fun `MutableChar toString() subsequent values`() = runTest {
    checkAll(propTestConfig, Arb.char(), Arb.list(Arb.char(), 10..10)) {
      initialCharValue,
      charValues ->
      val mutableChar = MutableChar(initialCharValue)
      charValues.forEach { charValue ->
        mutableChar.value = charValue
        mutableChar.toString() shouldBe charValue.toString()
      }
    }
  }

  @Test
  fun `MutableChar hashCode() initial value`() = runTest {
    checkAll(propTestConfig, Arb.char()) { charValue ->
      val mutableChar = MutableChar(charValue)
      mutableChar.hashCode() shouldBe charValue.hashCode()
    }
  }

  @Test
  fun `MutableChar hashCode() subsequent values`() = runTest {
    checkAll(propTestConfig, Arb.char(), Arb.list(Arb.char(), 10..10)) {
      initialCharValue,
      charValues ->
      val mutableChar = MutableChar(initialCharValue)
      charValues.forEach { charValue ->
        mutableChar.value = charValue
        mutableChar.hashCode() shouldBe charValue.hashCode()
      }
    }
  }

  @Test
  fun `MutableChar hashCode() and equals() consistency`() = runTest {
    checkAll(propTestConfig, Arb.char()) { charValue ->
      val mutableChar1 = MutableChar(charValue)
      val mutableChar2 = MutableChar(charValue)
      check(mutableChar1 == mutableChar2)
      mutableChar1.hashCode() shouldBe mutableChar2.hashCode()
    }
  }

  @Test
  fun `MutableChar equals(self)`() = runTest {
    checkAll(propTestConfig, Arb.char()) { charValue ->
      val mutableChar = MutableChar(charValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableChar.equals(mutableChar) shouldBe true
    }
  }

  @Test
  fun `MutableChar equals(equal, but distinct, instance)`() = runTest {
    checkAll(propTestConfig, Arb.char()) { charValue ->
      val mutableChar1 = MutableChar(charValue)
      val mutableChar2 = MutableChar(charValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableChar1.equals(mutableChar2) shouldBe true
    }
  }

  @Test
  fun `MutableChar equals(equal, but distinct, instance, after mutations)`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.char(), 10..10).pair(), Arb.char()) {
      (charValues1, charValues2),
      finalCharValue ->
      val mutableChar1 = MutableChar(charValues1[0])
      val mutableChar2 = MutableChar(charValues2[0])
      charValues1.drop(1).forEach { mutableChar1.value = it }
      charValues2.drop(1).forEach { mutableChar2.value = it }
      mutableChar1.value = finalCharValue
      mutableChar2.value = finalCharValue
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableChar1.equals(mutableChar2) shouldBe true
    }
  }

  @Test
  fun `MutableChar equals(unequal instance)`() = runTest {
    checkAll(propTestConfig, Arb.char().distinctPair()) { (charValue1, charValue2) ->
      val mutableChar1 = MutableChar(charValue1)
      val mutableChar2 = MutableChar(charValue2)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableChar1.equals(mutableChar2) shouldBe false
    }
  }

  @Test
  fun `MutableChar equals(unequal instance, after mutations)`() = runTest {
    checkAll(
      propTestConfig,
      Arb.char(),
      Arb.list(Arb.char(), 10..10).pair(),
      Arb.char().distinctPair(),
    ) { initialCharValue, (charValues1, charValues2), (finalCharValue1, finalCharValue2) ->
      val mutableChar1 = MutableChar(initialCharValue)
      val mutableChar2 = MutableChar(initialCharValue)
      charValues1.forEach { mutableChar1.value = it }
      charValues2.forEach { mutableChar2.value = it }
      mutableChar1.value = finalCharValue1
      mutableChar2.value = finalCharValue2
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableChar1.equals(mutableChar2) shouldBe false
    }
  }

  @Test
  fun `MutableChar equals(value)`() = runTest {
    checkAll(propTestConfig, Arb.char()) { charValue ->
      val mutableChar = MutableChar(charValue)
      mutableChar.equals(mutableChar.value) shouldBe false
    }
  }

  @Test
  fun `MutableChar equals(null)`() = runTest {
    checkAll(propTestConfig, Arb.char()) { charValue ->
      val mutableChar = MutableChar(charValue)
      mutableChar.equals(null) shouldBe false
    }
  }

  @Test
  fun `MutableChar equals(different type)`() = runTest {
    checkAll(
      propTestConfig,
      Arb.char(),
      Arb.any(exclude = MutableChar::class, extra = Arb.char())
    ) { charValue, other ->
      val mutableChar = MutableChar(charValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableChar.equals(other) shouldBe false
    }
  }

  @Test
  fun `MutableFloat value initialization from constructor`() = runTest {
    checkAll(propTestConfig, Arb.float()) { floatValue ->
      val mutableFloat = MutableFloat(floatValue)
      mutableFloat.value shouldBe floatValue
    }
  }

  @Test
  fun `MutableFloat toString() initial value`() = runTest {
    checkAll(propTestConfig, Arb.float()) { floatValue ->
      val mutableFloat = MutableFloat(floatValue)
      mutableFloat.toString() shouldBe floatValue.toString()
    }
  }

  @Test
  fun `MutableFloat toString() subsequent values`() = runTest {
    checkAll(propTestConfig, Arb.float(), Arb.list(Arb.float(), 10..10)) {
      initialFloatValue,
      floatValues ->
      val mutableFloat = MutableFloat(initialFloatValue)
      floatValues.forEach { floatValue ->
        mutableFloat.value = floatValue
        mutableFloat.toString() shouldBe floatValue.toString()
      }
    }
  }

  @Test
  fun `MutableFloat hashCode() initial value`() = runTest {
    checkAll(propTestConfig, Arb.float()) { floatValue ->
      val mutableFloat = MutableFloat(floatValue)
      mutableFloat.hashCode() shouldBe floatValue.hashCode()
    }
  }

  @Test
  fun `MutableFloat hashCode() subsequent values`() = runTest {
    checkAll(propTestConfig, Arb.float(), Arb.list(Arb.float(), 10..10)) {
      initialFloatValue,
      floatValues ->
      val mutableFloat = MutableFloat(initialFloatValue)
      floatValues.forEach { floatValue ->
        mutableFloat.value = floatValue
        mutableFloat.hashCode() shouldBe floatValue.hashCode()
      }
    }
  }

  @Test
  fun `MutableFloat hashCode() and equals() consistency`() = runTest {
    checkAll(propTestConfig, Arb.float()) { floatValue ->
      val mutableFloat1 = MutableFloat(floatValue)
      val mutableFloat2 = MutableFloat(floatValue)
      check(mutableFloat1 == mutableFloat2)
      mutableFloat1.hashCode() shouldBe mutableFloat2.hashCode()
    }
  }

  @Test
  fun `MutableFloat equals(self)`() = runTest {
    checkAll(propTestConfig, Arb.float()) { floatValue ->
      val mutableFloat = MutableFloat(floatValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableFloat.equals(mutableFloat) shouldBe true
    }
  }

  @Test
  fun `MutableFloat equals(equal, but distinct, instance)`() = runTest {
    checkAll(propTestConfig, Arb.float()) { floatValue ->
      val mutableFloat1 = MutableFloat(floatValue)
      val mutableFloat2 = MutableFloat(floatValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableFloat1.equals(mutableFloat2) shouldBe true
    }
  }

  @Test
  fun `MutableFloat equals(equal, but distinct, instance, after mutations)`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.float(), 10..10).pair(), Arb.float()) {
      (floatValues1, floatValues2),
      finalFloatValue ->
      val mutableFloat1 = MutableFloat(floatValues1[0])
      val mutableFloat2 = MutableFloat(floatValues2[0])
      floatValues1.drop(1).forEach { mutableFloat1.value = it }
      floatValues2.drop(1).forEach { mutableFloat2.value = it }
      mutableFloat1.value = finalFloatValue
      mutableFloat2.value = finalFloatValue
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableFloat1.equals(mutableFloat2) shouldBe true
    }
  }

  @Test
  fun `MutableFloat equals(unequal instance)`() = runTest {
    checkAll(propTestConfig, Arb.float().distinctPair()) { (floatValue1, floatValue2) ->
      val mutableFloat1 = MutableFloat(floatValue1)
      val mutableFloat2 = MutableFloat(floatValue2)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableFloat1.equals(mutableFloat2) shouldBe false
    }
  }

  @Test
  fun `MutableFloat equals(unequal instance, after mutations)`() = runTest {
    checkAll(
      propTestConfig,
      Arb.float(),
      Arb.list(Arb.float(), 10..10).pair(),
      Arb.float().distinctPair(),
    ) { initialFloatValue, (floatValues1, floatValues2), (finalFloatValue1, finalFloatValue2) ->
      val mutableFloat1 = MutableFloat(initialFloatValue)
      val mutableFloat2 = MutableFloat(initialFloatValue)
      floatValues1.forEach { mutableFloat1.value = it }
      floatValues2.forEach { mutableFloat2.value = it }
      mutableFloat1.value = finalFloatValue1
      mutableFloat2.value = finalFloatValue2
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableFloat1.equals(mutableFloat2) shouldBe false
    }
  }

  @Test
  fun `MutableFloat equals(value)`() = runTest {
    checkAll(propTestConfig, Arb.float()) { floatValue ->
      val mutableFloat = MutableFloat(floatValue)
      mutableFloat.equals(mutableFloat.value) shouldBe false
    }
  }

  @Test
  fun `MutableFloat equals(null)`() = runTest {
    checkAll(propTestConfig, Arb.float()) { floatValue ->
      val mutableFloat = MutableFloat(floatValue)
      mutableFloat.equals(null) shouldBe false
    }
  }

  @Test
  fun `MutableFloat equals(different type)`() = runTest {
    checkAll(
      propTestConfig,
      Arb.float(),
      Arb.any(exclude = MutableFloat::class, extra = Arb.float())
    ) { floatValue, other ->
      val mutableFloat = MutableFloat(floatValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableFloat.equals(other) shouldBe false
    }
  }

  @Test
  fun `MutableFloat equals(-0 and +0)`() = runTest {
    val arb = Arb.of(Pair(-0.0f, 0.0f), Pair(0.0f, -0.0f))
    checkAll(propTestConfig, arb) { (floatValue1, floatValue2) ->
      val mutableFloat1 = MutableFloat(floatValue1)
      val mutableFloat2 = MutableFloat(floatValue2)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableFloat1.equals(mutableFloat2) shouldBe false
    }
  }

  @Test
  fun `MutableFloat hashCode(-0 and +0)`() = runTest {
    val arb = Arb.of(Pair(-0.0f, 0.0f), Pair(0.0f, -0.0f))
    checkAll(propTestConfig, arb) { (floatValue1, floatValue2) ->
      val mutableFloat1 = MutableFloat(floatValue1)
      val mutableFloat2 = MutableFloat(floatValue2)
      mutableFloat1.hashCode() shouldNotBe mutableFloat2.hashCode()
    }
  }

  @Test
  fun `MutableFloat equals(NaN)`() = runTest {
    val mutableFloat1 = MutableFloat(Float.NaN)
    val mutableFloat2 = MutableFloat(Float.NaN)
    @Suppress("ReplaceCallWithBinaryOperator")
    mutableFloat1.equals(mutableFloat2) shouldBe true
  }

  @Test
  fun `MutableFloat hashCode(NaN)`() = runTest {
    val mutableFloat1 = MutableFloat(Float.NaN)
    val mutableFloat2 = MutableFloat(Float.NaN)
    mutableFloat1.hashCode() shouldBe mutableFloat2.hashCode()
  }

  @Test
  fun `MutableDouble value initialization from constructor`() = runTest {
    checkAll(propTestConfig, Arb.double()) { doubleValue ->
      val mutableDouble = MutableDouble(doubleValue)
      mutableDouble.value shouldBe doubleValue
    }
  }

  @Test
  fun `MutableDouble toString() initial value`() = runTest {
    checkAll(propTestConfig, Arb.double()) { doubleValue ->
      val mutableDouble = MutableDouble(doubleValue)
      mutableDouble.toString() shouldBe doubleValue.toString()
    }
  }

  @Test
  fun `MutableDouble toString() subsequent values`() = runTest {
    checkAll(propTestConfig, Arb.double(), Arb.list(Arb.double(), 10..10)) {
      initialDoubleValue,
      doubleValues ->
      val mutableDouble = MutableDouble(initialDoubleValue)
      doubleValues.forEach { doubleValue ->
        mutableDouble.value = doubleValue
        mutableDouble.toString() shouldBe doubleValue.toString()
      }
    }
  }

  @Test
  fun `MutableDouble hashCode() initial value`() = runTest {
    checkAll(propTestConfig, Arb.double()) { doubleValue ->
      val mutableDouble = MutableDouble(doubleValue)
      mutableDouble.hashCode() shouldBe doubleValue.hashCode()
    }
  }

  @Test
  fun `MutableDouble hashCode() subsequent values`() = runTest {
    checkAll(propTestConfig, Arb.double(), Arb.list(Arb.double(), 10..10)) {
      initialDoubleValue,
      doubleValues ->
      val mutableDouble = MutableDouble(initialDoubleValue)
      doubleValues.forEach { doubleValue ->
        mutableDouble.value = doubleValue
        mutableDouble.hashCode() shouldBe doubleValue.hashCode()
      }
    }
  }

  @Test
  fun `MutableDouble hashCode() and equals() consistency`() = runTest {
    checkAll(propTestConfig, Arb.double()) { doubleValue ->
      val mutableDouble1 = MutableDouble(doubleValue)
      val mutableDouble2 = MutableDouble(doubleValue)
      check(mutableDouble1 == mutableDouble2)
      mutableDouble1.hashCode() shouldBe mutableDouble2.hashCode()
    }
  }

  @Test
  fun `MutableDouble equals(self)`() = runTest {
    checkAll(propTestConfig, Arb.double()) { doubleValue ->
      val mutableDouble = MutableDouble(doubleValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableDouble.equals(mutableDouble) shouldBe true
    }
  }

  @Test
  fun `MutableDouble equals(equal, but distinct, instance)`() = runTest {
    checkAll(propTestConfig, Arb.double()) { doubleValue ->
      val mutableDouble1 = MutableDouble(doubleValue)
      val mutableDouble2 = MutableDouble(doubleValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableDouble1.equals(mutableDouble2) shouldBe true
    }
  }

  @Test
  fun `MutableDouble equals(equal, but distinct, instance, after mutations)`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.double(), 10..10).pair(), Arb.double()) {
      (doubleValues1, doubleValues2),
      finalDoubleValue ->
      val mutableDouble1 = MutableDouble(doubleValues1[0])
      val mutableDouble2 = MutableDouble(doubleValues2[0])
      doubleValues1.drop(1).forEach { mutableDouble1.value = it }
      doubleValues2.drop(1).forEach { mutableDouble2.value = it }
      mutableDouble1.value = finalDoubleValue
      mutableDouble2.value = finalDoubleValue
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableDouble1.equals(mutableDouble2) shouldBe true
    }
  }

  @Test
  fun `MutableDouble equals(unequal instance)`() = runTest {
    checkAll(propTestConfig, Arb.double().distinctPair()) { (doubleValue1, doubleValue2) ->
      val mutableDouble1 = MutableDouble(doubleValue1)
      val mutableDouble2 = MutableDouble(doubleValue2)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableDouble1.equals(mutableDouble2) shouldBe false
    }
  }

  @Test
  fun `MutableDouble equals(unequal instance, after mutations)`() = runTest {
    checkAll(
      propTestConfig,
      Arb.double(),
      Arb.list(Arb.double(), 10..10).pair(),
      Arb.double().distinctPair(),
    ) { initialDoubleValue, (doubleValues1, doubleValues2), (finalDoubleValue1, finalDoubleValue2)
      ->
      val mutableDouble1 = MutableDouble(initialDoubleValue)
      val mutableDouble2 = MutableDouble(initialDoubleValue)
      doubleValues1.forEach { mutableDouble1.value = it }
      doubleValues2.forEach { mutableDouble2.value = it }
      mutableDouble1.value = finalDoubleValue1
      mutableDouble2.value = finalDoubleValue2
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableDouble1.equals(mutableDouble2) shouldBe false
    }
  }

  @Test
  fun `MutableDouble equals(value)`() = runTest {
    checkAll(propTestConfig, Arb.double()) { doubleValue ->
      val mutableDouble = MutableDouble(doubleValue)
      mutableDouble.equals(mutableDouble.value) shouldBe false
    }
  }

  @Test
  fun `MutableDouble equals(null)`() = runTest {
    checkAll(propTestConfig, Arb.double()) { doubleValue ->
      val mutableDouble = MutableDouble(doubleValue)
      mutableDouble.equals(null) shouldBe false
    }
  }

  @Test
  fun `MutableDouble equals(different type)`() = runTest {
    checkAll(
      propTestConfig,
      Arb.double(),
      Arb.any(exclude = MutableDouble::class, extra = Arb.double())
    ) { doubleValue, other ->
      val mutableDouble = MutableDouble(doubleValue)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableDouble.equals(other) shouldBe false
    }
  }

  @Test
  fun `MutableDouble equals(-0 and +0)`() = runTest {
    val arb = Arb.of(Pair(-0.0, 0.0), Pair(0.0, -0.0))
    checkAll(propTestConfig, arb) { (doubleValue1, doubleValue2) ->
      val mutableDouble1 = MutableDouble(doubleValue1)
      val mutableDouble2 = MutableDouble(doubleValue2)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableDouble1.equals(mutableDouble2) shouldBe false
    }
  }

  @Test
  fun `MutableDouble hashCode(-0 and +0)`() = runTest {
    val arb = Arb.of(Pair(-0.0, 0.0), Pair(0.0, -0.0))
    checkAll(propTestConfig, arb) { (doubleValue1, doubleValue2) ->
      val mutableDouble1 = MutableDouble(doubleValue1)
      val mutableDouble2 = MutableDouble(doubleValue2)
      mutableDouble1.hashCode() shouldNotBe mutableDouble2.hashCode()
    }
  }

  @Test
  fun `MutableDouble equals(NaN)`() = runTest {
    val mutableDouble1 = MutableDouble(Double.NaN)
    val mutableDouble2 = MutableDouble(Double.NaN)
    @Suppress("ReplaceCallWithBinaryOperator")
    mutableDouble1.equals(mutableDouble2) shouldBe true
  }

  @Test
  fun `MutableDouble hashCode(NaN)`() = runTest {
    val mutableDouble1 = MutableDouble(Double.NaN)
    val mutableDouble2 = MutableDouble(Double.NaN)
    mutableDouble1.hashCode() shouldBe mutableDouble2.hashCode()
  }

  @Test
  fun `MutableReference value initialization from constructor`() = runTest {
    checkAll(propTestConfig, Arb.any()) { value ->
      val mutableReference = MutableReference(value)
      mutableReference.value shouldBe value
    }
  }

  @Test
  fun `MutableReference toString() initial value`() = runTest {
    checkAll(propTestConfig, Arb.any()) { value ->
      val mutableReference = MutableReference(value)
      mutableReference.toString() shouldBe value.toString()
    }
  }

  @Test
  fun `MutableReference toString() subsequent values`() = runTest {
    checkAll(propTestConfig, Arb.any(), Arb.list(Arb.any(), 10..10)) { initialReferenceValue, values
      ->
      val mutableReference = MutableReference(initialReferenceValue)
      values.forEach { value ->
        mutableReference.value = value
        mutableReference.toString() shouldBe value.toString()
      }
    }
  }

  @Test
  fun `MutableReference hashCode() initial value`() = runTest {
    checkAll(propTestConfig, Arb.any()) { value ->
      val mutableReference = MutableReference(value)
      mutableReference.hashCode() shouldBe value.hashCode()
    }
  }

  @Test
  fun `MutableReference hashCode() subsequent values`() = runTest {
    checkAll(propTestConfig, Arb.any(), Arb.list(Arb.any(), 10..10)) { initialReferenceValue, values
      ->
      val mutableReference = MutableReference(initialReferenceValue)
      values.forEach { value ->
        mutableReference.value = value
        mutableReference.hashCode() shouldBe value.hashCode()
      }
    }
  }

  @Test
  fun `MutableReference hashCode() and equals() consistency`() = runTest {
    checkAll(propTestConfig, Arb.any()) { value ->
      val mutableReference1 = MutableReference(value)
      val mutableReference2 = MutableReference(value)
      check(mutableReference1 == mutableReference2)
      mutableReference1.hashCode() shouldBe mutableReference2.hashCode()
    }
  }

  @Test
  fun `MutableReference equals(self)`() = runTest {
    checkAll(propTestConfig, Arb.any()) { value ->
      val mutableReference = MutableReference(value)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableReference.equals(mutableReference) shouldBe true
    }
  }

  @Test
  fun `MutableReference equals(equal, but distinct, instance)`() = runTest {
    checkAll(propTestConfig, Arb.any()) { value ->
      val mutableReference1 = MutableReference(value)
      val mutableReference2 = MutableReference(value)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableReference1.equals(mutableReference2) shouldBe true
    }
  }

  @Test
  fun `MutableReference equals(equal, but distinct, instance, after mutations)`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.any(), 10..10).pair(), Arb.any()) {
      (values1, values2),
      finalReferenceValue ->
      val mutableReference1 = MutableReference(values1[0])
      val mutableReference2 = MutableReference(values2[0])
      values1.drop(1).forEach { mutableReference1.value = it }
      values2.drop(1).forEach { mutableReference2.value = it }
      mutableReference1.value = finalReferenceValue
      mutableReference2.value = finalReferenceValue
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableReference1.equals(mutableReference2) shouldBe true
    }
  }

  @Test
  fun `MutableReference equals(unequal instance)`() = runTest {
    checkAll(propTestConfig, Arb.any().distinctPair()) { (value1, value2) ->
      val mutableReference1 = MutableReference(value1)
      val mutableReference2 = MutableReference(value2)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableReference1.equals(mutableReference2) shouldBe false
    }
  }

  @Test
  fun `MutableReference equals(unequal instance, after mutations)`() = runTest {
    checkAll(
      propTestConfig,
      Arb.any(),
      Arb.list(Arb.any(), 10..10).pair(),
      Arb.any().distinctPair(),
    ) { initialReferenceValue, (values1, values2), (finalReferenceValue1, finalReferenceValue2) ->
      val mutableReference1 = MutableReference(initialReferenceValue)
      val mutableReference2 = MutableReference(initialReferenceValue)
      values1.forEach { mutableReference1.value = it }
      values2.forEach { mutableReference2.value = it }
      mutableReference1.value = finalReferenceValue1
      mutableReference2.value = finalReferenceValue2
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableReference1.equals(mutableReference2) shouldBe false
    }
  }

  @Test
  fun `MutableReference equals(value)`() = runTest {
    checkAll(propTestConfig, Arb.any()) { value ->
      val mutableReference = MutableReference(value)
      mutableReference.equals(mutableReference.value) shouldBe false
    }
  }

  @Test
  fun `MutableReference equals(null)`() = runTest {
    checkAll(propTestConfig, Arb.any()) { value ->
      val mutableReference = MutableReference(value)
      mutableReference.equals(null) shouldBe false
    }
  }

  @Test
  fun `MutableReference equals(different type)`() = runTest {
    checkAll(propTestConfig, Arb.any(), Arb.any(exclude = MutableReference::class)) { value, other
      ->
      val mutableReference = MutableReference(value)
      @Suppress("ReplaceCallWithBinaryOperator")
      mutableReference.equals(other) shouldBe false
    }
  }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 200,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )
