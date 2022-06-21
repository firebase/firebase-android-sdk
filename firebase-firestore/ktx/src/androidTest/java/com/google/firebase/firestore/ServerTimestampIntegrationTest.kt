package com.google.firebase.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.testutil.testCollection
import com.google.firebase.firestore.testutil.waitFor
import java.util.*
import kotlin.test.assertEquals
import org.junit.Test

class ServerTimestampIntegrationTest {

    @Test
    fun can_be_applied_on_not_null_field() {
        val docRefKotlin = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("456")

        class TimestampPOJO {
            @ServerTimestamp
            var timestamp1: Timestamp? = null

            @ServerTimestamp
            val timestamp2: Date? = null
        }

        val timestampPOJO = TimestampPOJO()
        timestampPOJO.timestamp1 = Timestamp(Date(100L))
        docRefPOJO.set(timestampPOJO)
        val expected = waitFor(docRefPOJO.get()).getData(ServerTimestampBehavior.NONE)
        assertEquals(expected, mutableMapOf<String, Any?>("timestamp1" to null))
    }
}
