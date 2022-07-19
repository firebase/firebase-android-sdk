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
import com.google.firebase.firestore.ktx.annotations.KDocumentId
import com.google.firebase.firestore.ktx.annotations.KServerTimestamp
import com.google.firebase.firestore.ktx.serialization.decodeFromMap
import com.google.firebase.firestore.ktx.serialization.encodeToMap
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DocumentIdTests {

    @Test
    fun `KDocumentId on wrong types throws`() {

        @Serializable class DocumentIdOnWrongTypeBean(@KDocumentId val intField: Int?)

        assertThrows<IllegalArgumentException> { encodeToMap(DocumentIdOnWrongTypeBean(null)) }
            .hasMessageThat()
            .contains("instead of String or DocumentReference")

        assertThrows<IllegalArgumentException> { encodeToMap(DocumentIdOnWrongTypeBean(123)) }
            .hasMessageThat()
            .contains("instead of String or DocumentReference")

        @Serializable
        class DocumentIdOnWrongTypeTimestampBean(@KDocumentId val timestamp: Timestamp?)

        assertThrows<IllegalArgumentException> {
                encodeToMap(DocumentIdOnWrongTypeTimestampBean(null))
            }
            .hasMessageThat()
            .contains("instead of String or DocumentReference")

        assertThrows<IllegalArgumentException> {
                encodeToMap(DocumentIdOnWrongTypeTimestampBean(Timestamp.now()))
            }
            .hasMessageThat()
            .contains("instead of String or DocumentReference")

        @Serializable
        class DocumentIdAndKServerTimestampTogetherOnWrongTypeBean(
            // always throw for invalid DocumentId first
            @KServerTimestamp @KDocumentId val geoPoint: GeoPoint?
        )

        assertThrows<IllegalArgumentException> {
                encodeToMap(DocumentIdAndKServerTimestampTogetherOnWrongTypeBean(null))
            }
            .hasMessageThat()
            .contains("instead of String or DocumentReference")

        assertThrows<IllegalArgumentException> {
                encodeToMap(
                    DocumentIdAndKServerTimestampTogetherOnWrongTypeBean(GeoPoint(1.0, 2.0))
                )
            }
            .hasMessageThat()
            .contains("instead of String or DocumentReference")

        @Serializable class Student(val id: Int = 0, val name: String = "foo")

        @Serializable
        class KDocumentIdOnWrongTypeNestedObject(@KDocumentId val student: Student? = null)

        assertThrows<IllegalArgumentException> { encodeToMap(KDocumentIdOnWrongTypeNestedObject()) }
            .hasMessageThat()
            .contains("instead of String or DocumentReference")

        assertThrows<IllegalArgumentException> {
                encodeToMap(KDocumentIdOnWrongTypeNestedObject(Student(100, "name")))
            }
            .hasMessageThat()
            .contains("instead of String or DocumentReference")
    }

    @Test
    fun `DocumentId annotated on list should throw`() {
        @Serializable class DocumentRefBean(@KDocumentId val value: List<DocumentReference?>)

        val docRef = documentReference("111/222")
        val docRefBean = DocumentRefBean(listOf(docRef, null, docRef))
        assertThrows<IllegalArgumentException> { encodeToMap(docRefBean) }
            .hasMessageThat()
            .contains("instead of String or DocumentReference")
    }

    @Test
    fun `DocumentId annotated on custom object should throw`() {
        @Serializable class Student(val name: String)
        @Serializable class DocumentRefBean(@KDocumentId val student: Student)

        val docRefBean = DocumentRefBean(Student("foo"))
        assertThrows<IllegalArgumentException> { encodeToMap(docRefBean) }
            .hasMessageThat()
            .contains("instead of String or DocumentReference")
    }

    @Test
    fun `DocumentId annotated on correct type without backfield is ignored during encoding`() {
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
    fun `DocumentId annotated on wrong type without backfield is ignored during encoding`() {
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
        assertThat(encodeToMap(GetterWithoutBackingFieldOnDocumentIdBean()))
            .containsExactlyEntriesIn(mutableMapOf("bar" to 0))
    }

    @Test
    fun `DocumentId annotated on correct types with backing fields should encode`() {
        val docRef = documentReference("coll/doc123")

        @Serializable class DocumentIdOnStringField(@KDocumentId val docId: String)
        assertThat(encodeToMap(DocumentIdOnStringField("docId")))
            .containsAtLeastEntriesIn(mutableMapOf<String, Any>())

        @Serializable
        class DocumentIdOnStringFieldAsProperty {
            @SerialName("DocIdProperty")
            @KDocumentId
            val docId = "doc-id"
                get() = field + "foobar"

            @SerialName("AnotherProperty") val someOtherProperty = 0
        }
        assertThat(encodeToMap(DocumentIdOnStringFieldAsProperty()))
            .containsAtLeastEntriesIn(mutableMapOf<String, Any>("AnotherProperty" to 0))

        @Serializable class DocumentIdOnDocRefProperty(@KDocumentId val docId: DocumentReference?)

        val documentIdOnDocRefField = DocumentIdOnDocRefProperty(docRef)
        assertThat(encodeToMap(documentIdOnDocRefField))
            .containsExactlyEntriesIn(mutableMapOf<String, Any>())

        @Serializable
        open class DocumentIdOnDocRefWithCustomGetter {
            @KDocumentId
            @SerialName("DocIdProperty")
            var docId: DocumentReference? = null
                get() = documentReference("coll/doc123456")

            @SerialName("AnotherProperty") var someOtherProperty: Int = 0
        }

        val documentIdOnDocRefAsProperty =
            DocumentIdOnDocRefWithCustomGetter().apply {
                docId = docRef
                someOtherProperty = 100
            }
        assertThat(encodeToMap(documentIdOnDocRefAsProperty))
            .containsExactlyEntriesIn(mutableMapOf<String, Any>("AnotherProperty" to 100))

        @Serializable
        class DocumentIdOnNestedObjects {
            @SerialName("nested")
            val nestedDocIdHolder: DocumentIdOnStringField = DocumentIdOnStringField("docId")
        }
        assertThat(encodeToMap(DocumentIdOnNestedObjects()))
            .containsExactlyEntriesIn(mutableMapOf<String, Any>("nested" to mapOf<String, Any>()))

        @Serializable
        class DocumentIdOnInheritedDocRefSetter(
            @KDocumentId val inheritedDocRef: DocumentReference
        ) : DocumentIdOnDocRefWithCustomGetter()

        val inheritedObject = DocumentIdOnInheritedDocRefSetter(inheritedDocRef = docRef)
        val actualMapOfInheritedObject = encodeToMap(inheritedObject)
        assertThat(actualMapOfInheritedObject)
            .containsExactlyEntriesIn(mutableMapOf<String, Any>("AnotherProperty" to 0))
    }

    @Test
    fun `non_null value fields with DocumentId annotation are replaced by docRef in decoding`() {
        @Serializable
        data class DocRefObject(
            @KDocumentId val doc: DocumentReference,
            @KDocumentId val docStr: String
        )

        val map = encodeToMap(DocRefObject(firestoreDocument, "foobar"))
        val decodedObject = decodeFromMap<DocRefObject>(map, firestoreDocument)
        assertThat(decodedObject).isEqualTo(DocRefObject(firestoreDocument, firestoreDocument.id))
    }

    @Test
    fun `null value fields with DocumentId annotation are replaced by docRef in decoding`() {
        @Serializable
        data class DocRefObject(
            @KDocumentId val doc: DocumentReference?,
            @KDocumentId val docStr: String?
        )

        val map = encodeToMap(DocRefObject(null, null))
        val decodedObject = decodeFromMap<DocRefObject>(map, firestoreDocument)
        assertThat(decodedObject).isEqualTo(DocRefObject(firestoreDocument, firestoreDocument.id))
    }

    @Test
    fun `null value contextual fields with DocumentId annotation are replaced by docRef in decoding`() {
        @Serializable
        data class DocRefObject(
            @Contextual @KDocumentId val doc: DocumentReference?,
            @Contextual @KDocumentId val docStr: String?
        )

        val map = encodeToMap(DocRefObject(firestoreDocument, "foobar"))
        val decodedObject = decodeFromMap<DocRefObject>(map, firestoreDocument)
        assertThat(decodedObject).isEqualTo(DocRefObject(firestoreDocument, firestoreDocument.id))
    }
}

private val firestoreDocument: DocumentReference = documentReference("abc/1234")
