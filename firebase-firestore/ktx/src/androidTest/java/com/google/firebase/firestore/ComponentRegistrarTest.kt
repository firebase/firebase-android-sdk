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

import com.google.common.truth.Truth.assertThat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.annotations.KDocumentId
import com.google.firebase.firestore.ktx.annotations.KServerTimestamp
import com.google.firebase.firestore.testCollection
import com.google.firebase.firestore.waitFor
import java.util.Date
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.junit.Test

class ComponentRegistrarTest {

    // Verify the DocumentReference.set() method is serializing @Serializable object via the
    // MapEncoderKtxImp.
    @Test
    fun documentReference_is_using_ktx_mapencoder_when_set_to_firestore() {
        val docRefKotlin = testCollection("ktx").document("123")
        @Serializable
        data class Student(
            val Name: String = "fool",
            @get:Exclude val isNotUsingJavaPOJOSetMethod: Boolean = true
        )
        docRefKotlin.set(Student())
        val actual = waitFor(docRefKotlin.get()).data
        // If Java POJO method is used for encoding, the first letter of the field name will be
        // converted to lower case, key name will be "name", instead of "Name".
        assertThat(actual?.keys).contains("Name")
        // If Java POJO method is used for encoding, this field will be excluded, then assertion
        // will equal to "null", instead of "true".
        assertThat(actual?.get("isNotUsingJavaPOJOSetMethod")).isEqualTo(true)
    }

    enum class Grade {
        FRESHMAN,
        SOPHOMORE,
        JUNIOR,
        SENIOR
    }

    @Test
    fun ktx_serialization_is_the_same_as_java_pojo_serialization() {
        val docRefKotlin = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("456")

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
        docRefKotlin.set(ktxStudent)
        docRefPOJO.set(pojoStudent)
        val actual = waitFor(docRefKotlin.get()).data
        val expected = waitFor(docRefPOJO.get()).data
        assertThat(actual).containsExactlyEntriesIn(expected)
    }

    @Test
    fun nested_object_serialization_is_equivalent() {
        val docRefKotlin = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("456")

        @Serializable data class Owner(val name: String? = null, val age: Int? = 100)

        @Serializable
        data class KtxProject(
            val name: String,
            val owner: Owner, // nested object without @Serializable cannot be complied
            @Contextual @KDocumentId val docRef: DocumentReference,
            @KDocumentId val docId: String
        )

        data class POJOProject(
            val name: String? = null,
            val owner: Owner? = null,
            @DocumentId val docRef: DocumentReference? = null,
            @DocumentId val docId: String? = null
        )

        docRefKotlin.set(KtxProject("foo", Owner("bar"), docRefKotlin, "foo"))
        docRefPOJO.set(POJOProject("foo", Owner("bar"), docRefKotlin, "bar"))
        val expected = waitFor(docRefPOJO.get()).data
        val actual = waitFor(docRefKotlin.get()).data
        assertThat(expected).containsExactlyEntriesIn(actual)
    }

    @Test
    fun serverTimestamp_annotated_serialization_is_equivalent() {
        val docRefKotlin = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("456")

        @Serializable
        class KtxTimestamp(
            @Contextual val timestamp0: Timestamp,
            @Contextual @KServerTimestamp var timestamp1: Timestamp? = null,
            @Contextual @KServerTimestamp val date: Date? = null
        )

        class POJOTimestamp(
            val timestamp0: Timestamp? = null,
            @ServerTimestamp var timestamp1: Timestamp? = null,
            @ServerTimestamp val date: Date? = null
        )

        docRefKotlin.set(KtxTimestamp(timestamp0 = Timestamp(Date(100000L))))
        docRefPOJO.set(POJOTimestamp(timestamp0 = Timestamp(Date(100000L))))

        // assert each of the fields equals to each other.
        val expected =
            waitFor(docRefPOJO.get()).getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        val actual =
            waitFor(docRefKotlin.get()).getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        assertThat(expected?.get("timestamp0")).isEqualTo(actual?.get("timestamp0"))

        // there will be nanosecond level difference between two set methods
        val expectedTimestampRoundToSeconds = (expected?.get("timestamp0") as Timestamp).seconds
        val actualTimestampRoundToSeconds = (actual?.get("timestamp0") as Timestamp).seconds
        assertThat(expectedTimestampRoundToSeconds).isEqualTo(actualTimestampRoundToSeconds)

        val expectedDateRoundToSeconds = (expected?.get("date") as Timestamp).seconds
        val actualDateRoundToSeconds = (actual?.get("date") as Timestamp).seconds
        assertThat(expectedDateRoundToSeconds).isEqualTo(actualDateRoundToSeconds)
    }

    @Test
    fun remove_mapper_test(){

        val docRefKotlin = testCollection("ktx").document("123")
        val docRefPOJO = testJavaCollection("pojo").document("456")

        @Serializable
        data class TestObj(val str:String?=null)
        docRefKotlin.set(TestObj("123"))
        docRefPOJO.set(TestObj("456"))

        val actual = waitFor(docRefPOJO.get()).data


    }
}
