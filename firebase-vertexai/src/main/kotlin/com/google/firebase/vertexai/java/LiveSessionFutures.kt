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
import com.google.firebase.vertexai.GenerativeModel
import com.google.firebase.vertexai.type.Content
import com.google.firebase.vertexai.type.ContentModality
import com.google.firebase.vertexai.type.FunctionResponsePart
import com.google.firebase.vertexai.type.LiveContentResponse
import com.google.firebase.vertexai.type.LiveSession
import com.google.firebase.vertexai.type.MediaData
import kotlinx.coroutines.reactive.asPublisher
import org.reactivestreams.Publisher

/**
 * Wrapper class providing Java compatible methods for [GenerativeModel].
 *
 * @see [GenerativeModel]
 */
public abstract class LiveSessionFutures internal constructor() {
  public abstract fun startAudioConversation(): ListenableFuture<Unit>

  public abstract fun stopAudioConversation(): ListenableFuture<Unit>

  public abstract fun stopReceiving()

  public abstract fun sendFunctionResponse(
    functionList: List<FunctionResponsePart>
  ): ListenableFuture<Unit>

  public abstract fun sendMediaStream(mediaChunks: List<MediaData>): ListenableFuture<Unit>

  public abstract fun send(content: Content): ListenableFuture<Unit>

  public abstract fun send(text: String): ListenableFuture<Unit>

  public abstract fun close(): ListenableFuture<Unit>

  public abstract fun receive(
    outputModalities: List<ContentModality>
  ): ListenableFuture<Publisher<LiveContentResponse>>

  private class FuturesImpl(private val session: LiveSession) : LiveSessionFutures() {

    override fun receive(
      outputModalities: List<ContentModality>
    ): ListenableFuture<Publisher<LiveContentResponse>> =
      SuspendToFutureAdapter.launchFuture { session.receive(outputModalities).asPublisher() }

    override fun close(): ListenableFuture<Unit> =
      SuspendToFutureAdapter.launchFuture { session.close() }

    override fun send(text: String) = SuspendToFutureAdapter.launchFuture { session.send(text) }

    override fun send(content: Content) =
      SuspendToFutureAdapter.launchFuture { session.send(content) }

    override fun sendFunctionResponse(functionList: List<FunctionResponsePart>) =
      SuspendToFutureAdapter.launchFuture { session.sendFunctionResponse(functionList) }

    override fun sendMediaStream(mediaChunks: List<MediaData>) =
      SuspendToFutureAdapter.launchFuture { session.sendMediaStream(mediaChunks) }

    override fun startAudioConversation() =
      SuspendToFutureAdapter.launchFuture { session.startAudioConversation() }

    override fun stopAudioConversation() =
      SuspendToFutureAdapter.launchFuture { session.stopAudioConversation() }

    override fun stopReceiving() = session.stopReceiving()
  }

  public companion object {

    /** @return a [GenerativeModelFutures] created around the provided [GenerativeModel] */
    @JvmStatic public fun from(session: LiveSession): LiveSessionFutures = FuturesImpl(session)
  }
}
