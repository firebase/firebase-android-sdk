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

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.ktx.annotations.KDocumentId
import com.google.firebase.firestore.ktx.serialization.encodeToMap
import com.google.firebase.firestore.ktx.testutil.documentReference
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DocumentIdTest {

    @Test
    fun `documentId on Wrong Types throws`() {
        @Serializable
        class DefaultValuePropertyWithDocumentIdOnWrongTypeBean(@KDocumentId var intField: Int = 123)

        @Serializable
        class NullablePropertyWithDocumentIdOnWrongTypeBean(@KDocumentId var intField: Int? = null)

        val exceptionMessage = "instead of String or DocumentReference"

        assertFailsWith<IllegalArgumentException>(
            message = exceptionMessage,
            block = {
                val encodedMap = encodeToMap(DefaultValuePropertyWithDocumentIdOnWrongTypeBean())
            }
        )

        assertFailsWith<IllegalArgumentException>(
            message = exceptionMessage,
            block = {
                val encodedMap = encodeToMap(NullablePropertyWithDocumentIdOnWrongTypeBean())
            }
        )

        assertFailsWith<IllegalArgumentException>(
            message = exceptionMessage,
            block = {
                val encodedMap = encodeToMap(NullablePropertyWithDocumentIdOnWrongTypeBean(123))
            }
        )
    }

    @Test
    fun `documentId annotated on correct type without backfield is ignored during encoding`() {
        @Serializable
        class GetterWithoutBackingFieldOnDocumentIdBean {
            @KDocumentId
            val foo: String
                get() = "doc-id" // getter only, no backing field -- not serialized
            val bar: Int = 0 // property with a backing field -- serialized
        }

        // This is different than the current Java Solution's behavior
        // Java will throw run time exception if @DocumentId applied to a non-writable field during serializing
        // While, the field without a backing field is transparent to Kotlin, so no exception can be thrown rather than just ignore this property during serialization
        val expectedMap = mutableMapOf<String, Any?>("bar" to 0)
        val actualMap = encodeToMap(GetterWithoutBackingFieldOnDocumentIdBean())
        assertTrue { expectedMap == actualMap }
    }

    @Test
    fun `documentId annotated on wrong type without backfield is ignored during encoding`() {
        @Serializable
        class GetterWithoutBackingFieldOnDocumentIdBean {
            @KDocumentId
            val foo: Long
                get() = 123L // getter only, no backing field -- not serialized
            val bar: Int = 0 // property with a backing field -- serialized
        }

        // This is different than the current Java Solution's behavior
        // Java will throw run time exception if @DocumentId applied to a non-writable field during serializing
        // While, the field without a backing field is transparent to Kotlin, so no exception can be thrown rather than just ignore this property during serialization
        val expectedMap = mutableMapOf<String, Any?>("bar" to 0)
        val actualMap = encodeToMap(GetterWithoutBackingFieldOnDocumentIdBean())
        assertTrue { expectedMap == actualMap }
    }

    @Test
    fun `documentId annotated on correct types with backing fields should be able to encode`() {
        val docRef = documentReference("coll/doc123")

        @Serializable
        class DocumentIdOnStringField {
            @KDocumentId
            var docId = "doc-id"
        }

        val actualMapWithStringField = encodeToMap(DocumentIdOnStringField())
        assertTrue { mutableMapOf<String, Any?>() == actualMapWithStringField }

        @Serializable
        class DocumentIdOnStringFieldAsProperty {
            @SerialName("DocIdProperty")
            @KDocumentId
            var docId = "doc-id"
                get() = field + "foobar"

            @SerialName("AnotherProperty")
            var someOtherProperty = 0
        }

        val actualMapWithStringFieldAsProperty = encodeToMap(DocumentIdOnStringFieldAsProperty())
        assertTrue { mutableMapOf<String, Any?>("AnotherProperty" to 0) == actualMapWithStringFieldAsProperty }

        @Serializable
        class DocumentIdOnDocRefField {
            @Contextual
            @KDocumentId
            var docId: DocumentReference? = null
        }

        val documentIdOnDocRefField = DocumentIdOnDocRefField().apply { docId = docRef }

        val actualMapWithDocRefField = encodeToMap(documentIdOnDocRefField)
        assertTrue { mutableMapOf<String, Any?>() == actualMapWithDocRefField }

        @Serializable
        open class DocumentIdOnDocRefAsProperty {
            @Contextual
            @KDocumentId
            @SerialName("DocIdProperty")
            var docId: DocumentReference? = null
                get() = field

            @SerialName("AnotherProperty")
            var someOtherProperty: Int = 0
        }

        val documentIdOnDocRefAsProperty = DocumentIdOnDocRefAsProperty().apply {
            docId = docRef
            someOtherProperty = 100
        }

        val actualMapWithDocRefFieldAsProperty = encodeToMap(documentIdOnDocRefAsProperty)
        assertTrue { mutableMapOf<String, Any?>("AnotherProperty" to 100) == actualMapWithDocRefFieldAsProperty }

        @Serializable
        class DocumentIdOnNestedObjects {
            @SerialName("nested")
            var nestedDocIdHolder: DocumentIdOnStringField? = DocumentIdOnStringField()
        }

        val actualMapOfNestedObject = encodeToMap(DocumentIdOnNestedObjects())
        assertTrue { mutableMapOf<String, Any?>("nested" to mapOf<String, Any>()) == actualMapOfNestedObject }

        @Serializable
        class DocumentIdOnInheritedDocRefSetter : DocumentIdOnDocRefAsProperty() {
            @Contextual
            @KDocumentId
            var inheritedDocRef: DocumentReference? = null
        }

        val inheritedObject = DocumentIdOnInheritedDocRefSetter()
        inheritedObject.inheritedDocRef = docRef
        val actualMapOfInheritedObject = encodeToMap(inheritedObject)
        assertTrue { mutableMapOf<String, Any?>("AnotherProperty" to 0) == actualMapOfInheritedObject }
    }


}
