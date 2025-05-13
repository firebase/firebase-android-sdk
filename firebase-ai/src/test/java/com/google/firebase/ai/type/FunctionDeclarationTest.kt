package com.google.firebase.ai.type

import io.kotest.assertions.json.shouldEqualJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

internal class FunctionDeclarationTest {

  @Test
  fun `Basic FunctionDeclaration with name, description and parameters`() {
    val functionDeclaration = FunctionDeclaration(
      name = "isUserAGoat",
      description = "Determine if the user is subject to teleportations.",
      parameters = mapOf(
        "userID" to Schema.string("ID of the User making the call")
      )
    )

    val expectedJson = """
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
    """.trimIndent()

    Json.encodeToString(functionDeclaration.toInternal()).shouldEqualJson(expectedJson)
  }

  @Test
  fun `FunctionDeclaration with optional parameters`() {
    val functionDeclaration = FunctionDeclaration(
      name = "isUserAGoat",
      description = "Determine if the user is subject to teleportations.",
      parameters = mapOf(
        "userID" to Schema.string("ID of the user making the call"),
        "userName" to Schema.string("Name of the user making the call")
      ),
      optionalParameters = listOf("userName")
    )

    val expectedJson = """
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
    """.trimIndent()

    Json.encodeToString(functionDeclaration.toInternal()).shouldEqualJson(expectedJson)
  }
}
