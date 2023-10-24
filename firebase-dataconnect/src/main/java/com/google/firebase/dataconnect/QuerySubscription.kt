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

import com.google.protobuf.Struct
import java.io.Closeable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

/**
 * Manages a subscription to a query.
 *
 * This class is entirely thread-safe. It is completely safe to call any methods, get any property,
 * or set any settable property concurrently from multiple threads.
 */
interface QuerySubscription : Closeable {

  /** The `FirebaseDataConnect` instance that this object uses. */
  val dataConnect: FirebaseDataConnect

  /** The query that is executed by this subscription. */
  val query: QueryRef

  /** Returns whether this object has been closed. */
  val isClosed: Boolean

  /**
   * Creates and returns a channel to which events from this query subscription are sent.
   *
   * This property returns a new object each time it is accessed. Each access is equivalent to
   * calling [sendTo] with a new [Channel] opened in [Channel.CONFLATED] mode. For greater control
   * over the semantics of the channel, call [sendTo] directly.
   */
  val channel: ReceiveChannel<QueryResult>

  /**
   * The variables used when the query is executed.
   *
   * When this property is set, a _copy_ of the given map is made and stored internally. Therefore,
   * any changes to the given map after setting this property have no effect on this object.
   *
   * Setting this property will cause the underlying query to be re-executed with the new variables,
   * as if [reload] had been called. As a result, if the variables are set after this object is
   * closed then the query is, in fact, _not_ re-executed (the same as [reload]).
   */
  var variables: Map<String, Any?>

  /**
   * Forcefully re-executes the query and posts the result to all associated channels.
   *
   * If no channels are registered then this method does nothing and returns as if successful.
   *
   * If this object is closed, then this method does nothing and returns as if successful.
   */
  fun reload()

  /**
   * Registers a channel to have events sent to it.
   *
   * The first invocation of this method triggers actual execution of the query. The result of the
   * query execution is sent to each registered channel concurrently.
   *
   * All subsequent invocations of this method immediately deliver the result most recently sent to
   * previously-registered channels.
   *
   * The given channel will be closed when this object is closed by a call to [close]. If this
   * method is invoked _after_ this object is already closed then the given channel will immediately
   * be closed.
   *
   * If the given channel is already registered then it will be registered again and will have each
   * event sent to it multiple times, once per registration. That is, this method does _not_ check
   * for duplicates. In order to completely unregister a channel, a matching number of
   * [stopSendingTo] invocations are required.
   */
  fun sendTo(channel: SendChannel<QueryResult>)

  /**
   * Unregisters a channel from having events sent to it.
   *
   * If the given channel is not currently registered then this method does nothing and returns as
   * if successful.
   *
   * If this object is closed then this method does nothing and returns as if successful.
   */
  fun stopSendingTo(channel: SendChannel<QueryResult>)

  /**
   * Closes this object.
   *
   * All channels currently-registered via [sendTo] will be closed by invoking their
   * [SendChannel.close] method.
   *
   * If this object is already closed then this method does nothing. If another thread is
   * concurrently invoking this method then this invocation will block, waiting for the other to
   * complete the close operation.
   */
  override fun close()
}

sealed interface QueryResult

class SuccessQueryResult(val data: Struct) : QueryResult

class FailedQueryResult(val errors: List<String>) : QueryResult
