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

import com.google.firebase.ai.common.JSON
import com.google.firebase.ai.type.AutoFunctionDeclaration
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FirebaseAutoFunctionException
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.InvalidStateException
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.TemplateAutoFunctionDeclaration
import com.google.firebase.ai.type.TemplateTool
import com.google.firebase.ai.type.TemplateToolConfig
import com.google.firebase.ai.type.content
import java.util.LinkedList
import java.util.concurrent.Semaphore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transform
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Representation of a multi-turn interaction with a server template model.
 */
@PublicPreviewAPI
public class TemplateChat(
  private val model: TemplateGenerativeModel,
  private val templateId: String,
  private val inputs: Map<String, Any>,
  public val history: MutableList<Content> = ArrayList(),
  private val tools: List<TemplateTool>? = null,
  private val toolConfig: TemplateToolConfig? = null,
) {
  private var lock = Semaphore(1)
  private var turns: Int = 0

  /**
   * Sends a message using the provided [prompt]; automatically providing the existing [history] as
   * context.
   *
   * @param prompt The input that, together with the history, will be given to the model as the
   * prompt.
   */
  public suspend fun sendMessage(prompt: Content): GenerateContentResponse {
    prompt.assertComesFromUser()
    attemptLock()
    var response: GenerateContentResponse
    try {
      val tempHistory = mutableListOf(prompt)
      while (true) {
        response =
          model.generateContentWithHistory(
            templateId,
            inputs,
            listOf(*history.toTypedArray(), *tempHistory.toTypedArray()),
            tools,
            toolConfig
          )
        tempHistory.add(response.candidates.first().content)
        val functionCallParts =
          response.candidates.first().content.parts.filterIsInstance<FunctionCallPart>()

        if (functionCallParts.isEmpty()) {
          break
        }
        if (model.requestOptions.autoFunctionCallingTurnLimit < ++turns) {
          throw RequestTimeoutException("Request took too many turns", history = tempHistory)
        }
        if (!functionCallParts.all { hasFunction(it) }) {
          break
        }
        val functionResponsePart = functionCallParts.map { executeFunction(it) }
        tempHistory.add(Content("function", functionResponsePart))
      }
      history.addAll(tempHistory)
      return response
    } finally {
      lock.release()
      turns = 0
    }
  }

  /**
   * Sends a message using the provided text [prompt]; automatically providing the existing [history]
   * as context.
   */
  public suspend fun sendMessage(prompt: String): GenerateContentResponse {
    val content = content { text(prompt) }
    return sendMessage(content)
  }

  /**
   * Sends a message using the provided [prompt]; automatically providing the existing [history] as
   * context. Returns a flow.
   */
  public fun sendMessageStream(prompt: Content): Flow<GenerateContentResponse> {
    prompt.assertComesFromUser()
    attemptLock()

    val fullPrompt = history + prompt
    val flow = model.generateContentWithHistoryStream(templateId, inputs, fullPrompt, tools, toolConfig)
    val tempHistory = LinkedList<Content>()
    tempHistory.add(prompt)

    return flow
      .transform { response -> automaticFunctionExecutingTransform(this, tempHistory, response) }
      .onCompletion {
        turns = 0
        lock.release()
        if (it == null) {
          history.addAll(tempHistory)
        }
      }
  }

  /**
   * Sends a message using the provided text [prompt]; automatically providing the existing [history]
   * as context. Returns a flow.
   */
  public fun sendMessageStream(prompt: String): Flow<GenerateContentResponse> {
    val content = content { text(prompt) }
    return sendMessageStream(content)
  }

  private suspend fun automaticFunctionExecutingTransform(
    transformer: FlowCollector<GenerateContentResponse>,
    tempHistory: MutableList<Content>,
    response: GenerateContentResponse
  ) {
    val functionCallParts =
      response.candidates.first().content.parts.filterIsInstance<FunctionCallPart>()
    if (functionCallParts.isNotEmpty()) {
      if (functionCallParts.all { hasFunction(it) }) {
        if (model.requestOptions.autoFunctionCallingTurnLimit < ++turns) {
          throw RequestTimeoutException("Request took too many turns", history = tempHistory)
        }
        val functionResponses =
          Content("function", functionCallParts.map { executeFunction(it) })
        tempHistory.add(Content("model", functionCallParts))
        tempHistory.add(functionResponses)
        model
          .generateContentWithHistoryStream(
            templateId,
            inputs,
            listOf(*history.toTypedArray(), *tempHistory.toTypedArray()),
            tools,
            toolConfig
          )
          .collect { automaticFunctionExecutingTransform(transformer, tempHistory, it) }
      } else {
        transformer.emit(response)
        tempHistory.add(Content("model", functionCallParts))
      }
    } else {
      transformer.emit(response)
      tempHistory.add(response.candidates.first().content)
    }
  }

  internal fun hasFunction(call: FunctionCallPart): Boolean {
    if (tools == null) return false
    return tools
      .flatMap { it.templateAutoFunctionDeclarations }
      .firstOrNull { it.name == call.name && it.functionReference != null } != null
  }

  @OptIn(InternalSerializationApi::class)
  internal suspend fun executeFunction(call: FunctionCallPart): FunctionResponsePart {
    if (tools.isNullOrEmpty()) {
      throw RuntimeException("No registered tools")
    }
    val tool = tools.flatMap { it.templateAutoFunctionDeclarations }
    val declaration =
      tool.firstOrNull() { it.name == call.name }
        ?: throw RuntimeException("No registered function named ${call.name}")
    return executeFunction<Any, Any>(
      call,
      declaration as TemplateAutoFunctionDeclaration<Any, Any>,
      JsonObject(call.args).toString()
    )
  }

  @OptIn(InternalSerializationApi::class)
  internal suspend fun <I : Any, O : Any> executeFunction(
    functionCall: FunctionCallPart,
    functionDeclaration: TemplateAutoFunctionDeclaration<I, O>,
    parameter: String
  ): FunctionResponsePart {
    val inputDeserializer = functionDeclaration.inputSchema.getSerializer()
    val input = JSON.decodeFromString(inputDeserializer, parameter)
    val functionReference =
      functionDeclaration.functionReference
        ?: throw RuntimeException("Function reference for ${functionDeclaration.name} is missing")
    try {
      val output = functionReference.invoke(input)
      val outputSerializer = functionDeclaration.outputSchema?.getSerializer()
      if (outputSerializer != null) {
        return FunctionResponsePart.from(
            JSON.encodeToJsonElement(outputSerializer, output).jsonObject
          )
          .normalizeAgainstCall(functionCall)
      }
      return (output as FunctionResponsePart).normalizeAgainstCall(functionCall)
    } catch (e: FirebaseAutoFunctionException) {
      return FunctionResponsePart.from(JsonObject(mapOf("error" to JsonPrimitive(e.message))))
        .normalizeAgainstCall(functionCall)
    }
  }

  private fun Content.assertComesFromUser() {
    if (role !in listOf("user", "function")) {
      throw InvalidStateException("Chat prompts should come from the 'user' or 'function' role.")
    }
  }

  private fun attemptLock() {
    if (!lock.tryAcquire()) {
      throw InvalidStateException(
        "This chat instance currently has an ongoing request, please wait for it to complete " +
          "before sending more messages"
      )
    }
  }
}
