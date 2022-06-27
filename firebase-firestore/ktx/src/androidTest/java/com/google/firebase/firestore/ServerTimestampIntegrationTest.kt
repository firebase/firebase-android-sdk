package com.google.firebase.firestore

import com.google.common.truth.Truth.assertThat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.ktx.annotations.KServerTimestamp
import com.google.firebase.firestore.testutil.setData
import com.google.firebase.firestore.testutil.testCollection
import com.google.firebase.firestore.testutil.waitFor
import java.util.Date
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
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

    // TODO: Add more integration test
}
