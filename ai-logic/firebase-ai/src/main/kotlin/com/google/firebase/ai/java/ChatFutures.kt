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

package com.google.firebase.ai.java

import androidx.concurrent.futures.SuspendToFutureAdapter
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.ai.Chat
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.InvalidStateException
import kotlinx.coroutines.reactive.asPublisher
import org.reactivestreams.Publisher

/**
 * Wrapper class providing Java compatible methods for [Chat].
 *
 * @see [Chat]
 */
public abstract class ChatFutures internal constructor() {

  /**
   * Sends a message using the existing history of this chat as context and the provided [Content]
   * prompt.
   *
   * If successful, the message and response will be added to the history. If unsuccessful, history
   * will remain unchanged.
   *
   * @param prompt The input(s) that, together with the history, will be given to the model as the
   * prompt.
   * @throws InvalidStateException if [prompt] is not coming from the 'user' role
   * @throws InvalidStateException if the [Chat] instance has an active request
   */
  public abstract fun sendMessage(prompt: Content): ListenableFuture<GenerateContentResponse>

  /**
   * Sends a message using the existing history of this chat as context and the provided [Content]
   * prompt.
   *
   * The response from the model is returned as a stream.
   *
   * If successful, the message and response will be added to the history. If unsuccessful, history
   * will remain unchanged.
   *
   * @param prompt The input(s) that, together with the history, will be given to the model as the
   * prompt.
   * @throws InvalidStateException if [prompt] is not coming from the 'user' role
   * @throws InvalidStateException if the [Chat] instance has an active request
   */
  public abstract fun sendMessageStream(prompt: Content): Publisher<GenerateContentResponse>

  /** Returns the [Chat] object wrapped by this object. */
  public abstract fun getChat(): Chat

  private class FuturesImpl(private val chat: Chat) : ChatFutures() {
    override fun sendMessage(prompt: Content): ListenableFuture<GenerateContentResponse> =
      SuspendToFutureAdapter.launchFuture { chat.sendMessage(prompt) }

    override fun sendMessageStream(prompt: Content): Publisher<GenerateContentResponse> =
      chat.sendMessageStream(prompt).asPublisher()

    override fun getChat(): Chat = chat
  }

  public companion object {

    /** @return a [ChatFutures] created around the provided [Chat] */
    @JvmStatic public fun from(chat: Chat): ChatFutures = FuturesImpl(chat)
  }
}
