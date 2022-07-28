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

package com.google.firebase.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.toObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComponentRegistrarTest {

    enum class Grade {
        FRESHMAN,
        SOPHOMORE,
        JUNIOR,
        SENIOR
    }

    @Test
    // Verify component registrar is working via assert Ktx mapper and POJO mapper save the same
    // result to Firestore.
    fun ktx_serialization_is_the_same_as_java_pojo_serialization() {
        @Serializable
        data class KtxStudent(
            val name: String,
            val id: Int,
            val age: Long,
            val termAvg: Double,
            val accumulateAvg: Float,
            val male: Boolean,
            val grade: Grade,
            @SerialName("NickName") val nickName: String,
            @Transient
            val homeAddress: String = "295 Lester ST" // @Transient must have default value
        )

        data class POJOStudent(
            val name: String? = null,
            val id: Int? = null,
            val age: Long? = null,
            val termAvg: Double? = null,
            val accumulateAvg: Float? = null,
            val male: Boolean? = null,
            val grade: Grade? = null,
            @get:PropertyName("NickName") val nickName: String? = null,
            @get:Exclude var homeAddress: String? = "305 Webber ST"
        )

        val ktxStudent =
            KtxStudent("foo", 1, 20L, 100.0, 99.5F, true, Grade.FRESHMAN, "bar", "foo-home-address")
        val pojoStudent =
            POJOStudent(
                "foo",
                1,
                20L,
                100.0,
                99.5F,
                true,
                Grade.FRESHMAN,
                "bar",
                "bar-home-address"
            )

        val docRefActual = testCollection("ktx").document("123")
        val docRefExpected = testCollection("pojo").document("456")
        docRefActual.set(ktxStudent)
        docRefExpected.withoutCustomMappers { set(pojoStudent) }
        val actual = waitFor(docRefActual.get()).data
        val expected = waitFor(docRefExpected.get()).data
        assertThat(actual).containsExactlyEntriesIn(expected)
    }

    @Test
    fun ktx_encode_decode_round_trip() {
        // This test assert ktx encoder and decoder are working as Java POJO mapper crashes without
        // no-argument constructor during decoding, Java POJO mapper encoder cannot encode field
        // start from Capital letters, and Boolean field start from `is`.
        @Serializable data class TestObj(val STR: String, val isBoolean: Boolean)

        val expected = TestObj("foobar", true)
        val docRefActual = testCollection("ktx").document("123")
        docRefActual.set(expected)
        val actual = waitFor(docRefActual.get()).toObject<TestObj>()
        assertThat(actual).isEqualTo(expected)
    }

    @Serializable
    private data class DocumentIdObj(@DocumentId val docId: String = "should be null in Java")

    @Test
    fun docId_annotation_should_be_shared_between_java_and_kotlin() {
        // Kotlin can see this @DocumentId annotation
        val annotations = DocumentIdObj.serializer().descriptor.getElementAnnotations(0)
        val annotation = annotations[0]
        assertThat(annotation::class.java).isAssignableTo(DocumentId::class.java)

        // Java POJO should also see this annotation and use it to skip the annotated field
        val docRef = testCollection("pojo").document("456")
        docRef.withoutCustomMappers { set(DocumentIdObj()) }
        val pojoMap = waitFor(docRef.get()).data
        assertThat(pojoMap).containsExactlyEntriesIn(emptyMap)
    }

    @Serializable private data class TimestampObj(@ServerTimestamp val time: Timestamp? = null)

    @Test
    fun serverTimestamp_annotation_should_be_shared_between_java_and_kotlin() {
        // Kotlin can see this @ServerTimestamp annotation
        val annotations = TimestampObj.serializer().descriptor.getElementAnnotations(0)
        val annotation = annotations[0]
        assertThat(annotation::class.java).isAssignableTo(ServerTimestamp::class.java)

        // Java POJO should also see this annotation and fill timestamp to the annotated field
        val docRef = testCollection("pojo").document("456")
        docRef.withoutCustomMappers { set(TimestampObj()) }
        val pojoMap: Map<String, Timestamp> =
            waitFor(docRef.get()).getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                as Map<String, Timestamp>
        // check generated timestamp equals `now` on second level
        assertThat(pojoMap["time"]).isEqualTo(Timestamp.now())
    }
}

private val emptyMap = emptyMap<String, Any?>()
