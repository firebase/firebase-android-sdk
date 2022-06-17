package com.google.firebase.firestore.ktx

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.ktx.serialization.encodeToMap
import kotlinx.serialization.Serializable
import org.junit.Test

class ServerTimestampTest {

    @Test
    fun `kServerTimestamp can be applied on not null field`() {
        class TimestampPOJO {
            @ServerTimestamp
            var timestamp1: Timestamp? = null
        }

        val timestampPOJO = TimestampPOJO()
        timestampPOJO.timestamp1 = Timestamp.now()
    }
}
