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

package com.google.firebase.vertexai

import com.google.firebase.vertexai.common.util.descriptorToJson
import com.google.firebase.vertexai.type.Candidate
import com.google.firebase.vertexai.type.CountTokensResponse
import com.google.firebase.vertexai.type.GenerateContentResponse
import com.google.firebase.vertexai.type.ModalityTokenCount
import com.google.firebase.vertexai.type.Schema
import io.kotest.assertions.json.shouldEqualJson
import org.junit.Test

internal class SerializationTests {
  @Test
  fun `test countTokensResponse serialization as Json`() {
    val expectedJsonAsString =
      """
      {
        "id": "CountTokensResponse",
        "type": "object",
        "properties": {
          "totalTokens": {
            "type": "integer"
          },
          "totalBillableCharacters": {
            "type": "integer"
          },
          "promptTokensDetails": {
            "type": "array",
            "items": {
              "${'$'}ref": "ModalityTokenCount"
            }
          }
        }
      }
      """
        .trimIndent()
    val actualJson = descriptorToJson(CountTokensResponse.Internal.serializer().descriptor)
    expectedJsonAsString shouldEqualJson actualJson.toString()
  }

  @Test
  fun `test modalityTokenCount serialization as Json`() {
    val expectedJsonAsString =
      """
      {
        "id": "ModalityTokenCount",
        "type": "object",
        "properties": {
          "modality": {
            "type": "string",
            "enum": [
              "UNSPECIFIED",
              "TEXT",
              "IMAGE",
              "VIDEO",
              "AUDIO",
              "DOCUMENT"
            ]
          },
          "tokenCount": {
            "type": "integer"
          }
        }
      }
      """
        .trimIndent()
    val actualJson = descriptorToJson(ModalityTokenCount.Internal.serializer().descriptor)
    expectedJsonAsString shouldEqualJson actualJson.toString()
  }

  @Test
  fun `test GenerateContentResponse serialization as Json`() {
    val expectedJsonAsString =
      """
      {
        "id": "GenerateContentResponse",
        "type": "object",
        "properties": {
            "candidates": {
                "type": "array",
                "items": {
                    "${'$'}ref": "Candidate"
                }
            },
            "promptFeedback": {
                "${'$'}ref": "PromptFeedback"
            },
            "usageMetadata": {
                "${'$'}ref": "UsageMetadata"
            }
        }
     }
     """
        .trimIndent()
    val actualJson = descriptorToJson(GenerateContentResponse.Internal.serializer().descriptor)
    expectedJsonAsString shouldEqualJson actualJson.toString()
  }

  @Test
  fun `test Candidate serialization as Json`() {
    val expectedJsonAsString =
      """
     {
      "id": "Candidate",
      "type": "object",
      "properties": {
        "content": {
          "${'$'}ref": "Content"
            },
        "finishReason": {
          "type": "string",
          "enum": [
                      "UNKNOWN",
            "UNSPECIFIED",
            "STOP",
            "MAX_TOKENS",
            "SAFETY",
            "RECITATION",
            "OTHER",
            "BLOCKLIST",
            "PROHIBITED_CONTENT",
            "SPII",
            "MALFORMED_FUNCTION_CALL"
          ]
        },
        "safetyRatings": {
          "type": "array",
          "items": {
            "${'$'}ref": "SafetyRating"
            }
        },
        "citationMetadata": {
          "${'$'}ref": "CitationMetadata"
            },
        "groundingMetadata": {
          "${'$'}ref": "GroundingMetadata"
            }
      }
    }
      """
        .trimIndent()
    val actualJson = descriptorToJson(Candidate.Internal.serializer().descriptor)
    expectedJsonAsString shouldEqualJson actualJson.toString()
  }

  @Test
  fun `test Schema serialization as Json`() {
    /**
     * Unlike the actual schema in the background, we don't represent "type" as an enum, but rather
     * as a string. This is because we restrict what values can be used (using helper methods,
     * rather than type).
     */
    val expectedJsonAsString =
      """
    {
      "id": "Schema",
      "type": "object",
      "properties": {
        "type": {
          "type": "string"
        },
        "format": {
          "type": "string"
        },
        "description": {
          "type": "string"
        },
        "nullable": {
          "type": "boolean"
        },
        "items": {
          "${'$'}ref": "Schema"
        },
        "enum": {
          "type": "array",
          "items": {
            "type": "string"
          }
        },
        "properties": {
          "type": "object",
          "additionalProperties": {
            "${'$'}ref": "Schema"
          }
        },
        "required": {
          "type": "array",
          "items": {
            "type": "string"
          }
        } 
      }
    }
      """
        .trimIndent()
    val actualJson = descriptorToJson(Schema.Internal.serializer().descriptor)
    expectedJsonAsString shouldEqualJson actualJson.toString()
  }
}
