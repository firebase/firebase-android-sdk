package com.google.firebase.firestore.ktx

import com.google.common.truth.Truth.assertThat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.assertThrows
import com.google.firebase.firestore.documentReference
import com.google.firebase.firestore.ktx.serialization.encodeToMap
import java.util.Date
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ServerTimestampTests {
    @Test
    fun `KServerTimestamp on wrong types throws`() {
        val docRef = documentReference("coll/doc123")

        @Serializable
        class DefaultValuePropertyWithServerTimestampOnWrongTypeBean(
            @ServerTimestamp val intField: Int?
        )

        assertThrows<IllegalArgumentException> {
                encodeToMap(DefaultValuePropertyWithServerTimestampOnWrongTypeBean(null))
            }
            .hasMessageThat()
            .contains("instead of Date or Timestamp.")

        assertThrows<IllegalArgumentException> {
                encodeToMap(DefaultValuePropertyWithServerTimestampOnWrongTypeBean(123))
            }
            .hasMessageThat()
            .contains("instead of Date or Timestamp.")

        @Serializable
        class KServerTimestampOnWrongTypeDocRefBean(
            @ServerTimestamp val documentReference: DocumentReference?
        )
        assertThrows<IllegalArgumentException> {
                encodeToMap(KServerTimestampOnWrongTypeDocRefBean(null))
            }
            .hasMessageThat()
            .contains("instead of Date or Timestamp.")

        assertThrows<IllegalArgumentException> {
                encodeToMap(KServerTimestampOnWrongTypeDocRefBean(docRef))
            }
            .hasMessageThat()
            .contains("instead of Date or Timestamp.")

        @Serializable
        class KServerTimestampOnTopOfKDocumentIdWrongTypeBean(
            @ServerTimestamp @DocumentId val documentReference: DocumentReference?
        )

        assertThrows<IllegalArgumentException> {
                encodeToMap(KServerTimestampOnTopOfKDocumentIdWrongTypeBean(null))
            }
            .hasMessageThat()
            .contains("instead of Date or Timestamp.")

        assertThrows<IllegalArgumentException> {
                encodeToMap(KServerTimestampOnTopOfKDocumentIdWrongTypeBean(docRef))
            }
            .hasMessageThat()
            .contains("instead of Date or Timestamp.")

        @Serializable
        class KServerTimestampAndKDocumentIdTogetherOnWrongTypeBean(
            @ServerTimestamp @DocumentId @Contextual val geoPoint: GeoPoint?
        )

        assertThrows<IllegalArgumentException> {
                encodeToMap(KServerTimestampAndKDocumentIdTogetherOnWrongTypeBean(null))
            }
            .hasMessageThat()
            .contains("instead of String or DocumentReference.") // always throw KDocumentId first

        assertThrows<IllegalArgumentException> {
                encodeToMap(
                    KServerTimestampAndKDocumentIdTogetherOnWrongTypeBean(GeoPoint(1.0, 2.0))
                )
            }
            .hasMessageThat()
            .contains("instead of String or DocumentReference.")

        @Serializable class Student(val id: Int = 0, val name: String = "foo")

        @Serializable
        class KServerTimestampOnWrongTypeNestedObject(@ServerTimestamp val student: Student?)

        assertThrows<IllegalArgumentException> {
                encodeToMap(KServerTimestampOnWrongTypeNestedObject(null))
            }
            .hasMessageThat()
            .contains("instead of Date or Timestamp.")

        assertThrows<IllegalArgumentException> {
                encodeToMap(KServerTimestampOnWrongTypeNestedObject(Student(100, "bar")))
            }
            .hasMessageThat()
            .contains("instead of Date or Timestamp.")

        @Serializable
        class KServerTimestampOnWrongTypeNestedListObject(
            @ServerTimestamp
            val listOfStudent: List<Student>? = listOf(Student(1), Student(2), Student(3))
        )

        assertThrows<IllegalArgumentException> {
                encodeToMap(KServerTimestampOnWrongTypeNestedListObject(null))
            }
            .hasMessageThat()
            .contains("instead of Date or Timestamp.")

        assertThrows<IllegalArgumentException> {
                encodeToMap(KServerTimestampOnWrongTypeNestedListObject())
            }
            .hasMessageThat()
            .contains("instead of Date or Timestamp.")
    }

    @Test
    fun `KServerTimestamp annotated on correct types with backing fields should encode`() {
        // correct type with null value will be replaced by FieldValue
        // correct type with non-null value will remain
        @Serializable
        class KServerTimestampOnDateField(@Contextual @ServerTimestamp val value: Date?)

        val dateFieldWithNullValue = encodeToMap(KServerTimestampOnDateField(null))
        assertThat(dateFieldWithNullValue)
            .containsExactlyEntriesIn(mutableMapOf("value" to FieldValue.serverTimestamp()))
        val dateFieldWithDateValue = encodeToMap(KServerTimestampOnDateField(Date(100000L)))
        assertThat(dateFieldWithDateValue)
            .containsExactlyEntriesIn(mutableMapOf("value" to Date(100000L)))

        @Serializable
        class KServerTimestampOnDateFieldAsProperty {
            @SerialName("DateProperty")
            @Contextual
            @ServerTimestamp
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
        class KServerTimestampOnTimestampField(@ServerTimestamp val value: Timestamp?)

        val annotationOnTimestampFieldWithNullValue =
            encodeToMap(KServerTimestampOnTimestampField(null))
        assertThat(annotationOnTimestampFieldWithNullValue)
            .containsExactlyEntriesIn(mutableMapOf("value" to FieldValue.serverTimestamp()))
        val annotationOnTimestampFieldWithRealValue =
            encodeToMap(KServerTimestampOnTimestampField(Timestamp(Date(100000L))))
        assertThat(annotationOnTimestampFieldWithRealValue)
            .containsExactlyEntriesIn(mutableMapOf("value" to Timestamp(Date(100000L))))

        @Serializable
        open class KTimestampOnTimestampFieldAsProperty {
            @ServerTimestamp
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
            @ServerTimestamp
            val foo: Timestamp
                get() = Timestamp(Date(100000L)) // getter only, no backing field -- not serialized
            @ServerTimestamp
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
        @Serializable
        class GetterWithoutBackingFieldOnWrongTypeBean {
            @ServerTimestamp
            val fooStr: String
                get() = "foobar"
            @ServerTimestamp
            val fooLong: Long
                get() = 100000L
            @ServerTimestamp
            val fooINt: Int
                get() = 0
            @ServerTimestamp
            val fooBool: Boolean
                get() = true
            @ServerTimestamp
            val fooDouble: Double
                get() = 200.0
            @ServerTimestamp
            val fooGeoPoint: GeoPoint
                get() = GeoPoint(100.0, 200.0)
        }
        assertThat(encodeToMap(GetterWithoutBackingFieldOnWrongTypeBean()))
            .containsExactlyEntriesIn(mutableMapOf<String, Any>())
    }
}
