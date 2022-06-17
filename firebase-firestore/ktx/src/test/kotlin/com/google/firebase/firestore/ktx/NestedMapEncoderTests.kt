// Copyright 2022 Google LLC
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

package com.google.firebase.firestore.ktx

import com.google.firebase.firestore.ktx.serialization.encodeToMap
import kotlin.test.assertFailsWith
import kotlinx.serialization.Serializable
import org.junit.Assert.assertTrue
import org.junit.Test

class NestedMapEncoderTests {

    @Test
    fun `plan custom object encoding is supported`() {
        @Serializable data class PlainProject(val name: String, val ownerName: String)
        val plainObject = PlainProject("kotlinx.serialization", "kotlin")
        val encodedMap = encodeToMap(plainObject)
        val expectedMap = mapOf("name" to "kotlinx.serialization", "ownerName" to "kotlin")
        assertTrue(encodedMap == expectedMap)
    }

    @Test
    fun `nested custom object encoding is supported`() {
        @Serializable data class Owner(val name: String)
        @Serializable data class Project(val name: String, val owner: Owner)
        val project = Project("kotlinx.serialization", Owner("kotlin"))
        val encodedMap = encodeToMap(project)
        val expectedMap =
            mapOf("name" to "kotlinx.serialization", "owner" to mapOf("name" to "kotlin"))
        assertTrue(encodedMap == expectedMap)
    }

    @Test
    fun `nested primitive list inside of custom object encoding`() {
        @Serializable data class Product(val name: String, val serialNumList: List<Long>)
        val product = Product("kotlinx.serialization", listOf(1L, 10L, 100L, 1000L))
        val encodeMap = encodeToMap(product)
        val expectedMap =
            mapOf(
                "name" to "kotlinx.serialization",
                "serialNumList" to listOf(1L, 10L, 100L, 1000L)
            )
        assertTrue(encodeMap == expectedMap)
    }

    @Test
    fun `nested custom obj list inside of custom object encoding is supported`() {
        @Serializable data class Owner(val name: String)
        @Serializable data class Store(val name: String, val listOfOwner: List<Owner>)
        val listOfOwner = listOf(Owner("a"), Owner("b"), Owner("c"))
        val store = Store("kotlinx.store", listOfOwner)
        val encodeMap = encodeToMap(store)
        val expectedMap =
            mapOf(
                "name" to "kotlinx.store",
                "listOfOwner" to
                    listOf(mapOf("name" to "a"), mapOf("name" to "b"), mapOf("name" to "c"))
            )
        assertTrue(encodeMap == expectedMap)
    }

    @Serializable
    enum class Direction {
        NORTH,
        SOUTH,
        WEST,
        EAST
    }

    @Test
    fun `enum field encoding is supported`() {
        @Serializable data class Movement(val direction: Direction, val distance: Long)
        val movement = Movement(Direction.EAST, 100)
        val encodedMap = encodeToMap(movement)
        val expectedMap = mapOf("direction" to "EAST", "distance" to 100L)
        assertTrue(encodedMap == expectedMap)
    }

    @Test
    fun `null-able field encoding is supported`() {
        @Serializable data class Visitor(val name: String? = null, val age: String)
        val visitor = Visitor(age = "100")
        val encodedMap = encodeToMap(visitor)
        val expectedMap = mutableMapOf("name" to null, "age" to "100")
        assertTrue(encodedMap == expectedMap)
    }

    // need to copy more test from Line 1428
    @Test
    open fun encodeStringBean() {
        @Serializable data class StringBean(val value: String? = null)
        val bean = StringBean("foo")
        val expectedMap = mutableMapOf("value" to "foo")
        assertTrue((expectedMap == encodeToMap(bean)))
    }

    @Test
    open fun encodeDoubleBean() {
        @Serializable data class DoubleBean(val value: Double? = null)
        val bean = DoubleBean(1.1)
        val expectedMap = mutableMapOf("value" to 1.1)
        assertTrue((expectedMap == encodeToMap(bean)))
    }

    @Test
    open fun encodeIntBean() {
        @Serializable data class IntBean(val value: Int? = null)
        val bean = IntBean(1)
        val expectedMap = mutableMapOf("value" to 1)
        assertTrue((expectedMap == encodeToMap(bean)))
    }

    @Test
    fun encodeLongBean() {
        @Serializable data class LongBean(val value: Long? = null)
        val bean = LongBean(Int.MAX_VALUE + 100L)
        val expectedMap = mutableMapOf("value" to Int.MAX_VALUE + 100L)
        assertTrue(expectedMap == encodeToMap(bean))
    }

    @Test
    fun encodeBooleanBean() {
        @Serializable data class BooleanBean(val value: Boolean? = null)
        val bean = BooleanBean(true)
        val expectedMap = mutableMapOf("value" to true)
        assertTrue((expectedMap == encodeToMap(bean)))
    }

    @Test
    fun `unicode object encoding is supported`() {
        @Serializable data class UnicodeObject(val 漢字: String? = null)
        val unicodeObject = UnicodeObject(漢字 = "foo")
        val encodeMap = encodeToMap(unicodeObject)
        val expectedMap = mutableMapOf("漢字" to "foo")
        assertTrue(encodeMap == expectedMap)
    }

    @Test
    fun `short encoding is supported`() {
        // encoding supports converting an object with short field to a map; However,
        // IllegalArgumentException will be thrown when try to set this map to firebase
        @Serializable data class ShortObject(val value: Short? = null)
        val shortObject = ShortObject(value = 1)
        val encodeMap = encodeToMap(shortObject)
        val expectedMap = mutableMapOf("value" to 1.toShort())
        assertTrue(encodeMap == expectedMap)
    }

    @Test
    fun `byte encoding is supported`() {
        // encoding supports converting an object with byte field to a map; However,
        // IllegalArgumentException will be thrown when try to set this map to firebase
        @Serializable data class ByteObject(val value: Byte? = null)
        val byteObject = ByteObject(value = 1)
        val encodeMap = encodeToMap(byteObject)
        val expectedMap = mutableMapOf("value" to 1.toByte())
        assertTrue(encodeMap == expectedMap)
    }

    @Test
    fun `chars encoding is supported`() {
        // encoding supports converting an object with char field to a map; However,
        // IllegalArgumentException will be thrown when try to set this map to firebase
        @Serializable data class CharObject(val value: Char? = null)
        val charObject = CharObject(value = 1.toChar())
        val encodeMap = encodeToMap(charObject)
        val expectedMap = mutableMapOf("value" to 1.toChar())
        assertTrue(encodeMap == expectedMap)
    }

    @Test
    fun `array encoding is supported`() {
        // encoding supports both array and list;
        @Serializable
        data class IntArrayObject(
            val kotlinArrayValue: Array<Int>? = null,
            val listValue: List<Int>? = null,
            val javaArrayValue: IntArray? = null
        )
        val array = arrayOf<Int>(1, 2, 3)
        val list = listOf<Int>(4, 5, 6)
        val javaIntArray = intArrayOf(7, 8, 9)
        val intArrayObject =
            IntArrayObject(
                kotlinArrayValue = array,
                listValue = list,
                javaArrayValue = javaIntArray
            )
        val encodeMap = encodeToMap(intArrayObject)
        val expectedMap =
            mutableMapOf(
                "kotlinArrayValue" to listOf(1, 2, 3),
                "listValue" to listOf(4, 5, 6),
                "javaArrayValue" to listOf(7, 8, 9)
            )
        assertTrue(encodeMap == expectedMap)
    }

    @Serializable private data class GenericObject<T>(val value: T? = null)

    @Serializable
    private data class DoubleGenericObject<A, B>(val valueA: A? = null, val valueB: B? = null)

    @Test
    fun `generic encoding is supported`() {
        val stringObj = GenericObject("foo")
        val encodeMapOfStringObj = encodeToMap(stringObj)
        val expectedMapOfStringObj = mutableMapOf("value" to "foo")
        assertTrue(encodeMapOfStringObj == expectedMapOfStringObj)

        val list = listOf("foo", "bar")
        val listObj = GenericObject(list)
        val encodeMapOfListObj = encodeToMap(listObj)
        val expectedMapOfListObj = mutableMapOf("value" to listOf("foo", "bar"))
        assertTrue(encodeMapOfListObj == expectedMapOfListObj)

        val innerObj = GenericObject("foo")
        val recursiveObj = GenericObject(innerObj)
        val encodeRecursiveObj = encodeToMap(recursiveObj)
        val expectedRecursiveObj = mutableMapOf("value" to mutableMapOf("value" to "foo"))
        assertTrue(encodeRecursiveObj == expectedRecursiveObj)

        val doubleGenericObj = DoubleGenericObject(valueA = "foo", valueB = 1L)
        val encodeDoubleGenericObj = encodeToMap(doubleGenericObj)
        val expectedDoubleGenericObj = mutableMapOf("valueA" to "foo", "valueB" to 1L)
        assertTrue(encodeDoubleGenericObj == expectedDoubleGenericObj)

        // TODO: Add support to encode a custom object with a generic map as field
        val map = mapOf("foo" to "foo", "bar" to 1L)
        val mapObj = GenericObject(map)
        val expectedMapOfMapObj = mutableMapOf("value" to mutableMapOf("foo" to "foo", "bar" to 1L))
        assertFailsWith<IllegalArgumentException>(
            message = "Incorrect format of nested object provided",
            block = { assertTrue(expectedMapOfMapObj == encodeToMap(mapObj)) }
        )
    }

    @Serializable
    private class StaticFieldBean {
        var value2: String? = null
        companion object {
            var value1 = "static-value"
                set(value) {
                    field = value1 + "foobar"
                }
        }
    }

    @Test
    fun `static field is not encoded`() {
        val value = StaticFieldBean()
        value.value2 = "foo"
        StaticFieldBean.value1 = "x"
        val encodedMap = encodeToMap(value)
        val expectedMap = mutableMapOf("value2" to "foo")
        assertTrue(encodedMap == expectedMap)
    }

    @Test
    fun `setters can override parents setters in encoding`() {
        open class ConflictingSetterBean {
            open var value: Int = 1
                set(value) {
                    field = value * (-100)
                }
        }
        // unlike Java, Kotlin does not allow conflict setters to compile
        @Serializable
        class NonConflictingSetterSubBean : ConflictingSetterBean() {
            override var value: Int = -1
                set(value) {
                    field = value * (-1)
                }
        }
        val nonConflictingSetterSubBean = NonConflictingSetterSubBean()
        nonConflictingSetterSubBean.value = 10
        val encodedMap = encodeToMap(nonConflictingSetterSubBean)
        val expectedMap = mutableMapOf("value" to -10)
        assertTrue(encodedMap == expectedMap)
    }

    @Test
    fun `throw when recursively calling encoding`() {
        @Serializable
        class ObjectBean {
            var value: ObjectBean? = null
        }
        val objectBean = ObjectBean()
        objectBean.value = objectBean
        assertFailsWith<IllegalArgumentException>(
            message =
                "Exceeded maximum depth of 500, which likely indicates there's an object cycle",
            block = { encodeToMap(objectBean) }
        )
    }
}
