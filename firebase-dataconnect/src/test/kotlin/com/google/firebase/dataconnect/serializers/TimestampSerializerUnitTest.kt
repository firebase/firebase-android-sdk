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
  fun `decoding should succeed when time offset is 0`() {
    assertThat(decodeTimestamp("2006-01-02T15:04:05-00:00"))
      .isEqualTo(decodeTimestamp("2006-01-02T15:04:05Z"))

    assertThat(decodeTimestamp("2006-01-02T15:04:05+00:00"))
      .isEqualTo(decodeTimestamp("2006-01-02T15:04:05Z"))
  }

  @Test
  fun `decoding should succeed when time offset is positive`() {
    assertThat(decodeTimestamp("2006-01-02T15:04:05+23:50"))
      .isEqualTo(decodeTimestamp("2006-01-03T14:54:05Z"))
  }

  @Test
  fun `decoding should succeed when time offset is negative`() {
    assertThat(decodeTimestamp("2006-01-02T15:04:05-05:10"))
      .isEqualTo(decodeTimestamp("2006-01-02T09:54:05Z"))
  }

  @Test
  fun `decoding should succeed when there are both time-secfrac and - time offset`() {
    assertThat(decodeTimestamp("2023-05-21T11:04:05.462-12:07"))
      .isEqualTo(decodeTimestamp("2023-05-20T22:57:05.462Z"))

    assertThat(decodeTimestamp("2053-11-02T15:04:05.743393-05:10"))
      .isEqualTo(decodeTimestamp("2053-11-02T09:54:05.743393Z"))

    assertThat(decodeTimestamp("1538-03-05T15:04:05.653498752-03:01"))
      .isEqualTo(decodeTimestamp("1538-03-05T12:03:05.653498752Z"))
  }

  @Test
  fun `decoding should succeed when there are both time-secfrac and + time offset`() {
    assertThat(decodeTimestamp("2023-05-21T11:04:05.662+12:07"))
      .isEqualTo(decodeTimestamp("2023-05-21T23:11:05.662Z"))

    assertThat(decodeTimestamp("2144-01-02T15:04:05.753493+01:00"))
      .isEqualTo(decodeTimestamp("2144-01-02T16:04:05.753493Z"))

    assertThat(decodeTimestamp("1358-03-05T15:04:05.527094582+13:03"))
      .isEqualTo(decodeTimestamp("1358-03-06T04:07:05.527094582Z"))
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
  fun `decoding should fail if 'time-offset' when 'time-secfrac' and time offset are both omitted`() {
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

  @Test
  fun `decoding should fail if time offset has no + or - sign`() {
    assertThrows(IllegalArgumentException::class) { decodeTimestamp("1985-04-12T23:20:5007:00") }
  }

  @Test
  fun `decoding should fail if time string has mix format`() {
    assertThrows(IllegalArgumentException::class) {
      decodeTimestamp("2006-01-02T15:04:05-07:00.123456X89Z")
    }
  }

  @Test
  fun `decoding should fail if time offset is not in the correct format`() {
    assertThrows(IllegalArgumentException::class) { decodeTimestamp("1985-04-12T23:20:50+7:00") }
  }

  @Test
  fun `decoding should throw an exception if the timestamp is invalid`() {
    invalidTimestampStrs.forEach {
      assertThrows(IllegalArgumentException::class) { decodeTimestamp(it) }
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

    // These strings were generated by Gemini
    val invalidTimestampStrs =
      listOf(
        "1985-04-12T23:20:50.123456789",
        "1985-04-12T23:20:50.123456789X",
        "1985-04-12T23:20:50.123456789+",
        "1985-04-12T23:20:50.123456789+07",
        "1985-04-12T23:20:50.123456789+07:",
        "1985-04-12T23:20:50.123456789+07:0",
        "1985-04-12T23:20:50.123456789+07:000",
        "1985-04-12T23:20:50.123456789+07:00a",
        "1985-04-12T23:20:50.123456789+07:a0",
        "1985-04-12T23:20:50.123456789+07::00",
        "1985-04-12T23:20:50.123456789+0:00",
        "1985-04-12T23:20:50.123456789+00:",
        "1985-04-12T23:20:50.123456789+00:0",
        "1985-04-12T23:20:50.123456789+00:a",
        "1985-04-12T23:20:50.123456789+00:0a",
        "1985-04-12T23:20:50.123456789+0:0a",
        "1985-04-12T23:20:50.123456789+0:a0",
        "1985-04-12T23:20:50.123456789+0::00",
        "1985-04-12T23:20:50.123456789-07:0a",
        "1985-04-12T23:20:50.123456789-07:a0",
        "1985-04-12T23:20:50.123456789-07::00",
        "1985-04-12T23:20:50.123456789-0:0a",
        "1985-04-12T23:20:50.123456789-0:a0",
        "1985-04-12T23:20:50.123456789-0::00",
        "1985-04-12T23:20:50.123456789-00:0a",
        "1985-04-12T23:20:50.123456789-00:a0",
        "1985-04-12T23:20:50.123456789-00::00",
        "1985-04-12T23:20:50.123456789-0:00",
        "1985-04-12T23:20:50.123456789-00:",
        "1985-04-12T23:20:50.123456789-00:0",
        "1985-04-12T23:20:50.123456789-00:a",
        "1985-04-12T23:20:50.123456789-00:0a",
        "1985-04-12T23:20:50.123456789-0:0a",
        "1985-04-12T23:20:50.123456789-0:a0",
        "1985-04-12T23:20:50.123456789-0::00",
        "1985/04/12T23:20:50.123456789Z",
        "1985-04-12T23:20:50.123456789Z.",
        "1985-04-12T23:20:50.123456789Z..",
        "1985-04-12T23:20:50.123456789Z...",
        "1985-04-12T23:20:50.123456789+07:00.",
        "1985-04-12T23:20:50.123456789+07:00..",
        "1985-04-12T23:20:50.123456789+07:00...",
        "1985-04-12T23:20:50.123456789-07:00.",
        "1985-04-12T23:20:50.123456789-07:00..",
        "1985-04-12T23:20:50.123456789-07:00...",
        "1985-04-12T23:20:50.1234567890Z",
        "1985-04-12T23:20:50.12345678900Z",
        "1985-04-12T23:20:50.123456789000Z",
        "1985-04-12T23:20:50.1234567890000Z",
        "1985-04-12T23:20:50.12345678900000Z",
        "1985-04-12T23:20:50.123456789000000Z",
        "1985-04-12T23:20:50.1234567890000000Z",
        "1985-04-12T23:20:50.12345678900000000Z",
        "1985-04-12T23:20:50.1234567891Z",
        "1985-04-12T23:20:50.12345678911Z",
        "1985-04-12T23:20:50.123456789111Z",
        "1985-04-12T23:20:50.1234567891111Z",
        "1985-04-12T23:20:50.12345678911111Z",
        "1985-04-12T23:20:50.123456789111111Z",
        "1985-04-12T23:20:50.1234567891111111Z",
        "1985-04-12T23:20:50.12345678911111111Z",
        "1985-04-12T23:20:50.123456789000000000Z",
        "1985-04-12T23:20:50.1234567890000000000Z",
        "1985-04-12T23:20:50.12345678900000000000Z",
      )
  }
}
