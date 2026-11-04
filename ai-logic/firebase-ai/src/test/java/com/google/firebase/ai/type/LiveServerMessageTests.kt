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
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Test

@OptIn(PublicPreviewAPI::class)
internal class LiveServerMessageTests {

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
    (goAway as LiveServerGoAway).timeLeft.shouldNotBeNull()
    goAway.timeLeft?.inWholeSeconds shouldBe 57
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

    goAway.timeLeft.shouldNotBeNull()
    goAway.timeLeft?.inWholeMilliseconds shouldBe 1500
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
  fun `LiveServerMessageSerializer returns LiveServerUnknownMessage for unrecognized message`() {
    val json = """{"unknownType": {"data": "value"}}"""

    val message = JSON.decodeFromString<InternalLiveServerMessage>(json)
    message.toPublic().shouldBeInstanceOf<LiveServerUnknownMessage>()
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

  // ===== LiveServerVoiceActivity & VoiceActivityType Tests =====

  @Test
  fun `LiveServerVoiceActivity with ACTIVITY_START and fractional audioOffset`() {
    val json = """{"voiceActivity": {"type": "ACTIVITY_START", "audioOffset": "2.520s"}}"""

    val message = JSON.decodeFromString<InternalLiveServerMessage>(json)
    val voiceActivity = message.toPublic()

    voiceActivity.shouldBeInstanceOf<LiveServerVoiceActivity>()
    voiceActivity.type shouldBe VoiceActivityType.ACTIVITY_START
    voiceActivity.audioOffset.shouldNotBeNull()
    voiceActivity.audioOffset?.inWholeMilliseconds shouldBe 2520
  }

  @Test
  fun `LiveServerVoiceActivity with ACTIVITY_END and fractional audioOffset`() {
    val json = """{"voiceActivity": {"type": "ACTIVITY_END", "audioOffset": "3.640s"}}"""

    val message = JSON.decodeFromString<InternalLiveServerMessage>(json)
    val voiceActivity = message.toPublic()

    voiceActivity.shouldBeInstanceOf<LiveServerVoiceActivity>()
    voiceActivity.type shouldBe VoiceActivityType.ACTIVITY_END
    voiceActivity.audioOffset.shouldNotBeNull()
    voiceActivity.audioOffset?.inWholeMilliseconds shouldBe 3640
  }

  @Test
  fun `LiveServerVoiceActivity with TYPE_UNSPECIFIED maps to UNKNOWN`() {
    val json = """{"voiceActivity": {"type": "TYPE_UNSPECIFIED", "audioOffset": "0s"}}"""

    val message = JSON.decodeFromString<InternalLiveServerMessage>(json)
    val voiceActivity = message.toPublic()

    voiceActivity.shouldBeInstanceOf<LiveServerVoiceActivity>()
    voiceActivity.type shouldBe VoiceActivityType.UNKNOWN
    voiceActivity.audioOffset.shouldNotBeNull()
    voiceActivity.audioOffset?.inWholeMilliseconds shouldBe 0
  }

  @Test
  fun `LiveServerVoiceActivity with unrecognized type maps to UNKNOWN`() {
    val json = """{"voiceActivity": {"type": "FUTURE_ACTIVITY_TYPE", "audioOffset": "1s"}}"""

    val message = JSON.decodeFromString<InternalLiveServerMessage>(json)
    val voiceActivity = message.toPublic()

    voiceActivity.shouldBeInstanceOf<LiveServerVoiceActivity>()
    voiceActivity.type shouldBe VoiceActivityType.UNKNOWN
    voiceActivity.audioOffset?.inWholeSeconds shouldBe 1
  }

  @Test
  fun `LiveServerVoiceActivity with missing fields defaults to UNKNOWN and null audioOffset`() {
    val json = """{"voiceActivity": {}}"""

    val message = JSON.decodeFromString<InternalLiveServerMessage>(json)
    val voiceActivity = message.toPublic()

    voiceActivity.shouldBeInstanceOf<LiveServerVoiceActivity>()
    voiceActivity.type shouldBe VoiceActivityType.UNKNOWN
    voiceActivity.audioOffset.shouldBeNull()
  }

  @Test
  fun `LiveServerVoiceActivity with null fields defaults to UNKNOWN and null audioOffset`() {
    val json = """{"voiceActivity": {"type": null, "audioOffset": null}}"""

    val message = JSON.decodeFromString<InternalLiveServerMessage>(json)
    val voiceActivity = message.toPublic()

    voiceActivity.shouldBeInstanceOf<LiveServerVoiceActivity>()
    voiceActivity.type shouldBe VoiceActivityType.UNKNOWN
    voiceActivity.audioOffset.shouldBeNull()
  }

  @Test
  fun `LiveServerVoiceActivity with malformed audioOffset returns null audioOffset`() {
    val json = """{"voiceActivity": {"type": "ACTIVITY_START", "audioOffset": "invalid"}}"""

    val message = JSON.decodeFromString<InternalLiveServerMessage>(json)
    val voiceActivity = message.toPublic()

    voiceActivity.shouldBeInstanceOf<LiveServerVoiceActivity>()
    voiceActivity.type shouldBe VoiceActivityType.ACTIVITY_START
    voiceActivity.audioOffset.shouldBeNull()
  }

  @Test
  fun `VoiceActivityType equals hashCode and toString behave as expected`() {
    VoiceActivityType.ACTIVITY_START shouldBe VoiceActivityType.ACTIVITY_START
    VoiceActivityType.ACTIVITY_START shouldNotBe VoiceActivityType.ACTIVITY_END
    VoiceActivityType.ACTIVITY_START shouldNotBe VoiceActivityType.UNKNOWN
    VoiceActivityType.ACTIVITY_START.equals("ACTIVITY_START") shouldBe false
    VoiceActivityType.ACTIVITY_START.equals(null) shouldBe false

    VoiceActivityType.ACTIVITY_START.hashCode() shouldBe "ACTIVITY_START".hashCode()
    VoiceActivityType.ACTIVITY_END.hashCode() shouldBe "ACTIVITY_END".hashCode()
    VoiceActivityType.UNKNOWN.hashCode() shouldBe "TYPE_UNSPECIFIED".hashCode()

    VoiceActivityType.ACTIVITY_START.toString() shouldBe "VoiceActivityType(ACTIVITY_START)"
    VoiceActivityType.ACTIVITY_END.toString() shouldBe "VoiceActivityType(ACTIVITY_END)"
    VoiceActivityType.UNKNOWN.toString() shouldBe "VoiceActivityType(TYPE_UNSPECIFIED)"
  }
}
