/*
 * Copyright 2024 Google LLC
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
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.dataconnect.core.FirebaseUserChangedException
import com.google.firebase.dataconnect.testutil.DataConnectBackend
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.InProcessDataConnectGrpcServer
import com.google.firebase.dataconnect.testutil.awaitAuthReady
import com.google.firebase.dataconnect.testutil.newInstance
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.schemas.RealtimeConnector
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import google.firebase.dataconnect.proto.executeMutationResponse
import google.firebase.dataconnect.proto.executeQueryResponse
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusException
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContainNull
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Rule
import org.junit.Test

class AuthIntegrationTest : DataConnectIntegrationTestBase() {

  @get:Rule val inProcessDataConnectGrpcServer = InProcessDataConnectGrpcServer()

  @Test
  fun authenticatedRequestsAreSuccessful() = runTest {
    val dataConnect = dataConnectFactory.newInstance(RealtimeConnector.config)
    val connector = RealtimeConnector.getInstance(dataConnect)
    signIn(dataConnect)
    val names = List(3) { sampleName() }

    val keys = names.map { connector.insertStringAuth.execute(it) }
    val item = connector.getStringByKeyAuth.execute(keys.last())
    item.asClue { it.shouldNotBeNull().name shouldBe names.last() }
  }

  @Test
  fun queryFailsAfterUserSignsOut() = runTest {
    val dataConnect = dataConnectFactory.newInstance(RealtimeConnector.config)
    val connector = RealtimeConnector.getInstance(dataConnect)
    val queryRef: QueryRef<*, *> =
      connector.getStringByKeyAuth.queryRef("D0B6AAA6-6ADD-4790-BC06-F63A21855899")

    signIn(dataConnect)
    // Verify that we are signed in by executing a query, which should succeed.
    queryRef.execute()
    signOut(dataConnect)

    val thrownException = shouldThrow<StatusException> { queryRef.execute() }
    thrownException.asClue { it.status.code shouldBe Status.UNAUTHENTICATED.code }
  }

  @Test
  fun mutationFailsAfterUserSignsOut() = runTest {
    val dataConnect = dataConnectFactory.newInstance(RealtimeConnector.config)
    val connector = RealtimeConnector.getInstance(dataConnect)
    val mutationRef: MutationRef<*, *> = connector.insertStringAuth.mutationRef("yzpbs55jsj")

    signIn(dataConnect)
    // Verify that we are signed in by executing a mutation, which should succeed.
    mutationRef.execute()
    signOut(dataConnect)

    val thrownException = shouldThrow<StatusException> { mutationRef.execute() }
    thrownException.asClue { it.status.code shouldBe Status.UNAUTHENTICATED.code }
  }

  @Test
  fun queryShouldRetryOnUnauthenticated() = runTest {
    val responseData = buildStructProto { put("foo", "sxzfa4bqkk") }
    val executeQueryResponse = executeQueryResponse { data = responseData }
    val grpcServer =
      inProcessDataConnectGrpcServer.newInstance(
        errors = listOf(Status.UNAUTHENTICATED),
        executeQueryResponse = executeQueryResponse,
        responseDelay = 1.seconds, // avoid getting the same access token from auth emulator
      )
    val authTokens = CopyOnWriteArrayList<String?>()
    backgroundScope.launch {
      grpcServer.metadatas.map { it.get(firebaseAuthTokenHeader) }.toCollection(authTokens)
    }
    val dataConnect = dataConnectFactory.newInstance(grpcServer)
    signIn(dataConnect)
    val operationName = Arb.dataConnect.operationName().next(rs)
    val queryRef =
      dataConnect.query(operationName, Unit, serializer<TestData>(), serializer<Unit>())

    val actualResponse = queryRef.execute()

    actualResponse.asClue { it.data shouldBe TestData("sxzfa4bqkk") }
    withClue("authTokens") {
      authTokens.shouldNotContainNull()
      authTokens.shouldHaveAtLeastSize(2)
    }
  }

  @Test
  fun mutationShouldRetryOnUnauthenticated() = runTest {
    val responseData = buildStructProto { put("foo", "nyecckzvsb") }
    val executeMutationResponse = executeMutationResponse { data = responseData }
    val grpcServer =
      inProcessDataConnectGrpcServer.newInstance(
        errors = listOf(Status.UNAUTHENTICATED),
        executeMutationResponse = executeMutationResponse,
        responseDelay = 1.seconds, // avoid getting the same access token from auth emulator
      )
    val authTokens = CopyOnWriteArrayList<String?>()
    backgroundScope.launch {
      grpcServer.metadatas.map { it.get(firebaseAuthTokenHeader) }.toCollection(authTokens)
    }
    val dataConnect = dataConnectFactory.newInstance(grpcServer)
    signIn(dataConnect)
    val operationName = Arb.dataConnect.operationName().next(rs)
    val mutationRef =
      dataConnect.mutation(operationName, Unit, serializer<TestData>(), serializer<Unit>())

    val actualResponse = mutationRef.execute()

    actualResponse.asClue { it.data shouldBe TestData("nyecckzvsb") }
    withClue("authTokens") {
      authTokens.shouldNotContainNull()
      authTokens.shouldHaveAtLeastSize(2)
    }
  }

  @Test
  fun queryShouldOnlyRetryOnUnauthenticatedOnce() = runTest {
    val grpcServer =
      inProcessDataConnectGrpcServer.newInstance(
        errors = listOf(Status.UNAUTHENTICATED, Status.UNAUTHENTICATED),
        responseDelay = 1.seconds, // avoid getting the same access token from auth emulator
      )
    val dataConnect = dataConnectFactory.newInstance(grpcServer)
    signIn(dataConnect)
    val operationName = Arb.dataConnect.operationName().next(rs)
    val queryRef = dataConnect.query(operationName, Unit, serializer<Unit>(), serializer<Unit>())

    val thrownException = shouldThrow<StatusException> { queryRef.execute() }

    thrownException.asClue { it.status shouldBe Status.UNAUTHENTICATED }
  }

  @Test
  fun mutationShouldOnlyRetryOnUnauthenticatedOnce() = runTest {
    val grpcServer =
      inProcessDataConnectGrpcServer.newInstance(
        errors = listOf(Status.UNAUTHENTICATED, Status.UNAUTHENTICATED),
        responseDelay = 1.seconds, // avoid getting the same access token from auth emulator
      )
    val dataConnect = dataConnectFactory.newInstance(grpcServer)
    signIn(dataConnect)
    val operationName = Arb.dataConnect.operationName().next(rs)
    val mutationRef =
      dataConnect.mutation(operationName, Unit, serializer<Unit>(), serializer<Unit>())

    val thrownException = shouldThrow<StatusException> { mutationRef.execute() }

    thrownException.asClue { it.status shouldBe Status.UNAUTHENTICATED }
  }

  @Test
  fun realtimeQuerySubscriptionUnauthenticated() = runTest {
    val dataConnect = dataConnectFactory.newInstance(RealtimeConnector.config)
    val connector = RealtimeConnector.getInstance(dataConnect)
    val queryRef = connector.getStringByKeyAuth.queryRef("E922A1E7-9F3C-4EF4-807E-CB163006F6D9")
    queryRef.subscribe().flow.test {
      val item = awaitItem()
      val exception = item.result.shouldBeFailure<DataConnectOperationException>()

      assertSoftly {
        withClue("exception.message") {
          exception.message shouldContainWithNonAbuttingTextIgnoringCase "unauthenticated"
        }
        withClue("exception.response.data") { exception.response.data.shouldBeNull() }
        withClue("exception.response.errors") { exception.response.errors.shouldNotBeEmpty() }
      }

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun realtimeQuerySubscriptionAuthenticated() = runTest {
    val dataConnect = dataConnectFactory.newInstance(RealtimeConnector.config)
    signIn(dataConnect)
    val connector = RealtimeConnector.getInstance(dataConnect)
    val key = connector.insertString("value0")
    val queryRef = connector.getStringByKeyAuth.queryRef(key)
    queryRef.subscribe().flow.test {
      withClue("item1") {
        val item = awaitItem().result.shouldBeSuccess().data.item
        item.shouldNotBeNull().name.shouldBe("value0")
      }

      connector.updateString(key, "value1")

      withClue("item2") {
        val item = awaitItem().result.shouldBeSuccess().data.item
        item.shouldNotBeNull().name.shouldBe("value1")
      }

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun realtimeQuerySubscriptionErrorsOnSignIn() = runTest {
    val dataConnect = dataConnectFactory.newInstance(RealtimeConnector.config)
    val connector = RealtimeConnector.getInstance(dataConnect)
    val queryRef = connector.getStringByKey.queryRef("FEE580E8-D824-4DF3-86CA-C235B122A203")
    queryRef.subscribe().flow.test {
      awaitItem().result.shouldBeSuccess()
      val newUser = signIn(dataConnect)

      val exception = awaitError().shouldBeInstanceOf<FirebaseUserChangedException>()

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "null"
        exception.message shouldContainWithNonAbuttingText newUser.uid
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "Firebase user changed"
      }
    }
  }

  @Test
  fun realtimeQuerySubscriptionErrorsOnSignOut() = runTest {
    val dataConnect = dataConnectFactory.newInstance(RealtimeConnector.config)
    val connector = RealtimeConnector.getInstance(dataConnect)
    val queryRef = connector.getStringByKey.queryRef("866B2B88-3B59-44E4-A66E-4760E97BBF2A")
    val oldUser = signIn(dataConnect)
    queryRef.subscribe().flow.test {
      awaitItem().result.shouldBeSuccess()
      signOut(dataConnect)

      val exception = awaitError().shouldBeInstanceOf<FirebaseUserChangedException>()

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "null"
        exception.message shouldContainWithNonAbuttingText oldUser.uid
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "Firebase user changed"
      }
    }
  }

  @Test
  fun realtimeQuerySubscriptionCanResubscribeAfterErroringOnSignIn() = runTest {
    val dataConnect = dataConnectFactory.newInstance(RealtimeConnector.config)
    val connector = RealtimeConnector.getInstance(dataConnect)
    val queryRef = connector.getStringByKey.queryRef("5EB095CB-25EF-4F69-AD17-073EA5DFDEE3")
    val querySubscription = queryRef.subscribe()

    querySubscription.flow.test {
      awaitItem().result.shouldBeSuccess()
      signIn(dataConnect)
      awaitError().shouldBeInstanceOf<FirebaseUserChangedException>()
    }

    querySubscription.flow.test { awaitItem().result.shouldBeSuccess() }
  }

  @Test
  fun realtimeQuerySubscriptionCanResubscribeErrorsOnSignOut() = runTest {
    val dataConnect = dataConnectFactory.newInstance(RealtimeConnector.config)
    val connector = RealtimeConnector.getInstance(dataConnect)
    val queryRef = connector.getStringByKey.queryRef("B94007D9-0F5A-4665-9D45-F2CD6D9C95A4")
    signIn(dataConnect)
    val querySubscription = queryRef.subscribe()

    querySubscription.flow.test {
      awaitItem().result.shouldBeSuccess()
      signOut(dataConnect)
      awaitError().shouldBeInstanceOf<FirebaseUserChangedException>()
    }

    querySubscription.flow.test { awaitItem().result.shouldBeSuccess() }
  }

  @Test
  fun realtimeQuerySubscriptionCanResubscribeErrorsOnSignOutThenSignBackIn() = runTest {
    val dataConnect = dataConnectFactory.newInstance(RealtimeConnector.config)
    val connector = RealtimeConnector.getInstance(dataConnect)
    val queryRef = connector.getStringByKeyAuth.queryRef("33D2591F-7CD1-465A-A241-02A07F8851E0")
    val querySubscription = queryRef.subscribe()

    val email = "sree9hqjks@google.com"
    val password = "casmwbbs4g"
    signIn(dataConnect) {
      try {
        createUserWithEmailAndPassword(email, password).await()
      } catch (_: FirebaseAuthUserCollisionException) {
        signInWithEmailAndPassword(email, password).await()
      }
    }

    querySubscription.flow.test {
      awaitItem().result.shouldBeSuccess()
      signOut(dataConnect)
      awaitError().shouldBeInstanceOf<FirebaseUserChangedException>()
    }

    signIn(dataConnect) { signInWithEmailAndPassword(email, password).await() }

    querySubscription.flow.test { awaitItem().result.shouldBeSuccess() }
  }

  private fun sampleName(): String =
    "name_" + Arb.dataConnect.alphabeticString(length = 20).sample()

  @Serializable data class TestData(val foo: String)

  private companion object {
    private val firebaseAuthTokenHeader: Metadata.Key<String> =
      Metadata.Key.of("x-firebase-auth-token", Metadata.ASCII_STRING_MARSHALLER)
  }
}

private fun getFirebaseAuth(app: FirebaseApp): FirebaseAuth =
  DataConnectBackend.fromInstrumentationArguments().authBackend.getFirebaseAuth(app)

private suspend inline fun <T> signIn(
  dataConnect: FirebaseDataConnect,
  signIn: FirebaseAuth.() -> T
): T {
  dataConnect.awaitAuthReady()
  val auth = getFirebaseAuth(dataConnect.app)
  return signIn(auth)
}

private suspend fun signIn(dataConnect: FirebaseDataConnect): FirebaseUser =
  signIn(dataConnect) {
    val authResult = signInAnonymously().await()
    checkNotNull(authResult.user) {
      "internal error kz97svg6c3: signInAnonymously().await().user is null"
    }
  }

private fun signOut(dataConnect: FirebaseDataConnect) {
  val auth = getFirebaseAuth(dataConnect.app)
  auth.signOut()
}
