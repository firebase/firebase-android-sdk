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

package com.google.firebase.vertexai

import android.graphics.Bitmap
import com.google.firebase.vertexai.type.BlobPart
import com.google.firebase.vertexai.type.Content
import com.google.firebase.vertexai.type.GenerateContentResponse
import com.google.firebase.vertexai.type.ImagePart
import com.google.firebase.vertexai.type.InvalidStateException
import com.google.firebase.vertexai.type.TextPart
import com.google.firebase.vertexai.type.content
import java.util.LinkedList
import java.util.concurrent.Semaphore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

/**
 * Representation of a multi-turn interaction with a model.
 *
 * Handles the capturing and storage of the communication with the model, providing methods for
 * further interaction.
 *
 * **Note:** This object is not thread-safe, and calling [sendMessage] multiple times without
 * waiting for a response will throw an [InvalidStateException].
 *
 * @param model The model to use for the interaction
 * @property history The previous interactions with the model
 */
class Chat(private val model: GenerativeModel, val history: MutableList<Content> = ArrayList()) {
  private var lock = Semaphore(1)

  /**
   * Generates a response from the backend with the provided [Content], and any previous ones
   * sent/returned from this chat.
   *
   * @param prompt A [Content] to send to the model.
   * @throws InvalidStateException if the prompt is not coming from the 'user' role
   * @throws InvalidStateException if the [Chat] instance has an active request.
   */
  suspend fun sendMessage(prompt: Content): GenerateContentResponse {
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
   * Generates a response from the backend with the provided text prompt.
   *
   * @param prompt The text to be converted into a single piece of [Content] to send to the model.
   * @throws InvalidStateException if the [Chat] instance has an active request.
   */
  suspend fun sendMessage(prompt: String): GenerateContentResponse {
    val content = content { text(prompt) }
    return sendMessage(content)
  }

  /**
   * Generates a response from the backend with the provided image prompt.
   *
   * @param prompt The image to be converted into a single piece of [Content] to send to the model.
   * @throws InvalidStateException if the [Chat] instance has an active request.
   */
  suspend fun sendMessage(prompt: Bitmap): GenerateContentResponse {
    val content = content { image(prompt) }
    return sendMessage(content)
  }

  /**
   * Generates a streaming response from the backend with the provided [Content].
   *
   * @param prompt A [Content] to send to the model.
   * @return A [Flow] which will emit responses as they are returned from the model.
   * @throws InvalidStateException if the prompt is not coming from the 'user' role
   * @throws InvalidStateException if the [Chat] instance has an active request.
   */
  fun sendMessageStream(prompt: Content): Flow<GenerateContentResponse> {
    prompt.assertComesFromUser()
    attemptLock()

    val flow = model.generateContentStream(*history.toTypedArray(), prompt)
    val bitmaps = LinkedList<Bitmap>()
    val blobs = LinkedList<BlobPart>()
    val text = StringBuilder()

    /**
     * TODO: revisit when images and blobs are returned. This will cause issues with how things are
     * structured in the response. eg; a text/image/text response will be (incorrectly) represented
     * as image/text
     */
    return flow
      .onEach {
        for (part in it.candidates.first().content.parts) {
          when (part) {
            is TextPart -> text.append(part.text)
            is ImagePart -> bitmaps.add(part.image)
            is BlobPart -> blobs.add(part)
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
              for (blob in blobs) {
                blob(blob.mimeType, blob.blob)
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
   * Generates a streaming response from the backend with the provided text prompt.
   *
   * @param prompt a text to be converted into a single piece of [Content] to send to the model
   * @return A [Flow] which will emit responses as they are returned from the model.
   * @throws InvalidStateException if the [Chat] instance has an active request.
   */
  fun sendMessageStream(prompt: String): Flow<GenerateContentResponse> {
    val content = content { text(prompt) }
    return sendMessageStream(content)
  }

  /**
   * Generates a streaming response from the backend with the provided image prompt.
   *
   * @param prompt A [Content] to send to the model.
   * @return A [Flow] which will emit responses as they are returned from the model.
   * @throws InvalidStateException if the [Chat] instance has an active request.
   */
  fun sendMessageStream(prompt: Bitmap): Flow<GenerateContentResponse> {
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
