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

package com.google.firebase.ai

import android.graphics.Bitmap
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.InvalidStateException
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.content
import java.util.LinkedList
import java.util.concurrent.Semaphore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

/**
 * Representation of a multi-turn interaction with a model.
 *
 * Captures and stores the history of communication in memory, and provides it as context with each
 * new message.
 *
 * **Note:** This object is not thread-safe, and calling [sendMessage] multiple times without
 * waiting for a response will throw an [InvalidStateException].
 *
 * @param model The model to use for the interaction.
 * @property history The previous content from the chat that has been successfully sent and received
 * from the model. This will be provided to the model for each message sent (as context for the
 * discussion).
 */
public class Chat(
  private val model: GenerativeModel,
  public val history: MutableList<Content> = ArrayList()
) {
  private var lock = Semaphore(1)

  /**
   * Sends a message using the provided [prompt]; automatically providing the existing [history] as
   * context.
   *
   * If successful, the message and response will be added to the [history]. If unsuccessful,
   * [history] will remain unchanged.
   *
   * @param prompt The input that, together with the history, will be given to the model as the
   * prompt.
   * @throws InvalidStateException if [prompt] is not coming from the 'user' role.
   * @throws InvalidStateException if the [Chat] instance has an active request.
   */
  public suspend fun sendMessage(prompt: Content): GenerateContentResponse {
    prompt.assertComesFromUser()
    attemptLock()
    try {
      val response = model.generateContent(*history.toTypedArray(), prompt)
      history.add(prompt)
      history.add(response.candidates.first().content)
      return response
    } finally {
      lock.release()
    }
  }

  /**
   * Sends a message using the provided [text prompt][prompt]; automatically providing the existing
   * [history] as context.
   *
   * If successful, the message and response will be added to the [history]. If unsuccessful,
   * [history] will remain unchanged.
   *
   * @param prompt The input that, together with the history, will be given to the model as the
   * prompt.
   * @throws InvalidStateException if [prompt] is not coming from the 'user' role.
   * @throws InvalidStateException if the [Chat] instance has an active request.
   */
  public suspend fun sendMessage(prompt: String): GenerateContentResponse {
    val content = content { text(prompt) }
    return sendMessage(content)
  }

  /**
   * Sends a message using the existing history of this chat as context and the provided image
   * prompt.
   *
   * If successful, the message and response will be added to the history. If unsuccessful, history
   * will remain unchanged.
   *
   * @param prompt The input that, together with the history, will be given to the model as the
   * prompt.
   * @throws InvalidStateException if [prompt] is not coming from the 'user' role.
   * @throws InvalidStateException if the [Chat] instance has an active request.
   */
  public suspend fun sendMessage(prompt: Bitmap): GenerateContentResponse {
    val content = content { image(prompt) }
    return sendMessage(content)
  }

  /**
   * Sends a message using the existing history of this chat as context and the provided [Content]
   * prompt.
   *
   * The response from the model is returned as a stream.
   *
   * If successful, the message and response will be added to the history. If unsuccessful, history
   * will remain unchanged.
   *
   * @param prompt The input that, together with the history, will be given to the model as the
   * prompt.
   * @throws InvalidStateException if [prompt] is not coming from the 'user' role.
   * @throws InvalidStateException if the [Chat] instance has an active request.
   */
  public fun sendMessageStream(prompt: Content): Flow<GenerateContentResponse> {
    prompt.assertComesFromUser()
    attemptLock()

    val flow = model.generateContentStream(*history.toTypedArray(), prompt)
    val bitmaps = LinkedList<Bitmap>()
    val inlineDataParts = LinkedList<InlineDataPart>()
    val text = StringBuilder()

    /**
     * TODO: revisit when images and inline data are returned. This will cause issues with how
     * things are structured in the response. eg; a text/image/text response will be (incorrectly)
     * represented as image/text
     */
    return flow
      .onEach {
        for (part in it.candidates.first().content.parts) {
          when (part) {
            is TextPart -> text.append(part.text)
            is ImagePart -> bitmaps.add(part.image)
            is InlineDataPart -> inlineDataParts.add(part)
          }
        }
      }
      .onCompletion {
        lock.release()
        if (it == null) {
          val content =
            content("model") {
              for (bitmap in bitmaps) {
                image(bitmap)
              }
              for (inlineDataPart in inlineDataParts) {
                inlineData(inlineDataPart.inlineData, inlineDataPart.mimeType)
              }
              if (text.isNotBlank()) {
                text(text.toString())
              }
            }

          history.add(prompt)
          history.add(content)
        }
      }
  }

  /**
   * Sends a message using the existing history of this chat as context and the provided text
   * prompt.
   *
   * The response from the model is returned as a stream.
   *
   * If successful, the message and response will be added to the history. If unsuccessful, history
   * will remain unchanged.
   *
   * @param prompt The input(s) that, together with the history, will be given to the model as the
   * prompt.
   * @throws InvalidStateException if [prompt] is not coming from the 'user' role.
   * @throws InvalidStateException if the [Chat] instance has an active request.
   */
  public fun sendMessageStream(prompt: String): Flow<GenerateContentResponse> {
    val content = content { text(prompt) }
    return sendMessageStream(content)
  }

  /**
   * Sends a message using the existing history of this chat as context and the provided image
   * prompt.
   *
   * The response from the model is returned as a stream.
   *
   * If successful, the message and response will be added to the history. If unsuccessful, history
   * will remain unchanged.
   *
   * @param prompt The input that, together with the history, will be given to the model as the
   * prompt.
   * @throws InvalidStateException if [prompt] is not coming from the 'user' role.
   * @throws InvalidStateException if the [Chat] instance has an active request.
   */
  public fun sendMessageStream(prompt: Bitmap): Flow<GenerateContentResponse> {
    val content = content { image(prompt) }
    return sendMessageStream(content)
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
