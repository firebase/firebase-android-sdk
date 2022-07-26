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
import com.google.firebase.firestore.ktx.serialization.encodeToMap
import java.util.Date
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
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
    fun `nullable nested primitive list inside of custom object decoding is supported`() {
        @Serializable data class Product(val name: String, val serialNumList: List<Long?>)

        val map =
            mapOf(
                "name" to "kotlinx.serialization",
                "serialNumList" to listOf(null, 1L, 10L, 100L, 1000L)
            )
        val decodedObject = decodeFromMap<Product>(map, firestoreDocument)
        val expectedObject = Product("kotlinx.serialization", listOf(null, 1L, 10L, 100L, 1000L))
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

    @Test
    fun `nullable nested custom obj list inside of custom object decoding is supported`() {
        @Serializable data class Owner(val name: String)

        @Serializable data class Store(val name: String, val listOfOwner: List<Owner?>)

        val map =
            mapOf(
                "name" to "kotlinx.store",
                "listOfOwner" to
                        listOf(null, mapOf("name" to "a"), mapOf("name" to "b"), mapOf("name" to "c"))
            )
        val decodedObject = decodeFromMap<Store>(map, firestoreDocument)
        val listOfOwner = listOf(null, Owner("a"), Owner("b"), Owner("c"))
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
    fun `throw on unmatched enum`() {
        @Serializable data class Movement(val direction: Direction, val distance: Long)

        val map = mapOf("direction" to "east", "distance" to 100L)
        assertThrows<Exception> { decodeFromMap<Movement>(map, firestoreDocument) }
            .hasMessageThat()
            .contains("Could not find a match for")
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
        @Serializable @ThrowOnExtraProperties data class ExtraPropertiesObj(val str: String = "123")
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

    @Serializable private data class StringBean(val value: String)

    @Test
    fun `primitive deserialize string`() {
        val bean = decodeFromMap<StringBean>(mapOf("value" to "foo"), firestoreDocument)
        assertThat(bean).isEqualTo(StringBean("foo"))

        assertThrows<Exception> {
                decodeFromMap<StringBean>(mapOf("value" to 1.1), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")

        assertThrows<Exception> {
                decodeFromMap<StringBean>(mapOf("value" to 1), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")

        assertThrows<Exception> {
                decodeFromMap<StringBean>(mapOf("value" to 1234567890123L), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")

        assertThrows<Exception> {
                decodeFromMap<StringBean>(mapOf("value" to true), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")
    }

    @Serializable private data class BooleanBean(val value: Boolean)

    @Test
    fun `primitive deserialize boolean`() {
        val bean = decodeFromMap<BooleanBean>(mapOf("value" to true), firestoreDocument)
        assertThat(bean).isEqualTo(BooleanBean(true))

        assertThrows<Exception> {
                decodeFromMap<BooleanBean>(mapOf("value" to 1.1), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")

        assertThrows<Exception> {
                decodeFromMap<BooleanBean>(mapOf("value" to 1), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")

        assertThrows<Exception> {
                decodeFromMap<BooleanBean>(mapOf("value" to 1234567890123L), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")

        assertThrows<Exception> {
                decodeFromMap<BooleanBean>(mapOf("value" to "foo"), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")
    }

    @Serializable private data class DoubleBean(val value: Double)

    @Test
    fun `primitive deserialize double`() {
        val bean = decodeFromMap<DoubleBean>(mapOf("value" to 1.1), firestoreDocument)
        assertThat(bean).isEqualTo(DoubleBean(1.1))

        assertThrows<Exception> {
                decodeFromMap<DoubleBean>(mapOf("value" to true), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")

        assertThrows<Exception> {
                decodeFromMap<DoubleBean>(mapOf("value" to 1), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")

        assertThrows<Exception> {
                decodeFromMap<DoubleBean>(mapOf("value" to 1234567890123L), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")

        assertThrows<Exception> {
                decodeFromMap<DoubleBean>(mapOf("value" to "foo"), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")
    }

    @Serializable private data class FloatBean(val value: Float)

    @Test
    fun `primitive deserialize float`() {
        val bean = decodeFromMap<FloatBean>(mapOf("value" to 1.1), firestoreDocument)
        // Firestore saves Float as Double on the server side
        assertThat(bean.value).isEqualTo(1.1F)

        assertThrows<Exception> {
                decodeFromMap<FloatBean>(mapOf("value" to 1.1F), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")

        assertThrows<Exception> {
                decodeFromMap<FloatBean>(mapOf("value" to true), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")

        assertThrows<Exception> { decodeFromMap<FloatBean>(mapOf("value" to 1), firestoreDocument) }
            .hasMessageThat()
            .contains("cannot be cast to")

        assertThrows<Exception> {
                decodeFromMap<FloatBean>(mapOf("value" to 1234567890123L), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")

        assertThrows<Exception> {
                decodeFromMap<FloatBean>(mapOf("value" to "foo"), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")
    }

    @Serializable private data class IntBean(val value: Int)

    @Test
    fun `primitive deserialize int`() {
        val bean = decodeFromMap<IntBean>(mapOf("value" to 1L), firestoreDocument)
        // Firestore saves Int as Long on the server side
        assertThat(bean.value).isEqualTo(1)

        assertThrows<Exception> { decodeFromMap<IntBean>(mapOf("value" to 1), firestoreDocument) }
            .hasMessageThat()
            .contains("cannot be cast to")

        assertThrows<Exception> {
                decodeFromMap<IntBean>(mapOf("value" to true), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")

        assertThrows<Exception> {
                decodeFromMap<IntBean>(mapOf("value" to 1e10), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")

        assertThrows<Exception> {
                decodeFromMap<FloatBean>(mapOf("value" to "foo"), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")
    }

    @Serializable private data class LongBean(val value: Long)

    @Test
    fun `primitive deserialize long`() {
        val bean = decodeFromMap<LongBean>(mapOf("value" to 1234567890123L), firestoreDocument)
        // Firestore saves Int as Long on the server side
        assertThat(bean.value).isEqualTo(1234567890123L)

        assertThrows<Exception> { decodeFromMap<LongBean>(mapOf("value" to 1), firestoreDocument) }
            .hasMessageThat()
            .contains("cannot be cast to")

        assertThrows<Exception> {
                decodeFromMap<LongBean>(mapOf("value" to true), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")

        assertThrows<Exception> {
                decodeFromMap<LongBean>(mapOf("value" to 1.1), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")

        assertThrows<Exception> {
                decodeFromMap<LongBean>(mapOf("value" to 1e300), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")

        assertThrows<Exception> {
                decodeFromMap<LongBean>(mapOf("value" to "foo"), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")
    }

    @Test
    fun `primitive deserialize wrong type map`() {
        assertThrows<Exception> {
                decodeFromMap<StringBean>(
                    mapOf("value" to mapOf("foo" to "bar")),
                    firestoreDocument
                )
            }
            .hasMessageThat()
            .contains("cannot be cast to")
    }

    @Test
    fun `primitive deserialize wrong type list`() {
        assertThrows<Exception> {
                decodeFromMap<StringBean>(mapOf("value" to listOf("foo", "bar")), firestoreDocument)
            }
            .hasMessageThat()
            .contains("cannot be cast to")
    }

    @Serializable
    private data class PublicPrivateFieldBean(
        var value1: String,
        var value2: String,
        private val value3: String
    )

    @Test
    fun `public private field deserialize`() {
        // All the field with backing field will be deserialized
        val bean =
            decodeFromMap<PublicPrivateFieldBean>(
                mapOf("value1" to "foo", "value2" to "bar", "value3" to "baz"),
                firestoreDocument
            )
        assertThat(bean).isEqualTo(PublicPrivateFieldBean("foo", "bar", "baz"))
    }

    @Test
    fun ignoreExtraProperties() {
        val bean =
            decodeFromMap<StringBean>(
                mapOf("value" to "bar", "unknown" to "bar"),
                firestoreDocument
            )
        assertThat(bean).isEqualTo(StringBean("bar"))
    }

    @ThrowOnExtraProperties
    @Serializable
    private data class ThrowOnUnknownPropertiesBean(val value: String)

    @Test
    fun throwOnUnknownProperties() {
        assertThrows<Exception> {
                decodeFromMap<ThrowOnUnknownPropertiesBean>(
                    mapOf("value" to "bar", "unknown" to "bar"),
                    firestoreDocument
                )
            }
            .hasMessageThat()
            .contains("Can not match unknown to any properties inside of Object")
    }

    @Serializable private data class XMLAndURLBean(val XMLAndURL1: String, val XMLAndURL2: String)

    @Test
    fun `XML And URL Bean`() {
        // Kotlin serialization will not change the first letter of each properties to lower case.
        val bean =
            decodeFromMap<XMLAndURLBean>(
                mapOf("XMLAndURL1" to "foo", "XMLAndURL2" to "bar"),
                firestoreDocument
            )
        assertThat(bean.XMLAndURL1).isEqualTo("foo")
        assertThat(bean.XMLAndURL2).isEqualTo("bar")
    }

    @Serializable
    private data class AllCapsDefaultHandlingBean(@SerialName("uuid") val UUID: String)

    @Test
    fun `convert upper case property name to lower case`() {
        // Customer can add @SerialName to set custom property names to match Java POJO behavior
        val bean = AllCapsDefaultHandlingBean("value")
        val actual =
            decodeFromMap<AllCapsDefaultHandlingBean>(mapOf("uuid" to "value"), firestoreDocument)
        assertThat(actual).isEqualTo(bean)
    }

    @Serializable
    private data class GetterBeanNoField(val lastName: String) {
        val fullName: String // getter only, no backing field
            get() = "Jone $lastName"
    }

    @Test
    fun `property with no backing field is not deserialized`() {
        val bean = decodeFromMap<GetterBeanNoField>(mapOf("lastName" to "Snow"), firestoreDocument)
        assertThat(bean).isEqualTo(GetterBeanNoField("Snow"))
    }

    @Serializable
    private data class CaseInSensitiveFieldBean(
        val VALUE: String,
        val value: String,
        val valUE: String
    )

    @Test
    fun `ktx serialization is case insensitive roundtrip test`() {
        val expected = CaseInSensitiveFieldBean("foo", "bar", "baz")
        val actual =
            decodeFromMap<CaseInSensitiveFieldBean>(
                mapOf("VALUE" to "foo", "value" to "bar", "valUE" to "baz"),
                firestoreDocument
            )
        assertThat(actual).isEqualTo(expected)
    }

    @Serializable private data class UnicodeObject(val 漢字: String)

    @Test
    fun `roundtrip unicode bean`() {
        val unicodeObject = UnicodeObject("foo")
        val encodeMap = encodeToMap(unicodeObject)
        val decodeObj = decodeFromMap<UnicodeObject>(encodeMap, firestoreDocument)
        assertThat(decodeObj).isEqualTo(unicodeObject)
    }

    @Serializable private data class ShortBean(val value: Short)

    @Test
    fun `shorts can be deserialized`() {
        val num: Short = 100
        val bean = decodeFromMap<ShortBean>(mapOf("value" to num), firestoreDocument)
        assertThat(bean).isEqualTo(ShortBean(num))
    }

    @Serializable private data class ByteBean(val value: Byte)

    @Test
    fun `bytes can be deserialized`() {
        val byte: Byte = 100
        val bean = decodeFromMap<ByteBean>(mapOf("value" to byte), firestoreDocument)
        assertThat(bean).isEqualTo(ByteBean(byte))
    }

    @Serializable private data class CharBean(val value: Char)

    @Test
    fun `chars can be deserialized`() {
        val bean = decodeFromMap<CharBean>(mapOf("value" to '1'), firestoreDocument)
        assertThat(bean).isEqualTo(CharBean('1'))
    }

    @Serializable private data class IntArrayBean(val values: Array<Int>)

    @Test
    fun `intArray can be deserialized`() {
        // Firestore saves int as long from the server side
        val bean =
            decodeFromMap<IntArrayBean>(mapOf("values" to listOf(1L, 2L, 3L)), firestoreDocument)
        assertThat(bean.values).isEqualTo(IntArrayBean(arrayOf(1, 2, 3)).values)
    }
}

private val firestoreDocument: DocumentReference = documentReference("abc/1234")
