// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.firebase.dataconnect

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

internal class QuerySubscription<VariablesType, ResultType>
internal constructor(internal val query: QueryRef<VariablesType, ResultType>) {
  // TODO: Call `coroutineScope.cancel()` when this object is no longer needed.
  internal val coroutineScope =
    CoroutineScope(
      query.dataConnect.coroutineScope.coroutineContext.let {
        it + SupervisorJob(it.job) + CoroutineName("QuerySubscriptionImpl")
      }
    )

  private val eventLoop = QuerySubscriptionEventLoop(this)

  val lastResult: Result<ResultType>?
    get() = eventLoop.lastResult.get()

  fun reload() = eventLoop.reload()

  fun subscribe() =
    channelFlow {
        eventLoop.registerSendChannel(channel)
        awaitClose { eventLoop.unregisterSendChannel(channel) }
      }
      .buffer(Channel.UNLIMITED)

  init {
    coroutineScope.launch { eventLoop.run() }
  }
}

private class QuerySubscriptionEventLoop<VariablesType, ResultType>(
  private val subscription: QuerySubscription<VariablesType, ResultType>
) {

  val lastResult = AtomicReference<Result<ResultType>>()

  private val running = AtomicBoolean(false)
  private val eventChannel = Channel<Event>(Channel.UNLIMITED)
  private val registeredSendChannels = mutableListOf<SendChannel<Result<ResultType>>>()
  private var reloadJob: Job? = null
  private var pendingReload = false

  suspend fun run() {
    if (!running.compareAndSet(false, true)) {
      throw IllegalStateException("run() has already been invoked")
    }

    try {
      for (event in eventChannel) {
        processEvent(event)
      }
    } finally {
      registeredSendChannels.clear()
      reloadJob = null
    }
  }

  fun registerSendChannel(channel: SendChannel<Result<ResultType>>) {
    eventChannel.trySend(RegisterSendChannelEvent(channel))
  }

  fun unregisterSendChannel(channel: SendChannel<Result<ResultType>>) {
    eventChannel.trySend(UnregisterSendChannelEvent(channel))
  }

  fun reload() {
    eventChannel.trySend(ReloadEvent)
  }

  private fun processEvent(event: Event): Unit =
    when (event) {
      is RegisterSendChannelEvent -> processRegisterSendChannelEvent(event)
      is UnregisterSendChannelEvent -> processUnregisterSendChannelEvent(event)
      is ReloadEvent -> processReloadEvent()
      is ResultEvent -> processResultEvent(event)
    }

  private fun processRegisterSendChannelEvent(event: RegisterSendChannelEvent) {
    registeredSendChannels.add(event.typedChannel())

    lastResult.get().also {
      if (it != null) {
        event.typedChannel<Result<ResultType>>().trySend(it)
      } else if (reloadJob == null) {
        processReloadEvent()
      }
    }
  }

  private fun processUnregisterSendChannelEvent(event: UnregisterSendChannelEvent) {
    registeredSendChannels.listIterator().let {
      while (it.hasNext()) {
        if (it.next() === event.channel) {
          it.remove()
          break
        }
      }
    }
  }

  private fun processReloadEvent() {
    if (reloadJob?.isActive == true) {
      pendingReload = true
      return
    }

    reloadJob =
      subscription.coroutineScope.launch {
        val result =
          try {
            Result.success(subscription.query.execute())
          } catch (e: Throwable) {
            Result.failure(e)
          }

        eventChannel.trySend(ResultEvent(result))
      }
  }

  private fun processResultEvent(event: ResultEvent) {
    if (pendingReload) {
      pendingReload = false
      reload()
    }

    lastResult.set(event.typedResult())

    registeredSendChannels.iterator().let {
      while (it.hasNext()) {
        val sendResult = it.next().trySend(event.typedResult())
        if (sendResult.isClosed) {
          it.remove()
        }
      }
    }
  }

  private sealed interface Event
  private object ReloadEvent : Event

  private sealed class SendChannelEvent(val channel: SendChannel<*>) : Event {
    @Suppress("UNCHECKED_CAST")
    fun <T> typedChannel(): SendChannel<T> {
      return channel as SendChannel<T>
    }
  }

  private class RegisterSendChannelEvent(channel: SendChannel<*>) : SendChannelEvent(channel)
  private class UnregisterSendChannelEvent(channel: SendChannel<*>) : SendChannelEvent(channel)

  private class ResultEvent(val result: Result<*>) : Event {
    @Suppress("UNCHECKED_CAST")
    fun <T> typedResult(): Result<T> {
      return result as Result<T>
    }
  }
}
