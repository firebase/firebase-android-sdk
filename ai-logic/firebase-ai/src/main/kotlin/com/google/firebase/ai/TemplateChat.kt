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

package com.google.firebase.ai

import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.InvalidStateException
import com.google.firebase.ai.type.Part
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.content
import java.util.concurrent.Semaphore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

/** Representation of a multi-turn interaction with a server template model. */
@PublicPreviewAPI
public class TemplateChat
internal constructor(
  private val model: TemplateGenerativeModel,
  private val templateId: String,
  private val inputs: Map<String, Any>,
  public val history: MutableList<Content> = ArrayList()
) {
  private var lock = Semaphore(1)

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
    try {
      return sendMessageWithFunctionHandling(listOf(prompt))
    } finally {
      lock.release()
    }
  }

  /**
   * Sends a message using the provided text [prompt]; automatically providing the existing
   * [history] as context.
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
    val flow = model.generateContentWithHistoryStream(templateId, inputs, fullPrompt)
    val tempHistory = mutableListOf<Content>()
    val responseParts = mutableListOf<Part>()

    return flow
      .onEach { response ->
        response.candidates.first().content.parts.let { responseParts.addAll(it) }
      }
      .onCompletion {
        lock.release()
        if (it == null) {
          tempHistory.add(prompt)
          tempHistory.add(
            content("model") { responseParts.forEach { part -> this.parts.add(part) } }
          )
          history.addAll(tempHistory)
        }
      }
  }

  /**
   * Sends a message using the provided text [prompt]; automatically providing the existing
   * [history] as context. Returns a flow.
   */
  public fun sendMessageStream(prompt: String): Flow<GenerateContentResponse> {
    val content = content { text(prompt) }
    return sendMessageStream(content)
  }

  internal suspend fun sendMessageWithFunctionHandling(
    tempHistory: List<Content>
  ): GenerateContentResponse {
    val response = model.generateContentWithHistory(templateId, inputs, history + tempHistory)
    if (response.functionCalls.isEmpty() || response.functionCalls.any { !model.hasFunction(it) }) {
      history.addAll(tempHistory + response.candidates.first().content)
      return response
    }
    val functionResponses = response.functionCalls.map { model.executeFunction(it) }
    return sendMessageWithFunctionHandling(
      tempHistory + response.candidates.first().content + Content("function", functionResponses)
    )
  }

  private suspend fun automaticFunctionExecutingTransform(
    transformer: FlowCollector<GenerateContentResponse>,
    tempHistory: MutableList<Content>,
    response: GenerateContentResponse
  ) {
    val functionCallParts =
      response.candidates.first().content.parts.filterIsInstance<FunctionCallPart>()
    if (functionCallParts.isNotEmpty()) {
      if (functionCallParts.all { model.hasFunction(it) }) {
        val functionResponses =
          Content("function", functionCallParts.map { model.executeFunction(it) })
        tempHistory.add(Content("model", functionCallParts))
        tempHistory.add(functionResponses)
        model
          .generateContentWithHistoryStream(
            templateId,
            inputs,
            listOf(*history.toTypedArray(), *tempHistory.toTypedArray())
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
