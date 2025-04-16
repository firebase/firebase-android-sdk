/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.vertexai.type

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GenerationConfigTests {

  @Test
  fun `serialization omits null responseModalities`() {
    val config = generationConfig { /* No responseModalities set */ }
    val internalConfig = config.toInternal()
    val jsonString = Json.encodeToString(internalConfig)
    val jsonObject = Json.parseToJsonElement(jsonString).jsonObject

    assertFalse(jsonObject.containsKey("response_modalities"))
  }

  @Test
  fun `serialization includes all responseModalities`() {
    val config = generationConfig {
      responseModalities = listOf(ResponseModality.TEXT, ResponseModality.IMAGE, ResponseModality.AUDIO)
    }
    val internalConfig = config.toInternal()
    val jsonString = Json.encodeToString(internalConfig)
    val jsonObject = Json.parseToJsonElement(jsonString).jsonObject

    // Assert the JSON output contains the correct array for response_modalities
    assertEquals(
      """["TEXT","IMAGE","AUDIO"]""",
      jsonObject["response_modalities"].toString()
    )
  }

    @Test
    fun `serialization includes some responseModalities`() {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.TEXT, ResponseModality.IMAGE)
        }
        val internalConfig = config.toInternal()
        val jsonString = Json.encodeToString(internalConfig)
        val jsonObject = Json.parseToJsonElement(jsonString).jsonObject

        // Assert the JSON output contains the correct array for response_modalities
        assertEquals(
            """["TEXT","IMAGE"]""",
            jsonObject["response_modalities"].toString()
        )
    }

    // TODO: Add tests for other properties as well
    @Test
    fun `serialization includes temperature`() {
        val temp = 0.8f
        val config = generationConfig { temperature = temp }
        val internalConfig = config.toInternal()
        val jsonString = Json.encodeToString(internalConfig)
        val jsonObject = Json.parseToJsonElement(jsonString).jsonObject

        assertEquals(temp, jsonObject["temperature"]?.jsonPrimitive?.content?.toFloat())
    }
}
