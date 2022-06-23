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

import com.google.common.truth.ExpectFailure
import com.google.common.truth.Truth.assertThat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.documentReference
import com.google.firebase.firestore.ktx.annotations.KDocumentId
import com.google.firebase.firestore.ktx.annotations.KServerTimestamp
import com.google.firebase.firestore.ktx.serialization.encodeToMap
import kotlin.test.assertFailsWith
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.Test

class DocumentIdTest {

    @Test
    fun `KDocumentId on wrong types throws`() {
        val exceptionMessage = "instead of String or DocumentReference"

        @Serializable
        class DefaultValuePropertyWithDocumentIdOnWrongTypeBean(
            @KDocumentId var intField: Int = 123
        )

        @Serializable
        class NullablePropertyWithDocumentIdOnWrongTypeBean(@KDocumentId var intField: Int? = null)

        @Serializable
        class KDocumentIdOnWrongTypeTimestampBean(
            @KDocumentId @Contextual var timestamp: Timestamp? = null
        )

        @Serializable
        class KDocumentIdOnTopOfKServerTimestampWrongTypeBean(
            @KServerTimestamp @KDocumentId @Contextual var timestamp: Timestamp? = null
        )

        @Serializable
        class KDocumentIdAndKServerTimestampTogetherOnWrongTypeBean(
            @KServerTimestamp @KDocumentId @Contextual var geoPoint: GeoPoint?
        )

        @Serializable class Student(val id: Int = 0, val name: String = "foo")

        @Serializable
        class KDocumentIdOnWrongTypeNestedObject(@KDocumentId var student: Student? = null)

        assertFailsWith<IllegalArgumentException>(
            message = exceptionMessage,
            block = { encodeToMap(DefaultValuePropertyWithDocumentIdOnWrongTypeBean()) }
        )


        assertFailsWith<IllegalArgumentException>(
            message = exceptionMessage,
            block = { encodeToMap(NullablePropertyWithDocumentIdOnWrongTypeBean()) }
        )

        assertFailsWith<IllegalArgumentException>(
            message = exceptionMessage,
            block = { encodeToMap(NullablePropertyWithDocumentIdOnWrongTypeBean(123)) }
        )

        assertFailsWith<IllegalArgumentException>(
            message = exceptionMessage,
            block = { encodeToMap(KDocumentIdOnWrongTypeTimestampBean()) }
        )

        assertFailsWith<IllegalArgumentException>(
            message = exceptionMessage,
            block = { encodeToMap(KDocumentIdOnTopOfKServerTimestampWrongTypeBean()) }
        )

        assertFailsWith<IllegalArgumentException>(
            message = exceptionMessage,
            block = {
                encodeToMap(
                    KDocumentIdAndKServerTimestampTogetherOnWrongTypeBean(GeoPoint(1.0, 2.0))
                )
            }
        )

        assertFailsWith<IllegalArgumentException>(
            message = exceptionMessage,
            block = { encodeToMap(KDocumentIdOnWrongTypeNestedObject()) }
        )
    }

    @Test
    fun `KDocumentId annotated on correct type without backfield is ignored during encoding`() {
        @Serializable
        class GetterWithoutBackingFieldOnDocumentIdBean {
            @KDocumentId
            val foo: String
                get() = "doc-id" // getter only, no backing field -- not serialized
            val bar: Int = 0 // property with a backing field -- serialized
        }

        // This is different than the current Java Solution's behavior
        // Java will throw run time exception if @DocumentId applied to a non-writable field during
        // serializing
        // While, the field without a backing field is transparent to Kotlin, so no exception can be
        // thrown rather than just ignore this property during serialization
        assertThat(encodeToMap(GetterWithoutBackingFieldOnDocumentIdBean()))
            .containsExactlyEntriesIn(mutableMapOf("bar" to 0))
    }

    @Test
    fun `KDocumentId annotated on wrong type without backfield is ignored during encoding`() {
        @Serializable
        class GetterWithoutBackingFieldOnDocumentIdBean {
            @KDocumentId
            val foo: Long
                get() = 123L // getter only, no backing field -- not serialized
            val bar: Int = 0 // property with a backing field -- serialized
        }

        // This is different than the current Java Solution's behavior
        // Java will throw run time exception if @DocumentId applied to a non-writable field during
        // serializing
        // While, the field without a backing field is transparent to Kotlin, so no exception can be
        // thrown rather than just ignore this property during serialization
        // TODO: Write a compiler plugin to check if KServerTimestamp is applied on wrong types
        assertThat(encodeToMap(GetterWithoutBackingFieldOnDocumentIdBean()))
            .containsExactlyEntriesIn(mutableMapOf("bar" to 0))
    }

    @Test
    fun `KDocumentId annotated on correct types with backing fields should encode`() {
        val docRef = documentReference("coll/doc123")

        @Serializable
        class DocumentIdOnStringField {
            @KDocumentId var docId = "doc-id"
        }
        assertThat(encodeToMap(DocumentIdOnStringField()))
            .containsAtLeastEntriesIn(mutableMapOf<String, Any?>())

        @Serializable
        class DocumentIdOnStringFieldAsProperty {
            @SerialName("DocIdProperty")
            @KDocumentId
            var docId = "doc-id"
                get() = field + "foobar"

            @SerialName("AnotherProperty") var someOtherProperty = 0
        }
        assertThat(encodeToMap(DocumentIdOnStringFieldAsProperty()))
            .containsAtLeastEntriesIn(mutableMapOf<String, Any?>("AnotherProperty" to 0))

        @Serializable
        class DocumentIdOnDocRefField {
            @Contextual @KDocumentId var docId: DocumentReference? = null
        }

        val documentIdOnDocRefField = DocumentIdOnDocRefField().apply { docId = docRef }
        assertThat(encodeToMap(documentIdOnDocRefField))
            .containsExactlyEntriesIn(mutableMapOf<String, Any?>())

        @Serializable
        open class DocumentIdOnDocRefAsProperty {
            @Contextual
            @KDocumentId
            @SerialName("DocIdProperty")
            var docId: DocumentReference? = null
                get() = field

            @SerialName("AnotherProperty") var someOtherProperty: Int = 0
        }

        val documentIdOnDocRefAsProperty =
            DocumentIdOnDocRefAsProperty().apply {
                docId = docRef
                someOtherProperty = 100
            }
        assertThat(encodeToMap(documentIdOnDocRefAsProperty))
            .containsExactlyEntriesIn(mutableMapOf<String, Any?>("AnotherProperty" to 100))

        @Serializable
        class DocumentIdOnNestedObjects {
            @SerialName("nested")
            var nestedDocIdHolder: DocumentIdOnStringField? = DocumentIdOnStringField()
        }
        assertThat(encodeToMap(DocumentIdOnNestedObjects()))
            .containsExactlyEntriesIn(mutableMapOf<String, Any?>("nested" to mapOf<String, Any>()))

        @Serializable
        class DocumentIdOnInheritedDocRefSetter : DocumentIdOnDocRefAsProperty() {
            @Contextual @KDocumentId var inheritedDocRef: DocumentReference? = null
        }

        val inheritedObject = DocumentIdOnInheritedDocRefSetter()
        inheritedObject.inheritedDocRef = docRef
        val actualMapOfInheritedObject = encodeToMap(inheritedObject)
        assertThat(actualMapOfInheritedObject)
            .containsExactlyEntriesIn(mutableMapOf<String, Any?>("AnotherProperty" to 0))
    }
}
