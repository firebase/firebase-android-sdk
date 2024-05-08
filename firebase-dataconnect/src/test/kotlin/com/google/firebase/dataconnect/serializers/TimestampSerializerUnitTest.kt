/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.serializers

import com.google.common.truth.Truth.assertThat
import com.google.firebase.Timestamp
import com.google.firebase.dataconnect.testutil.assertThrows
import com.google.firebase.dataconnect.util.buildStructProto
import com.google.firebase.dataconnect.util.decodeFromStruct
import com.google.firebase.dataconnect.util.encodeToStruct
import kotlinx.serialization.Serializable
import org.junit.Test

class TimestampSerializerUnitTest {

  @Test
  fun `seconds=0 nanoseconds=0 can be encoded and decoded`() {
    verifyEncodeDecodeRoundTrip(Timestamp(0, 0))
  }

  @Test
  fun `smallest value can be encoded and decoded`() {
    verifyEncodeDecodeRoundTrip(Timestamp(-62_135_596_800, 0))
  }

  @Test
  fun `largest value can be encoded and decoded`() {
    verifyEncodeDecodeRoundTrip(Timestamp(253_402_300_799, 999_999_999))
  }

  @Test
  fun `nanoseconds with millisecond precision can be encoded and decoded`() {
    verifyEncodeDecodeRoundTrip(Timestamp(130804, 642))
  }

  @Test
  fun `nanoseconds with microsecond precision can be encoded and decoded`() {
    verifyEncodeDecodeRoundTrip(Timestamp(-46239, 472302))
  }

  @Test
  fun `decoding should succeed when 'time-secfrac' is omitted`() {
    assertThat(decodeTimestamp("2006-01-02T15:04:05Z")).isEqualTo(Timestamp(1136214245, 0))
  }

  @Test
  fun `decoding should succeed when 'time-secfrac' has millisecond precision`() {
    assertThat(decodeTimestamp("2006-01-02T15:04:05.123Z"))
      .isEqualTo(Timestamp(1136214245, 123_000_000))
  }

  @Test
  fun `decoding should succeed when 'time-secfrac' has microsecond precision`() {
    assertThat(decodeTimestamp("2006-01-02T15:04:05.123456Z"))
      .isEqualTo(Timestamp(1136214245, 123_456_000))
  }

  @Test
  fun `decoding should succeed when 'time-secfrac' has nanosecond precision`() {
    assertThat(decodeTimestamp("2006-01-02T15:04:05.123456789Z"))
      .isEqualTo(Timestamp(1136214245, 123_456_789))
  }

  @Test
  fun `decoding should be case-insensitive`() {
    // According to https://www.rfc-editor.org/rfc/rfc3339#section-5.6 the "t" and "z" are
    // case-insensitive.
    assertThat(decodeTimestamp("2006-01-02t15:04:05.123456789z"))
      .isEqualTo(decodeTimestamp("2006-01-02T15:04:05.123456789Z"))
  }

  @Test
  fun `decoding should parse the minimum value officially supported by Data Connect`() {
    assertThat(decodeTimestamp("1583-01-01T00:00:00.000000Z")).isEqualTo(Timestamp(-12212553600, 0))
  }

  @Test
  fun `decoding should parse the maximum value officially supported by Data Connect`() {
    assertThat(decodeTimestamp("9999-12-31T23:59:59.999999999Z"))
      .isEqualTo(Timestamp(253402300799, 999999999))
  }

  @Test
  fun `decoding should fail for an empty string`() {
    assertThrows(IllegalArgumentException::class) { decodeTimestamp("") }
  }

  @Test
  fun `decoding should fail if 'time-offset' is omitted`() {
    assertThrows(IllegalArgumentException::class) {
      decodeTimestamp("2006-01-02T15:04:05.123456789")
    }
  }

  @Test
  fun `decoding should fail if 'time-offset' when 'time-secfrac' is also omitted`() {
    assertThrows(IllegalArgumentException::class) { decodeTimestamp("2006-01-02T15:04:05") }
  }

  @Test
  fun `decoding should fail if the date portion cannot be parsed`() {
    assertThrows(IllegalArgumentException::class) {
      decodeTimestamp("200X-01-02T15:04:05.123456789Z")
    }
  }

  @Test
  fun `decoding should fail if some character other than period delimits the 'time-secfrac'`() {
    assertThrows(IllegalArgumentException::class) {
      decodeTimestamp("2006-01-02T15:04:05 123456789Z")
    }
  }

  @Test
  fun `decoding should fail if 'time-secfrac' contains an invalid character`() {
    assertThrows(IllegalArgumentException::class) {
      decodeTimestamp("2006-01-02T15:04:05.123456X89Z")
    }
  }

  @Serializable
  private data class TimestampWrapper(
    @Serializable(with = TimestampSerializer::class) val timestamp: Timestamp
  )

  private companion object {

    fun verifyEncodeDecodeRoundTrip(timestamp: Timestamp) {
      val encoded = encodeToStruct(TimestampWrapper(timestamp))
      val decoded = decodeFromStruct<TimestampWrapper>(encoded)
      assertThat(decoded.timestamp).isEqualTo(timestamp)
    }

    fun decodeTimestamp(text: String): Timestamp {
      val encodedAsStruct = buildStructProto { put("timestamp", text) }
      val decodedStruct = decodeFromStruct<TimestampWrapper>(encodedAsStruct)
      return decodedStruct.timestamp
    }
  }
}
