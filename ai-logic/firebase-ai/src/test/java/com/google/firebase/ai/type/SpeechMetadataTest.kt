/*
 * Copyright 2026 Google LLC
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
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.encodeToString
import org.junit.Test

@OptIn(PublicPreviewAPI::class)
internal class SpeechMetadataTest {

  @Test
  fun `SpeechMetadata with all fields serializes correctly`() {
    val speechMetadata = SpeechMetadata(speaker = "Joe", style = "cheerful")

    val expectedJson =
      """
      {
        "speaker": "Joe",
        "style": "cheerful"
      }
      """
        .trimIndent()

    JSON.encodeToString(speechMetadata.toInternal()).shouldEqualJson(expectedJson)
  }

  @Test
  fun `SpeechMetadata omits unset fields`() {
    val speechMetadata = SpeechMetadata(style = "whispering")

    val expectedJson =
      """
      {
        "style": "whispering"
      }
      """
        .trimIndent()

    JSON.encodeToString(speechMetadata.toInternal()).shouldEqualJson(expectedJson)
  }

  @Test
  fun `TextPart with SpeechMetadata serializes correctly`() {
    val part = TextPart("Hello there!", SpeechMetadata(speaker = "Jane", style = "excited"))

    val expectedJson =
      """
      {
        "text": "Hello there!",
        "thought": false,
        "speechMetadata": {
          "speaker": "Jane",
          "style": "excited"
        }
      }
      """
        .trimIndent()

    JSON.encodeToString(part.toInternal() as TextPart.Internal).shouldEqualJson(expectedJson)
  }

  @Test
  fun `TextPart without SpeechMetadata omits the field`() {
    val part = TextPart("Hello there!")

    val expectedJson =
      """
      {
        "text": "Hello there!",
        "thought": false
      }
      """
        .trimIndent()

    JSON.encodeToString(part.toInternal() as TextPart.Internal).shouldEqualJson(expectedJson)
  }

  @Test
  fun `TextPart with SpeechMetadata deserializes correctly`() {
    val json =
      """
      {
        "text": "Hello there!",
        "speechMetadata": {
          "speaker": "Jane",
          "style": "excited"
        }
      }
      """
        .trimIndent()

    val part = JSON.decodeFromString<InternalPart>(json).toPublic()

    part.shouldBeInstanceOf<TextPart>()
    part.text shouldBe "Hello there!"
    val speechMetadata = part.speechMetadata.shouldNotBeNull()
    speechMetadata.speaker shouldBe "Jane"
    speechMetadata.style shouldBe "excited"
  }

  @Test
  fun `TextPart without SpeechMetadata deserializes with null metadata`() {
    val json = """{ "text": "Hello there!" }"""

    val part = JSON.decodeFromString<InternalPart>(json).toPublic()

    part.shouldBeInstanceOf<TextPart>()
    part.speechMetadata.shouldBeNull()
  }
}
