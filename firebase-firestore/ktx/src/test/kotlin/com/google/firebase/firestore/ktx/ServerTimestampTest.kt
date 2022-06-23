package com.google.firebase.firestore.ktx

import com.google.common.truth.Truth.assertThat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.documentReference
import com.google.firebase.firestore.ktx.annotations.KDocumentId
import com.google.firebase.firestore.ktx.annotations.KServerTimestamp
import com.google.firebase.firestore.ktx.serialization.encodeToMap
import java.util.Date
import kotlin.test.assertFailsWith
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.Test

class ServerTimestampTest {
    @Test
    fun `KServerTimestamp on wrong types throws`() {
        val docRef = documentReference("coll/doc123")

        @Serializable
        class DefaultValuePropertyWithDocumentIdOnWrongTypeBean(
            @KServerTimestamp var intField: Int = 123
        )

        @Serializable
        class NullablePropertyWithDocumentIdOnWrongTypeBean(
            @KServerTimestamp var intField: Int? = null
        )

        @Serializable
        class KServerTimestampOnWrongTypeDocRefBean(
            @KServerTimestamp @Contextual var documentReference: DocumentReference?
        )

        @Serializable
        class KServerTimestampOnTopOfKDocumentIdWrongTypeBean(
            @KServerTimestamp @KDocumentId @Contextual var documentReference: DocumentReference?
        )

        @Serializable
        class KServerTimestampAndKDocumentIdTogetherOnWrongTypeBean(
            @KServerTimestamp @KDocumentId @Contextual var geoPoint: GeoPoint?
        )

        @Serializable class Student(val id: Int = 0, val name: String = "foo")

        @Serializable
        class KServerTimestampOnWrongTypeNestedObject(
            @KServerTimestamp var student: Student? = null
        )

        @Serializable
        class KServerTimestampOnWrongTypeNestedListObject(
            @KServerTimestamp
            var listOfStudent: List<Student> = listOf(Student(1), Student(2), Student(3))
        )

        val exceptionMessage = "instead of Date or Timestamp"

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
            block = { encodeToMap(KServerTimestampOnWrongTypeDocRefBean(docRef)) }
        )

        assertFailsWith<IllegalArgumentException>(
            message = exceptionMessage,
            block = { encodeToMap(KServerTimestampOnTopOfKDocumentIdWrongTypeBean(docRef)) }
        )

        assertFailsWith<IllegalArgumentException>(
            message = exceptionMessage,
            block = { encodeToMap(KServerTimestampOnWrongTypeNestedObject(Student(100, "bar"))) }
        )

        assertFailsWith<IllegalArgumentException>(
            message = exceptionMessage,
            block = {
                encodeToMap(
                    KServerTimestampAndKDocumentIdTogetherOnWrongTypeBean(GeoPoint(1.0, 2.0))
                )
            }
        )

        assertFailsWith<IllegalArgumentException>(
            message = exceptionMessage,
            block = { encodeToMap(KServerTimestampOnWrongTypeNestedListObject()) }
        )
    }

    @Test
    fun `KServerTimestamp annotated on correct types with backing fields should encode`() {
        // correct type with null value will be replaced by FieldValue
        // correct type with non-null value will remain
        @Serializable
        class KServerTimestampOnDateField {
            @Contextual @KServerTimestamp var value: Date? = null
        }

        val dateFieldWithNullValue = encodeToMap(KServerTimestampOnDateField())
        assertThat(dateFieldWithNullValue)
            .containsExactlyEntriesIn(mutableMapOf("value" to FieldValue.serverTimestamp()))
        val dateFieldWithDateValue =
            encodeToMap(KServerTimestampOnDateField().apply { value = Date(100000L) })
        assertThat(dateFieldWithDateValue)
            .containsExactlyEntriesIn(mutableMapOf("value" to Date(100000L)))

        @Serializable
        class KServerTimestampOnDateFieldAsProperty {
            @SerialName("DateProperty")
            @Contextual
            @KServerTimestamp
            var value: Date? = null
                get() =
                    if (field == null) {
                        null
                    } else {
                        Date(100000L)
                    }

            @SerialName("AnotherProperty") var someOtherProperty = 0
        }

        val datePropertyWithNullValue = encodeToMap(KServerTimestampOnDateFieldAsProperty())
        val datePropertyWithDateValue =
            encodeToMap(KServerTimestampOnDateFieldAsProperty().apply { value = Date(800000L) })

        assertThat(datePropertyWithNullValue)
            .containsExactlyEntriesIn(
                mutableMapOf("DateProperty" to FieldValue.serverTimestamp(), "AnotherProperty" to 0)
            )

        assertThat(datePropertyWithDateValue)
            .containsExactlyEntriesIn(
                mutableMapOf("DateProperty" to Date(100000L), "AnotherProperty" to 0)
            )

        @Serializable
        class KServerTimestampOnTimestampField {
            @Contextual @KServerTimestamp var value: Timestamp? = null
        }

        val annotationOnTimestampFieldWithNullValue =
            encodeToMap(KServerTimestampOnTimestampField())
        assertThat(annotationOnTimestampFieldWithNullValue)
            .containsExactlyEntriesIn(mutableMapOf("value" to FieldValue.serverTimestamp()))
        val annotationOnTimestampFieldWithRealValue =
            encodeToMap(
                KServerTimestampOnTimestampField().apply { value = Timestamp(Date(100000L)) }
            )
        assertThat(annotationOnTimestampFieldWithRealValue)
            .containsExactlyEntriesIn(mutableMapOf("value" to Timestamp(Date(100000L))))

        @Serializable
        open class KTimestampOnTimestampFieldAsProperty {
            @Contextual
            @KServerTimestamp
            @SerialName("TimestampProperty")
            var docId: Timestamp? = null
                get() = field

            @SerialName("AnotherProperty") var someOtherProperty: Int = 0
        }

        val annotationOnTimestampPropertyWithNullValue =
            encodeToMap(KTimestampOnTimestampFieldAsProperty())
        assertThat(annotationOnTimestampPropertyWithNullValue)
            .containsExactlyEntriesIn(
                mutableMapOf(
                    "TimestampProperty" to FieldValue.serverTimestamp(),
                    "AnotherProperty" to 0
                )
            )

        val annotationOnTimestampPropertyWithRealValue =
            KTimestampOnTimestampFieldAsProperty().apply {
                docId = Timestamp(Date(100000L))
                someOtherProperty = 100
            }
        val timestampOnProperty = encodeToMap(annotationOnTimestampPropertyWithRealValue)
        assertThat(timestampOnProperty)
            .containsExactlyEntriesIn(
                mutableMapOf(
                    "TimestampProperty" to Timestamp(Date(100000L)),
                    "AnotherProperty" to 100
                )
            )
    }

    @Test
    fun `KServerTimestamp annotated on correct types without backing fields is ignored during encoding`() {
        @Serializable
        class GetterWithoutBackingFieldOnCorrectTypeBean {
            @KServerTimestamp
            val foo: Timestamp
                get() = Timestamp(Date(100000L)) // getter only, no backing field -- not serialized
            @KServerTimestamp
            val bar: Date
                get() = Date(100000L) // getter only, no backing field --not serialized
            val foobar: Int = 0 // property with a backing field -- serialized
        }

        assertThat(encodeToMap(GetterWithoutBackingFieldOnCorrectTypeBean()))
            .containsExactlyEntriesIn(mutableMapOf("foobar" to 0))
    }

    @Test
    fun `KServerTimestamp annotated on wrong types without backing field is also ignored during encoding`() {
        // fields without a getter is transparent to serialization process, so the data type where
        // annotation applied can not be verified
        // TODO: Write a compiler plugin to check if KServerTimestamp is applied on wrong types
        @Serializable
        class GetterWithoutBackingFieldOnWrongTypeBean {
            @KServerTimestamp
            val fooStr: String
                get() = "foobar"
            @KServerTimestamp
            val fooLong: Long
                get() = 100000L
            @KServerTimestamp
            val fooINt: Int
                get() = 0
            @KServerTimestamp
            val fooBool: Boolean
                get() = true
            @KServerTimestamp
            val fooDouble: Double
                get() = 200.0
            @KServerTimestamp
            val fooGeoPoint: GeoPoint
                get() = GeoPoint(100.0, 200.0)
        }
        assertThat(encodeToMap(GetterWithoutBackingFieldOnWrongTypeBean()))
            .containsExactlyEntriesIn(mutableMapOf<String, Any>())
    }
}
