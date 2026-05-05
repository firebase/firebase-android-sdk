/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect

import app.cash.turbine.test
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.createGrpcManagedChannel
import com.google.firebase.dataconnect.testutil.schemas.RealtimeConnector
import com.google.firebase.dataconnect.testutil.schemas.RealtimeConnector.GetStringByKeyQuery
import com.google.firebase.dataconnect.testutil.schemas.RealtimeConnector.InsertStringMutation
import com.google.firebase.dataconnect.util.ProtoUtil.decodeFromStruct
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToStruct
import google.firebase.dataconnect.proto.ConnectorStreamServiceGrpcKt.ConnectorStreamServiceCoroutineStub
import google.firebase.dataconnect.proto.ExecuteRequest
import google.firebase.dataconnect.proto.StreamRequest
import google.firebase.dataconnect.proto.StreamResponse
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.az
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ConnectRPCIntegrationTest : DataConnectIntegrationTestBase() {

  @Test
  fun responseIsReceivedForExecuteQueryRequest() = runTest {
    val requestId = requestIdArb().sample()
    val name = nameArb().sample()
    val (connector, outgoingRequests, incomingResponses) = connect()
    val key = connector.insertString(name)
    outgoingRequests.sendInitRequest(requestId = "init", connector.resourceName)

    incomingResponses.test {
      outgoingRequests.sendNonBlockingOrThrow(
        StreamRequest.newBuilder().let { streamRequest ->
          streamRequest.setRequestId(requestId)
          streamRequest.setExecute(
            ExecuteRequest.newBuilder().let { executeRequest ->
              executeRequest.setOperationName(GetStringByKeyQuery.OPERATION_NAME)
              executeRequest.setVariables(encodeToStruct(GetStringByKeyQuery.Variables(key)))
              executeRequest.build()
            }
          )
          streamRequest.build()
        }
      )

      val streamResponse = awaitItem()

      streamResponse.shouldBeGetStringByKeyDataWithName(requestId = requestId, name = name)
    }
  }

  @Test
  fun responseIsReceivedForExecuteMutationRequest() = runTest {
    val requestId = requestIdArb().sample()
    val name = nameArb().sample()
    val (connector, outgoingRequests, incomingResponses) = connect()
    outgoingRequests.sendInitRequest(requestId = "init", connector.resourceName)

    incomingResponses.test {
      outgoingRequests.sendNonBlockingOrThrow(
        StreamRequest.newBuilder().let { streamRequest ->
          streamRequest.setRequestId(requestId)
          streamRequest.setExecute(
            ExecuteRequest.newBuilder().let { executeRequest ->
              executeRequest.setOperationName(InsertStringMutation.OPERATION_NAME)
              executeRequest.setVariables(encodeToStruct(InsertStringMutation.Variables(name)))
              executeRequest.build()
            }
          )
          streamRequest.build()
        }
      )

      val streamResponse = awaitItem()

      val key = streamResponse.shouldBeInsertStringData(requestId = requestId)
      connector.getString(key).shouldNotBeNull().name shouldBe name
    }
  }

  /**
   * Establishes a connection to the Data Connect `Connect` RPC and returns the streams for
   * communication.
   */
  private fun connect(): ConnectionStreams {
    val connector = RealtimeConnector.getInstance(dataConnectFactory)
    val grpcManagedChannel = createGrpcManagedChannel(connector.dataConnect)
    val outgoingRequests = Channel<StreamRequest>(Channel.UNLIMITED)
    val stub = ConnectorStreamServiceCoroutineStub(grpcManagedChannel)
    val incomingResponses = stub.connect(outgoingRequests.consumeAsFlow())
    return ConnectionStreams(connector, outgoingRequests, incomingResponses)
  }

  /**
   * Convenience extension function on [Arb] that gets a non-edge-case value using the [rs] property
   * of the [DataConnectIntegrationTestBase] superclass for the randomness source.
   */
  private fun <T> Arb<T>.sample(): T = sample(rs).value
}

/**
 * Creates and returns an [Arb] that generates strings that are suitable for, and recognizable as,
 * "request IDs" in [StreamRequest] messages.
 */
private fun requestIdArb(): Arb<String> = Arb.string(size = 4, Codepoint.az()).map { "rid$it" }

/**
 * Creates and returns an [Arb] that generates strings that are suitable for, and recognizable as,
 * "name" values for [RealtimeConnector.InsertStringMutation.Variables.name] and
 * [RealtimeConnector.UpdateStringMutation.Variables.name].
 */
private fun nameArb(): Arb<String> = Arb.string(size = 4, Codepoint.az()).map { "nam$it" }

/** Exception thrown by [sendNonBlockingOrThrow] if sending fails. */
private class SendChannelTrySendUnsuccessfulException(message: String, cause: Throwable?) :
  Exception(message, cause)

/**
 * Sends an element to receiver [SendChannel] in a non-blocking way, throwing an exception if
 * sending was unsuccessful.
 *
 * @throws SendChannelTrySendUnsuccessfulException if [SendChannel.trySend] returns a failed result.
 */
private fun <T> SendChannel<T>.sendNonBlockingOrThrow(element: T) {
  val result = trySend(element)
  if (!result.isSuccess) {
    throw SendChannelTrySendUnsuccessfulException(
      "trySend() failed with isClosed=${result.isClosed}",
      result.exceptionOrNull()
    )
  }
}

private data class ConnectionStreams(
  val connector: RealtimeConnector,
  val outgoingRequests: SendChannel<StreamRequest>,
  val incomingResponses: Flow<StreamResponse>,
)

/**
 * Sends an initialization request to the receiver [ConnectionStreams.outgoingRequests].
 *
 * @param requestId The "request ID" to send in the outgoing message.
 * @param connectorResourceName The "connector resource name" to send in the outgoing message.
 */
private fun SendChannel<StreamRequest>.sendInitRequest(
  requestId: String,
  connectorResourceName: String
) {
  val streamRequest =
    StreamRequest.newBuilder().let {
      it.setRequestId(requestId)
      it.setName(connectorResourceName)
      it.build()
    }
  sendNonBlockingOrThrow(streamRequest)
}

/**
 * Assertion to verify that a [StreamResponse] matches the expected "RealtimeString_GetByKey" data.
 *
 * @param requestId The expected request ID.
 * @param name The expected name in the data.
 */
private fun StreamResponse.shouldBeGetStringByKeyDataWithName(requestId: String, name: String) {
  withClue("StreamResponse.shouldBeGetStringByKeyDataWithName") {
    assertSoftly {
      val data = shouldBeSuccess<GetStringByKeyQuery.Data>(requestId = requestId)
      withClue("data") { data.item.shouldNotBeNull().name shouldBe name }
    }
  }
}

/**
 * Assertion to verify that a [StreamResponse] matches the expected "RealtimeString_Insert" data.
 *
 * @param requestId The expected request ID.
 */
private fun StreamResponse.shouldBeInsertStringData(requestId: String): RealtimeConnector.Key =
  withClue("StreamResponse.shouldBeInsertStringData") {
    assertSoftly { shouldBeSuccess<InsertStringMutation.Data>(requestId = requestId).key }
  }

/**
 * Assertion to verify that a [StreamResponse] matches the expected "RealtimeString_Insert" data.
 *
 * @param requestId The expected request ID.
 * @return the [Data] decoded from the receiver.
 */
private inline fun <reified Data> StreamResponse.shouldBeSuccess(requestId: String): Data {
  withClue("requestId") { this.requestId shouldBe requestId }
  withClue("errorsCount") { this.errorsCount shouldBe 0 }

  val dataDecodeResult = runCatching { decodeFromStruct<Data>(this.data) }
  val data = withClue("decodeFromStruct(data)") { dataDecodeResult.shouldBeSuccess() }

  return data
}
