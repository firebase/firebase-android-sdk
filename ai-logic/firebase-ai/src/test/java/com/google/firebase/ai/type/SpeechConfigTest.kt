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

import kotlin.OptIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(PublicPreviewAPI::class)
class SpeechConfigTest {

  @Test
  fun singleSpeakerConstructor_setsVoiceAndLanguageCode() {
    val voice = Voice("Kora")
    val config = SpeechConfig(voice, "en-US")
    assertEquals(voice, config.voice)
    assertEquals("en-US", config.languageCode)
    assertNull(config.multiSpeakerVoiceConfig)
  }

  @Test
  fun singleSpeakerConstructor_defaultLanguageCode() {
    val voice = Voice("Kora")
    val config = SpeechConfig(voice)
    assertEquals(voice, config.voice)
    assertNull(config.languageCode)
    assertNull(config.multiSpeakerVoiceConfig)
  }

  @Test
  fun multiSpeakerConstructor_setsConfigAndLanguageCode() {
    val speakerConfigs =
      listOf(SpeakerVoiceConfig("Joe", Voice("Kora")), SpeakerVoiceConfig("Jane", Voice("Kora")))
    val multiConfig = MultiSpeakerVoiceConfig(speakerConfigs)
    val config = SpeechConfig(multiConfig, "en-US")
    assertEquals(multiConfig, config.multiSpeakerVoiceConfig)
    assertEquals("en-US", config.languageCode)
    assertNull(config.voice)
  }

  @Test
  fun multiSpeakerConstructor_defaultLanguageCode() {
    val speakerConfigs =
      listOf(SpeakerVoiceConfig("Joe", Voice("Kora")), SpeakerVoiceConfig("Jane", Voice("Kora")))
    val multiConfig = MultiSpeakerVoiceConfig(speakerConfigs)
    val config = SpeechConfig(multiConfig)
    assertEquals(multiConfig, config.multiSpeakerVoiceConfig)
    assertNull(config.languageCode)
    assertNull(config.voice)
  }

  @Test
  fun serialization_singleSpeaker() {
    val voice = Voice("Kora")
    val config = SpeechConfig(voice, "en-US")
    val internal = config.toInternal()
    assertEquals("Kora", internal.voiceConfig?.prebuiltVoiceConfig?.voiceName)
    assertEquals("en-US", internal.languageCode)
    assertNull(internal.multiSpeakerVoiceConfig)
  }

  @Test
  fun serialization_multiSpeaker() {
    val speakerConfigs =
      listOf(SpeakerVoiceConfig("Joe", Voice("Kora")), SpeakerVoiceConfig("Jane", Voice("Kora")))
    val multiConfig = MultiSpeakerVoiceConfig(speakerConfigs)
    val config = SpeechConfig(multiConfig, "en-US")
    val internal = config.toInternal()
    assertEquals("en-US", internal.languageCode)
    assertNull(internal.voiceConfig)
    assertEquals(2, internal.multiSpeakerVoiceConfig?.speakerVoiceConfigs?.size)
    assertEquals("Joe", internal.multiSpeakerVoiceConfig?.speakerVoiceConfigs?.get(0)?.speaker)
    assertEquals(
      "Kora",
      internal.multiSpeakerVoiceConfig
        ?.speakerVoiceConfigs
        ?.get(0)
        ?.voiceConfig
        ?.prebuiltVoiceConfig
        ?.voiceName
    )
  }

  @Test
  fun serialization_multiSpeaker_moreThanTwoSpeakers() {
    val speakerConfigs =
      listOf(
        SpeakerVoiceConfig("Joe", Voice("Kora")),
        SpeakerVoiceConfig("Jane", Voice("Kora")),
        SpeakerVoiceConfig("Jack", Voice("Kora"))
      )
    val multiConfig = MultiSpeakerVoiceConfig(speakerConfigs)
    val config = SpeechConfig(multiConfig)
    val internal = config.toInternal()
    assertEquals(3, internal.multiSpeakerVoiceConfig?.speakerVoiceConfigs?.size)
  }
}
