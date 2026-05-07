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
import com.google.firebase.dataconnect.testutil.property.arbitrary.pair
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.firebase.dataconnect.testutil.schemas.RealtimeConnector
import com.google.firebase.dataconnect.testutil.schemas.RealtimeConnector.GetStringByKeyQuery
import com.google.firebase.dataconnect.testutil.schemas.RealtimeConnector.InsertStringMutation
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.util.ProtoUtil.decodeFromStruct
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToStruct
import google.firebase.dataconnect.proto.ConnectorStreamServiceGrpcKt.ConnectorStreamServiceCoroutineStub
import google.firebase.dataconnect.proto.ExecuteRequest
import google.firebase.dataconnect.proto.StreamRequest
import google.firebase.dataconnect.proto.StreamResponse
import io.grpc.Status
import io.grpc.StatusException
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.print.print
import io.kotest.assertions.withClue
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.az
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ConnectRPCIntegrationTest : DataConnectIntegrationTestBase() {

  @Before
  fun registerPrinters() {
    registerDataConnectKotestPrinters()
  }

  @Test
  fun executeRequestForQuerySucceeds() = runTest {
    val name = nameArb().sample()
    val streams = connect()
    val key = streams.connector.insertString(name)
    streams.sendInitRequest()

    val streamResponse =
      verifyExecuteRequestReturnsCorrectRequestIdAndCancelledTrue(
        streams,
        expectedErrorsCount = 0,
        ExecuteRequest.newBuilder().let { executeRequest ->
          executeRequest.setOperationName(GetStringByKeyQuery.OPERATION_NAME)
          executeRequest.setVariables(encodeToStruct(GetStringByKeyQuery.Variables(key)))
          executeRequest.build()
        }
      )

    val data = streamResponse.shouldHaveData<GetStringByKeyQuery.Data>()
    data.item.shouldNotBeNull().name shouldBe name
  }

  @Test
  fun executeRequestForMutationSucceeds() = runTest {
    val name = nameArb().sample()
    val streams = connect()
    streams.sendInitRequest()

    val streamResponse =
      verifyExecuteRequestReturnsCorrectRequestIdAndCancelledTrue(
        streams,
        expectedErrorsCount = 0,
        ExecuteRequest.newBuilder().let { executeRequest ->
          executeRequest.setOperationName(InsertStringMutation.OPERATION_NAME)
          executeRequest.setVariables(encodeToStruct(InsertStringMutation.Variables(name)))
          executeRequest.build()
        }
      )

    val data = streamResponse.shouldHaveData<InsertStringMutation.Data>()
    streams.connector.getString(data.key).shouldNotBeNull().name shouldBe name
  }

  @Test
  fun executeRequestForNonExistentOperationFails() = runTest {
    val streams = connect()
    streams.sendInitRequest()

    val streamResponse =
      verifyExecuteRequestReturnsCorrectRequestIdAndCancelledTrue(
        streams,
        expectedErrorsCount = 1,
        ExecuteRequest.newBuilder().let { executeRequest ->
          executeRequest.setOperationName("NonExistentOperationName")
          executeRequest.build()
        }
      )

    val error = streamResponse.errorsList.single()
    withClue("error=${error.print().value}") {
      error.message shouldContainWithNonAbuttingText "NonExistentOperationName"
    }
  }

  @Test
  fun executeRequestWithoutInitFails() = runTest {
    checkAll(propTestConfig, validStreamRequestArb()) { streamRequest ->
      val streams = connect()
      streams.incomingResponses.test {
        streams.outgoingRequests.send(streamRequest)

        val exception = awaitError()

        val statusException = exception.shouldBeInstanceOf<StatusException>()
        statusException.status.code shouldBe Status.Code.INVALID_ARGUMENT
        statusException.message shouldContainWithNonAbuttingTextIgnoringCase
          "invalid connector name"
      }
    }
  }

  @Test
  fun closingOutgoingRequestsBeforeSendingAndListeningFailsIncomingResponses() = runTest {
    val streams = connect()
    streams.outgoingRequests.close()

    streams.incomingResponses.test {
      val exception = awaitError()

      val statusException = exception.shouldBeInstanceOf<StatusException>()
      statusException.status.code shouldBe Status.Code.UNKNOWN
      statusException.message shouldContainWithNonAbuttingTextIgnoringCase
        "failed to receive first request"
    }
  }

  @Test
  fun closingOutgoingRequestsBeforeSendingButAfterListeningFailsIncomingResponses() = runTest {
    val streams = connect()

    streams.incomingResponses.test {
      streams.outgoingRequests.close()

      val exception = awaitError()

      val statusException = exception.shouldBeInstanceOf<StatusException>()
      statusException.status.code shouldBe Status.Code.UNKNOWN
      statusException.message shouldContainWithNonAbuttingTextIgnoringCase
        "failed to receive first request"
    }
  }

  @Test
  fun closingOutgoingRequestsAfterStreamEstablishedClosesIncomingResponses() = runTest {
    val streams = connect()
    streams.sendInitRequest()

    streams.incomingResponses.test {
      val streamRequest = validStreamRequestArb().sample()
      streams.outgoingRequests.send(streamRequest)
      awaitItem().requestId shouldBe streamRequest.requestId

      streams.outgoingRequests.close()

      awaitComplete()
    }
  }

  @Test
  fun closingOutgoingRequestsBeforeSendingWithCancellationExceptionFailsIncomingResponses() =
    runTest {
      val streams = connect()

      streams.incomingResponses.test {
        streams.outgoingRequests.close(CancellationException("forced exception v5bgf9ppmm"))

        val exception = awaitError()

        val cancellationException = exception.shouldBeInstanceOf<CancellationException>()
        cancellationException shouldHaveMessage "forced exception v5bgf9ppmm"
      }
    }

  @Test
  fun closingOutgoingRequestsBeforeSendingWithNonCancellationExceptionFailsIncomingResponses() =
    runTest {
      class TestException(message: String) : Exception(message)
      val streams = connect()

      streams.incomingResponses.test {
        streams.outgoingRequests.close(TestException("forced exception gajhw85ajt"))

        val exception = awaitError()

        val testException = exception.shouldBeInstanceOf<TestException>()
        testException shouldHaveMessage "forced exception gajhw85ajt"
      }
    }

  @Test
  fun collectIncomingResponsesCancelsBeforeCollectingAnyResponses() = runTest {
    val streams = connect()
    // Note: Specify CoroutineStart.UNDISPATCHED to guarantee that collect() is called before
    // cancelling the coroutine via the cancelAndJoin() call.
    val collectJob =
      backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
        streams.incomingResponses.collect()
      }
    collectJob.cancelAndJoin()

    streams.sendInitRequest()
    checkAll(propTestConfig, validStreamRequestArb()) { streamRequest ->
      val sendResult = streams.outgoingRequests.trySend(streamRequest)
      sendResult.isSuccess shouldBe true
      sendResult.isClosed shouldBe false
    }
  }

  @Test
  fun collectIncomingResponsesCancelsAfterCollectingResponses() = runTest {
    val streams = connect()
    val collectedValues = Channel<StreamResponse>(capacity = UNLIMITED)
    val collectJob =
      backgroundScope.launch { streams.incomingResponses.collect(collectedValues::send) }

    val (streamRequest1, streamRequest2) = validStreamRequestArb().pair().sample()
    streams.sendInitRequest()
    streams.outgoingRequests.send(streamRequest1)
    collectedValues.receive().requestId shouldBe streamRequest1.requestId
    collectJob.cancelAndJoin()
    val sendResult = streams.outgoingRequests.trySend(streamRequest2)

    sendResult.exceptionOrNull().shouldBeInstanceOf<CancellationException>()
    sendResult.isClosed shouldBe true
  }

  @Test
  fun collectIncomingResponsesThrowsBeforeFirstSendToOutgoingRequests() = runTest {
    class TestException(message: String) : Exception(message)
    val streams = connect()
    val collectJob =
      backgroundScope.launch {
        streams.incomingResponses.runCatching {
          collect { throw TestException("forced exception kxf3z6dbmr") }
        }
      }

    val (streamRequest1, streamRequest2) = validStreamRequestArb().pair().sample()
    streams.sendInitRequest()
    streams.outgoingRequests.send(streamRequest1)
    collectJob.join()
    val sendResult = streams.outgoingRequests.trySend(streamRequest2)

    val cancellationException =
      sendResult.exceptionOrNull().shouldBeInstanceOf<CancellationException>()
    val testException = cancellationException.cause.shouldBeInstanceOf<TestException>()
    testException shouldHaveMessage "forced exception kxf3z6dbmr"
    sendResult.isClosed shouldBe true
  }

  @Test
  fun testValidStreamRequestArb() = runTest {
    val streams = connect()
    streams.sendInitRequest()

    streams.incomingResponses.test {
      checkAll(propTestConfig, validStreamRequestArb()) { streamRequest ->
        streams.outgoingRequests.send(streamRequest)
        val streamResponse = awaitItem()
        streamResponse.requestId shouldBe streamRequest.requestId
        streamResponse.errorsCount shouldBe 0
      }
    }
  }

  private suspend fun verifyExecuteRequestReturnsCorrectRequestIdAndCancelledTrue(
    streams: ConnectionStreams,
    expectedErrorsCount: Int,
    executeRequest: ExecuteRequest,
  ): StreamResponse {
    val requestId = requestIdArb().sample()
    val streamRequest =
      StreamRequest.newBuilder().let { streamRequest ->
        streamRequest.setRequestId(requestId)
        streamRequest.setExecute(executeRequest)
        streamRequest.build()
      }

    var streamResponse: StreamResponse? = null
    streams.incomingResponses.test {
      streams.outgoingRequests.sendNonBlockingOrThrow(streamRequest)

      streamResponse = awaitItem()

      assertSoftly {
        withClue("requestId") { streamResponse.requestId shouldBe requestId }
        withClue("cancelled") { streamResponse.cancelled shouldBe true }
        withClue("errorsCount") { streamResponse.errorsCount shouldBe expectedErrorsCount }
      }
    }

    return streamResponse!!
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

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 5,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )

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

/**
 * Creates and returns an [Arb] that generates "key" objects for rows inserted by
 * [RealtimeConnector.InsertStringMutation] and fetched by [RealtimeConnector.GetStringByKeyQuery].
 */
private fun keyArb(uuid: Arb<UUID> = Arb.uuid()): Arb<RealtimeConnector.Key> =
  uuid.map(RealtimeConnector::Key)

private fun ConnectionStreams.sendInitRequest() {
  outgoingRequests.sendInitRequest(requestId = "init", connector.resourceName)
}

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
 * Assertion to verify that a [StreamResponse] is successful and has data that matches the given
 * predicate.
 *
 * @param predicate the function to call with the data to verify it.
 * @return the [Data] decoded from the receiver, that was specified to the given [predicate].
 */
private inline fun <reified Data> StreamResponse.shouldHaveData(
  predicate: (Data) -> Unit = {}
): Data {
  val dataDecodeResult = runCatching { decodeFromStruct<Data>(data) }
  val data = withClue("decodeFromStruct(data)") { dataDecodeResult.shouldBeSuccess() }
  withClue("data") { predicate(data) }
  return data
}

private enum class ValidStreamRequestType {
  ExecuteQuery,
  ExecuteMutation,
}

/**
 * Creates and returns an [Arb] that generates [StreamRequest] objects that, when sent to the
 * [ConnectionStreams.outgoingRequests] after the "init" request, should succeed.
 */
private fun validStreamRequestArb(
  requestId: Arb<String> = @OptIn(DelicateKotest::class) requestIdArb().distinct(),
  name: Arb<String> = nameArb(),
  key: Arb<RealtimeConnector.Key> = keyArb(),
  validStreamRequestType: Arb<ValidStreamRequestType> = Arb.enum<ValidStreamRequestType>(),
): Arb<StreamRequest> = arbitrary {
  val streamRequest = StreamRequest.newBuilder()
  streamRequest.setRequestId(requestId.bind())

  when (validStreamRequestType.bind()) {
    ValidStreamRequestType.ExecuteQuery ->
      streamRequest.setExecute(
        ExecuteRequest.newBuilder().let { executeRequest ->
          executeRequest.setOperationName(GetStringByKeyQuery.OPERATION_NAME)
          executeRequest.setVariables(encodeToStruct(GetStringByKeyQuery.Variables(key.bind())))
          executeRequest.build()
        }
      )
    ValidStreamRequestType.ExecuteMutation ->
      streamRequest.setExecute(
        ExecuteRequest.newBuilder().let { executeRequest ->
          executeRequest.setOperationName(InsertStringMutation.OPERATION_NAME)
          executeRequest.setVariables(encodeToStruct(InsertStringMutation.Variables(name.bind())))
          executeRequest.build()
        }
      )
  }

  streamRequest.build()
}
