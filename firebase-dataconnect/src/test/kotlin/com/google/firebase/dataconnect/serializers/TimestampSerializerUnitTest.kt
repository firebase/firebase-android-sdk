// Copyright 2024 Google LLC
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
package com.google.firebase.dataconnect.serializers

import com.google.firebase.Timestamp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("ReplaceCallWithBinaryOperator")
class TimestampSerializerUnitTest {

  @Serializable
  data class TimestampTest(
    @Serializable(with = TimestampSerializer::class) var timestamp: Timestamp
  )

  @Test
  fun `test serialization between timestamp and RFC3339`() {
    val timestamps =
      listOf(
        // zero
        TimestampTest(Timestamp(0, 0)),
        // min
        TimestampTest(Timestamp(-62_135_596_800, 0)),
        // max
        TimestampTest(Timestamp(253_402_300_799, 999_999_999)),
        // positive
        TimestampTest(Timestamp(130804, 642)),
        // negative
        TimestampTest(Timestamp(-46239, 472302))
      )
    timestamps.forEach {
      val rfc3339 = Json.encodeToString(TimestampTest.serializer(), it)
      val timestamp = Json.decodeFromString(TimestampTest.serializer(), rfc3339)
      assertEquals(it, timestamp)
    }
  }
}
