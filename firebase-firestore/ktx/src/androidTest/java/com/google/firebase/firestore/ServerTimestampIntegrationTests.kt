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
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.ktx.annotations.KServerTimestamp
import com.google.firebase.firestore.ktx.toObject
import java.lang.Math.abs
import java.util.Date
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.junit.Assert
import org.junit.Test

class ServerTimestampIntegrationTests {

    companion object {
        // Tolerate up to 48*60*60 seconds of clock skew between client and server. This should be
        // more than enough to compensate for timezone issues (even after taking daylight saving
        // into account) and should allow local clocks to deviate from true time slightly and still
        // pass the test.
        private const val deltaSec = 48 * 60 * 60
    }

    @Serializable
    private data class TimestampPOJO(
        @KServerTimestamp @ServerTimestamp val timestamp: Timestamp? = null,
        @Contextual @KServerTimestamp @ServerTimestamp val date: Date? = null
    )

    @Test
    fun ktx_resolved_timestamp_is_equivalent_to_java() {
        val docRefKotlin = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("456")

        val timestampPOJO = TimestampPOJO()

        docRefKotlin.set(timestampPOJO)
        docRefPOJO.withEmptyMapper { set(timestampPOJO) }
        val expected =
            waitFor(docRefPOJO.get()).withEmptyMapper {
                toObject<TimestampPOJO>(ServerTimestampBehavior.ESTIMATE)
            } as TimestampPOJO
        val actual =
            waitFor(docRefKotlin.get()).toObject<TimestampPOJO>(ServerTimestampBehavior.ESTIMATE)

        // assert ktx round-trip result is the same as Java
        val ktxTimestamp = actual?.timestamp!!
        val javaTimestamp = expected?.timestamp!!
        Assert.assertTrue(
            "ktx resolved timestamp ($ktxTimestamp) should be within $deltaSec of java ( $javaTimestamp )",
            abs(ktxTimestamp.seconds - javaTimestamp.seconds) < deltaSec
        )

        val ktxDate = actual?.date!!
        val javaDate = expected?.date!!
        Assert.assertTrue(
            "ktx resolved timestamp ($ktxDate) should be within $deltaSec of java ( $javaDate )",
            abs(ktxDate.seconds - javaDate.seconds) < deltaSec
        )
    }

    @Test
    fun serverTimestamp_annotation_on_null_value_correct_field_round_trip() {
        // Both Date and Timestamp are saved as Timestamp in Firestore
        val docRefKotlin = testCollection("ktx").document("123")
        @Serializable
        data class TimestampKtx(
            @KServerTimestamp val timestamp: Timestamp? = null,
            @Contextual @KServerTimestamp val date: Date? = null,
            val timestampWithDefaultValue: Timestamp = Timestamp(Date(100000L)),
            @Contextual val dateWithDefaultValue: Date = Date(100000L)
        )
        docRefKotlin.set(TimestampKtx()) // encoding with annotations

        val actualObjWithEstimateTimestamp =
            waitFor(docRefKotlin.get()).toObject<TimestampKtx>(ServerTimestampBehavior.ESTIMATE)

        val serverGeneratedTimestamp = actualObjWithEstimateTimestamp?.timestamp!! // cannot be null
        val serverGeneratedDate =
            Timestamp(actualObjWithEstimateTimestamp?.date!!) // cannot be null
        assertThat(serverGeneratedTimestamp).isEquivalentAccordingToCompareTo(serverGeneratedDate)

        val now = Timestamp.now()
        Assert.assertTrue(
            "resolved timestamp ($serverGeneratedTimestamp) should be within $deltaSec of now ( $now )",
            abs(serverGeneratedTimestamp.seconds - now.seconds) < deltaSec
        )

        // Validate the rest of the document.
        val actualObjWithNullTimestamp = waitFor(docRefKotlin.get()).toObject<TimestampKtx>()
        assertThat(actualObjWithNullTimestamp).isEqualTo(TimestampKtx())
    }
}
