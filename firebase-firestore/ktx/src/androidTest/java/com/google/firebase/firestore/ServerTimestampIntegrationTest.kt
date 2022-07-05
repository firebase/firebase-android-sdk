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
import com.google.firebase.firestore.testutil.getData
import com.google.firebase.firestore.testutil.setData
import com.google.firebase.firestore.testutil.testCollection
import com.google.firebase.firestore.testutil.waitFor
import java.util.Date
import kotlin.math.abs
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.junit.Assert
import org.junit.Test

class ServerTimestampIntegrationTest {

    @Test
    fun encoding_Timestamp_is_supported() {
        val docRefKotlin = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("456")

        @Serializable
        class TimestampPOJO {
            @Contextual @KServerTimestamp @ServerTimestamp var timestamp1: Timestamp? = null

            @Contextual @KServerTimestamp @ServerTimestamp val timestamp2: Date? = null
        }

        val timestampPOJO = TimestampPOJO()
        timestampPOJO.timestamp1 = Timestamp(Date(100L))
        docRefPOJO.set(timestampPOJO)
        docRefKotlin.setData(timestampPOJO)
        val expected = waitFor(docRefKotlin.get()).getData(ServerTimestampBehavior.NONE)
        val actual = waitFor(docRefPOJO.get()).getData(ServerTimestampBehavior.NONE)
        assertThat(expected).containsExactlyEntriesIn(actual)
    }

    @Test
    fun kServerTimestamp_annotation_on_null_value_correct_field_round_trip() {
        // Both Date and Timestamp are saved as Timestamp in Firestore
        val docRefKotlin = testCollection("ktx").document("123")
        @Serializable
        data class TimestampKtx(
            @Contextual @KServerTimestamp val timestamp: Timestamp? = null,
            @Contextual @KServerTimestamp val date: Date? = null,
            @Contextual val timestampWithDefaultValue: Timestamp = Timestamp(Date(100000L)),
            @Contextual val dateWithDefaultValue: Date = Date(100000L)
        )
        docRefKotlin.set(TimestampKtx()) // encoding with annotation
        val actualObjWithEstimateTimestamp =
            waitFor(docRefKotlin.get()).getData<TimestampKtx>(ServerTimestampBehavior.ESTIMATE)
        val serverGeneratedTimestamp = actualObjWithEstimateTimestamp?.timestamp!! // cannot be null
        val serverGeneratedDate = Timestamp(actualObjWithEstimateTimestamp?.date!!) // cannot be null
        assertThat(serverGeneratedTimestamp).isEquivalentAccordingToCompareTo(serverGeneratedDate)
        // Tolerate up to 48*60*60 seconds of clock skew between client and server. This should be
        // more than enough to compensate for timezone issues (even after taking daylight saving
        // into account) and should allow local clocks to deviate from true time slightly and still
        // pass the test.
        val deltaSec = 48 * 60 * 60
        val now = Timestamp.now()
        Assert.assertTrue(
            "resolved timestamp ($serverGeneratedTimestamp) should be within $deltaSec of now ( $now )",
            abs(serverGeneratedTimestamp.seconds - now.seconds) < deltaSec
        )

        // Validate the rest of the document.
        val actualObjWithNullTimestamp = waitFor(docRefKotlin.get()).getData<TimestampKtx>()
        assertThat(actualObjWithNullTimestamp).isEqualTo(TimestampKtx())
    }

    // TODO: Add more integration test
}
