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

package com.google.firebase.vertexai.java

import androidx.concurrent.futures.SuspendToFutureAdapter
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.vertexai.LiveGenerativeModel
import com.google.firebase.vertexai.type.LiveSession
import com.google.firebase.vertexai.type.PublicPreviewAPI
import com.google.firebase.vertexai.type.ServiceConnectionHandshakeFailedException

/**
 * Wrapper class providing Java compatible methods for [LiveGenerativeModel].
 *
 * @see [LiveGenerativeModel]
 */
@PublicPreviewAPI
public abstract class LiveModelFutures internal constructor() {

  /**
   * Start a [LiveSession] with the server for bidirectional streaming.
   * @return A [LiveSession] that you can use to stream messages to and from the server.
   * @throws [ServiceConnectionHandshakeFailedException] If the client was not able to establish a
   * connection with the server.
   */
  public abstract fun connect(): ListenableFuture<LiveSession>

  private class FuturesImpl(private val model: LiveGenerativeModel) : LiveModelFutures() {
    override fun connect(): ListenableFuture<LiveSession> {
      return SuspendToFutureAdapter.launchFuture { model.connect() }
    }
  }

  public companion object {

    /** @return a [LiveModelFutures] created around the provided [LiveGenerativeModel] */
    @JvmStatic public fun from(model: LiveGenerativeModel): LiveModelFutures = FuturesImpl(model)
  }
}
