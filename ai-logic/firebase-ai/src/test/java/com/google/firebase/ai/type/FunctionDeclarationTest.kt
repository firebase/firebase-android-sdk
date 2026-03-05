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

import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

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
}
