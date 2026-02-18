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

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.dataconnect.core.FirebaseDataConnectInternal
import com.google.firebase.dataconnect.testutil.DataConnectBackend
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.InProcessDataConnectGrpcServer
import com.google.firebase.dataconnect.testutil.awaitAuthReady
import com.google.firebase.dataconnect.testutil.newInstance
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetPersonAuthQuery
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.firebase.util.nextAlphanumericString
import google.firebase.dataconnect.proto.executeMutationResponse
import google.firebase.dataconnect.proto.executeQueryResponse
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusException
import io.kotest.assertions.asClue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldNotContainNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
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

  private val key = "e6w33rw36t"

  @get:Rule val inProcessDataConnectGrpcServer = InProcessDataConnectGrpcServer()

  private val personSchema by lazy { PersonSchema(dataConnectFactory) }

  private val auth: FirebaseAuth by lazy {
    DataConnectBackend.fromInstrumentationArguments()
      .authBackend
      .getFirebaseAuth(personSchema.dataConnect.app)
  }

  @Test
  fun authenticatedRequestsAreSuccessful() = runTest {
    signIn()
    val person1Id = Arb.alphanumericString(prefix = "person1Id").next()
    val person2Id = Arb.alphanumericString(prefix = "person2Id").next()
    val person3Id = Arb.alphanumericString(prefix = "person3Id").next()

    personSchema.createPersonAuth(id = person1Id, name = "TestName1", age = 42).execute()
    personSchema.createPersonAuth(id = person2Id, name = "TestName2", age = 43).execute()
    personSchema.createPersonAuth(id = person3Id, name = "TestName3", age = 44).execute()
    val queryResult = personSchema.getPersonAuth(id = person2Id).execute()

    queryResult.asClue { it.data.person shouldBe GetPersonAuthQuery.Data.Person("TestName2", 43) }
  }

  @Test
  fun queryFailsAfterUserSignsOut() = runTest {
    signIn()
    // Verify that we are signed in by executing a query, which should succeed.
    personSchema.getPersonAuth(id = "foo").execute()
    signOut()

    val thrownException =
      shouldThrow<StatusException> { personSchema.getPersonAuth(id = "foo").execute() }

    thrownException.asClue { it.status.code shouldBe Status.UNAUTHENTICATED.code }
  }

  @Test
  fun mutationFailsAfterUserSignsOut() = runTest {
    signIn()
    // Verify that we are signed in by executing a mutation, which should succeed.
    personSchema.createPersonAuth(id = Random.nextAlphanumericString(20), name = "foo").execute()
    signOut()

    val thrownException =
      shouldThrow<StatusException> {
        personSchema
          .createPersonAuth(id = Random.nextAlphanumericString(20), name = "foo")
          .execute()
      }

    thrownException.asClue { it.status.code shouldBe Status.UNAUTHENTICATED.code }
  }

  @Test
  fun queryShouldRetryOnUnauthenticated() = runTest {
    signIn()
    val responseData = buildStructProto { put("foo", key) }
    val executeQueryResponse = executeQueryResponse { data = responseData }
    val grpcServer =
      inProcessDataConnectGrpcServer.newInstance(
        errors = listOf(Status.UNAUTHENTICATED),
        executeQueryResponse = executeQueryResponse
      )
    val authTokens = CopyOnWriteArrayList<String?>()
    backgroundScope.launch {
      grpcServer.metadatas.map { it.get(firebaseAuthTokenHeader) }.toCollection(authTokens)
    }
    val dataConnect = dataConnectFactory.newInstance(auth.app, grpcServer)
    (dataConnect as FirebaseDataConnectInternal).awaitAuthReady()
    val operationName = Arb.dataConnect.operationName().next(rs)
    val queryRef =
      dataConnect.query(operationName, Unit, serializer<TestData>(), serializer<Unit>())

    val actualResponse = queryRef.execute()

    actualResponse.asClue { it.data shouldBe TestData(key) }
    withClue("authTokens") {
      authTokens.shouldNotContainNull()
      authTokens.shouldHaveAtLeastSize(2)
    }
  }

  @Test
  fun mutationShouldRetryOnUnauthenticated() = runTest {
    signIn()
    val responseData = buildStructProto { put("foo", key) }
    val executeMutationResponse = executeMutationResponse { data = responseData }
    val grpcServer =
      inProcessDataConnectGrpcServer.newInstance(
        errors = listOf(Status.UNAUTHENTICATED),
        executeMutationResponse = executeMutationResponse
      )
    val authTokens = CopyOnWriteArrayList<String?>()
    backgroundScope.launch {
      grpcServer.metadatas.map { it.get(firebaseAuthTokenHeader) }.toCollection(authTokens)
    }
    val dataConnect = dataConnectFactory.newInstance(auth.app, grpcServer)
    (dataConnect as FirebaseDataConnectInternal).awaitAuthReady()
    val operationName = Arb.dataConnect.operationName().next(rs)
    val mutationRef =
      dataConnect.mutation(operationName, Unit, serializer<TestData>(), serializer<Unit>())

    val actualResponse = mutationRef.execute()

    actualResponse.asClue { it.data shouldBe TestData(key) }
    withClue("authTokens") {
      authTokens.shouldNotContainNull()
      authTokens.shouldHaveAtLeastSize(2)
    }
  }

  @Test
  fun queryShouldOnlyRetryOnUnauthenticatedOnce() = runTest {
    signIn()
    val grpcServer =
      inProcessDataConnectGrpcServer.newInstance(
        errors = listOf(Status.UNAUTHENTICATED, Status.UNAUTHENTICATED),
      )
    val dataConnect = dataConnectFactory.newInstance(auth.app, grpcServer)
    val operationName = Arb.dataConnect.operationName().next(rs)
    val queryRef = dataConnect.query(operationName, Unit, serializer<Unit>(), serializer<Unit>())

    val thrownException = shouldThrow<StatusException> { queryRef.execute() }

    thrownException.asClue { it.status shouldBe Status.UNAUTHENTICATED }
  }

  @Test
  fun mutationShouldOnlyRetryOnUnauthenticatedOnce() = runTest {
    signIn()
    val grpcServer =
      inProcessDataConnectGrpcServer.newInstance(
        errors = listOf(Status.UNAUTHENTICATED, Status.UNAUTHENTICATED),
      )
    val dataConnect = dataConnectFactory.newInstance(auth.app, grpcServer)
    val operationName = Arb.dataConnect.operationName().next(rs)
    val mutationRef =
      dataConnect.mutation(operationName, Unit, serializer<Unit>(), serializer<Unit>())

    val thrownException = shouldThrow<StatusException> { mutationRef.execute() }

    thrownException.asClue { it.status shouldBe Status.UNAUTHENTICATED }
  }

  private suspend fun signIn() {
    personSchema.dataConnect.awaitAuthReady()
    val authResult = auth.run { signInAnonymously().await() }
    withClue("authResult.user returned from signInAnonymously()") {
      authResult.user.shouldNotBeNull()
    }
  }

  private fun signOut() {
    auth.run { signOut() }
  }

  @Serializable data class TestData(val foo: String)

  private companion object {
    private val firebaseAuthTokenHeader: Metadata.Key<String> =
      Metadata.Key.of("x-firebase-auth-token", Metadata.ASCII_STRING_MARSHALLER)
  }
}
