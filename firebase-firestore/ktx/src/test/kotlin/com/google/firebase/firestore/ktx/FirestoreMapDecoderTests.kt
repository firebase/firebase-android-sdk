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
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ThrowOnExtraProperties
import com.google.firebase.firestore.assertThrows
import com.google.firebase.firestore.documentReference
import com.google.firebase.firestore.ktx.serialization.decodeFromMap
import java.util.Date
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FirestoreMapDecoderTests {

    @Test
    fun `plain custom object decoding is supported`() {
        @Serializable data class PlainProject(val name: String, val ownerName: String)

        val map = mapOf<String, Any>("name" to "kotlinx.serialization", "ownerName" to "kotlin")
        val decodedObject = decodeFromMap<PlainProject>(map, firestoreDocument)
        val expectedObject = PlainProject("kotlinx.serialization", "kotlin")
        assertThat(decodedObject).isEqualTo(expectedObject)
    }

    @Test
    fun `nested custom object decoding is supported`() {
        @Serializable data class Owner(val name: String)

        @Serializable data class Project(val name: String, val owner: Owner)

        val map = mapOf("name" to "kotlinx.serialization", "owner" to mapOf("name" to "kotlin"))
        val decodedObject = decodeFromMap<Project>(map, firestoreDocument)
        val expectedObject = Project("kotlinx.serialization", Owner("kotlin"))
        assertThat(decodedObject).isEqualTo(expectedObject)
    }

    @Test
    fun `nested primitive list inside of custom object decoding is supported`() {
        @Serializable data class Product(val name: String, val serialNumList: List<Long>)

        val map =
            mapOf(
                "name" to "kotlinx.serialization",
                "serialNumList" to listOf(1L, 10L, 100L, 1000L)
            )
        val decodedObject = decodeFromMap<Product>(map, firestoreDocument)
        val expectedObject = Product("kotlinx.serialization", listOf(1L, 10L, 100L, 1000L))
        assertThat(decodedObject).isEqualTo(expectedObject)
    }

    @Test
    fun `nested custom obj list inside of custom object decoding is supported`() {
        @Serializable data class Owner(val name: String)

        @Serializable data class Store(val name: String, val listOfOwner: List<Owner>)

        val map =
            mapOf(
                "name" to "kotlinx.store",
                "listOfOwner" to
                    listOf(mapOf("name" to "a"), mapOf("name" to "b"), mapOf("name" to "c"))
            )
        val decodedObject = decodeFromMap<Store>(map, firestoreDocument)
        val listOfOwner = listOf(Owner("a"), Owner("b"), Owner("c"))
        val expectedObject = Store("kotlinx.store", listOfOwner)
        assertThat(decodedObject).isEqualTo(expectedObject)
    }

    @Serializable
    enum class Direction {
        NORTH,
        SOUTH,
        WEST,
        EAST
    }

    @Test
    fun `enum field decoding is supported`() {
        @Serializable data class Movement(val direction: Direction, val distance: Long)

        val map = mapOf("direction" to "EAST", "distance" to 100L)
        val decodedObject = decodeFromMap<Movement>(map, firestoreDocument)
        val expectedObject = Movement(Direction.EAST, 100)
        assertThat(decodedObject).isEqualTo(expectedObject)
    }

    @Test
    fun `miss-matched enum field decoding throws`() {
        @Serializable data class Movement(val direction: Direction, val distance: Long)
        val map = mapOf("direction" to "snake_case_enum_value", "distance" to 100L)
        assertThrows<IllegalArgumentException> { decodeFromMap<Movement>(map, firestoreDocument) }
            .hasMessageThat()
            .contains("Could not find a match for enum field name of snake_case_enum_value.")
    }

    @Test
    fun `null-able field decoding is supported`() {
        @Serializable data class Visitor(val name: String? = null, val age: String)

        val visitor = Visitor(age = "100")
        val map = mutableMapOf("name" to null, "age" to "100")
        val decodedObject = decodeFromMap<Visitor>(map, firestoreDocument)
        assertThat(decodedObject).isEqualTo(visitor)
    }

    @Serializable private data class GenericObject<T>(val value: T? = null)

    @Serializable
    private data class DoubleGenericObject<A, B>(val valueA: A? = null, val valueB: B? = null)

    @Test
    fun `generic decoding is supported`() {
        val stringObj = GenericObject("foo")
        val strMap = mutableMapOf("value" to "foo")
        val decodedStrObject = decodeFromMap<GenericObject<String>>(strMap, firestoreDocument)
        assertThat(decodedStrObject).isEqualTo(stringObj)

        val list = listOf("foo", "bar")
        val listObj = GenericObject(list)
        val listMap = mutableMapOf("value" to listOf("foo", "bar"))
        val decodedMapObject =
            decodeFromMap<GenericObject<List<String>>>(listMap, firestoreDocument)
        assertThat(decodedMapObject).isEqualTo(listObj)

        val innerObj = GenericObject("foo")
        val recursiveObj = GenericObject(innerObj)
        val recursiveObjMap = mutableMapOf("value" to mutableMapOf("value" to "foo"))
        val decodedRecursiveObject =
            decodeFromMap<GenericObject<GenericObject<String>>>(recursiveObjMap, firestoreDocument)
        assertThat(decodedRecursiveObject).isEqualTo(recursiveObj)

        val doubleGenericObj = DoubleGenericObject(valueA = "foo", valueB = 1L)
        val doubleGenericObjMap = mutableMapOf("valueA" to "foo", "valueB" to 1L)
        val decodedDoubleGenericObj =
            decodeFromMap<DoubleGenericObject<String, Long>>(doubleGenericObjMap, firestoreDocument)
        assertThat(decodedDoubleGenericObj).isEqualTo(doubleGenericObj)

        // TODO: Add support to decode a custom object with a generic map as field,
        //  currently it is not possible to obtain serializer for type Any at compile time
        val map = mapOf("foo" to "foo", "bar" to 1L)
        assertThrows<IllegalArgumentException> { decodeFromMap(map, firestoreDocument) }
            .hasMessageThat()
            .contains("Serializer for class 'Any' is not found.")
    }

    @Serializable
    private data class StaticFieldBean(val value2: String? = null) {
        companion object {
            var value1 = "static-value"
                set(value) {
                    field = value + "foobar"
                }
        }
    }

    @Test
    fun `static field is not decoded`() {
        val staticFieldObj = StaticFieldBean(value2 = "foo")
        StaticFieldBean.value1 =
            "static field can be set as anything, but will not be involved in decoding"
        val staticFieldObjMap = mutableMapOf("value2" to "foo")
        val decodedObj = decodeFromMap<StaticFieldBean>(staticFieldObjMap, firestoreDocument)
        assertThat(decodedObj).isEqualTo(staticFieldObj)
    }

    @Test
    fun `setters can override parents setters in decoding`() {
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
        val nonConflictingSetterMap = mutableMapOf("value" to -10L)
        val decodedObj =
            decodeFromMap<NonConflictingSetterSubBean>(nonConflictingSetterMap, firestoreDocument)
        assertThat(decodedObj?.value).isEqualTo(nonConflictingSetterSubBean.value)
    }

    @Test
    fun `long value in map can be decoded as int field`() {
        @Serializable data class Student(val id: Int)

        val longAsIntMap = mapOf("id" to 100L)
        val decodedObj = decodeFromMap<Student>(longAsIntMap, firestoreDocument)
        assertThat(decodedObj).isEqualTo(Student(100))
    }

    @Test
    fun `double value in map can be decoded as float field`() {
        @Serializable data class Student(val gpa: Float)

        val floatAsDoubleMap = mapOf("gpa" to 4.95)
        val decodedObj = decodeFromMap<Student>(floatAsDoubleMap, firestoreDocument)
        assertThat(decodedObj).isEqualTo(Student(4.95F))
    }

    @Test
    fun `geoPoint value in map can be decoded`() {
        @Serializable data class GeoPointObject(val value: GeoPoint)

        val geoPoint = GeoPointObject(GeoPoint(1.0, 2.0))
        val geoPointMap = mapOf("value" to GeoPoint(1.0, 2.0))
        val decodedObj = decodeFromMap<GeoPointObject>(geoPointMap, firestoreDocument)
        assertThat(decodedObj).isEqualTo(geoPoint)
    }

    @Test
    fun `documentRef value in map can be decoded`() {
        @Serializable data class DocRefObject(val value: DocumentReference)

        val docRef = DocRefObject(firestoreDocument)
        val docRefMap = mapOf("value" to firestoreDocument)
        val decodedObj = decodeFromMap<DocRefObject>(docRefMap, firestoreDocument)
        assertThat(decodedObj).isEqualTo(docRef)
    }

    @Test
    fun `timeStamp value in map can be decoded as timeStamp and date`() {
        @Serializable data class TimeObject(val value1: Timestamp, @Contextual val value2: Date)

        val date = Date(123581321L)
        val timeObject = TimeObject(Timestamp(date), date)
        val docRefMap = mapOf("value1" to Timestamp(date), "value2" to Timestamp(date))
        val decodedObj = decodeFromMap<TimeObject>(docRefMap, firestoreDocument)
        assertThat(decodedObj).isEqualTo(timeObject)
    }

    @Test
    fun `ignoreOnExtraProperties is the default behavior during decoding`() {
        @Serializable data class ExtraPropertiesObj(val str: String = "123")
        val docRefMap = mapOf("foo" to 123L, "bar" to 456L)
        val decodedObj = decodeFromMap<ExtraPropertiesObj>(docRefMap, firestoreDocument)
        assertThat(decodedObj).isEqualTo(ExtraPropertiesObj())
    }

    @Test
    fun `ignoreOnExtraProperties works for object with no-argument constructor during decoding`() {
        @Serializable @IgnoreExtraProperties data class ExtraPropertiesObj(val str: String = "123")
        val docRefMap = mapOf("foo" to 123L, "bar" to 456L)
        val decodedObj = decodeFromMap<ExtraPropertiesObj>(docRefMap, firestoreDocument)
        assertThat(decodedObj).isEqualTo(ExtraPropertiesObj())
    }

    @Test
    fun `throwOnExtraProperties works for object with no-argument constructor during decoding`() {
        @Serializable
        @ThrowOnExtraProperties
        data class ExtraPropertiesObj(val str: String = "123")
        val docRefMap = mapOf("foo" to 123L, "bar" to 456L)
        assertThrows<Exception> { decodeFromMap<ExtraPropertiesObj>(docRefMap, firestoreDocument) }
            .hasMessageThat()
            .contains("Can not match")
    }

    @Test
    fun `ignoreOnExtraProperties without default constructor should throw anyways`() {
        @Serializable @IgnoreExtraProperties data class ExtraPropertiesObj(val str: String)
        val docRefMap = mapOf("foo" to 123L, "bar" to 456L)
        assertThrows<Exception> { decodeFromMap<ExtraPropertiesObj>(docRefMap, firestoreDocument) }
            .hasMessageThat()
            .contains("but it was missing")
    }

    @Test
    fun `throwOnExtraProperties works for object without default constructor`() {
        @Serializable @ThrowOnExtraProperties data class ExtraPropertiesObj(val str: String)
        val docRefMap = mapOf("foo" to 123L, "bar" to 456L)
        assertThrows<Exception> { decodeFromMap<ExtraPropertiesObj>(docRefMap, firestoreDocument) }
            .hasMessageThat()
            .contains("Can not match")
    }
}

private val firestoreDocument: DocumentReference = documentReference("abc/1234")
