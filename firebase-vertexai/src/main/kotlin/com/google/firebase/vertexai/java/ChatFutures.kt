/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.vertexai.java

import androidx.concurrent.futures.SuspendToFutureAdapter
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.vertexai.Chat
import com.google.firebase.vertexai.type.Content
import com.google.firebase.vertexai.type.GenerateContentResponse
import kotlinx.coroutines.reactive.asPublisher
import org.reactivestreams.Publisher

/**
 * Helper method for interacting with a [Chat] from Java.
 *
 * @see from
 */
abstract class ChatFutures internal constructor() {

  /**
   * Generates a response from the backend with the provided [Content], and any previous ones
   * sent/returned from this chat.
   *
   * @param prompt A [Content] to send to the model.
   */
  abstract fun sendMessage(prompt: Content): ListenableFuture<GenerateContentResponse>

  /**
   * Generates a streaming response from the backend with the provided [Content].
   *
   * @param prompt A [Content] to send to the model.
   */
  abstract fun sendMessageStream(prompt: Content): Publisher<GenerateContentResponse>

  /** Returns the [Chat] instance that was used to create this instance */
  abstract fun getChat(): Chat

  private class FuturesImpl(private val chat: Chat) : ChatFutures() {
    override fun sendMessage(prompt: Content): ListenableFuture<GenerateContentResponse> =
      SuspendToFutureAdapter.launchFuture { chat.sendMessage(prompt) }

    override fun sendMessageStream(prompt: Content): Publisher<GenerateContentResponse> =
      chat.sendMessageStream(prompt).asPublisher()

    override fun getChat(): Chat = chat
  }

  companion object {

    /** @return a [ChatFutures] created around the provided [Chat] */
    @JvmStatic fun from(chat: Chat): ChatFutures = FuturesImpl(chat)
  }
}
