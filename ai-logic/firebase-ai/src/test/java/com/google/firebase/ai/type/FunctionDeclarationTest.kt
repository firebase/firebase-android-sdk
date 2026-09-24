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
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

@OptIn(PublicPreviewAPI::class)
internal class FunctionDeclarationTest {

  @Test
  fun `Basic FunctionDeclaration with name, description and parameters`() {
    val functionDeclaration =
      FunctionDeclaration(
        name = "isUserAGoat",
        description = "Determine if the user is subject to teleportations.",
        parameters = mapOf("userID" to Schema.string("ID of the User making the call"))
      )

    val expectedJson =
      """
      {
          "name": "isUserAGoat",
          "description": "Determine if the user is subject to teleportations.",
          "parameters": {
            "type": "OBJECT",
            "properties": {
              "userID": {
                "type": "STRING",
                "description": "ID of the User making the call"
              }
            },
            "required": [
              "userID"
            ]
          }
        }
    """
        .trimIndent()

    Json.encodeToString(functionDeclaration.toInternal()).shouldEqualJson(expectedJson)
  }

  @Test
  fun `FunctionDeclaration with optional parameters`() {
    val functionDeclaration =
      FunctionDeclaration(
        name = "isUserAGoat",
        description = "Determine if the user is subject to teleportations.",
        parameters =
          mapOf(
            "userID" to Schema.string("ID of the user making the call"),
            "userName" to Schema.string("Name of the user making the call")
          ),
        optionalParameters = listOf("userName")
      )

    val expectedJson =
      """
      {
          "name": "isUserAGoat",
          "description": "Determine if the user is subject to teleportations.",
          "parameters": {
            "type": "OBJECT",
            "properties": {
              "userID": {
                "type": "STRING",
                "description": "ID of the user making the call"
              },
              "userName": {
                "type": "STRING",
                "description": "Name of the user making the call"
              }
            },
            "required": [
              "userID"
            ]
          }
        }
    """
        .trimIndent()

    Json.encodeToString(functionDeclaration.toInternal()).shouldEqualJson(expectedJson)
  }

  @Test
  fun `FunctionDeclaration with NON_BLOCKING behavior serializes behavior field`() {
    val declaration =
      FunctionDeclaration(
        name = "search_crm",
        description = "Searches CRM for customer records.",
        parameters = mapOf("query" to Schema.string("Search query")),
        behavior = FunctionBehavior.NON_BLOCKING,
      )

    declaration.behavior shouldBe FunctionBehavior.NON_BLOCKING

    val expectedJson =
      """
      {
        "name": "search_crm",
        "description": "Searches CRM for customer records.",
        "parameters": {
          "type": "OBJECT",
          "properties": {
            "query": {
              "type": "STRING",
              "description": "Search query"
            }
          },
          "required": ["query"]
        },
        "behavior": "NON_BLOCKING"
      }
    """
        .trimIndent()

    JSON.encodeToString(declaration.toInternal()).shouldEqualJson(expectedJson)
  }

  @Test
  fun `FunctionDeclaration without explicit behavior omits behavior field as passthrough`() {
    val declarationWithoutExplicitBehavior =
      FunctionDeclaration(
        name = "fetchWeather",
        description = "Get weather conditions.",
        parameters = mapOf("city" to Schema.string("City name")),
      )

    val tool = Tool.functionDeclarations(listOf(declarationWithoutExplicitBehavior))
    val internalTool = tool.toInternal()

    val expectedJson =
      """
      {
        "functionDeclarations": [
          {
            "name": "fetchWeather",
            "description": "Get weather conditions.",
            "parameters": {
              "type": "OBJECT",
              "properties": {
                "city": {
                  "type": "STRING",
                  "description": "City name"
                }
              },
              "required": ["city"]
            }
          }
        ]
      }
    """
        .trimIndent()

    JSON.encodeToString(internalTool).shouldEqualJson(expectedJson)
  }

  @Test
  fun `FunctionResponsePart serializes scheduling WHEN_IDLE`() {
    val responsePart =
      FunctionResponsePart(
        name = "search_crm",
        response = JsonObject(mapOf("status" to JsonPrimitive("found"))),
        id = "function-call-123",
        scheduling = FunctionResponseScheduling.WHEN_IDLE,
      )

    val expectedJson =
      """
      {
        "name": "search_crm",
        "response": {
          "status": "found"
        },
        "id": "function-call-123",
        "parts": [],
        "scheduling": "WHEN_IDLE"
      }
    """
        .trimIndent()

    JSON.encodeToString(responsePart.toInternalFunctionResponse()).shouldEqualJson(expectedJson)
  }
}
