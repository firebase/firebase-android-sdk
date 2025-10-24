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

package com.google.firebase.ai.java

import androidx.concurrent.futures.SuspendToFutureAdapter
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.ai.TemplateChat
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.InvalidStateException
import com.google.firebase.ai.type.PublicPreviewAPI
import kotlinx.coroutines.reactive.asPublisher
import org.reactivestreams.Publisher

/**
 * Wrapper class providing Java compatible methods for [TemplateChat].
 *
 * @see [TemplateChat]
 */
@PublicPreviewAPI
public abstract class TemplateChatFutures internal constructor() {

  /**
   * Sends a message using the existing history of this chat as context and the provided [Content]
   * prompt.
   *
   * If successful, the message and response will be added to the history. If unsuccessful, history
   * will remain unchanged.
   *
   * @param prompt The input that, together with the history, will be given to the model as the
   * prompt.
   * @param inputs the inputs needed to fill in the template ID
   * @throws InvalidStateException if [prompt] is not coming from the 'user' role
   * @throws InvalidStateException if the [TemplateChat] instance has an active request
   */
  public abstract fun sendMessage(
    prompt: Content,
    inputs: Map<String, Any>
  ): ListenableFuture<GenerateContentResponse>

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
   * @param inputs the inputs needed to fill in the template ID
   * @throws InvalidStateException if [prompt] is not coming from the 'user' role
   * @throws InvalidStateException if the [TemplateChat] instance has an active request
   */
  public abstract fun sendMessageStream(
    prompt: Content,
    inputs: Map<String, Any>
  ): Publisher<GenerateContentResponse>

  /** Returns the [TemplateChat] object wrapped by this object. */
  public abstract fun getChat(): TemplateChat

  private class FuturesImpl(private val chat: TemplateChat) : TemplateChatFutures() {
    override fun sendMessage(
      prompt: Content,
      inputs: Map<String, Any>
    ): ListenableFuture<GenerateContentResponse> =
      SuspendToFutureAdapter.launchFuture { chat.sendMessage(prompt, inputs) }

    override fun sendMessageStream(
      prompt: Content,
      inputs: Map<String, Any>
    ): Publisher<GenerateContentResponse> = chat.sendMessageStream(prompt, inputs).asPublisher()

    override fun getChat(): TemplateChat = chat
  }

  public companion object {

    /** @return a [TemplateChatFutures] created around the provided [TemplateChat] */
    @JvmStatic public fun from(chat: TemplateChat): TemplateChatFutures = FuturesImpl(chat)
  }
}
