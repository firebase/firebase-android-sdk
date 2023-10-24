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

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal class QuerySubscriptionImpl(
  override val dataConnect: FirebaseDataConnectImpl,
  override val query: QueryRef,
  initialVariables: Map<String, Any?>
) : QuerySubscription {

  private var _variables = AtomicReference(initialVariables.toMap())
  override var variables: Map<String, Any?>
    get() = _variables.get()
    set(value) {
      _variables.set(value.toMap())
      reload()
    }

  private val registeredChannels = mutableListOf<SendChannel<QueryResult>>()
  private val jobQueue = Channel<WorkItem>(Channel.UNLIMITED)
  private val job =
    dataConnect.coroutineScope
      .launch {
        for (workItem in jobQueue) {
          when (workItem) {
            is RegisterChannelWorkItem -> registeredChannels.add(workItem.channel)
            is UnregisterChannelWorkItem ->
              for (i in 0 until registeredChannels.size) {
                if (registeredChannels[i] === workItem.channel) {
                  registeredChannels.removeAt(i)
                  break
                }
              }
            is ReloadWorkItem -> {
              val result = dataConnect.executeQuery(query, variables)
              registeredChannels.forEach { launch { it.send(SuccessQueryResult(result)) } }
            }
          }
        }
      }
      .apply {
        invokeOnCompletion {
          registeredChannels.forEach { it.close() }
          registeredChannels.clear()
        }
      }

  override val isClosed: Boolean
    get() = !job.isActive

  override val channel: ReceiveChannel<QueryResult>
    get() = Channel<QueryResult>(Channel.CONFLATED).also { sendTo(it) }

  override fun reload() {
    jobQueue.trySend(ReloadWorkItem())
  }

  override fun sendTo(channel: SendChannel<QueryResult>) {
    jobQueue.trySend(RegisterChannelWorkItem(channel))
  }

  override fun stopSendingTo(channel: SendChannel<QueryResult>) {
    jobQueue.trySend(UnregisterChannelWorkItem(channel))
  }

  override fun close() {
    jobQueue.close()
    job.cancel()
  }
}

private sealed interface WorkItem

private class RegisterChannelWorkItem(val channel: SendChannel<QueryResult>) : WorkItem

private class UnregisterChannelWorkItem(val channel: SendChannel<QueryResult>) : WorkItem

private class ReloadWorkItem : WorkItem
