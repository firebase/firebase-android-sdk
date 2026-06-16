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
package com.google.firebase.dataconnect.core

import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.dataconnect.DataConnectSettings
import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.core.DataConnectAuth.GetAuthTokenResult
import com.google.firebase.dataconnect.core.DataConnectBidiConnectStream.Companion.setReconnectPendingAuthTokenForTesting
import com.google.firebase.dataconnect.core.DataConnectBidiConnectStream.Companion.unsetReconnectPendingAuthTokenForTesting
import com.google.firebase.dataconnect.testutil.CleanupsRule
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.FirebaseAppUnitTestingRule
import com.google.firebase.dataconnect.testutil.ImmediateDeferred
import com.google.firebase.dataconnect.testutil.InProcessDataConnectGrpcStreamingServer
import com.google.firebase.dataconnect.testutil.InProcessDataConnectGrpcStreamingServer.Event.ConnectRpcStarted
import com.google.firebase.dataconnect.testutil.InProcessDataConnectGrpcStreamingServer.Event.StreamRequestReceived
import com.google.firebase.dataconnect.testutil.LoggedInInternalAuthProvider
import com.google.firebase.dataconnect.testutil.LoggedInMultiTokenAndUidAuthProvider
import com.google.firebase.dataconnect.testutil.NotLoggedInInternalAuthProvider
import com.google.firebase.dataconnect.testutil.OperationNameVariablesPair
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.UnavailableDeferred
import com.google.firebase.dataconnect.testutil.appCheckTokenGrpcMetadataKey
import com.google.firebase.dataconnect.testutil.authTokenGrpcMetadataKey
import com.google.firebase.dataconnect.testutil.awaitCall
import com.google.firebase.dataconnect.testutil.awaitConnectRpcStarted
import com.google.firebase.dataconnect.testutil.awaitResponseSender
import com.google.firebase.dataconnect.testutil.awaitUntilCancelStreamRequest
import com.google.firebase.dataconnect.testutil.awaitUntilInitStreamRequest
import com.google.firebase.dataconnect.testutil.awaitUntilItemIsInstance
import com.google.firebase.dataconnect.testutil.awaitUntilResumeStreamRequest
import com.google.firebase.dataconnect.testutil.awaitUntilStatusExceptionReceived
import com.google.firebase.dataconnect.testutil.awaitUntilStreamRequest
import com.google.firebase.dataconnect.testutil.awaitUntilStreamRequestWithRequestId
import com.google.firebase.dataconnect.testutil.awaitUntilSubscribeStreamRequest
import com.google.firebase.dataconnect.testutil.property.arbitrary.authUid
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.distinctPair
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.testutil.tokenUidPairOrNullIfUidNull
import com.google.firebase.dataconnect.util.IdStringGenerator
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToStruct
import com.google.firebase.dataconnect.util.SequencedReference
import com.google.firebase.dataconnect.util.SequencedReference.Companion.nextSequenceNumber
import com.google.firebase.internal.InternalTokenResult
import google.firebase.dataconnect.proto.StreamRequest
import google.firebase.dataconnect.proto.StreamRequest.RequestKindCase
import google.firebase.dataconnect.proto.StreamResponse
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import io.kotest.assertions.asClue
import io.kotest.assertions.print.print
import io.kotest.assertions.withClue
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotStartWith
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.az
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlin.random.Random
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuerySubscriptionImplUnitTest {

  @get:Rule val cleanups = CleanupsRule()
  @get:Rule val testName = TestName()
  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()
  @get:Rule(order = Int.MIN_VALUE) val randomSeedTestRule = RandomSeedTestRule()

  @get:Rule
  val firebaseAppFactory =
    FirebaseAppUnitTestingRule(
      appNameKey = "vt9xvmbqja",
      applicationIdKey = "e9tnx2t8aa",
      projectIdKey = "snbchrkbz8"
    )

  private val rs: RandomSource by randomSeedTestRule.rs

  @Before
  fun registerPrinters() {
    registerDataConnectKotestPrinters()
  }

  @Test
  fun `collecting flow after DataConnect is closed completes immediately`() = runTest {
    val subscription = querySubscription()
    subscription.query.dataConnect.suspendingClose()
    subscription.flow.test { awaitComplete() }
  }

  @Test
  fun `collecting flow sends init StreamRequest first`() = runTest {
    val server = runningInProcessDataConnectServer()
    val dataConnect = dataConnect(server)
    val subscription = querySubscription(dataConnect)

    server.events.test {
      backgroundScope.launch { subscription.flow.collect() }

      val event: StreamRequestReceived = awaitUntilItemIsInstance()
      event.streamRequest.let { request ->
        withClue("request=${request.print().value}") {
          request.requestId shouldBe "init"
          request.requestKindCase shouldBe RequestKindCase.REQUESTKIND_NOT_SET
        }
      }

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `collecting flow sends expected subscribe StreamRequest`() = runTest {
    val server = runningInProcessDataConnectServer()
    val subscribeRequestId = Arb.dataConnect.requestId().sample()
    val idStringGenerator = idStringGeneratorThatGeneratesRequestId(subscribeRequestId)
    val dataConnect = dataConnect(server, idStringGenerator)
    val subscription = querySubscription(dataConnect)

    server.events.test {
      backgroundScope.launch { subscription.flow.collect() }
      awaitUntilInitStreamRequest()

      val event: StreamRequestReceived = awaitUntilItemIsInstance()
      event.streamRequest.shouldBeSubscribeRequestFor(subscription.query, subscribeRequestId)

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `flow correctly decodes StreamResponse messages`() = runTest {
    val server = runningInProcessDataConnectServer()
    val subscribeRequestId = Arb.dataConnect.requestId().sample()
    val idStringGenerator = idStringGeneratorThatGeneratesRequestId(subscribeRequestId)
    val dataConnect = dataConnect(server, idStringGenerator)
    val subscription = querySubscription(dataConnect)

    turbineScope {
      val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
      val clientCollector = subscription.flow.testIn(backgroundScope, name = "clientCollector")

      val responseSender = serverCollector.awaitResponseSender()
      serverCollector.awaitUntilInitStreamRequest()
      serverCollector.awaitUntilStreamRequestWithRequestId(subscribeRequestId)

      val testDataArb = testDataArb()
      repeat(5) {
        val testData = testDataArb.sample()
        responseSender.onNext(subscribeRequestId, testData)

        val querySubscriptionResult = clientCollector.awaitItem()
        withClue(querySubscriptionResult.print().value) {
          val queryResult = querySubscriptionResult.result.shouldBeSuccess()
          queryResult.data shouldBe testData
        }
      }

      serverCollector.cancelAndIgnoreRemainingEvents()
      clientCollector.cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `flows for distinct operation name and variables pairs share same connection`() = runTest {
    val requestIds = distinctRequestIdArb().sampleList(10)
    val subscriptionParameters =
      distinctOperationNameVariablesPairWithRepeatedComponentsArb().sampleList(requestIds.size)
    val idStringGenerator = idStringGeneratorThatGeneratesRequestIds(requestIds)
    val server = runningInProcessDataConnectServer()
    val dataConnect = dataConnect(server, idStringGenerator)
    val subscriptions =
      subscriptionParameters
        .map { querySubscription(dataConnect, it.operationName, it.variables) }
        .shuffled(rs.random)

    turbineScope {
      val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
      val clientCollectors =
        subscriptions.mapIndexed { index, subscription ->
          subscription.flow.testIn(backgroundScope, name = "clientCollector$index")
        }

      val connection: ConnectRpcStarted = serverCollector.awaitUntilItemIsInstance()
      serverCollector.awaitUntilInitStreamRequest()
      val unacknowledgedRequestIds = requestIds.toMutableSet()
      while (unacknowledgedRequestIds.isNotEmpty()) {
        serverCollector.awaitItem().shouldBeInstanceOf<StreamRequestReceived>().let {
          it.connectionId shouldBe connection.connectionId
          it.streamRequest.requestId shouldBeIn unacknowledgedRequestIds
          unacknowledgedRequestIds.remove(it.streamRequest.requestId)
        }
      }

      serverCollector.cancelAndIgnoreRemainingEvents()
      clientCollectors.forEach { it.cancelAndIgnoreRemainingEvents() }
    }
  }

  @Test
  fun `flows for identical operation name and variables pairs share same request`() = runTest {
    val requestIds = distinctRequestIdArb().sampleList(10)
    val operationName = Arb.dataConnect.operationName().sample()
    val variables = testVariablesArb().sample()
    val idStringGenerator = idStringGeneratorThatGeneratesRequestIds(requestIds)
    val server = runningInProcessDataConnectServer()
    val dataConnect = dataConnect(server, idStringGenerator)
    val subscriptions =
      List(requestIds.size) { querySubscription(dataConnect, operationName, variables) }

    turbineScope {
      val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
      val clientCollector0 =
        subscriptions[0].flow.testIn(backgroundScope, name = "clientCollector0")

      val responseObserver = serverCollector.awaitConnectRpcStarted().responseObserver
      val subscribeRequest = serverCollector.awaitUntilSubscribeStreamRequest()
      val testData = testDataArb().let { arb -> List(requestIds.size) { arb.sample() } }
      val respondJob =
        backgroundScope.launch {
          val requestId = subscribeRequest.streamRequest.requestId
          val testDataIterator = testData.iterator()
          while (true) {
            responseObserver.onNext(requestId, testDataIterator.next())
            val resumeRequest = serverCollector.awaitUntilResumeStreamRequest()
            resumeRequest.connectionId shouldBe subscribeRequest.connectionId
            resumeRequest.streamRequest.requestId shouldBe subscribeRequest.streamRequest.requestId
            resumeRequest.streamRequest.requestKindCase shouldBe RequestKindCase.RESUME
          }
        }

      subscribeRequest.streamRequest.requestId shouldBeIn requestIds
      subscribeRequest.streamRequest.requestKindCase shouldBe RequestKindCase.SUBSCRIBE
      clientCollector0.awaitItem()
      val clientCollectors =
        subscriptions.drop(1).mapIndexed { index, subscription ->
          if (rs.random.nextBoolean()) yield() // let some of the resume requests coalesce
          subscription.flow.testIn(backgroundScope, name = "clientCollector${index+1}")
        }
      clientCollectors.forEach { clientCollector ->
        clientCollector.awaitItem().result.shouldBeSuccess().data shouldBeIn testData
      }

      clientCollectors.forEach { it.cancelAndIgnoreRemainingEvents() }
      respondJob.cancelAndJoin()
      clientCollector0.cancelAndIgnoreRemainingEvents()
      serverCollector.cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `cancel message is sent after last unsubscription for a query`() = runTest {
    val subscriptionParameters =
      distinctOperationNameVariablesPairWithRepeatedComponentsArb().sampleList(10)
    val server = runningInProcessDataConnectServer()
    val dataConnect = dataConnect(server)
    val subscriptions =
      subscriptionParameters.map { querySubscription(dataConnect, it.operationName, it.variables) }

    turbineScope {
      val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
      val clientCollectors =
        List(2) {
          subscriptions
            .mapIndexed { index, subscription ->
              subscription.flow.testIn(backgroundScope, name = "clientCollector$index")
            }
            .shuffled(rs.random)
        }
      val requestIds =
        MutableList(subscriptions.size) {
          serverCollector.awaitUntilSubscribeStreamRequest().streamRequest.requestId
        }

      clientCollectors[0].forEach { clientCollector ->
        clientCollector.cancelAndIgnoreRemainingEvents()
      }
      while (true) {
        yield()
        val event = serverCollector.asChannel().tryReceive().getOrNull() ?: break
        if (event is StreamRequestReceived) {
          event.streamRequest.requestKindCase shouldNotBe RequestKindCase.CANCEL
        }
      }

      // Specify drop(1) so that we don't close the very last connection as that will shut down
      // the entire connection, confusing the logic below.
      clientCollectors[1].drop(1).forEach { clientCollector ->
        clientCollector.cancelAndIgnoreRemainingEvents()
        val requestId = serverCollector.awaitUntilCancelStreamRequest().streamRequest.requestId
        requestId shouldBeIn requestIds
        requestIds.removeAt(requestIds.indexOf(requestId))
      }

      serverCollector.cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `connection is closed when close() is called on dataConnect`() = runTest {
    val server = runningInProcessDataConnectServer()
    val dataConnect = dataConnect(server)
    val subscription = querySubscription(dataConnect)

    turbineScope {
      val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
      val clientCollector = subscription.flow.testIn(backgroundScope, name = "clientCollector")
      serverCollector.awaitUntilSubscribeStreamRequest()

      dataConnect.close()
      serverCollector.awaitUntilClientClosesConnection()

      serverCollector.cancelAndIgnoreRemainingEvents()
      clientCollector.cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `flows complete normally when close() is called on dataConnect`() = runTest {
    val server = runningInProcessDataConnectServer()
    val dataConnect = dataConnect(server)
    val subscription = querySubscription(dataConnect)

    turbineScope {
      val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
      val clientCollector = subscription.flow.testIn(backgroundScope, name = "clientCollector")
      serverCollector.awaitUntilSubscribeStreamRequest()

      dataConnect.close()
      clientCollector.awaitComplete()

      serverCollector.cancelAndIgnoreRemainingEvents()
      clientCollector.cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `connection is closed after the last subscriber unsubscribes`() = runTest {
    val subscriptionParameters =
      distinctOperationNameVariablesPairWithRepeatedComponentsArb().sampleList(10)
    val server = runningInProcessDataConnectServer()
    val dataConnect = dataConnect(server)
    val subscriptions = buildList {
      subscriptionParameters.forEach {
        add(querySubscription(dataConnect, it.operationName, it.variables))
      }
      repeat(size / 2) { add(get(it)) } // Add some duplicate subscriptions
      shuffle(rs.random)
    }
    val testData = testDataArb().let { arb -> List(subscriptions.size) { arb.next(rs) } }

    turbineScope {
      val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
      val clientCollectors =
        subscriptions.mapIndexed { index, subscription ->
          subscription.flow.testIn(backgroundScope, name = "clientCollector$index")
        }

      val respondJob =
        backgroundScope.launch {
          val responseObserver = serverCollector.awaitConnectRpcStarted().responseObserver
          val testDataIterator = testData.iterator()
          while (true) {
            val streamRequest = serverCollector.awaitUntilStreamRequest().streamRequest
            if (streamRequest.hasSubscribe() || streamRequest.hasResume()) {
              responseObserver.onNext(streamRequest.requestId, testDataIterator.next())
            }
          }
        }
      clientCollectors.forEach { clientCollector -> clientCollector.awaitItem() }
      respondJob.cancelAndJoin()

      clientCollectors.forEach { it.cancelAndIgnoreRemainingEvents() }
      serverCollector.awaitUntilClientClosesConnection()

      serverCollector.cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `connection is re-established after being closed due to unsubscriptions`() = runTest {
    val server = runningInProcessDataConnectServer()
    val dataConnect = dataConnect(server)
    val subscription = querySubscription(dataConnect)

    turbineScope {
      val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
      val clientCollector1 = subscription.flow.testIn(backgroundScope, name = "clientCollector1")
      val connection1 = serverCollector.awaitConnectRpcStarted()
      serverCollector.awaitUntilInitStreamRequest()
      clientCollector1.cancelAndIgnoreRemainingEvents()
      serverCollector.awaitUntilClientClosesConnection()

      val clientCollector2 = subscription.flow.testIn(backgroundScope, name = "clientCollector2")
      val connection2 = serverCollector.awaitConnectRpcStarted()
      connection2.connectionId shouldNotBe connection1.connectionId
      val requestId = serverCollector.awaitUntilSubscribeStreamRequest().streamRequest.requestId
      val testData = testDataArb().sample()
      connection2.responseObserver.onNext(requestId, testData)
      val streamResponse = clientCollector2.awaitItem()
      streamResponse.result.shouldBeSuccess().data shouldBe testData

      clientCollector2.cancelAndIgnoreRemainingEvents()
      serverCollector.cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `later flow subscriptions do not return data from previous subscribe result`() = runTest {
    val server = runningInProcessDataConnectServer()
    val dataConnect = dataConnect(server)
    val subscription = querySubscription(dataConnect)
    val testDataArb = testDataArb()

    turbineScope {
      val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
      val clientCollector1 = subscription.flow.testIn(backgroundScope, name = "clientCollector1")

      val responseSender = serverCollector.awaitResponseSender()
      val requestId = serverCollector.awaitUntilSubscribeStreamRequest().streamRequest.requestId
      val testData1 = testDataArb.sample()
      responseSender.onNext(requestId, testData1)
      clientCollector1.awaitItem().result.shouldBeSuccess().data shouldBe testData1

      val clientCollector2 = subscription.flow.testIn(backgroundScope, name = "clientCollector2")
      serverCollector.awaitUntilResumeStreamRequest()
      val testData2 = testDataArb.sample()
      responseSender.onNext(requestId, testData2)
      clientCollector1.awaitItem().result.shouldBeSuccess().data shouldBe testData2
      clientCollector2.awaitItem().result.shouldBeSuccess().data shouldBe testData2

      clientCollector2.cancelAndIgnoreRemainingEvents()
      clientCollector1.cancelAndIgnoreRemainingEvents()
      serverCollector.cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `later flow subscriptions do not return data from in-flight subscribe`() = runTest {
    assumeTrue("This behavior should be fixed to ensure read-after-write semantics", false)

    val server = runningInProcessDataConnectServer()
    val dataConnect = dataConnect(server)
    val subscription = querySubscription(dataConnect)
    val testDataArb = testDataArb()

    turbineScope {
      val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
      val clientCollector1 = subscription.flow.testIn(backgroundScope, name = "clientCollector1")
      val responseSender = serverCollector.awaitResponseSender()
      val requestId = serverCollector.awaitUntilSubscribeStreamRequest().streamRequest.requestId
      val clientCollector2 = subscription.flow.testIn(backgroundScope, name = "clientCollector2")

      val testData1 = testDataArb.sample()
      responseSender.onNext(requestId, testData1)
      clientCollector1.awaitItem().result.shouldBeSuccess().data shouldBe testData1

      serverCollector.awaitUntilResumeStreamRequest()
      val testData2 = testDataArb.sample()
      responseSender.onNext(requestId, testData2)
      clientCollector1.awaitItem().result.shouldBeSuccess().data shouldBe testData2
      clientCollector2.awaitItem().result.shouldBeSuccess().data shouldBe testData2

      clientCollector2.cancelAndIgnoreRemainingEvents()
      clientCollector1.cancelAndIgnoreRemainingEvents()
      serverCollector.cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `flow retries if server completes the RPC gracefully mid-stream`() = runTest {
    testFlowReconnectsUponConnectionClosure { it.onCompleted() }
  }

  @Test
  fun `flow retries if server aborts the RPC mid-stream with StatusException`() = runTest {
    testFlowReconnectsUponConnectionClosureWithGrpcFailureStatusCode {
      StatusException(it.toStatus())
    }
  }

  @Test
  fun `flow retries if server aborts the RPC mid-stream with StatusRuntimeException`() = runTest {
    testFlowReconnectsUponConnectionClosureWithGrpcFailureStatusCode {
      StatusRuntimeException(it.toStatus())
    }
  }

  @Test
  fun `flow retries if server aborts with a non-grpc Exception`() = runTest {
    class TestException(message: String) : Exception(message)
    testFlowReconnectsUponConnectionClosure {
      it.onError(TestException(Arb.dataConnect.string().sample()))
    }
  }

  @Test
  fun `flow retries if server connection is lost`() = runTest {
    val server1 = runningInProcessDataConnectServer()
    val server2 = InProcessDataConnectGrpcStreamingServer()
    cleanups.register(server2)
    val dataConnect = dataConnect(server1)
    val subscription = querySubscription(dataConnect)
    val testData = testDataArb().sample()

    turbineScope {
      val server1Collector = server1.events.testIn(backgroundScope, name = "server1Collector")
      val server2Collector = server2.events.testIn(backgroundScope, name = "server2Collector")
      val clientCollector = subscription.flow.testIn(backgroundScope, name = "clientCollector1")
      server1Collector.awaitConnectRpcStarted()
      server1Collector.awaitUntilSubscribeStreamRequest()
      server1.close()
      server2.open(server1.port)

      server2Collector.awaitResponseSender().let { responseSender ->
        val requestId = server2Collector.awaitUntilSubscribeStreamRequest().streamRequest.requestId
        responseSender.onNext(requestId, testData)
        clientCollector.awaitItem().result.shouldBeSuccess().data shouldBe testData
      }

      clientCollector.cancelAndIgnoreRemainingEvents()
      server2Collector.cancelAndIgnoreRemainingEvents()
      server1Collector.cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `flow fails with AuthUidChangedException if auth uid changes mid-stream`() = runTest {
    val server = runningInProcessDataConnectServer()

    checkAll(
      propTestConfig,
      Arb.dataConnect.authUid().orNull(nullProbability = 0.3).distinctPair(),
      Arb.dataConnect.authToken().distinctPair(),
    ) { (authUid1, authUid2), (authToken1, authToken2) ->
      check(authUid1 != authUid2)
      check(authToken1 != authToken2)

      val tokenUidPair1 = tokenUidPairOrNullIfUidNull(authToken1, authUid1?.string)
      val tokenUidPair2 = tokenUidPairOrNullIfUidNull(authToken2, authUid2?.string)
      val authProvider = LoggedInMultiTokenAndUidAuthProvider(listOf(tokenUidPair1, tokenUidPair2))

      val dataConnect =
        dataConnect(
          serverLocalBindPort = server.port,
          deferredAuthProvider = ImmediateDeferred(authProvider)
        )
      try {
        dataConnect.awaitAuthReady()

        val subscription = querySubscription(dataConnect)
        turbineScope {
          val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
          val clientCollector = subscription.flow.testIn(backgroundScope, name = "clientCollector")

          serverCollector.awaitResponseSender()
          serverCollector.awaitUntilSubscribeStreamRequest()

          // Trigger the auth token update
          checkNotNull(authProvider.idTokenListener)
            .onIdTokenChanged(InternalTokenResult(authToken2))

          // The flow should throw AuthUidChangedException and terminate
          val exception = clientCollector.awaitError()
          exception.shouldBeInstanceOf<AuthUidChangedException>()
          exception.message shouldContainWithNonAbuttingTextIgnoringCase "Firebase Auth UID changed"
          exception.message shouldContainWithNonAbuttingText "cgvra2bwg3"
          exception.message shouldContainWithNonAbuttingText authUid1.toString()
          exception.message shouldContainWithNonAbuttingText authUid2.toString()

          serverCollector.cancelAndIgnoreRemainingEvents()
          clientCollector.cancelAndIgnoreRemainingEvents()
        }
      } finally {
        dataConnect.suspendingClose()
      }
    }
  }

  @Test
  fun `flow fails with AuthUidChangedException if auth uid changes during reconnection`() =
    runTest {
      val server = runningInProcessDataConnectServer()

      checkAll(
        propTestConfig,
        Arb.dataConnect.authUid().orNull(nullProbability = 0.3).distinctPair(),
        Arb.dataConnect.authToken().distinctPair(),
      ) { (authUid1, authUid2), (authToken1, authToken2) ->
        check(authUid1 != authUid2)
        check(authToken1 != authToken2)

        val tokenUidPair1 = tokenUidPairOrNullIfUidNull(authToken1, authUid1?.string)
        val tokenUidPair2 = tokenUidPairOrNullIfUidNull(authToken2, authUid2?.string)
        val authProvider =
          LoggedInMultiTokenAndUidAuthProvider(listOf(tokenUidPair1, tokenUidPair2))

        val dataConnect =
          dataConnect(
            serverLocalBindPort = server.port,
            deferredAuthProvider = ImmediateDeferred(authProvider)
          )
        try {
          dataConnect.awaitAuthReady()

          val subscription = querySubscription(dataConnect)
          turbineScope {
            val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
            val clientCollector =
              subscription.flow.testIn(backgroundScope, name = "clientCollector")

            val responseSender = serverCollector.awaitResponseSender()
            serverCollector.awaitUntilSubscribeStreamRequest()

            // Close the connection from the server to force a reconnection attempt
            responseSender.onCompleted()

            // The flow should throw AuthUidChangedException and terminate
            val exception = clientCollector.awaitError()

            // The flow should throw AuthUidChangedException and terminate
            exception.shouldBeInstanceOf<AuthUidChangedException>()
            exception.message shouldContainWithNonAbuttingTextIgnoringCase
              "Firebase Auth UID changed"
            exception.message shouldContainWithNonAbuttingText "ytd7yf2geh"
            exception.message shouldContainWithNonAbuttingText authUid1.toString()
            exception.message shouldContainWithNonAbuttingText authUid2.toString()

            serverCollector.cancelAndIgnoreRemainingEvents()
            clientCollector.cancelAndIgnoreRemainingEvents()
          }
        } finally {
          dataConnect.suspendingClose()
        }
      }
    }

  @Test
  fun `flow fails with AuthUidChangedException if auth uid changes concurrently with reconnection`() =
    runTest {
      val server = runningInProcessDataConnectServer()

      // Make sure that AuthUidChangedException is thrown even if the sequence number of the pending
      // reconnect token is stale; otherwise, auth uid changes could slip through.
      val postReconnectSequenceNumberArb = Arb.of(nextSequenceNumber(), Long.MAX_VALUE)

      checkAll(
        propTestConfig,
        postReconnectSequenceNumberArb,
        Arb.dataConnect.authUid().orNull(nullProbability = 0.3).distinctPair(),
        Arb.dataConnect.authToken().distinctPair(),
      ) { postReconnectSequenceNumber, (authUid1, authUid2), (authToken1, authToken2) ->
        check(authUid1 != authUid2)
        check(authToken1 != authToken2)

        val authProvider =
          if (authUid1 == null) {
            NotLoggedInInternalAuthProvider
          } else {
            LoggedInInternalAuthProvider(token = authToken1, uid = authUid1.string)
          }

        val postReconnectGetAuthTokenResult =
          if (authUid2 == null) {
            GetAuthTokenResult(null, null)
          } else {
            GetAuthTokenResult(authToken2, authUid2)
          }
        val postReconnectPendingAuthToken =
          SequencedReference(postReconnectSequenceNumber, postReconnectGetAuthTokenResult)

        val dataConnect =
          dataConnect(
            serverLocalBindPort = server.port,
            deferredAuthProvider = ImmediateDeferred(authProvider)
          )
        try {
          dataConnect.awaitAuthReady()

          val subscription = querySubscription(dataConnect)
          turbineScope {
            val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
            val clientCollector =
              subscription.flow.testIn(backgroundScope, name = "clientCollector")

            val responseSender = serverCollector.awaitResponseSender()
            serverCollector.awaitUntilSubscribeStreamRequest()

            setReconnectPendingAuthTokenForTesting(postReconnectPendingAuthToken)
            val exception =
              try {
                // Close the connection from the server to force a reconnection attempt
                responseSender.onCompleted()

                // The flow should throw AuthUidChangedException and terminate
                clientCollector.awaitError()
              } finally {
                unsetReconnectPendingAuthTokenForTesting(postReconnectPendingAuthToken)
              }

            // The flow should throw AuthUidChangedException and terminate
            exception.shouldBeInstanceOf<AuthUidChangedException>()
            exception.message shouldContainWithNonAbuttingTextIgnoringCase
              "Firebase Auth UID changed"
            exception.message shouldContainWithNonAbuttingText "cgvra2bwg3"
            exception.message shouldContainWithNonAbuttingText authUid1.toString()
            exception.message shouldContainWithNonAbuttingText authUid2.toString()

            serverCollector.cancelAndIgnoreRemainingEvents()
            clientCollector.cancelAndIgnoreRemainingEvents()
          }
        } finally {
          dataConnect.suspendingClose()
        }
      }
    }

  @Test
  fun `auth logged in initial connection header`() = runTest {
    val server = runningInProcessDataConnectServer()

    checkAll(
      propTestConfig,
      Arb.dataConnect.loggedInAuthProvider(),
      Arb.dataConnect.deferredAppCheckProvider(),
    ) { loggedInInternalAuthProvider, deferredAppCheckProvider ->
      testDataConnectInitialHeader(
        server,
        deferredAuthProvider = ImmediateDeferred(loggedInInternalAuthProvider),
        deferredAppCheckProvider = deferredAppCheckProvider,
        awaitAuthReady = true,
        awaitAppCheckReady = false,
        header = authTokenGrpcMetadataKey,
        expectedHeaderValue = loggedInInternalAuthProvider.token,
      )
    }
  }

  @Test
  fun `auth not logged in initial connection header`() =
    testAuthHeaderOmittedOnInitialConnection(
      awaitAuthReady = true,
      ImmediateDeferred(NotLoggedInInternalAuthProvider),
    )

  @Test
  fun `auth provider not available initial connection header`() =
    testAuthHeaderOmittedOnInitialConnection(awaitAuthReady = false, UnavailableDeferred())

  private fun testAuthHeaderOmittedOnInitialConnection(
    awaitAuthReady: Boolean,
    deferredAuthProvider: com.google.firebase.inject.Deferred<InternalAuthProvider>
  ) = runTest {
    val server = runningInProcessDataConnectServer()
    checkAll(
      propTestConfig,
      Arb.dataConnect.deferredAppCheckProvider(),
    ) { deferredAppCheckProvider ->
      testDataConnectInitialHeader(
        server,
        deferredAuthProvider = deferredAuthProvider,
        deferredAppCheckProvider = deferredAppCheckProvider,
        awaitAuthReady = awaitAuthReady,
        awaitAppCheckReady = false,
        header = authTokenGrpcMetadataKey,
        expectedHeaderValue = null,
      )
    }
  }

  @Test
  fun `appCheck token non-null initial connection header`() = runTest {
    val server = runningInProcessDataConnectServer()

    checkAll(
      propTestConfig,
      Arb.dataConnect.appCheckProvider(),
      Arb.dataConnect.deferredAuthProvider(),
    ) { appCheckTokenProvider, deferredAuthProvider ->
      testDataConnectInitialHeader(
        server,
        deferredAuthProvider = deferredAuthProvider,
        deferredAppCheckProvider = ImmediateDeferred(appCheckTokenProvider),
        awaitAuthReady = false,
        awaitAppCheckReady = true,
        header = appCheckTokenGrpcMetadataKey,
        expectedHeaderValue = appCheckTokenProvider.token,
      )
    }
  }

  @Test
  fun `appCheck provider not available initial connection header`() = runTest {
    val server = runningInProcessDataConnectServer()
    checkAll(
      propTestConfig,
      Arb.dataConnect.deferredAuthProvider(),
    ) { deferredAuthProvider ->
      testDataConnectInitialHeader(
        server,
        deferredAuthProvider = deferredAuthProvider,
        deferredAppCheckProvider = UnavailableDeferred(),
        awaitAuthReady = false,
        awaitAppCheckReady = false,
        header = appCheckTokenGrpcMetadataKey,
        expectedHeaderValue = null,
      )
    }
  }

  private suspend fun TestScope.testDataConnectInitialHeader(
    server: InProcessDataConnectGrpcStreamingServer,
    deferredAuthProvider: com.google.firebase.inject.Deferred<InternalAuthProvider>,
    deferredAppCheckProvider: com.google.firebase.inject.Deferred<InteropAppCheckTokenProvider>,
    awaitAuthReady: Boolean,
    awaitAppCheckReady: Boolean,
    header: Metadata.Key<String>,
    expectedHeaderValue: String?,
  ) {
    val dataConnect =
      dataConnect(
        serverLocalBindPort = server.port,
        deferredAuthProvider = deferredAuthProvider,
        deferredAppCheckProvider = deferredAppCheckProvider,
      )

    try {
      if (awaitAuthReady) {
        dataConnect.awaitAuthReady()
      }
      if (awaitAppCheckReady) {
        dataConnect.awaitAppCheckReady()
      }

      val subscription = querySubscription(dataConnect)
      turbineScope {
        val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
        val clientCollector = subscription.flow.testIn(backgroundScope, name = "clientCollector")

        val callEvent = serverCollector.awaitCall()
        callEvent.headers.get(header) shouldBe expectedHeaderValue

        serverCollector.cancelAndIgnoreRemainingEvents()
        clientCollector.cancelAndIgnoreRemainingEvents()
      }
    } finally {
      dataConnect.suspendingClose()
    }
  }

  @Test
  fun `auth token non-null reconnection header`() = runTest {
    val server = runningInProcessDataConnectServer()

    checkAll(
      propTestConfig,
      Arb.dataConnect.loggedInMultiTokenAuthProvider(count = 2),
      Arb.dataConnect.deferredAppCheckProvider(),
    ) { authProvider, deferredAppCheckProvider ->
      testDataConnectReconnectHeader(
        server,
        deferredAuthProvider = ImmediateDeferred(authProvider),
        deferredAppCheckProvider = deferredAppCheckProvider,
        awaitAuthReady = true,
        awaitAppCheckReady = false,
        header = authTokenGrpcMetadataKey,
        expectedHeaderValue1 = authProvider.tokens[0],
        expectedHeaderValue2 = authProvider.tokens[1],
      )
    }
  }

  @Test
  fun `auth token null reconnection header`() =
    testAuthHeaderOmittedOnReconnection(
      awaitAuthReady = true,
      ImmediateDeferred(NotLoggedInInternalAuthProvider),
    )

  @Test
  fun `auth provider not available reconnection header`() =
    testAuthHeaderOmittedOnReconnection(awaitAuthReady = false, UnavailableDeferred())

  private fun testAuthHeaderOmittedOnReconnection(
    awaitAuthReady: Boolean,
    deferredAuthProvider: com.google.firebase.inject.Deferred<InternalAuthProvider>
  ) = runTest {
    val server = runningInProcessDataConnectServer()
    checkAll(
      propTestConfig,
      Arb.dataConnect.deferredAppCheckProvider(),
    ) { deferredAppCheckProvider ->
      testDataConnectReconnectHeader(
        server,
        deferredAuthProvider = deferredAuthProvider,
        deferredAppCheckProvider = deferredAppCheckProvider,
        awaitAuthReady = awaitAuthReady,
        awaitAppCheckReady = false,
        header = authTokenGrpcMetadataKey,
        expectedHeaderValue1 = null,
        expectedHeaderValue2 = null,
      )
    }
  }

  @Test
  fun `app check token reconnection header`() = runTest {
    val server = runningInProcessDataConnectServer()

    checkAll(
      propTestConfig,
      Arb.dataConnect.deferredAuthProvider(),
      Arb.dataConnect.appCheckMultiTokenProvider(count = 2),
    ) { deferredAuthProvider, appCheckProvider ->
      testDataConnectReconnectHeader(
        server,
        deferredAuthProvider = deferredAuthProvider,
        deferredAppCheckProvider = ImmediateDeferred(appCheckProvider),
        awaitAuthReady = false,
        awaitAppCheckReady = true,
        header = appCheckTokenGrpcMetadataKey,
        expectedHeaderValue1 = appCheckProvider.tokens[0],
        expectedHeaderValue2 = appCheckProvider.tokens[1],
      )
    }
  }

  @Test
  fun `appCheck provider not available reconnection header`() = runTest {
    val server = runningInProcessDataConnectServer()
    checkAll(
      propTestConfig,
      Arb.dataConnect.deferredAuthProvider(),
    ) { deferredAuthProvider ->
      testDataConnectReconnectHeader(
        server,
        deferredAuthProvider = deferredAuthProvider,
        deferredAppCheckProvider = UnavailableDeferred(),
        awaitAuthReady = false,
        awaitAppCheckReady = false,
        header = appCheckTokenGrpcMetadataKey,
        expectedHeaderValue1 = null,
        expectedHeaderValue2 = null,
      )
    }
  }

  private suspend fun TestScope.testDataConnectReconnectHeader(
    server: InProcessDataConnectGrpcStreamingServer,
    deferredAuthProvider: com.google.firebase.inject.Deferred<InternalAuthProvider>,
    deferredAppCheckProvider: com.google.firebase.inject.Deferred<InteropAppCheckTokenProvider>,
    awaitAuthReady: Boolean,
    awaitAppCheckReady: Boolean,
    header: Metadata.Key<String>,
    expectedHeaderValue1: String?,
    expectedHeaderValue2: String?,
  ) {
    val dataConnect =
      dataConnect(
        serverLocalBindPort = server.port,
        deferredAuthProvider = deferredAuthProvider,
        deferredAppCheckProvider = deferredAppCheckProvider,
      )

    try {
      if (awaitAuthReady) {
        dataConnect.awaitAuthReady()
      }
      if (awaitAppCheckReady) {
        dataConnect.awaitAppCheckReady()
      }

      val subscription = querySubscription(dataConnect)
      turbineScope {
        val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
        val clientCollector = subscription.flow.testIn(backgroundScope, name = "clientCollector")

        val originalHeaders = serverCollector.awaitCall().headers
        check(originalHeaders.get(header) == expectedHeaderValue1)

        val responseSender = serverCollector.awaitResponseSender()
        serverCollector.awaitUntilSubscribeStreamRequest()

        // Close the connection from the server to force a reconnection attempt
        responseSender.onCompleted()

        // Verify that reconnection attempt specifies the correct header value.
        val reconnectionHeaders = serverCollector.awaitCall().headers
        reconnectionHeaders.get(header) shouldBe expectedHeaderValue2

        serverCollector.cancelAndIgnoreRemainingEvents()
        clientCollector.cancelAndIgnoreRemainingEvents()
      }
    } finally {
      dataConnect.suspendingClose()
    }
  }

  private suspend fun TestScope.testFlowReconnectsUponConnectionClosureWithGrpcFailureStatusCode(
    createException: (Status.Code) -> Throwable
  ) {
    failureGrpcStatusCodes.forEach { code ->
      withClue("code=$code") {
        val exception = createException(code)
        testFlowReconnectsUponConnectionClosure { it.onError(exception) }
      }
    }
  }

  private suspend fun TestScope.testFlowReconnectsUponConnectionClosure(
    abort: (StreamObserver<StreamResponse>) -> Unit
  ) {
    val server = runningInProcessDataConnectServer()
    val dataConnect = dataConnect(server)
    val subscription = querySubscription(dataConnect)
    val testData = testDataArb().sample()

    turbineScope {
      val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
      val clientCollector = subscription.flow.testIn(backgroundScope, name = "clientCollector1")
      serverCollector.awaitResponseSender().let { responseSender ->
        serverCollector.awaitUntilSubscribeStreamRequest()
        abort(responseSender)
      }

      serverCollector.awaitResponseSender().let { responseSender ->
        val requestId = serverCollector.awaitUntilSubscribeStreamRequest().streamRequest.requestId
        responseSender.onNext(requestId, testData)
        clientCollector.awaitItem().result.shouldBeSuccess().data shouldBe testData
      }

      clientCollector.cancelAndIgnoreRemainingEvents()
      serverCollector.cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `auth token change mid-stream sends update`() = runTest {
    val server = runningInProcessDataConnectServer()

    checkAll(
      propTestConfig,
      Arb.dataConnect.authUid(),
      Arb.dataConnect.authToken().distinctPair(),
    ) { authUid, (authToken1, authToken2) ->
      check(authToken1 != authToken2)

      val authProvider =
        LoggedInMultiTokenAndUidAuthProvider(
          listOf(authToken1, authToken2).map { tokenUidPairOrNullIfUidNull(it, authUid.string) }
        )

      val dataConnect =
        dataConnect(
          serverLocalBindPort = server.port,
          deferredAuthProvider = ImmediateDeferred(authProvider)
        )
      try {
        dataConnect.awaitAuthReady()

        val subscription = querySubscription(dataConnect)
        turbineScope {
          val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
          val clientCollector = subscription.flow.testIn(backgroundScope, name = "clientCollector")

          serverCollector.awaitResponseSender()
          serverCollector.awaitUntilSubscribeStreamRequest()

          // Trigger the auth token update
          checkNotNull(authProvider.idTokenListener)
            .onIdTokenChanged(InternalTokenResult(authToken2))

          val authUpdateRequest = serverCollector.awaitUntilStreamRequest()
          authUpdateRequest.streamRequest.asClue {
            it.requestId shouldStartWith "auth"
            it.headersMap["x-firebase-auth-token"] shouldBe authToken2
          }

          serverCollector.cancelAndIgnoreRemainingEvents()
          clientCollector.cancelAndIgnoreRemainingEvents()
        }
      } finally {
        dataConnect.suspendingClose()
      }
    }
  }

  @Test
  fun `auth token change concurrent with reconnection sends update`() = runTest {
    val server = runningInProcessDataConnectServer()

    checkAll(
      propTestConfig,
      Arb.dataConnect.authUid(),
      Arb.dataConnect.authToken().distinctPair(),
    ) { authUid, (authToken1, authToken2) ->
      check(authToken1 != authToken2)

      val authProvider = LoggedInInternalAuthProvider(token = authToken1, uid = authUid.string)
      val postReconnectGetAuthTokenResult = GetAuthTokenResult(authToken2, authUid)
      val postReconnectPendingAuthToken =
        SequencedReference(Long.MAX_VALUE, postReconnectGetAuthTokenResult)

      val dataConnect =
        dataConnect(
          serverLocalBindPort = server.port,
          deferredAuthProvider = ImmediateDeferred(authProvider)
        )
      try {
        dataConnect.awaitAuthReady()

        val subscription = querySubscription(dataConnect)
        turbineScope {
          val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
          val clientCollector = subscription.flow.testIn(backgroundScope, name = "clientCollector")

          val responseSender = serverCollector.awaitResponseSender()
          serverCollector.awaitUntilSubscribeStreamRequest()

          setReconnectPendingAuthTokenForTesting(postReconnectPendingAuthToken)
          try {
            // Close the connection from the server to force a reconnection attempt
            responseSender.onCompleted()

            val authUpdateRequest =
              serverCollector.awaitUntilStreamRequest { it.requestId.startsWith("auth") }
            authUpdateRequest.streamRequest.asClue {
              it.headersMap["x-firebase-auth-token"] shouldBe authToken2
            }
          } finally {
            unsetReconnectPendingAuthTokenForTesting(postReconnectPendingAuthToken)
          }

          serverCollector.cancelAndIgnoreRemainingEvents()
          clientCollector.cancelAndIgnoreRemainingEvents()
        }
      } finally {
        dataConnect.suspendingClose()
      }
    }
  }

  @Test
  fun `auth token change concurrent with reconnection but stale does NOT send update`() = runTest {
    val server = runningInProcessDataConnectServer()

    checkAll(
      propTestConfig,
      Arb.dataConnect.authUid(),
      Arb.dataConnect.authToken().distinctPair(),
    ) { authUid, (authToken1, authToken2) ->
      check(authToken1 != authToken2)

      val authProvider = LoggedInInternalAuthProvider(token = authToken1, uid = authUid.string)
      val postReconnectGetAuthTokenResult = GetAuthTokenResult(authToken2, authUid)
      val staleSequenceNumber = nextSequenceNumber()
      val postReconnectPendingAuthToken =
        SequencedReference(staleSequenceNumber, postReconnectGetAuthTokenResult)

      val dataConnect =
        dataConnect(
          serverLocalBindPort = server.port,
          deferredAuthProvider = ImmediateDeferred(authProvider)
        )
      try {
        dataConnect.awaitAuthReady()

        val subscription = querySubscription(dataConnect)
        turbineScope {
          val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
          val clientCollector = subscription.flow.testIn(backgroundScope, name = "clientCollector")

          val responseSender = serverCollector.awaitResponseSender()
          serverCollector.awaitUntilSubscribeStreamRequest()

          setReconnectPendingAuthTokenForTesting(postReconnectPendingAuthToken)
          try {
            // Close the connection from the server to force a reconnection attempt
            responseSender.onCompleted()

            val nonAuthUpdateRequest = serverCollector.awaitUntilStreamRequest()
            nonAuthUpdateRequest.streamRequest.asClue {
              it.requestId shouldNotStartWith "auth"
              it.headersMap.shouldBeEmpty()
            }
          } finally {
            unsetReconnectPendingAuthTokenForTesting(postReconnectPendingAuthToken)
          }

          serverCollector.cancelAndIgnoreRemainingEvents()
          clientCollector.cancelAndIgnoreRemainingEvents()
        }
      } finally {
        dataConnect.suspendingClose()
      }
    }
  }

  private fun runningInProcessDataConnectServer(): InProcessDataConnectGrpcStreamingServer {
    val server = InProcessDataConnectGrpcStreamingServer()
    cleanups.register(server)
    server.open()
    return server
  }

  private fun TestScope.dataConnect(
    server: InProcessDataConnectGrpcStreamingServer,
    idStringGenerator: IdStringGenerator? = null,
    deferredAuthProvider: com.google.firebase.inject.Deferred<InternalAuthProvider> =
      UnavailableDeferred(),
    deferredAppCheckProvider: com.google.firebase.inject.Deferred<InteropAppCheckTokenProvider> =
      UnavailableDeferred(),
  ): FirebaseDataConnectImpl =
    dataConnect(
      server.port,
      idStringGenerator,
      deferredAuthProvider,
      deferredAppCheckProvider,
    )

  private fun TestScope.dataConnect(
    serverLocalBindPort: Int? = null,
    idStringGenerator: IdStringGenerator? = null,
    deferredAuthProvider: com.google.firebase.inject.Deferred<InternalAuthProvider> =
      UnavailableDeferred(),
    deferredAppCheckProvider: com.google.firebase.inject.Deferred<InteropAppCheckTokenProvider> =
      UnavailableDeferred(),
  ): FirebaseDataConnectImpl {
    val executor = StandardTestDispatcher(testScheduler).asExecutor()

    val settings: DataConnectSettings =
      if (serverLocalBindPort === null) {
        Arb.dataConnect.dataConnectSettings().sample()
      } else {
        DataConnectSettings("localhost:$serverLocalBindPort", sslEnabled = false)
      }

    return FirebaseDataConnectImpl(
      context =
        mockk(name = "FirebaseDataConnectImpl.context") {
          every { getSystemService(CONNECTIVITY_SERVICE) } returns
            mockk<ConnectivityManager>(relaxed = true)
        },
      app = firebaseAppFactory.newInstance(),
      projectId = Arb.dataConnect.projectId().sample(),
      config = Arb.dataConnect.connectorConfig().sample(),
      blockingExecutor = executor,
      nonBlockingExecutor = executor,
      deferredAuthProvider = deferredAuthProvider,
      deferredAppCheckProvider = deferredAppCheckProvider,
      creator = mockk(name = "FirebaseDataConnectImpl.creator", relaxed = true),
      settings = settings,
      idStringGenerator = idStringGenerator ?: IdStringGenerator(Random.Default),
    )
  }

  private fun idStringGeneratorThatGeneratesRequestId(requestId: String): IdStringGenerator =
    idStringGeneratorThatGeneratesRequestIds(requestId)

  private fun idStringGeneratorThatGeneratesRequestIds(
    requestIds: Collection<String>
  ): IdStringGenerator = idStringGeneratorThatGeneratesRequestIds(*requestIds.toTypedArray())

  private fun idStringGeneratorThatGeneratesRequestIds(
    vararg requestIds: String
  ): IdStringGenerator =
    spyk(IdStringGenerator(Random.Default), name = "IdStringGenerator for ${testName.methodName}") {
      every { next("sub") }
        .returnsMany(requestIds.toList())
        .andThenThrows(
          IllegalStateException(
            "I only know how to generate ${requestIds.size} requestIds, " +
              "and I've already generated all of them [gpap8mjgg5]"
          )
        )
    }

  private fun TestScope.querySubscription(
    dataConnect: FirebaseDataConnectImpl? = null,
    operationName: String? = null,
    variables: TestVariables? = null,
  ): QuerySubscriptionImpl<TestData, TestVariables> =
    queryRef(dataConnect, operationName, variables).subscribe()

  private fun TestScope.queryRef(
    dataConnect: FirebaseDataConnectImpl? = null,
    operationName: String? = null,
    variables: TestVariables? = null,
  ): QueryRefImpl<TestData, TestVariables> =
    QueryRefImpl(
      dataConnect = dataConnect ?: dataConnect(),
      operationName = operationName ?: "opName_${alphabeticStringArb().sample()}",
      variables = variables ?: testVariablesArb().sample(),
      dataDeserializer = serializer<TestData>(),
      variablesSerializer = serializer(),
      callerSdkType = Arb.enum<CallerSdkType>().sample(),
      dataSerializersModule = Arb.dataConnect.serializersModule().sample(),
      variablesSerializersModule = Arb.dataConnect.serializersModule().sample(),
    )

  private fun <T> Arb<T>.sample(): T = sample(rs).value

  private fun <T> Arb<T>.sampleList(size: Int): List<T> = List(size) { sample() }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 50,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )

@Serializable private data class TestVariables(val stringValue: String)

@Serializable private data class TestData(val intValue: Int)

private fun alphabeticStringArb(): Arb<String> = Arb.string(0..5, Codepoint.az())

private fun testVariablesArb(stringValue: Arb<String> = alphabeticStringArb()): Arb<TestVariables> =
  stringValue.map(::TestVariables)

private fun testDataArb(intValue: Arb<Int> = Arb.int()): Arb<TestData> = intValue.map(::TestData)

suspend fun ReceiveTurbine<InProcessDataConnectGrpcStreamingServer.Event>
  .awaitUntilClientClosesConnection() = awaitUntilStatusExceptionReceived(Status.Code.CANCELLED)

private fun StreamRequest.shouldBeSubscribeRequestFor(
  queryRef: QueryRef<TestData, TestVariables>,
  expectedRequestId: String
): Unit =
  withClue("StreamRequest=${print().value}") {
    withClue("requestId") { requestId shouldBe expectedRequestId }
    withClue("requestKindCase") { requestKindCase shouldBe RequestKindCase.SUBSCRIBE }
    withClue("operationName") { subscribe.operationName shouldBe queryRef.operationName }
    withClue("variables") { subscribe.variables shouldBe encodeToStruct(queryRef.variables) }
  }

private fun distinctRequestIdArb(): Arb<String> =
  @OptIn(DelicateKotest::class) Arb.dataConnect.requestId().distinct()

/**
 * Creates and returns an [Arb] that generates operation name/variables pairs such that both
 * operation names and variables are recycled but _never_ in the same combination.
 *
 * Informally, the returned Arb generates a sequence like this:
 * 1. (op1, vars1)
 * 2. (op2, vars1)
 * 3. (op2, vars2)
 * 4. (op3, vars1)
 * 5. (op3, vars2)
 * 6. (op3, vars3)
 * 7. and so on...
 *
 * Therefore, tests are recommended to generate a bunch and then shuffle their order.
 */
private fun distinctOperationNameVariablesPairWithRepeatedComponentsArb(
  operationName: Arb<String> =
    @OptIn(DelicateKotest::class) Arb.dataConnect.operationName().distinct(),
  variables: Arb<TestVariables> = @OptIn(DelicateKotest::class) testVariablesArb().distinct(),
): Arb<OperationNameVariablesPair<TestVariables>> {
  val producedOperationNames = mutableSetOf<String>()
  val producedVariables = mutableListOf<TestVariables>()
  var currentVariables = producedVariables.iterator()

  return arbitrary {
    if (!currentVariables.hasNext()) {
      val newOperationName = operationName.bind()
      check(newOperationName !in producedOperationNames)
      val newVariables = variables.bind()
      check(newVariables !in producedVariables)
      producedOperationNames.add(newOperationName)
      producedVariables.add(newVariables)
      currentVariables = producedVariables.iterator()
    }

    val operationName = producedOperationNames.last()
    val variables = currentVariables.next()
    OperationNameVariablesPair(operationName, variables)
  }
}

private fun StreamObserver<StreamResponse>.onNext(requestId: String, data: TestData) {
  onNext(StreamResponse.newBuilder().setRequestId(requestId).setData(encodeToStruct(data)).build())
}

private val failureGrpcStatusCodes: List<Status.Code> =
  Status.Code.entries.filterNot { it == Status.Code.OK }
