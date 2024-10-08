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

package com.google.firebase.dataconnect

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.intRange
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.short
import io.kotest.property.arbitrary.string
import kotlinx.serialization.Serializable

object SerializationTestData {

  enum class TestEnum {
    A,
    B,
    C,
    D,
  }

  @Serializable @JvmInline value class TestStringValueClass(val a: String)

  @Serializable @JvmInline value class TestIntValueClass(val a: Int)

  @Serializable data class TestData1(val s: String, val i: Int)

  fun Arb.Companion.serializationTestData1(
    s: Arb<String> = Arb.string(),
    i: Arb<Int> = Arb.int()
  ): Arb<TestData1> = arbitrary { TestData1(s.bind(), i.bind()) }

  @Serializable data class TestData2(val td: TestData1, val ntd: TestData1?)

  fun Arb.Companion.serializationTestData2(
    testData1: Arb<TestData1> = Arb.serializationTestData1()
  ): Arb<TestData2> = arbitrary { TestData2(testData1.bind(), testData1.orNull(0.33).bind()) }

  @Serializable
  data class AllTheTypes(
    val boolean: Boolean,
    val byte: Byte,
    val char: Char,
    val double: Double,
    val enum: TestEnum,
    val float: Float,
    val inlineString: TestStringValueClass,
    val inlineInt: TestIntValueClass,
    val int: Int,
    val long: Long,
    val unit: Unit,
    val short: Short,
    val string: String,
    val testData: TestData2,
    val booleanList: List<Boolean>,
    val byteList: List<Byte>,
    val charList: List<Char>,
    val doubleList: List<Double>,
    val enumList: List<TestEnum>,
    val floatList: List<Float>,
    val inlineStringList: List<TestStringValueClass>,
    val inlineIntList: List<TestIntValueClass>,
    val intList: List<Int>,
    val longList: List<Long>,
    val shortList: List<Short>,
    val stringList: List<String>,
    val testDataList: List<TestData2>,
    val booleanNullable: Boolean?,
    val byteNullable: Byte?,
    val charNullable: Char?,
    val doubleNullable: Double?,
    val enumNullable: TestEnum?,
    val floatNullable: Float?,
    val inlineStringNullable: TestStringValueClass?,
    val inlineIntNullable: TestIntValueClass?,
    val intNullable: Int?,
    val longNullable: Long?,
    val shortNullable: Short?,
    val stringNullable: String?,
    val testDataNullable: TestData2?,
    val booleanNullableList: List<Boolean?>,
    val byteNullableList: List<Byte?>,
    val charNullableList: List<Char?>,
    val doubleNullableList: List<Double?>,
    val enumNullableList: List<TestEnum?>,
    val floatNullableList: List<Float?>,
    val inlineStringNullableList: List<TestStringValueClass?>,
    val inlineIntNullableList: List<TestIntValueClass?>,
    val intNullableList: List<Int?>,
    val longNullableList: List<Long?>,
    val shortNullableList: List<Short?>,
    val stringNullableList: List<String?>,
    val testDataNullableList: List<TestData2?>,
    val nested: AllTheTypes?,
    val nullableUnit: Unit?,
    val listOfUnit: List<Unit>,
    val listOfNullableUnit: List<Unit?>,
  )

  fun Arb.Companion.serializationTestDataAllTypes(
    boolean: Arb<Boolean> = Arb.boolean(),
    byte: Arb<Byte> = Arb.byte(),
    char: Arb<Char> = Arb.char(),
    short: Arb<Short> = Arb.short(),
    int: Arb<Int> = Arb.int(),
    long: Arb<Long> = Arb.long(),
    float: Arb<Float> = Arb.float(),
    double: Arb<Double> = Arb.double(),
    string: Arb<String> = Arb.string(),
    enum: Arb<TestEnum> = Arb.enum<TestEnum>(),
    testData2: Arb<TestData2> = Arb.serializationTestData2(),
    listSize: Arb<IntRange> = Arb.intRange(0..20).filterNot { it.isEmpty() },
  ): Arb<AllTheTypes> = arbitrary {
    val inlineString: Arb<TestStringValueClass> = arbitrary { TestStringValueClass(string.bind()) }
    val inlineInt: Arb<TestIntValueClass> = arbitrary { TestIntValueClass(int.bind()) }
    val nullProbability = 0.33

    AllTheTypes(
      boolean = boolean.bind(),
      byte = byte.bind(),
      char = char.bind(),
      double = double.bind(),
      enum = enum.bind(),
      float = float.bind(),
      inlineString = inlineString.bind(),
      inlineInt = inlineInt.bind(),
      int = int.bind(),
      long = long.bind(),
      unit = Unit,
      short = short.bind(),
      string = string.bind(),
      testData = testData2.bind(),
      booleanList = Arb.list(boolean, listSize).bind(),
      byteList = Arb.list(byte, listSize).bind(),
      charList = Arb.list(char, listSize).bind(),
      doubleList = Arb.list(double, listSize).bind(),
      enumList = Arb.list(enum, listSize).bind(),
      floatList = Arb.list(float, listSize).bind(),
      inlineStringList = Arb.list(inlineString, listSize).bind(),
      inlineIntList = Arb.list(inlineInt, listSize).bind(),
      intList = Arb.list(int, listSize).bind(),
      longList = Arb.list(long, listSize).bind(),
      shortList = Arb.list(short, listSize).bind(),
      stringList = Arb.list(string, listSize).bind(),
      testDataList = Arb.list(testData2, listSize).bind(),
      booleanNullable = boolean.orNull(nullProbability).bind(),
      byteNullable = byte.orNull(nullProbability).bind(),
      charNullable = char.orNull(nullProbability).bind(),
      doubleNullable = double.orNull(nullProbability).bind(),
      enumNullable = enum.orNull(nullProbability).bind(),
      floatNullable = float.orNull(nullProbability).bind(),
      inlineStringNullable = inlineString.orNull(nullProbability).bind(),
      inlineIntNullable = inlineInt.orNull(nullProbability).bind(),
      intNullable = int.orNull(nullProbability).bind(),
      longNullable = long.orNull(nullProbability).bind(),
      shortNullable = short.orNull(nullProbability).bind(),
      stringNullable = string.orNull(nullProbability).bind(),
      testDataNullable = testData2.orNull(nullProbability).bind(),
      booleanNullableList = Arb.list(boolean.orNull(nullProbability), listSize).bind(),
      byteNullableList = Arb.list(byte.orNull(nullProbability), listSize).bind(),
      charNullableList = Arb.list(char.orNull(nullProbability), listSize).bind(),
      doubleNullableList = Arb.list(double.orNull(nullProbability), listSize).bind(),
      enumNullableList = Arb.list(enum.orNull(nullProbability), listSize).bind(),
      floatNullableList = Arb.list(float.orNull(nullProbability), listSize).bind(),
      inlineStringNullableList = Arb.list(inlineString.orNull(nullProbability), listSize).bind(),
      inlineIntNullableList = Arb.list(inlineInt.orNull(nullProbability), listSize).bind(),
      intNullableList = Arb.list(int.orNull(nullProbability), listSize).bind(),
      longNullableList = Arb.list(long.orNull(nullProbability), listSize).bind(),
      shortNullableList = Arb.list(short.orNull(nullProbability), listSize).bind(),
      stringNullableList = Arb.list(string.orNull(nullProbability), listSize).bind(),
      testDataNullableList = Arb.list(testData2.orNull(nullProbability), listSize).bind(),
      nested =
        Arb.serializationTestDataAllTypes(
            boolean = boolean,
            byte = byte,
            char = char,
            short = short,
            int = int,
            long = long,
            float = float,
            double = double,
            string = string,
            enum = enum,
            testData2 = testData2,
            listSize = listSize,
          )
          .orNull(nullProbability = 0.15)
          .bind(),
      nullableUnit = Arb.constant(Unit).orNull(nullProbability).bind(),
      listOfUnit = Arb.list(Arb.constant(Unit), listSize).bind(),
      listOfNullableUnit = Arb.list(Arb.constant(Unit).orNull(nullProbability), listSize).bind(),
    )
  }

  /**
   * Creates and returns a new instance with the exact same property values but with all lists of
   * [Unit] to be empty. This may be useful if testing an encoder/decoder that does not support
   * [kotlinx.serialization.descriptors.StructureKind.OBJECT] in lists.
   */
  fun AllTheTypes.withEmptyListOfUnitRecursive(): AllTheTypes =
    copy(
      listOfUnit = emptyList(),
      nested = nested?.withEmptyListOfUnitRecursive(),
    )
}
