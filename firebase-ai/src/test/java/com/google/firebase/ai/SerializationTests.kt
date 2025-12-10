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

package com.google.firebase.ai

import com.google.firebase.ai.common.TemplateGenerateContentRequest
import com.google.firebase.ai.common.TemplateGenerateImageRequest
import com.google.firebase.ai.common.util.descriptorToJson
import com.google.firebase.ai.type.Candidate
import com.google.firebase.ai.type.CountTokensResponse
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GoogleSearch
import com.google.firebase.ai.type.GroundingAttribution
import com.google.firebase.ai.type.GroundingChunk
import com.google.firebase.ai.type.GroundingMetadata
import com.google.firebase.ai.type.GroundingSupport
import com.google.firebase.ai.type.ImagenReferenceImage
import com.google.firebase.ai.type.ModalityTokenCount
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SearchEntryPoint
import com.google.firebase.ai.type.Segment
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.UrlContext
import com.google.firebase.ai.type.UrlContextMetadata
import com.google.firebase.ai.type.UrlMetadata
import com.google.firebase.ai.type.WebGroundingChunk
import io.kotest.assertions.json.shouldEqualJson
import org.junit.Test

@OptIn(PublicPreviewAPI::class)
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
            },
        "urlContextMetadata": {
          "${'$'}ref": "UrlContextMetadata"
        }
      }
    }
      """
        .trimIndent()
    val actualJson = descriptorToJson(Candidate.Internal.serializer().descriptor)
    expectedJsonAsString shouldEqualJson actualJson.toString()
  }

  @Test
  fun `test GroundingMetadata serialization as Json`() {
    val expectedJsonAsString =
      """
     {
      "id": "GroundingMetadata",
      "type": "object",
      "properties": {
        "webSearchQueries": { "type": "array", "items": { "type": "string" } },
        "searchEntryPoint": { "${'$'}ref": "SearchEntryPoint" },
        "retrievalQueries": { "type": "array", "items": { "type": "string" } },
        "groundingAttribution": { "type": "array", "items": { "${'$'}ref": "GroundingAttribution" } },
        "groundingChunks": { "type": "array", "items": { "${'$'}ref": "GroundingChunk" } },
        "groundingSupports": { "type": "array", "items": { "${'$'}ref": "GroundingSupport" } }
      }
    }
      """
        .trimIndent()
    val actualJson = descriptorToJson(GroundingMetadata.Internal.serializer().descriptor)
    println(actualJson)
    expectedJsonAsString shouldEqualJson actualJson.toString()
  }

  @Test
  fun `test SearchEntryPoint serialization as Json`() {
    val expectedJsonAsString =
      """
     {
      "id": "SearchEntryPoint",
      "type": "object",
      "properties": {
        "renderedContent": { "type": "string" },
        "sdkBlob": { "type": "string" }
      }
    }
      """
        .trimIndent()
    val actualJson = descriptorToJson(SearchEntryPoint.Internal.serializer().descriptor)
    expectedJsonAsString shouldEqualJson actualJson.toString()
  }

  @Test
  fun `test GroundingChunk serialization as Json`() {
    val expectedJsonAsString =
      """
     {
      "id": "GroundingChunk",
      "type": "object",
      "properties": {
        "web": { "${'$'}ref": "WebGroundingChunk" }
      }
    }
      """
        .trimIndent()
    val actualJson = descriptorToJson(GroundingChunk.Internal.serializer().descriptor)
    expectedJsonAsString shouldEqualJson actualJson.toString()
  }

  @Test
  fun `test WebGroundingChunk serialization as Json`() {
    val expectedJsonAsString =
      """
     {
      "id": "WebGroundingChunk",
      "type": "object",
      "properties": {
        "uri": { "type": "string" },
        "title": { "type": "string" },
        "domain": { "type": "string" }
      }
    }
      """
        .trimIndent()
    val actualJson = descriptorToJson(WebGroundingChunk.Internal.serializer().descriptor)
    expectedJsonAsString shouldEqualJson actualJson.toString()
  }

  @Test
  fun `test GroundingSupport serialization as Json`() {
    val expectedJsonAsString =
      """
      {
        "id": "GroundingSupport",
        "type": "object",
        "properties": {
          "segment": {
            "${'$'}ref": "Segment"
          },
          "groundingChunkIndices": {
            "type": "array",
            "items": { "type": "integer" }
          }
        }
      }
      """
        .trimIndent()
    val actualJson = descriptorToJson(GroundingSupport.Internal.serializer().descriptor)
    expectedJsonAsString shouldEqualJson actualJson.toString()
  }

  @Test
  fun `test Segment serialization as Json`() {
    val expectedJsonAsString =
      """
      {
        "id": "Segment",
        "type": "object",
        "properties": {
          "startIndex": { "type": "integer" },
          "endIndex": { "type": "integer" },
          "partIndex": { "type": "integer" },
          "text": { "type": "string" }
        }
      }
      """
        .trimIndent()
    val actualJson = descriptorToJson(Segment.Internal.serializer().descriptor)
    expectedJsonAsString shouldEqualJson actualJson.toString()
  }

  @Test
  fun `test UrlContextMetadata serialization as Json`() {
    val expectedJsonAsString =
      """
     {
      "id": "UrlContextMetadata",
      "type": "object",
      "properties": {
        "urlMetadata": { "type": "array", "items": { "${'$'}ref": "UrlMetadata" } }
      }
    }
      """
        .trimIndent()
    val actualJson = descriptorToJson(UrlContextMetadata.Internal.serializer().descriptor)
    println(actualJson)
    expectedJsonAsString shouldEqualJson actualJson.toString()
  }

  @Test
  fun `test UrlMetadata serialization as Json`() {
    val expectedJsonAsString =
      """
      {
        "id": "UrlMetadata",
        "type": "object",
        "properties": {
          "retrievedUrl": {
            "type": "string"
          },
          "urlRetrievalStatus": {
            "type": "string",
            "enum": [
              "UNSPECIFIED",
              "SUCCESS",
              "ERROR",
              "PAYWALL",
              "UNSAFE"
            ]
          }
        }
      }
      """
        .trimIndent()
    val actualJson = descriptorToJson(UrlMetadata.Internal.serializer().descriptor)
    expectedJsonAsString shouldEqualJson actualJson.toString()
  }

  @Test
  fun `test GroundingAttribution serialization as Json`() {
    val expectedJsonAsString =
      """
      {
        "id": "GroundingAttribution",
        "type": "object",
        "properties": {
          "segment": {
            "${'$'}ref": "Segment"
          },
          "confidenceScore": {
            "type": "number"
          }
        }
      }
      """
        .trimIndent()
    val actualJson = descriptorToJson(GroundingAttribution.Internal.serializer().descriptor)
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
        "description": {
          "type": "string"
        },
        "format": {
          "type": "string"
        },
        "nullable": {
          "type": "boolean"
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
        },
        "items": {
          "${'$'}ref": "Schema"
        },
        "title": {
          "type": "string"
        },
        "minItems": {
          "type": "integer"
        },
        "maxItems": {
          "type": "integer"
        },
        "minimum": {
          "type": "number"
        },
        "maximum": {
          "type": "number"
        },
        "anyOf": {
          "type": "array",
          "items": {
            "${'$'}ref": "Schema"
          }
        }
      }
    }
      """
        .trimIndent()
    val actualJson = descriptorToJson(Schema.InternalOpenAPI.serializer().descriptor)
    expectedJsonAsString shouldEqualJson actualJson.toString()
  }

  @Test
  fun `test ReferenceImage serialization as Json`() {
    val expectedJsonAsString =
      """
     {
       "id": "ImagenReferenceImage",
       "type": "object",
        "properties": {
            "referenceType": {
                "type": "string"
            },
            "referenceImage": {
                "${'$'}ref": "ImagenInlineImage"
            },
            "referenceId": {
                "type": "integer"
            },
            "subjectImageConfig": {
                "${'$'}ref": "ImagenSubjectConfig"
            },
            "maskImageConfig": {
                "${'$'}ref": "ImagenMaskConfig"
            },
            "styleImageConfig": {
                "${'$'}ref": "ImagenStyleConfig"
            },
            "controlConfig": {
                "${'$'}ref": "ImagenControlConfig"
            }
        }
    }
      """
        .trimIndent()
    val actualJson = descriptorToJson(ImagenReferenceImage.Internal.serializer().descriptor)
    expectedJsonAsString shouldEqualJson actualJson.toString()
  }

  @Test
  fun `test Tool serialization as Json`() {
    val expectedJsonAsString =
      """
      {
        "id": "Tool",
        "type": "object",
        "properties": {
          "functionDeclarations": {
            "type": "array",
            "items": {
              "${'$'}ref": "FunctionDeclaration"
            }
          },
          "googleSearch": {
            "${'$'}ref": "GoogleSearch"
          },
          "codeExecution": {
            "type": "object",
            "additionalProperties": {
              "${'$'}ref": "JsonElement"
             }
          },
          "urlContext": {
            "${'$'}ref": "UrlContext"
          }
        }
      }
      """
        .trimIndent()
    val actualJson = descriptorToJson(Tool.Internal.serializer().descriptor)
    expectedJsonAsString shouldEqualJson actualJson.toString()
  }

  @Test
  fun `test template request serialization as Json`() {
    val expectedJsonAsString =
      """
        {
          "id": "TemplateGenerateContentRequest",
          "type": "object",
          "properties": {
            "inputs": {
              "type": "object",
              "additionalProperties": {
                "${"$"}ref": "JsonElement"
              }
            },
            "history": {
              "type": "array",
              "items": {
                "${"$"}ref": "Content"
              }
            }
          }
        }
      """
        .trimIndent()
    val actualJson = descriptorToJson(TemplateGenerateContentRequest.serializer().descriptor)
    expectedJsonAsString shouldEqualJson actualJson.toString()
  }

  @Test
  fun `test template imagen request serialization as Json`() {
    val expectedJsonAsString =
      """
        {
          "id": "TemplateGenerateImageRequest",
          "type": "object",
          "properties": {
            "inputs": {
              "type": "object",
              "additionalProperties": {
                "${"$"}ref": "JsonElement"
              }
            }
          }
        }
      """
        .trimIndent()
    val actualJson = descriptorToJson(TemplateGenerateImageRequest.serializer().descriptor)
    expectedJsonAsString shouldEqualJson actualJson.toString()
  }

  @Test
  fun `test GoogleSearch serialization as Json`() {
    val expectedJsonAsString =
      """
      {
        "id": "GoogleSearch",
        "type": "object",
        "properties": {}
      }
      """
        .trimIndent()
    val actualJson = descriptorToJson(GoogleSearch.Internal.serializer().descriptor)
    expectedJsonAsString shouldEqualJson actualJson.toString()
  }

  @Test
  fun `test UrlContext serialization as Json`() {
    val expectedJsonAsString =
      """
      {
        "id": "UrlContext",
        "type": "object",
        "properties": {}
      }
      """
        .trimIndent()
    val actualJson = descriptorToJson(UrlContext.Internal.serializer().descriptor)
    expectedJsonAsString shouldEqualJson actualJson.toString()
  }
}
