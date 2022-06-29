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
import com.google.firebase.firestore.ktx.annotations.KDocumentId
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
            @KDocumentId var DocID: String?
        )

        val plainProject = PlainProject("foo", "bar", true, null)
        docRefKotlin.set(plainProject)
        val actualObj = waitFor(docRefKotlin.get()).toObject<PlainProject>()
        Truth.assertThat(actualObj).isEqualTo(plainProject)
    }
}
