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

import com.google.firebase.ai.AIModels.Companion.app
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionCallingConfig
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.ToolConfig
import com.google.firebase.ai.type.content
import io.ktor.util.toLowerCasePreservingASCIIRules
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class ToolTests {
  val validator = TypesValidator()

  @Test
  fun testTools_functionCallStructuring() {
    val schema =
      mapOf(
        Pair(
          "character",
          Schema.obj(
            mapOf(
              Pair("name", Schema.string("the character's full name")),
              Pair("gender", Schema.string("the character's gender")),
              Pair("weight", Schema.float("the character's weight, in kilograms")),
              Pair("height", Schema.float("the character's height, in centimeters")),
              Pair(
                "favorite_foods",
                Schema.array(
                  Schema.string("the name of a food"),
                  "a short list of the character's favorite foods"
                )
              ),
              Pair(
                "mother",
                Schema.obj(
                  mapOf(Pair("name", Schema.string("the character's mother's name"))),
                  description = "information about the character's mother"
                )
              ),
            )
          )
        ),
      )
    val model =
      setupModel(
        FunctionDeclaration(
          name = "getFavoriteColor",
          description =
            "determines a video game character's favorite color based on their features",
          parameters = schema
        ),
      )
    runBlocking {
      val response =
        model.generateContent(
          "I'm imagining a video game character whose name is sam, but I can't think of the rest of their traits, could you make them up for me and figure out the character's favorite color?"
        )
      validator.validateResponse((response))
      assert(response.functionCalls.size == 1)
      val call = response.functionCalls[0]
      assert(call.name == "getFavoriteColor")
      validateSchema(schema, call.args)
    }
  }

  @Test
  fun testTools_basicDecisionMaking() {
    val schema =
      mapOf(
        Pair("character", Schema.string("the character whose favorite color should be obtained"))
      )
    val model =
      setupModel(
        FunctionDeclaration(
          name = "getFavoriteColor",
          description = "returns the favorite color from a provided character's name",
          parameters = schema
        ),
        FunctionDeclaration(
          name = "eatAllSnacks",
          description =
            "orders a robot to find the kitchen of the provided character by their name, then eat all of their snacks so they get really sad. returns how many snacks were eaten",
          parameters = schema
        )
      )
    runBlocking {
      val response = model.generateContent("what is amy's favorite color?")
      validator.validateResponse((response))
      assert(response.functionCalls.size == 1)
      val call = response.functionCalls[0]
      assert(call.name == "getFavoriteColor")
      validateSchema(schema, call.args)
    }
  }

  /** Ensures the model is capable of a simple question, tool call, response workflow. */
  @Test
  fun testTools_BasicToolCall() {
    val schema =
      mapOf(
        Pair("character", Schema.string("the character whose favorite color should be obtained"))
      )
    val model =
      setupModel(
        FunctionDeclaration(
          name = "getFavoriteColor",
          description = "returns the favorite color from a provided character's name",
          parameters = schema
        )
      )
    runBlocking {
      val question = content { text("what's bob's favorite color?") }
      val response = model.generateContent(question)
      validator.validateResponse((response))
      assert(response.functionCalls.size == 1)
      for (call in response.functionCalls) {
        assert(call.name == "getFavoriteColor")
        validateSchema(schema, call.args)
        assert(
          call.args["character"]!!.jsonPrimitive.content.toLowerCasePreservingASCIIRules() == "bob"
        )
        model.generateContent(
          question,
          Content(
            role = "model",
            parts =
              listOf(
                call,
              )
          ),
          Content(
            parts =
              listOf(
                FunctionResponsePart(
                  id = call.id,
                  name = call.name,
                  response = JsonObject(mapOf(Pair("result", JsonPrimitive("green"))))
                ),
              )
          )
        )
      }
    }
  }

  /**
   * Ensures the model can chain function calls together to reach trivial conclusions. In this case,
   * the model needs to use the output of one function call as the input to another.
   */
  @Test
  fun testTools_sequencingFunctionCalls() {
    val nameSchema =
      mapOf(
        Pair("name", Schema.string("the name of the person whose birth month should be obtained"))
      )
    val monthSchema =
      mapOf(Pair("month", Schema.string("the month whose color should be obtained")))
    val model =
      setupModel(
        FunctionDeclaration(
          name = "getBirthMonth",
          description = "returns a person's birth month based on their name",
          parameters = nameSchema
        ),
        FunctionDeclaration(
          name = "getMonthColor",
          description = "returns the color for a certain month",
          parameters = monthSchema
        )
      )
    runBlocking {
      val question = content { text("what color is john's birth month") }
      val response = model.generateContent(question)
      assert(response.functionCalls.size == 1)
      val call = response.functionCalls[0]
      assert(call.name == "getBirthMonth")
      assert(call.args["name"]!!.jsonPrimitive.content.toLowerCasePreservingASCIIRules() == "john")
      validateSchema(nameSchema, call.args)
      val response2 =
        model.generateContent(
          question,
          Content(
            role = "model",
            parts =
              listOf(
                call,
              )
          ),
          Content(
            parts =
              listOf(
                FunctionResponsePart(
                  id = call.id,
                  name = call.name,
                  response = JsonObject(mapOf(Pair("result", JsonPrimitive("june"))))
                ),
              )
          )
        )
      validator.validateResponse((response))
      assert(response2.functionCalls.size == 1)
      val call2 = response2.functionCalls[0]
      assert(call2.name == "getMonthColor")
      assert(
        call2.args["month"]!!.jsonPrimitive.content.toLowerCasePreservingASCIIRules() == "june"
      )
      validateSchema(monthSchema, call2.args)
    }
  }

  fun validateSchema(schema: Map<String, Schema>, args: Map<String, JsonElement>) {
    // Model should not call the function with unspecified arguments
    assert(schema.keys.containsAll(args.keys))
    for (entry in schema) {
      validateSchema(entry.value, args.get(entry.key))
    }
  }

  /** Simple schema validation. Not comprehensive, but should detect notable inaccuracy. */
  fun validateSchema(schema: Schema, json: JsonElement?) {
    if (json == null) {
      assert(schema.nullable == true)
      return
    }
    when (json) {
      is JsonNull -> {
        assert(schema.nullable == true)
      }
      is JsonPrimitive -> {
        if (schema.type == "INTEGER") {
          assert(json.intOrNull != null)
        } else if (schema.type == "NUMBER") {
          assert(json.doubleOrNull != null)
        } else if (schema.type == "BOOLEAN") {
          assert(json.booleanOrNull != null)
        } else if (schema.type == "STRING") {
          assert(json.isString)
        } else {
          assert(false)
        }
      }
      is JsonObject -> {
        assert(schema.type == "OBJECT")
        val required = schema.required ?: listOf()
        val obj = json.jsonObject
        for (entry in schema.properties!!) {
          if (obj.containsKey(entry.key)) {
            validateSchema(entry.value, obj.get(entry.key))
          } else {
            assert(!required.contains(entry.key))
          }
        }
      }
      is JsonArray -> {
        assert(schema.type == "ARRAY")
        for (e in json.jsonArray) {
          validateSchema(schema.items!!, e)
        }
      }
    }
  }

  companion object {
    @JvmStatic
    fun setupModel(vararg functions: FunctionDeclaration): GenerativeModel {
      val model =
        FirebaseAI.getInstance(app(), GenerativeBackend.vertexAI())
          .generativeModel(
            modelName = "gemini-2.5-flash",
            toolConfig =
              ToolConfig(
                functionCallingConfig = FunctionCallingConfig(FunctionCallingConfig.Mode.ANY)
              ),
            tools = listOf(Tool.functionDeclarations(functions.toList())),
          )
      return model
    }
  }
}
