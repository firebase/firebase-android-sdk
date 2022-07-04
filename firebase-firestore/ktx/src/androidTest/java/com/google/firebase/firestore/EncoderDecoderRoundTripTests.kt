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

import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.ktx.annotations.KDocumentId
import com.google.firebase.firestore.ktx.getField
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.testutil.testCollection
import com.google.firebase.firestore.testutil.waitFor
import kotlinx.serialization.Serializable
import org.junit.Test

class EncoderDecoderRoundTripTests {

    // This test is intended to fail for demo purpose
    //
    // expected: PlainProject(Name=foo, OwnerName=bar, BoolField=true, DocID=null) -> this is the
    // Kotlin Custom Object before the round trip
    //
    // but was : PlainProject(Name=foo, OwnerName=bar, BoolField=true, DocID=123-456-789) -> this is
    // the object after the round trip, DocID is filled by @KDcoumentId annotation
    //
    // This difference proofs that the DocumentSnapshot.toObject() is using the Kotlin decoder under the hood.
    //
    // It is also interesting to note that the field names does not need to have lower case letter at the beginning;
    // the boolean field name does not need to in the format of `isXXX`
    @Test
    fun encode_decode_round_trip() {
        val docRefKotlin = testCollection("ktx").document("123-456-789")
        @Serializable
        data class PlainProject(
            val Name: String,
            val OwnerName: String,
            val BoolField: Boolean,
//            @KDocumentId var DocID: String?
        )

        val plainProject = PlainProject("foo", "bar", true)
        docRefKotlin.set(plainProject)
        val actualObj = waitFor(docRefKotlin.get()).toObject<PlainProject>()
        Truth.assertThat(actualObj).isEqualTo(plainProject)
    }

    data class PlainProject(
        val Name: String? = null,
        val OwnerName: String? = null,
        val IsMyBoolField: Boolean? = null,
        @DocumentId val DocID: String? = null
    )

    // The current pain point of POJO solution (with the toObject() method):
    // 1. Must have the non-argument constructor, otherwise, it is a runtime error,
    // 2. All the field must start with lower case letter, xYYY, otherwise, it will just return an object with default value (because the .data map can not match the object field)
    // 3. Boolean field, if field start with 'is', must be annotated with @field:JvmField annotation, otherwise, it will just return an object with default value.

    // The current pain points of the POJO solution (with the docSnapshot.data method)
    // 1. Must have teh non-argument constructor, otherwise, it is a runtime error
    // 2. If the field name does not start with lower case letter, the generated map will have the first letter be converted to lower case anyways (because this is converted during the encoding process)
    // 3. If the boolean field name start with 'is', but is not annoated with @field:JvmField annotation, the generated map key will lost the leading 'is'
    @Test
    fun deocder_with_documetnId_path() {
        val docRefKotlin = testCollection("ktx").document("123-456-789")

        val plainProject = PlainProject("foo", "bar", true, null)
        docRefKotlin.set(plainProject)
//        val actualObj = waitFor(docRefKotlin.get()).toObject<PlainProject>()
//        Truth.assertThat(actualObj).isEqualTo(plainProject)

        val actualMap = waitFor(docRefKotlin.get()).data
        Truth.assertThat(actualMap).isEqualTo(mutableMapOf<String, Any>())
    }

    data class School(
        val name: String? = null,
        val ownerName: String? = null,
        val student: Student? = null,
        @DocumentId
        val outsideDocId: String? = null
    )

    data class Student(
        val age: Int = 10,
        val id: String = "foobar",
        @DocumentId
        val docId: String? = null
    )

    @Test
    fun test_for_docSnapshot_get() {
        val docRefKotlin = testCollection("ktx").document("123-456-789")
        val school = School("foo", "bar", Student(100, "sname"))
        docRefKotlin.set(mapOf("field" to school))

        val docSnapshot = waitFor(docRefKotlin.get())
        // snapshot.get(path) -> returns a map, I can decode a map to a object, should be no problem, this will be easy
//        val docSnapshotGetField = docSnapshot.get(FieldPath.of("field")) as MutableMap<String, Any>
//        Truth.assertThat(docSnapshotGetField["student"]).isEqualTo(mutableMapOf<String, Any>())
//        but was : {id=sname, age=100}

        val docSnapshotGetField = docSnapshot.getField<Student>(FieldPath.of("field", "student"))
//        Truth.assertThat(docSnapshotGetField).isEqualTo(mutableMapOf<String, Any>())
//        // Student(age=100, id=sname, docId=123-456-789)

//        val docSnapshotGetField = docSnapshot.getField<School>(FieldPath.of("field"))
//        Truth.assertThat(docSnapshotGetField).isEqualTo(mutableMapOf<String, Any>())
//        but was : School(name=foo, ownerName=bar, student=Student(age=100, id=sname, docId=123-456-789), outsideDocId=123-456-789)
    }

    @Test
    fun can_any_field_in_firestore_be_null() {
        val docRefKotlin = testCollection("ktx").document("123-456-789")
        data class Pig(val name: String? = null, val weight: Long? = 10L)
        docRefKotlin.set(Pig())
        val actualObj = waitFor(docRefKotlin.get()).data
        assertThat(actualObj).isEqualTo(mutableMapOf<String, Any>())
        // but was         : {name=null, weight=10}
    }
}
