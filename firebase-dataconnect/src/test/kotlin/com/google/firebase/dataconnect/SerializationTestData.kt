// Copyright 2023 Google LLC
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

package com.google.firebase.dataconnect

import kotlin.math.PI
import kotlin.math.abs
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

  @Serializable
  data class TestData1(val s: String, val i: Int) {
    companion object {
      fun newInstance(seed: String = "abcdef01234567890"): TestData1 =
        seed.run { TestData1(s = seededString("s"), i = seededInt("i")) }
    }
  }

  @Serializable
  data class TestData2(val td: TestData1, val ntd: TestData1?, val noll: TestData1?) {
    companion object {
      fun newInstance(seed: String = "abcdef01234567890"): TestData2 =
        TestData2(
          td = TestData1.newInstance(seed + "td"),
          ntd = TestData1.newInstance(seed + "ntd"),
          noll = null
        )
    }
  }

  @Serializable
  data class AllTheTypes(
    val boolean: Boolean,
    val byte: Byte,
    val char: Char,
    val double: Double,
    val doubleMinValue: Double,
    val doubleMaxValue: Double,
    val doubleNegativeInfinity: Double,
    val doublePositiveInfinity: Double,
    val doubleNaN: Double,
    val enum: TestEnum,
    val float: Float,
    val floatMinValue: Float,
    val floatMaxValue: Float,
    val floatNegativeInfinity: Float,
    val floatPositiveInfinity: Float,
    val floatNaN: Float,
    val inlineString: TestStringValueClass,
    val inlineInt: TestIntValueClass,
    val int: Int,
    val long: Long,
    val noll: Unit?,
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
    val booleanNull: Boolean?,
    val byteNull: Byte?,
    val charNull: Char?,
    val doubleNull: Double?,
    val enumNull: TestEnum?,
    val floatNull: Float?,
    val inlineStringNull: TestStringValueClass?,
    val inlineIntNull: TestIntValueClass?,
    val intNull: Int?,
    val longNull: Long?,
    val shortNull: Short?,
    val stringNull: String?,
    val testDataNull: TestData2?,
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
    val unit: Unit,
    val nullUnit: Unit?,
    val nullableUnit: Unit?,
    val listOfUnit: List<Unit>,
    val listOfNullableUnit: List<Unit?>,
  ) {
    companion object {

      fun newInstance(seed: String = "abcdef01234567890", nesting: Int = 1): AllTheTypes =
        seed.run {
          AllTheTypes(
            boolean = seededBoolean("plain"),
            byte = seededByte("plain"),
            char = seededChar("plain"),
            double = seededDouble("plain"),
            doubleMinValue = Double.MIN_VALUE,
            doubleMaxValue = Double.MAX_VALUE,
            doubleNegativeInfinity = Double.POSITIVE_INFINITY,
            doublePositiveInfinity = Double.NEGATIVE_INFINITY,
            doubleNaN = Double.NaN,
            enum = seededEnum("plain"),
            float = seededFloat("plain"),
            floatMinValue = Float.MIN_VALUE,
            floatMaxValue = Float.MAX_VALUE,
            floatNegativeInfinity = Float.POSITIVE_INFINITY,
            floatPositiveInfinity = Float.NEGATIVE_INFINITY,
            floatNaN = Float.NaN,
            inlineString = TestStringValueClass(seededString("value")),
            inlineInt = TestIntValueClass(seededInt("value")),
            int = seededInt("plain"),
            long = seededLong("plain"),
            noll = null,
            short = seededShort("plain"),
            string = seededString("plain"),
            testData = TestData2.newInstance(seededString("plain")),
            booleanList = listOf(seededBoolean("list0"), seededBoolean("list1")),
            byteList = listOf(seededByte("list0"), seededByte("list1")),
            charList = listOf(seededChar("list0"), seededChar("list1")),
            doubleList = listOf(seededDouble("list0"), seededDouble("list1")),
            enumList = listOf(seededEnum("list0"), seededEnum("list1")),
            floatList = listOf(seededFloat("list0"), seededFloat("list1")),
            inlineStringList =
              listOf(
                TestStringValueClass(seededString("list0")),
                TestStringValueClass(seededString("list1"))
              ),
            inlineIntList =
              listOf(TestIntValueClass(seededInt("list0")), TestIntValueClass(seededInt("list1"))),
            intList = listOf(seededInt("list0"), seededInt("list1")),
            longList = listOf(seededLong("list0"), seededLong("list1")),
            shortList = listOf(seededShort("list0"), seededShort("list1")),
            stringList = listOf(seededString("list0"), seededString("list1")),
            testDataList =
              listOf(
                TestData2.newInstance(seededString("list0")),
                TestData2.newInstance(seededString("list1"))
              ),
            booleanNull = null,
            byteNull = null,
            charNull = null,
            doubleNull = null,
            enumNull = null,
            floatNull = null,
            inlineStringNull = null,
            inlineIntNull = null,
            intNull = null,
            longNull = null,
            shortNull = null,
            stringNull = null,
            testDataNull = null,
            booleanNullable = seededBoolean("nullable"),
            byteNullable = seededByte("nullable"),
            charNullable = seededChar("nullable"),
            doubleNullable = seededDouble("nullable"),
            enumNullable = seededEnum("nullable"),
            floatNullable = seededFloat("nullable"),
            inlineStringNullable = TestStringValueClass(seededString("nullable")),
            inlineIntNullable = TestIntValueClass(seededInt("nullable")),
            intNullable = seededInt("nullable"),
            longNullable = seededLong("nullable"),
            shortNullable = seededShort("nullable"),
            stringNullable = seededString("nullable"),
            testDataNullable = TestData2.newInstance(seededString("nullable")),
            booleanNullableList = listOf(seededBoolean("nlist0"), seededBoolean("nlist1"), null),
            byteNullableList = listOf(seededByte("nlist0"), seededByte("nlist1"), null),
            charNullableList = listOf(seededChar("nlist0"), seededChar("nlist1"), null),
            doubleNullableList = listOf(seededDouble("nlist0"), seededDouble("nlist1"), null),
            enumNullableList = listOf(seededEnum("nlist0"), seededEnum("nlist1"), null),
            floatNullableList = listOf(seededFloat("nlist0"), seededFloat("nlist1"), null),
            inlineStringNullableList =
              listOf(
                TestStringValueClass(seededString("nlist0")),
                TestStringValueClass(seededString("nlist1")),
                null
              ),
            inlineIntNullableList =
              listOf(
                TestIntValueClass(seededInt("nlist0")),
                TestIntValueClass(seededInt("nlist1")),
                null
              ),
            intNullableList = listOf(seededInt("nlist0"), seededInt("nlist1"), null),
            longNullableList = listOf(seededLong("nlist0"), seededLong("nlist1"), null),
            shortNullableList = listOf(seededShort("nlist0"), seededShort("nlist1"), null),
            stringNullableList = listOf(seededString("nlist0"), seededString("nlist1"), null),
            testDataNullableList =
              listOf(
                TestData2.newInstance(seededString("nlist0")),
                TestData2.newInstance(seededString("nlist1"))
              ),
            nested = if (nesting <= 0) null else newInstance("${seed}nest${nesting}", nesting - 1),
            unit = Unit,
            nullUnit = null,
            nullableUnit = Unit,
            listOfUnit = listOf(Unit, Unit),
            listOfNullableUnit = listOf(Unit, null, Unit, null),
          )
        }
    }
  }
}

/**
 * Creates and returns a new instance with the exact same property values but with all lists of
 * [Unit] to be empty. This may be useful if testing an encoder/decoder that does not support
 * [kotlinx.serialization.descriptors.StructureKind.OBJECT] in lists.
 */
fun SerializationTestData.AllTheTypes.withEmptyUnitLists(): SerializationTestData.AllTheTypes =
  copy(
    listOfUnit = emptyList(),
    listOfNullableUnit = emptyList(),
    nested = nested?.withEmptyUnitLists()
  )

private fun String.seededBoolean(id: String): Boolean = seededInt(id) % 2 == 0

private fun String.seededByte(id: String): Byte = seededInt(id).toByte()

private fun String.seededChar(id: String): Char = get(abs(id.hashCode()) % length)

private fun String.seededDouble(id: String): Double = seededLong(id).toDouble() / PI

private fun String.seededEnum(id: String): SerializationTestData.TestEnum =
  SerializationTestData.TestEnum.values().let { it[abs(seededInt(id)) % it.size] }

private fun String.seededFloat(id: String): Float = (seededInt(id).toFloat() / PI.toFloat())

private fun String.seededInt(id: String): Int = (hashCode() * id.hashCode())

private fun String.seededLong(id: String): Long = (hashCode().toLong() * id.hashCode().toLong())

private fun String.seededShort(id: String): Short = seededInt(id).toShort()

private fun String.seededString(id: String): String = "${this}_${id}"
