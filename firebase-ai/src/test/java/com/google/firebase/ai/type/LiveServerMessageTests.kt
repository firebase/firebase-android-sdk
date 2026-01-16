/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.ai.type

import com.google.firebase.ai.common.JSON
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Test

@OptIn(PublicPreviewAPI::class)
internal class LiveServerMessageTests {

  // ===== Duration Parsing Tests =====

  @Test
  fun `parseTimeLeft with integer seconds`() {
    val goAway = LiveServerGoAway("57s")
    val duration = goAway.parseTimeLeft()

    duration.shouldNotBeNull()
    duration.inWholeSeconds shouldBe 57
  }

  @Test
  fun `parseTimeLeft with fractional seconds`() {
    val goAway = LiveServerGoAway("1.5s")
    val duration = goAway.parseTimeLeft()

    duration.shouldNotBeNull()
    duration.inWholeMilliseconds shouldBe 1500
  }

  @Test
  fun `parseTimeLeft with small fractional seconds (nanoseconds)`() {
    val goAway = LiveServerGoAway("0.000000001s")
    val duration = goAway.parseTimeLeft()

    duration.shouldNotBeNull()
    duration.inWholeNanoseconds shouldBe 1
  }

  @Test
  fun `parseTimeLeft with zero seconds`() {
    val goAway = LiveServerGoAway("0s")
    val duration = goAway.parseTimeLeft()

    duration.shouldNotBeNull()
    duration.inWholeSeconds shouldBe 0
  }

  @Test
  fun `parseTimeLeft with null timeLeft`() {
    val goAway = LiveServerGoAway(null)
    val duration = goAway.parseTimeLeft()

    duration.shouldBeNull()
  }

  @Test
  fun `parseTimeLeft with invalid format returns null`() {
    val goAway = LiveServerGoAway("invalid")
    val duration = goAway.parseTimeLeft()

    duration.shouldBeNull()
  }

  @Test
  fun `parseTimeLeft with non-second units returns null`() {
    val goAway = LiveServerGoAway("100ms")
    val duration = goAway.parseTimeLeft()

    // Protobuf Duration only uses 's' suffix
    duration.shouldBeNull()
  }

  // ===== LiveServerGoAway Deserialization Tests =====

  @Test
  fun `LiveServerGoAway with timeLeft as string`() {
    val json =
      """
      {
        "goAway": {
          "timeLeft": "57s"
        }
      }
    """
        .trimIndent()

    val message = JSON.decodeFromString<InternalLiveServerMessage>(json)
    val goAway = message.toPublic()

    goAway.shouldBeInstanceOf<LiveServerGoAway>()
    goAway.timeLeft shouldBe "57s"

    // Test parsing
    val duration = goAway.parseTimeLeft()
    duration.shouldNotBeNull()
    duration.inWholeSeconds shouldBe 57
  }

  @Test
  fun `LiveServerGoAway with fractional seconds string`() {
    val json =
      """
      {
        "goAway": {
          "timeLeft": "1.5s"
        }
      }
    """
        .trimIndent()

    val message = JSON.decodeFromString<InternalLiveServerMessage>(json)
    val goAway = message.toPublic() as LiveServerGoAway

    goAway.timeLeft shouldBe "1.5s"

    val duration = goAway.parseTimeLeft()
    duration.shouldNotBeNull()
    duration.inWholeMilliseconds shouldBe 1500
  }

  @Test
  fun `LiveServerGoAway with null timeLeft`() {
    val json = """{"goAway": {"timeLeft": null}}"""

    val message = JSON.decodeFromString<InternalLiveServerMessage>(json)
    val goAway = message.toPublic()

    goAway.shouldBeInstanceOf<LiveServerGoAway>()
    (goAway as LiveServerGoAway).timeLeft.shouldBeNull()
  }

  @Test
  fun `LiveServerGoAway with missing timeLeft field`() {
    val json = """{"goAway": {}}"""

    val message = JSON.decodeFromString<InternalLiveServerMessage>(json)
    val goAway = message.toPublic() as LiveServerGoAway

    goAway.timeLeft.shouldBeNull()
  }

  // ===== Polymorphic Serializer Tests =====

  @Test
  fun `LiveServerMessageSerializer recognizes goAway message`() {
    val json =
      """
      {
        "goAway": {
          "timeLeft": "30s"
        }
      }
    """
        .trimIndent()

    // Should not throw SerializationException
    val message = JSON.decodeFromString<InternalLiveServerMessage>(json)
    message.toPublic().shouldBeInstanceOf<LiveServerGoAway>()
  }

  @Test
  fun `LiveServerMessageSerializer throws on unknown message type`() {
    val json = """{"unknownType": {"data": "value"}}"""

    shouldThrow<SerializationException> { JSON.decodeFromString<InternalLiveServerMessage>(json) }
  }

  @Test
  fun `LiveServerMessageSerializer recognizes serverContent message`() {
    val json =
      """
      {
        "serverContent": {
          "modelTurn": null,
          "interrupted": false,
          "turnComplete": false
        }
      }
    """
        .trimIndent()

    val message = JSON.decodeFromString<InternalLiveServerMessage>(json)
    message.toPublic().shouldBeInstanceOf<LiveServerContent>()
  }

  @Test
  fun `LiveServerMessageSerializer recognizes setupComplete message`() {
    val json = """{"setupComplete": {}}"""

    val message = JSON.decodeFromString<InternalLiveServerMessage>(json)
    message.toPublic().shouldBeInstanceOf<LiveServerSetupComplete>()
  }

  @Test
  fun `LiveServerMessageSerializer recognizes toolCall message`() {
    val json = """{"toolCall": {"functionCalls": []}}"""

    val message = JSON.decodeFromString<InternalLiveServerMessage>(json)
    message.toPublic().shouldBeInstanceOf<LiveServerToolCall>()
  }

  @Test
  fun `LiveServerMessageSerializer recognizes toolCallCancellation message`() {
    val json = """{"toolCallCancellation": {"functionIds": []}}"""

    val message = JSON.decodeFromString<InternalLiveServerMessage>(json)
    message.toPublic().shouldBeInstanceOf<LiveServerToolCallCancellation>()
  }
}
