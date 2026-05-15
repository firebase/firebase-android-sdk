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

@file:OptIn(ExperimentalRealtimeQueries::class)

package com.google.firebase.dataconnect.core

import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.google.firebase.dataconnect.DataConnectSettings
import com.google.firebase.dataconnect.ExperimentalRealtimeQueries
import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.testutil.CleanupsRule
import com.google.firebase.dataconnect.testutil.FirebaseAppUnitTestingRule
import com.google.firebase.dataconnect.testutil.InProcessDataConnectGrpcStreamingServer
import com.google.firebase.dataconnect.testutil.InProcessDataConnectGrpcStreamingServer.Event.CompletedReceived
import com.google.firebase.dataconnect.testutil.InProcessDataConnectGrpcStreamingServer.Event.ConnectRpcStarted
import com.google.firebase.dataconnect.testutil.InProcessDataConnectGrpcStreamingServer.Event.ErrorReceived
import com.google.firebase.dataconnect.testutil.InProcessDataConnectGrpcStreamingServer.Event.StreamRequestReceived
import com.google.firebase.dataconnect.testutil.OperationNameVariablesPair
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.SuspendingCountDownLatch
import com.google.firebase.dataconnect.testutil.TurbinePredicateResult
import com.google.firebase.dataconnect.testutil.UnavailableDeferred
import com.google.firebase.dataconnect.testutil.awaitUntilItem
import com.google.firebase.dataconnect.testutil.awaitUntilItemIsInstance
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.util.IdStringGenerator
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToStruct
import google.firebase.dataconnect.proto.StreamRequest
import google.firebase.dataconnect.proto.StreamRequest.RequestKindCase
import google.firebase.dataconnect.proto.StreamResponse
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.fail
import io.kotest.assertions.print.print
import io.kotest.assertions.withClue
import io.kotest.common.DelicateKotest
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.az
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlin.random.Random
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealtimeQuerySubscriptionImplUnitTest {

  @get:Rule val cleanups = CleanupsRule()
  @get:Rule val testName = TestName()
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

      val responseSender =
        serverCollector.awaitUntilItemIsInstance<_, ConnectRpcStarted>().responseObserver
      serverCollector.awaitUntilInitStreamRequest()
      serverCollector.awaitUntilStreamRequestWithRequestId(subscribeRequestId)

      val testDataArb = testDataArb()
      repeat(5) {
        val testData = testDataArb.sample()

        responseSender.onNext(
          StreamResponse.newBuilder()
            .setRequestId(subscribeRequestId)
            .setData(encodeToStruct(testData))
            .build()
        )

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
      val clientCollectors =
        subscriptions.mapIndexed { index, subscription ->
          subscription.flow.testIn(backgroundScope, name = "clientCollector$index")
        }

      serverCollector.awaitUntilInitStreamRequest()
      val subscribeRequest = serverCollector.awaitUntilSubscribeStreamRequest()
      subscribeRequest.streamRequest.requestId shouldBeIn requestIds
      subscribeRequest.streamRequest.requestKindCase shouldBe RequestKindCase.SUBSCRIBE
      repeat(subscriptions.size - 1) {
        val resumeRequest: StreamRequestReceived = serverCollector.awaitUntilItemIsInstance()
        resumeRequest.connectionId shouldBe subscribeRequest.connectionId
        resumeRequest.streamRequest.requestId shouldBe subscribeRequest.streamRequest.requestId
        resumeRequest.streamRequest.requestKindCase shouldBe RequestKindCase.RESUME
      }

      serverCollector.cancelAndIgnoreRemainingEvents()
      clientCollectors.forEach { it.cancelAndIgnoreRemainingEvents() }
    }
  }

  @Test
  fun `cancel message is sent after last unsubscription for a query`() = runTest {
    val operationName = Arb.dataConnect.operationName().sample()
    val variables = testVariablesArb().sample()
    val server = runningInProcessDataConnectServer()
    val dataConnect = dataConnect(server)
    val subscriptions = List(10) { querySubscription(dataConnect, operationName, variables) }

    turbineScope {
      val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
      val clientCollectors =
        subscriptions.mapIndexed { index, subscription ->
          subscription.flow.testIn(backgroundScope, name = "clientCollector$index")
        }

      serverCollector.awaitUntilInitStreamRequest()
      val subscribeRequest = serverCollector.awaitUntilSubscribeStreamRequest()
      check(subscribeRequest.streamRequest.hasSubscribe())

      clientCollectors.forEach { it.cancelAndIgnoreRemainingEvents() }

      val cancelRequest = serverCollector.awaitUntilCancelStreamRequest()
      cancelRequest.streamRequest.requestId shouldBe subscribeRequest.streamRequest.requestId

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
      serverCollector.awaitUntilStatusExceptionReceived(Status.Code.CANCELLED)

      serverCollector.cancelAndIgnoreRemainingEvents()
      clientCollector.cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `flows are cancelled when close() is called on dataConnect`() = runTest {
    val server = runningInProcessDataConnectServer()
    val dataConnect = dataConnect(server)
    val subscription = querySubscription(dataConnect)

    val latch = SuspendingCountDownLatch(2)
    launch {
      latch.countDown().await()
      println("zzyzx dataConnect.close() starting")
      dataConnect
        .runCatching { close() }
        .fold(
          onSuccess = { println("zzyzx dataConnect.close() completed normally") },
          onFailure = { println("zzyzx dataConnect.close() completed exceptionally: $it") },
        )
    }

    subscription.flow
      .onStart {
        println("zzyzx onStart")
        latch.countDown()
      }
      .collect()
    println("zzyzx collect completed")

    turbineScope {
      val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
      val clientCollector = subscription.flow.testIn(backgroundScope, name = "clientCollector")
      serverCollector.awaitUntilSubscribeStreamRequest()

      dataConnect.close()
      while (true) {
        clientCollector.awaitEvent().let { println("zzyzx awaitEvent() returned $it") }
      }
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

    turbineScope {
      val serverCollector = server.events.testIn(backgroundScope, name = "serverCollector")
      val clientCollectors =
        subscriptions.mapIndexed { index, subscription ->
          subscription.flow.testIn(backgroundScope, name = "clientCollector$index")
        }

      serverCollector.awaitUntilSubscribeStreamRequest()
      repeat(subscriptions.size - 1) { serverCollector.awaitUntilResumeStreamRequest() }

      clientCollectors.forEach { it.cancelAndIgnoreRemainingEvents() }

      dataConnect.close()
      serverCollector.awaitUntilItem { it is CompletedReceived }

      serverCollector.cancelAndIgnoreRemainingEvents()
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
  ): FirebaseDataConnectImpl = dataConnect(server.port, idStringGenerator)

  private fun TestScope.dataConnect(
    serverLocalBindPort: Int? = null,
    idStringGenerator: IdStringGenerator? = null,
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
      deferredAuthProvider = UnavailableDeferred(),
      deferredAppCheckProvider = UnavailableDeferred(),
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
  ): RealtimeQuerySubscriptionImpl<TestData, TestVariables> =
    queryRef(dataConnect, operationName, variables).subscribe()

  private fun TestScope.queryRef(
    dataConnect: FirebaseDataConnectImpl? = null,
    operationName: String? = null,
    variables: TestVariables? = null,
  ): RealtimeQueryRefImpl<TestData, TestVariables> =
    RealtimeQueryRefImpl(
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

@Serializable private data class TestVariables(val stringValue: String)

@Serializable private data class TestData(val intValue: Int)

private fun alphabeticStringArb(): Arb<String> = Arb.string(0..5, Codepoint.az())

private fun testVariablesArb(stringValue: Arb<String> = alphabeticStringArb()): Arb<TestVariables> =
  stringValue.map(::TestVariables)

private fun testDataArb(intValue: Arb<Int> = Arb.int()): Arb<TestData> = intValue.map(::TestData)

private suspend fun ReceiveTurbine<InProcessDataConnectGrpcStreamingServer.Event>
  .awaitUntilInitStreamRequest(): StreamRequestReceived =
  withClue("awaiting 'init' StreamRequest message") {
    awaitUntilItemIsInstance<_, StreamRequestReceived>().also { event ->
      val streamRequest = event.streamRequest
      withClue("request=${streamRequest.print().value}") {
        assertSoftly {
          streamRequest.requestId shouldBe "init"
          streamRequest.requestKindCase shouldBe RequestKindCase.REQUESTKIND_NOT_SET
        }
      }
    }
  }

private suspend inline fun ReceiveTurbine<InProcessDataConnectGrpcStreamingServer.Event>
  .awaitUntilStreamRequest(predicate: (StreamRequest) -> Boolean): StreamRequestReceived =
  awaitUntilItem("event is StreamRequest") {
    if (it is StreamRequestReceived && predicate(it.streamRequest)) {
      TurbinePredicateResult.Satisfied(it)
    } else {
      TurbinePredicateResult.Unsatisfied
    }
  }

private suspend inline fun ReceiveTurbine<InProcessDataConnectGrpcStreamingServer.Event>
  .awaitUntilErrorReceived(predicate: (ErrorReceived) -> Boolean): ErrorReceived =
  awaitUntilItem("event is ErrorReceived") {
    if (it is ErrorReceived && predicate(it)) {
      TurbinePredicateResult.Satisfied(it)
    } else {
      TurbinePredicateResult.Unsatisfied
    }
  }

private suspend inline fun ReceiveTurbine<InProcessDataConnectGrpcStreamingServer.Event>
  .awaitUntilStatusExceptionReceived(code: Status.Code) =
  withClue("expecting ErrorReceived event with StatusException(code=$code)") {
    awaitUntilItem { event ->
      if (event !is ErrorReceived) {
        return@awaitUntilItem false
      }
      val exception = event.exception
      val status =
        if (exception is StatusException) {
          exception.status
        } else if (exception is StatusRuntimeException) {
          exception.status
        } else {
          fail(
            "Got ErrorReceived event with exception=$exception, " +
              "but expected StatusException with code=$code"
          )
        }
      if (status.code != code) {
        fail(
          "Got ErrorReceived event with StatusException/StatusRuntimeException (as expected), " +
            "but code=${status.code} (expected code: $code, exception=$exception)"
        )
      }
      true
    }
  }

private suspend fun ReceiveTurbine<InProcessDataConnectGrpcStreamingServer.Event>
  .awaitUntilSubscribeStreamRequest(): StreamRequestReceived =
  withClue("awaiting 'subscribe' StreamRequest message") {
    awaitUntilStreamRequest { it.hasSubscribe() }
  }

private suspend fun ReceiveTurbine<InProcessDataConnectGrpcStreamingServer.Event>
  .awaitUntilResumeStreamRequest(): StreamRequestReceived =
  withClue("awaiting 'resume' StreamRequest message") { awaitUntilStreamRequest { it.hasResume() } }

private suspend fun ReceiveTurbine<InProcessDataConnectGrpcStreamingServer.Event>
  .awaitUntilCancelStreamRequest(): StreamRequestReceived =
  withClue("awaiting 'cancel' StreamRequest message") { awaitUntilStreamRequest { it.hasCancel() } }

private suspend fun ReceiveTurbine<InProcessDataConnectGrpcStreamingServer.Event>
  .awaitUntilStreamRequestWithRequestId(requestId: String): StreamRequestReceived {
  val predicateDescription = "StreamRequest with requestId=$requestId"
  return withClue("awaiting $predicateDescription") {
    awaitUntilItem(predicateDescription) {
      when (it) {
        is StreamRequestReceived ->
          if (it.streamRequest.requestId == requestId) {
            TurbinePredicateResult.Satisfied(it)
          } else {
            TurbinePredicateResult.Unsatisfied
          }
        else -> TurbinePredicateResult.Unsatisfied
      }
    }
  }
}

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
