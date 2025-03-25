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
import com.google.firebase.vertexai.GenerativeModel
import com.google.firebase.vertexai.LiveGenerativeModel
import com.google.firebase.vertexai.type.LiveSession
import java.nio.channels.ClosedChannelException

/**
 * Wrapper class providing Java compatible methods for [GenerativeModel].
 *
 * @see [GenerativeModel]
 */
public abstract class LiveModelFutures internal constructor() {

  /**
   * Returns a LiveSession object using which you could send/receive messages from the server
   * @return LiveSession object created. Returns null if the object cannot be created.
   * @throws [ClosedChannelException] if channel was closed before creating a websocket connection.
   */
  public abstract fun connect(): ListenableFuture<LiveSession>

  private class FuturesImpl(private val model: LiveGenerativeModel) : LiveModelFutures() {
    override fun connect(): ListenableFuture<LiveSession> {
      return SuspendToFutureAdapter.launchFuture { model.connect() }
    }
  }

  public companion object {

    /** @return a [GenerativeModelFutures] created around the provided [GenerativeModel] */
    @JvmStatic public fun from(model: LiveGenerativeModel): LiveModelFutures = FuturesImpl(model)
  }
}
