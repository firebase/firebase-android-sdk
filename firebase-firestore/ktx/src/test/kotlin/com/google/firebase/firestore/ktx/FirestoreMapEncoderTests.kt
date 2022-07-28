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

import com.google.common.truth.Truth.assertThat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.assertThrows
import com.google.firebase.firestore.documentReference
import com.google.firebase.firestore.ktx.serialization.encodeToMap
import java.util.Date
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FirestoreMapEncoderTests {

    @Test
    fun `plain custom object encoding is supported`() {
        @Serializable data class PlainProject(val name: String, val ownerName: String)
        val plainObject = PlainProject("kotlinx.serialization", "kotlin")
        val encodedMap = encodeToMap(plainObject)
        val expectedMap = mapOf("name" to "kotlinx.serialization", "ownerName" to "kotlin")
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun `nested custom object encoding is supported`() {
        @Serializable data class Owner(val name: String)
        @Serializable data class Project(val name: String, val owner: Owner)
        val project = Project("kotlinx.serialization", Owner("kotlin"))
        val encodedMap = encodeToMap(project)
        val expectedMap =
            mapOf("name" to "kotlinx.serialization", "owner" to mapOf("name" to "kotlin"))
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun `nested primitive list inside of custom object encoding is supported`() {
        @Serializable data class Product(val name: String, val serialNumList: List<Long>)
        val product = Product("kotlinx.serialization", listOf(1L, 10L, 100L, 1000L))
        val encodedMap = encodeToMap(product)
        val expectedMap =
            mapOf(
                "name" to "kotlinx.serialization",
                "serialNumList" to listOf(1L, 10L, 100L, 1000L)
            )
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun `nested custom obj list inside of custom object encoding is supported`() {
        @Serializable data class Owner(val name: String)
        @Serializable data class Store(val name: String, val listOfOwner: List<Owner>)
        val listOfOwner = listOf(Owner("a"), Owner("b"), Owner("c"))
        val store = Store("kotlinx.store", listOfOwner)
        val encodedMap = encodeToMap(store)
        val expectedMap =
            mapOf(
                "name" to "kotlinx.store",
                "listOfOwner" to
                    listOf(mapOf("name" to "a"), mapOf("name" to "b"), mapOf("name" to "c"))
            )
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
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
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun `null-able field encoding is supported`() {
        @Serializable data class Visitor(val name: String? = null, val age: String)
        val visitor = Visitor(age = "100")
        val encodedMap = encodeToMap(visitor)
        val expectedMap = mutableMapOf("name" to null, "age" to "100")
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    // need to copy more test from Line 1428
    @Test
    fun encodeStringBean() {
        @Serializable data class StringBean(val value: String)
        val bean = StringBean("foo")
        val expectedMap = mutableMapOf("value" to "foo")
        assertThat(encodeToMap(bean)).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun encodeDoubleBean() {
        @Serializable data class DoubleBean(val value: Double)
        val bean = DoubleBean(1.1)
        val expectedMap = mutableMapOf("value" to 1.1)
        assertThat(encodeToMap(bean)).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun encodeIntBean() {
        @Serializable data class IntBean(val value: Int)
        val bean = IntBean(1)
        val expectedMap = mutableMapOf("value" to 1)
        assertThat(encodeToMap(bean)).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun encodeLongBean() {
        @Serializable data class LongBean(val value: Long)
        val bean = LongBean(Int.MAX_VALUE + 100L)
        val expectedMap = mutableMapOf("value" to Int.MAX_VALUE + 100L)
        assertThat(encodeToMap(bean)).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun encodeBooleanBean() {
        @Serializable data class BooleanBean(val value: Boolean)
        val bean = BooleanBean(true)
        val expectedMap = mutableMapOf("value" to true)
        assertThat(encodeToMap(bean)).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun `unicode object encoding is supported`() {
        @Serializable data class UnicodeObject(val 漢字: String)
        val unicodeObject = UnicodeObject(漢字 = "foo")
        val encodedMap = encodeToMap(unicodeObject)
        val expectedMap = mutableMapOf("漢字" to "foo")
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun `short encoding is supported`() {
        // encoding supports converting an object with short field to a map; However,
        // IllegalArgumentException will be thrown when try to set this map to firebase
        @Serializable data class ShortObject(val value: Short)
        val shortObject = ShortObject(value = 1)
        val encodedMap = encodeToMap(shortObject)
        val expectedMap = mutableMapOf("value" to 1.toShort())
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun `byte encoding is supported`() {
        // encoding supports converting an object with byte field to a map; However,
        // IllegalArgumentException will be thrown when try to set this map to firebase
        @Serializable data class ByteObject(val value: Byte)
        val byteObject = ByteObject(value = 1)
        val encodedMap = encodeToMap(byteObject)
        val expectedMap = mutableMapOf("value" to 1.toByte())
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun `chars encoding is supported`() {
        // encoding supports converting an object with char field to a map; However,
        // IllegalArgumentException will be thrown when try to set this map to firebase
        @Serializable data class CharObject(val value: Char)
        val charObject = CharObject(value = 1.toChar())
        val encodedMap = encodeToMap(charObject)
        val expectedMap = mutableMapOf("value" to 1.toChar())
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun `array encoding is supported`() {
        // encoding supports both array and list;
        @Serializable
        data class IntArrayObject(
            val kotlinArrayValue: Array<Int>,
            val listValue: List<Int>,
            val javaArrayValue: IntArray
        )
        val array = arrayOf(1, 2, 3)
        val list = listOf(4, 5, 6)
        val javaIntArray = intArrayOf(7, 8, 9)
        val intArrayObject =
            IntArrayObject(
                kotlinArrayValue = array,
                listValue = list,
                javaArrayValue = javaIntArray
            )
        val encodedMap = encodeToMap(intArrayObject)
        val expectedMap =
            mutableMapOf(
                "kotlinArrayValue" to listOf(1, 2, 3),
                "listValue" to listOf(4, 5, 6),
                "javaArrayValue" to listOf(7, 8, 9)
            )
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Serializable private data class GenericObject<T>(val value: T)

    @Serializable private data class DoubleGenericObject<A, B>(val valueA: A, val valueB: B)

    @Test
    fun `generic encoding is supported`() {
        val stringObj = GenericObject("foo")
        val encodedMapOfStringObj = encodeToMap(stringObj)
        val expectedMapOfStringObj = mutableMapOf("value" to "foo")
        assertThat(encodedMapOfStringObj).containsExactlyEntriesIn(expectedMapOfStringObj)

        val list = listOf("foo", "bar")
        val listObj = GenericObject(list)
        val encodedMapOfListObj = encodeToMap(listObj)
        val expectedMapOfListObj = mutableMapOf("value" to listOf("foo", "bar"))
        assertThat(encodedMapOfListObj).containsExactlyEntriesIn(expectedMapOfListObj)

        val innerObj = GenericObject("foo")
        val recursiveObj = GenericObject(innerObj)
        val encodedRecursiveObj = encodeToMap(recursiveObj)
        val expectedRecursiveObj = mutableMapOf("value" to mutableMapOf("value" to "foo"))
        assertThat(encodedRecursiveObj).containsExactlyEntriesIn(expectedRecursiveObj)

        val doubleGenericObj = DoubleGenericObject(valueA = "foo", valueB = 1L)
        val encodedDoubleGenericObj = encodeToMap(doubleGenericObj)
        val expectedDoubleGenericObj = mutableMapOf("valueA" to "foo", "valueB" to 1L)
        assertThat(encodedDoubleGenericObj).containsExactlyEntriesIn(expectedDoubleGenericObj)

        // TODO: Add support to encode a custom object with a generic map as field,
        //  currently it is not possible to obtain serializer for type Any at compile time
        val map = mapOf("foo" to "foo", "bar" to 1L)
        val mapObj = GenericObject(map)
        val expectedMapOfMapObj = mutableMapOf("value" to mutableMapOf("foo" to "foo", "bar" to 1L))
        assertThrows<IllegalArgumentException> { encodeToMap(mapObj) }
            .hasMessageThat()
            .contains("Serializer for class 'Any' is not found")
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
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
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
        assertThat(encodedMap).containsExactlyEntriesIn(expectedMap)
    }

    @Test
    fun `throw when recursively calling encoding`() {
        @Serializable
        class ObjectBean {
            var value: ObjectBean? = null
        }
        val objectBean = ObjectBean()
        objectBean.value = objectBean
        assertThrows<IllegalArgumentException> { encodeToMap(objectBean) }
            .hasMessageThat()
            .contains(
                "Exceeded maximum depth of 500, which likely indicates there's an object cycle"
            )
    }

    @Test
    fun `documentReference is encoded`() {
        @Serializable class DocumentRefBean(val value: DocumentReference)

        val docRef = documentReference("111/222")
        val docRefBean = DocumentRefBean(docRef)
        assertThat(encodeToMap(docRefBean)).containsExactlyEntriesIn(mapOf("value" to docRef))
    }

    @Test
    fun `date is encoded`() {
        @Serializable class DateBean(@Contextual val value: Date)

        val dateBean = DateBean(Date(10000L))
        assertThat(encodeToMap(dateBean)).containsExactlyEntriesIn(mapOf("value" to Date(10000L)))
    }

    @Test
    fun `timestamp is encoded`() {
        @Serializable class TimestampBean(@Contextual val value: Timestamp)

        val timestampBean = TimestampBean(Timestamp(Date(10000L)))
        assertThat(encodeToMap(timestampBean))
            .containsExactlyEntriesIn(mapOf("value" to Timestamp(Date(10000L))))
    }

    @Test
    fun `geoPoint is encoded`() {
        @Serializable class GeoPointBean(@Contextual val value: GeoPoint)

        val geoPointBean = GeoPointBean(GeoPoint(1.1, 2.2))
        assertThat(encodeToMap(geoPointBean))
            .containsExactlyEntriesIn(mapOf("value" to GeoPoint(1.1, 2.2)))
    }

    @Test
    fun `list of firestore supported type is encoded`() {
        @Serializable
        class FirestoreSupportedTypeLists(
            val docRef: List<DocumentReference?>,
            val date: List<@Contextual Date?>,
            val timestamp: List<Timestamp?>,
            val geoPoint: List<GeoPoint?>
        )

        val docRef = documentReference("111/222")
        val date = Date(123L)
        val timestamp = Timestamp(date)
        val geoPoint = GeoPoint(1.1, 2.2)
        val firestoreSupportedTypeLists =
            FirestoreSupportedTypeLists(
                listOf(docRef, null, docRef),
                listOf(date, null, date),
                listOf(timestamp, null, timestamp),
                listOf(geoPoint, null, geoPoint)
            )
        assertThat(encodeToMap(firestoreSupportedTypeLists))
            .containsExactlyEntriesIn(
                mapOf(
                    "docRef" to listOf(docRef, null, docRef),
                    "date" to listOf(date, null, date),
                    "timestamp" to listOf(timestamp, null, timestamp),
                    "geoPoint" to listOf(geoPoint, null, geoPoint)
                )
            )
    }
}
