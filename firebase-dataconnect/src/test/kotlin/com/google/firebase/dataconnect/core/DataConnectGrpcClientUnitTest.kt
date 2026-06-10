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
@file:OptIn(ExperimentalKotest::class)

package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.DataSource
import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.QueryRef.FetchPolicy
import com.google.firebase.dataconnect.core.DataConnectAppCheck.GetAppCheckTokenResult
import com.google.firebase.dataconnect.core.DataConnectAuth.GetAuthTokenResult
import com.google.firebase.dataconnect.core.DataConnectGrpcClient.OperationResult
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs.ExecuteQueryResult
import com.google.firebase.dataconnect.sqlite.SqliteSequencedReference
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.newMockLogger
import com.google.firebase.dataconnect.testutil.property.arbitrary.TwoValues
import com.google.firebase.dataconnect.testutil.property.arbitrary.appCheckTokenResult
import com.google.firebase.dataconnect.testutil.property.arbitrary.authTokenResult
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.executeMutationResponse
import com.google.firebase.dataconnect.testutil.property.arbitrary.executeQueryResultFromCache
import com.google.firebase.dataconnect.testutil.property.arbitrary.executeQueryResultFromServer
import com.google.firebase.dataconnect.testutil.property.arbitrary.pair
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.sqliteSequenceNumber
import com.google.firebase.dataconnect.testutil.property.arbitrary.sqliteSequencedReference
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.firebase.dataconnect.testutil.shouldHaveLoggedExactlyOneMessageContaining
import com.google.firebase.dataconnect.util.SequencedReference
import com.google.firebase.dataconnect.util.SequencedReference.Companion.nextSequenceNumber
import google.firebase.dataconnect.proto.ExecuteMutationResponse
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import io.grpc.Status
import io.grpc.StatusException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private val propTestConfig =
  PropTestConfig(iterations = 20, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.25))

class DataConnectGrpcClientUnitTest {

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()

  @Before
  fun registerPrinters() {
    registerDataConnectKotestPrinters()
  }

  @Test
  fun `executeQuery() should forward arguments to DataConnectGrpcRPCs`() = runTest {
    checkAll(
      propTestConfig,
      testEnvironmentArb(),
      Arb.dataConnect.requestId(),
      Arb.dataConnect.operationName(),
      Arb.proto.struct().map { it.struct },
      Arb.enum<CallerSdkType>(),
      Arb.enum<FetchPolicy>()
    ) {
      (dataConnectGrpcClient, mockDataConnectGrpcRPCs),
      requestId,
      operationName,
      variables,
      callerSdkType,
      fetchPolicy ->
      dataConnectGrpcClient.executeQuery(
        requestId,
        operationName,
        variables,
        callerSdkType,
        fetchPolicy
      )
      coVerify {
        mockDataConnectGrpcRPCs.executeQuery(
          requestId,
          operationName,
          variables,
          callerSdkType,
          fetchPolicy,
          any(),
          any(),
        )
      }
    }
  }

  @Test
  fun `executeMutation() should forward arguments to DataConnectGrpcRPCs`() = runTest {
    checkAll(
      propTestConfig,
      testEnvironmentArb(),
      Arb.dataConnect.requestId(),
      Arb.dataConnect.operationName(),
      Arb.proto.struct().map { it.struct },
      Arb.enum<CallerSdkType>(),
    ) {
      (dataConnectGrpcClient, mockDataConnectGrpcRPCs),
      requestId,
      operationName,
      variables,
      callerSdkType ->
      dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)

      coVerify {
        mockDataConnectGrpcRPCs.executeMutation(
          requestId,
          operationName,
          variables,
          callerSdkType,
          any(),
          any(),
        )
      }
    }
  }

  @Test
  fun `executeQuery() should return data and empty errors if response is from cache`() = runTest {
    checkAll(
      propTestConfig,
      testEnvironmentArb(
        executeQueryResult =
          Arb.dataConnect.sqliteSequencedReference(Arb.dataConnect.executeQueryResultFromCache())
      ),
      Arb.dataConnect.requestId(),
      Arb.dataConnect.operationName(),
      Arb.proto.struct().map { it.struct },
      Arb.enum<CallerSdkType>(),
      Arb.enum<FetchPolicy>(),
    ) { env, requestId, operationName, variables, callerSdkType, fetchPolicy ->
      val (dataConnectGrpcClient) = env

      val sourcedOperationResult =
        dataConnectGrpcClient.executeQuery(
          requestId,
          operationName,
          variables,
          callerSdkType,
          fetchPolicy
        )

      sourcedOperationResult shouldBe
        SourcedData(
          DataSource.CACHE,
          env.executeQueryResult.sqliteSequenceNumber,
          OperationResult(
            data = (env.executeQueryResult.ref as ExecuteQueryResult.FromCache).data,
            errors = emptyList(),
          ),
        )
    }
  }

  @Test
  fun `executeQuery() should return null data and empty errors if response is empty`() = runTest {
    checkAll(
      propTestConfig,
      testEnvironmentArb(
        executeQueryResult =
          Arb.dataConnect.sqliteSequencedReference(
            Arb.constant(ExecuteQueryResult.FromServer(ExecuteQueryResponse.getDefaultInstance()))
          )
      ),
      Arb.dataConnect.requestId(),
      Arb.dataConnect.operationName(),
      Arb.proto.struct().map { it.struct },
      Arb.enum<CallerSdkType>(),
      Arb.enum<FetchPolicy>(),
    ) { env, requestId, operationName, variables, callerSdkType, fetchPolicy ->
      val (dataConnectGrpcClient) = env

      val sourcedOperationResult =
        dataConnectGrpcClient.executeQuery(
          requestId,
          operationName,
          variables,
          callerSdkType,
          fetchPolicy
        )

      sourcedOperationResult shouldBe
        SourcedData(
          DataSource.SERVER,
          env.executeQueryResult.sqliteSequenceNumber,
          OperationResult(data = null, errors = emptyList())
        )
    }
  }

  @Test
  fun `executeMutation() should return null data and empty errors if response is empty`() =
    runTest {
      checkAll(
        propTestConfig,
        testEnvironmentArb(
          executeMutationResult = Arb.constant(ExecuteMutationResponse.getDefaultInstance())
        ),
        Arb.dataConnect.requestId(),
        Arb.dataConnect.operationName(),
        Arb.proto.struct().map { it.struct },
        Arb.enum<CallerSdkType>(),
      ) { (dataConnectGrpcClient), requestId, operationName, variables, callerSdkType ->
        val operationResult =
          dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)

        operationResult shouldBe OperationResult(data = null, errors = emptyList())
      }
    }

  @Test
  fun `executeQuery() should return data and errors`() = runTest {
    checkAll(
      propTestConfig,
      testEnvironmentArb(
        executeQueryResult =
          Arb.dataConnect.sqliteSequencedReference(Arb.dataConnect.executeQueryResultFromServer())
      ),
      Arb.dataConnect.requestId(),
      Arb.dataConnect.operationName(),
      Arb.proto.struct().map { it.struct },
      Arb.enum<CallerSdkType>(),
      Arb.enum<FetchPolicy>(),
    ) { env, requestId, operationName, variables, callerSdkType, fetchPolicy ->
      val (dataConnectGrpcClient) = env

      val sourcedOperationResult =
        dataConnectGrpcClient.executeQuery(
          requestId,
          operationName,
          variables,
          callerSdkType,
          fetchPolicy
        )

      val executeQueryResult = env.executeQueryResult.ref as (ExecuteQueryResult.FromServer)
      sourcedOperationResult shouldBe
        SourcedData(
          DataSource.SERVER,
          env.executeQueryResult.sqliteSequenceNumber,
          OperationResult(
            data = executeQueryResult.response.data,
            errors = executeQueryResult.response.errorsList,
          )
        )
    }
  }

  @Test
  fun `executeMutation() should return data and errors`() = runTest {
    checkAll(
      propTestConfig,
      testEnvironmentArb(executeMutationResult = Arb.dataConnect.executeMutationResponse()),
      Arb.dataConnect.requestId(),
      Arb.dataConnect.operationName(),
      Arb.proto.struct().map { it.struct },
      Arb.enum<CallerSdkType>(),
    ) { env, requestId, operationName, variables, callerSdkType ->
      val (dataConnectGrpcClient) = env

      val operationResult =
        dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)

      operationResult shouldBe
        OperationResult(
          data = env.executeMutationResult.data,
          errors = env.executeMutationResult.errorsList,
        )
    }
  }

  @Test
  fun `executeQuery() should propagate non-grpc exceptions`() = runTest {
    checkAll(
      propTestConfig,
      testEnvironmentArb(),
      Arb.dataConnect.requestId(),
      Arb.dataConnect.operationName(),
      Arb.proto.struct().map { it.struct },
      Arb.enum<CallerSdkType>(),
      Arb.enum<FetchPolicy>(),
    ) {
      (dataConnectGrpcClient, mockDataConnectGrpcRPCs),
      requestId,
      operationName,
      variables,
      callerSdkType,
      fetchPolicy ->
      val exception = TestException("k6hzgp7hvz")
      coEvery {
        mockDataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any(), any(), any())
      } throws exception

      val thrownException =
        shouldThrow<TestException> {
          dataConnectGrpcClient.executeQuery(
            requestId,
            operationName,
            variables,
            callerSdkType,
            fetchPolicy
          )
        }

      thrownException shouldBe exception
    }
  }

  @Test
  fun `executeMutation() should propagate non-grpc exceptions`() = runTest {
    checkAll(
      propTestConfig,
      testEnvironmentArb(),
      Arb.dataConnect.requestId(),
      Arb.dataConnect.operationName(),
      Arb.proto.struct().map { it.struct },
      Arb.enum<CallerSdkType>(),
    ) {
      (dataConnectGrpcClient, mockDataConnectGrpcRPCs),
      requestId,
      operationName,
      variables,
      callerSdkType ->
      val exception = TestException("g32376rnd3")
      coEvery {
        mockDataConnectGrpcRPCs.executeMutation(any(), any(), any(), any(), any(), any())
      } throws exception

      val thrownException =
        shouldThrow<TestException> {
          dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)
        }

      thrownException shouldBe exception
    }
  }

  @Test
  fun `executeQuery() should retry with fresh auth and app check tokens on UNAUTHENTICATED`() =
    runTest {
      checkAll(
        propTestConfig,
        testEnvironmentArb(),
        Arb.dataConnect.requestId(),
        Arb.dataConnect.operationName(),
        Arb.proto.struct().map { it.struct },
        Arb.enum<CallerSdkType>(),
        Arb.enum<FetchPolicy>(),
        authTokenPairArb(),
        appCheckTokenPairArb(),
        Arb.proto.struct().map { it.struct },
        Arb.dataConnect.sqliteSequenceNumber().orNull(nullProbability = 0.2),
      ) {
        (
          dataConnectGrpcClient,
          mockDataConnectGrpcRPCs,
          mockDataConnectAuth,
          mockDataConnectAppCheck,
          mockLogger),
        requestId,
        operationName,
        variables,
        callerSdkType,
        fetchPolicy,
        authTokens,
        appCheckTokens,
        responseData,
        sqliteSequenceNumber,
        ->
        mockDataConnectAuth.stubGetTokensSimulatingForceRefresh(authTokens)
        mockDataConnectAppCheck.stubGetTokensSimulatingForceRefresh(appCheckTokens)
        coEvery {
            mockDataConnectGrpcRPCs.executeQuery(
              any(),
              any(),
              any(),
              any(),
              any(),
              any(),
              any(),
            )
          }
          .throws(
            StatusException(
              Status.UNAUTHENTICATED.withDescription(
                "status exception ${Random.nextInt()} bjh9zc6h5n"
              )
            )
          )
          .andThen(
            SqliteSequencedReference(
              sqliteSequenceNumber,
              ExecuteQueryResult.FromServer(
                ExecuteQueryResponse.newBuilder().setData(responseData).build()
              )
            )
          )

        val sourcedOperationResult =
          dataConnectGrpcClient.executeQuery(
            requestId,
            operationName,
            variables,
            callerSdkType,
            fetchPolicy,
          )

        sourcedOperationResult shouldBe
          SourcedData(
            DataSource.SERVER,
            sqliteSequenceNumber,
            OperationResult(data = responseData, errors = emptyList())
          )
        coVerifyOrder {
          mockDataConnectGrpcRPCs.executeQuery(
            any(),
            any(),
            any(),
            any(),
            any(),
            authTokens.value1,
            appCheckTokens.value1
          )
          mockDataConnectGrpcRPCs.executeQuery(
            any(),
            any(),
            any(),
            any(),
            any(),
            authTokens.value2,
            appCheckTokens.value2
          )
        }
        mockLogger.shouldHaveLoggedExactlyOneMessageContaining(
          "retrying with fresh Auth and/or AppCheck tokens"
        )
        mockLogger.shouldHaveLoggedExactlyOneMessageContaining("UNAUTHENTICATED")
      }
    }

  @Test
  fun `executeMutation() should retry with fresh auth and app check tokens on UNAUTHENTICATED`() =
    runTest {
      checkAll(
        propTestConfig,
        testEnvironmentArb(),
        Arb.dataConnect.requestId(),
        Arb.dataConnect.operationName(),
        Arb.proto.struct().map { it.struct },
        Arb.enum<CallerSdkType>(),
        authTokenPairArb(),
        appCheckTokenPairArb(),
        Arb.proto.struct().map { it.struct },
      ) {
        (
          dataConnectGrpcClient,
          mockDataConnectGrpcRPCs,
          mockDataConnectAuth,
          mockDataConnectAppCheck,
          mockLogger),
        requestId,
        operationName,
        variables,
        callerSdkType,
        authTokens,
        appCheckTokens,
        responseData ->
        mockDataConnectAuth.stubGetTokensSimulatingForceRefresh(authTokens)
        mockDataConnectAppCheck.stubGetTokensSimulatingForceRefresh(appCheckTokens)
        coEvery {
            mockDataConnectGrpcRPCs.executeMutation(any(), any(), any(), any(), any(), any())
          }
          .throws(
            StatusException(
              Status.UNAUTHENTICATED.withDescription(
                "status exception ${Random.nextInt()} m8gzej7pmy"
              )
            )
          )
          .andThen(ExecuteMutationResponse.newBuilder().setData(responseData).build())

        val operationResult =
          dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)

        operationResult shouldBe OperationResult(data = responseData, errors = emptyList())
        coVerifyOrder {
          mockDataConnectGrpcRPCs.executeMutation(
            any(),
            any(),
            any(),
            any(),
            authTokens.value1,
            appCheckTokens.value1
          )
          mockDataConnectGrpcRPCs.executeMutation(
            any(),
            any(),
            any(),
            any(),
            authTokens.value2,
            appCheckTokens.value2
          )
        }
        mockLogger.shouldHaveLoggedExactlyOneMessageContaining(
          "retrying with fresh Auth and/or AppCheck tokens"
        )
        mockLogger.shouldHaveLoggedExactlyOneMessageContaining("UNAUTHENTICATED")
      }
    }

  @Test
  fun `executeQuery() should NOT retry on error status other than UNAUTHENTICATED`() = runTest {
    checkAll(
      propTestConfig,
      testEnvironmentArb(),
      Arb.dataConnect.requestId(),
      Arb.dataConnect.operationName(),
      Arb.proto.struct().map { it.struct },
      Arb.enum<CallerSdkType>(),
      Arb.enum<FetchPolicy>(),
    ) {
      (
        dataConnectGrpcClient,
        mockDataConnectGrpcRPCs,
        mockDataConnectAuth,
        mockDataConnectAppCheck),
      requestId,
      operationName,
      variables,
      callerSdkType,
      fetchPolicy ->
      val exception = StatusException(Status.INTERNAL)
      coEvery {
        mockDataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any(), any(), any())
      } throws exception

      val thrownException =
        shouldThrow<StatusException> {
          dataConnectGrpcClient.executeQuery(
            requestId,
            operationName,
            variables,
            callerSdkType,
            fetchPolicy
          )
        }

      thrownException shouldBeSameInstanceAs exception
      coVerify(exactly = 0) { mockDataConnectAuth.forceRefresh() }
      coVerify(exactly = 0) { mockDataConnectAppCheck.forceRefresh() }
    }
  }

  @Test
  fun `executeMutation() should NOT retry on error status other than UNAUTHENTICATED`() = runTest {
    checkAll(
      propTestConfig,
      testEnvironmentArb(),
      Arb.dataConnect.requestId(),
      Arb.dataConnect.operationName(),
      Arb.proto.struct().map { it.struct },
      Arb.enum<CallerSdkType>(),
    ) {
      (
        dataConnectGrpcClient,
        mockDataConnectGrpcRPCs,
        mockDataConnectAuth,
        mockDataConnectAppCheck),
      requestId,
      operationName,
      variables,
      callerSdkType ->
      val exception = StatusException(Status.INTERNAL)
      coEvery {
        mockDataConnectGrpcRPCs.executeMutation(any(), any(), any(), any(), any(), any())
      } throws exception

      val thrownException =
        shouldThrow<StatusException> {
          dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)
        }

      thrownException shouldBeSameInstanceAs exception
      coVerify(exactly = 0) { mockDataConnectAuth.forceRefresh() }
      coVerify(exactly = 0) { mockDataConnectAppCheck.forceRefresh() }
    }
  }

  @Test
  fun `executeQuery() should throw the exception from the retry if retry also fails with UNAUTHENTICATED`() =
    runTest {
      checkAll(
        propTestConfig,
        testEnvironmentArb(),
        Arb.dataConnect.requestId(),
        Arb.dataConnect.operationName(),
        Arb.proto.struct().map { it.struct },
        Arb.enum<CallerSdkType>(),
        Arb.enum<FetchPolicy>(),
      ) {
        (dataConnectGrpcClient, mockDataConnectGrpcRPCs),
        requestId,
        operationName,
        variables,
        callerSdkType,
        fetchPolicy ->
        val exception1 = StatusException(Status.UNAUTHENTICATED)
        val exception2 = StatusException(Status.UNAUTHENTICATED)
        coEvery {
          mockDataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any(), any(), any())
        } throwsMany (listOf(exception1, exception2))

        val thrownException =
          shouldThrow<StatusException> {
            dataConnectGrpcClient.executeQuery(
              requestId,
              operationName,
              variables,
              callerSdkType,
              fetchPolicy
            )
          }

        thrownException shouldBeSameInstanceAs exception2
      }
    }

  @Test
  fun `executeMutation() should throw the exception from the retry if retry also fails with UNAUTHENTICATED`() =
    runTest {
      checkAll(
        propTestConfig,
        testEnvironmentArb(),
        Arb.dataConnect.requestId(),
        Arb.dataConnect.operationName(),
        Arb.proto.struct().map { it.struct },
        Arb.enum<CallerSdkType>(),
      ) {
        (dataConnectGrpcClient, mockDataConnectGrpcRPCs),
        requestId,
        operationName,
        variables,
        callerSdkType ->
        val exception1 = StatusException(Status.UNAUTHENTICATED)
        val exception2 = StatusException(Status.UNAUTHENTICATED)
        coEvery {
          mockDataConnectGrpcRPCs.executeMutation(any(), any(), any(), any(), any(), any())
        } throwsMany (listOf(exception1, exception2))

        val thrownException =
          shouldThrow<StatusException> {
            dataConnectGrpcClient.executeMutation(
              requestId,
              operationName,
              variables,
              callerSdkType
            )
          }

        thrownException shouldBeSameInstanceAs exception2
      }
    }

  @Test
  fun `executeQuery() should throw the exception from the retry if retry fails with a code other than UNAUTHENTICATED`() =
    runTest {
      checkAll(
        propTestConfig,
        testEnvironmentArb(),
        Arb.dataConnect.requestId(),
        Arb.dataConnect.operationName(),
        Arb.proto.struct().map { it.struct },
        Arb.enum<CallerSdkType>(),
        Arb.enum<FetchPolicy>(),
      ) {
        (dataConnectGrpcClient, mockDataConnectGrpcRPCs),
        requestId,
        operationName,
        variables,
        callerSdkType,
        fetchPolicy ->
        val exception1 = StatusException(Status.UNAUTHENTICATED)
        val exception2 = StatusException(Status.ABORTED)
        coEvery {
          mockDataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any(), any(), any())
        } throwsMany (listOf(exception1, exception2))

        val thrownException =
          shouldThrow<StatusException> {
            dataConnectGrpcClient.executeQuery(
              requestId,
              operationName,
              variables,
              callerSdkType,
              fetchPolicy
            )
          }

        thrownException shouldBeSameInstanceAs exception2
      }
    }

  @Test
  fun `executeMutation() should throw the exception from the retry if retry fails with a code other than UNAUTHENTICATED`() =
    runTest {
      checkAll(
        propTestConfig,
        testEnvironmentArb(),
        Arb.dataConnect.requestId(),
        Arb.dataConnect.operationName(),
        Arb.proto.struct().map { it.struct },
        Arb.enum<CallerSdkType>(),
      ) {
        (dataConnectGrpcClient, mockDataConnectGrpcRPCs),
        requestId,
        operationName,
        variables,
        callerSdkType ->
        val exception1 = StatusException(Status.UNAUTHENTICATED)
        val exception2 = StatusException(Status.ABORTED)
        coEvery {
          mockDataConnectGrpcRPCs.executeMutation(any(), any(), any(), any(), any(), any())
        } throwsMany (listOf(exception1, exception2))

        val thrownException =
          shouldThrow<StatusException> {
            dataConnectGrpcClient.executeMutation(
              requestId,
              operationName,
              variables,
              callerSdkType
            )
          }

        thrownException shouldBeSameInstanceAs exception2
      }
    }

  @Test
  fun `executeQuery() should throw the exception from the retry if retry fails with some other exception`() =
    runTest {
      checkAll(
        propTestConfig,
        testEnvironmentArb(),
        Arb.dataConnect.requestId(),
        Arb.dataConnect.operationName(),
        Arb.proto.struct().map { it.struct },
        Arb.enum<CallerSdkType>(),
        Arb.enum<FetchPolicy>(),
      ) {
        (dataConnectGrpcClient, mockDataConnectGrpcRPCs),
        requestId,
        operationName,
        variables,
        callerSdkType,
        fetchPolicy ->
        val exception1 = StatusException(Status.UNAUTHENTICATED)
        val exception2 = TestException("eysrmxmxk7")
        coEvery {
          mockDataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any(), any(), any())
        } throwsMany (listOf(exception1, exception2))

        val thrownException =
          shouldThrow<TestException> {
            dataConnectGrpcClient.executeQuery(
              requestId,
              operationName,
              variables,
              callerSdkType,
              fetchPolicy
            )
          }

        thrownException shouldBeSameInstanceAs exception2
      }
    }

  @Test
  fun `executeMutation() should throw the exception from the retry if retry fails with some other exception`() =
    runTest {
      checkAll(
        propTestConfig,
        testEnvironmentArb(),
        Arb.dataConnect.requestId(),
        Arb.dataConnect.operationName(),
        Arb.proto.struct().map { it.struct },
        Arb.enum<CallerSdkType>(),
      ) {
        (dataConnectGrpcClient, mockDataConnectGrpcRPCs),
        requestId,
        operationName,
        variables,
        callerSdkType ->
        val exception1 = StatusException(Status.UNAUTHENTICATED)
        val exception2 = TestException("qz2ykb8wa2")
        coEvery {
          mockDataConnectGrpcRPCs.executeMutation(any(), any(), any(), any(), any(), any())
        } throwsMany (listOf(exception1, exception2))

        val thrownException =
          shouldThrow<TestException> {
            dataConnectGrpcClient.executeMutation(
              requestId,
              operationName,
              variables,
              callerSdkType
            )
          }

        thrownException shouldBeSameInstanceAs exception2
      }
    }

  private class TestException(message: String) : Exception(message)
}

private fun DataConnectAuth.stubGetTokensSimulatingForceRefresh(
  tokens: Iterable<GetAuthTokenResult?>
) {
  val tokenIterator = tokens.iterator()
  val currentToken = AtomicReference(tokenIterator.next())
  coEvery { getToken(any()) } answers { currentToken.get().sequenced() }
  coEvery { forceRefresh() } answers { currentToken.set(tokenIterator.next()) }
}

private fun DataConnectAppCheck.stubGetTokensSimulatingForceRefresh(
  tokens: Iterable<GetAppCheckTokenResult?>
) {
  val tokenIterator = tokens.iterator()
  val currentToken = AtomicReference(tokenIterator.next())
  coEvery { getToken(any()) } answers { currentToken.get().sequenced() }
  coEvery { forceRefresh() } answers { currentToken.set(tokenIterator.next()) }
}

private fun authTokenPairArb(): Arb<TwoValues<GetAuthTokenResult?>> =
  Arb.dataConnect.authTokenResult().orNull(nullProbability = 0.33).pair()

private fun appCheckTokenPairArb(): Arb<TwoValues<GetAppCheckTokenResult?>> =
  Arb.dataConnect.appCheckTokenResult().orNull(nullProbability = 0.33).pair()

private class TestEnvironment(
  authTokenResult: GetAuthTokenResult,
  appCheckTokenResult: GetAppCheckTokenResult,
  val executeQueryResult: SqliteSequencedReference<ExecuteQueryResult>,
  val executeMutationResult: ExecuteMutationResponse,
) {

  val mockDataConnectAuth: DataConnectAuth =
    mockk(relaxed = true, name = "mockDataConnectAuth-cr64w58dm5") {
      coEvery { getToken(any()) } answers { authTokenResult.sequenced() }
    }

  val mockDataConnectAppCheck: DataConnectAppCheck =
    mockk(relaxed = true, name = "mockDataConnectAppCheck-e2mv4tpqsy") {
      coEvery { getToken(any()) } answers { appCheckTokenResult.sequenced() }
    }

  val mockDataConnectGrpcRPCs: DataConnectGrpcRPCs =
    mockk(relaxed = true, name = "mockDataConnectGrpcRPCs-s2whj7vjc3") {
      coEvery { executeQuery(any(), any(), any(), any(), any(), any(), any()) } returns
        executeQueryResult
      coEvery { executeMutation(any(), any(), any(), any(), any(), any()) } returns
        executeMutationResult
    }

  val mockLogger = newMockLogger("tmrrzrtqke")

  val dataConnectGrpcClient =
    DataConnectGrpcClient(
      grpcRPCs = mockDataConnectGrpcRPCs,
      dataConnectAuth = mockDataConnectAuth,
      dataConnectAppCheck = mockDataConnectAppCheck,
      logger = mockLogger,
    )

  operator fun component1() = dataConnectGrpcClient
  operator fun component2() = mockDataConnectGrpcRPCs
  operator fun component3() = mockDataConnectAuth
  operator fun component4() = mockDataConnectAppCheck
  operator fun component5() = mockLogger
}

private fun testEnvironmentArb(
  authTokenResult: Arb<GetAuthTokenResult> = Arb.dataConnect.authTokenResult(),
  appCheckTokenResult: Arb<GetAppCheckTokenResult> = Arb.dataConnect.appCheckTokenResult(),
  executeQueryResult: Arb<SqliteSequencedReference<ExecuteQueryResult>> =
    Arb.constant(defaultExecuteQueryResult),
  executeMutationResult: Arb<ExecuteMutationResponse> = Arb.constant(defaultExecuteMutationResult),
): Arb<TestEnvironment> =
  Arb.bind(
    authTokenResult,
    appCheckTokenResult,
    executeQueryResult,
    executeMutationResult,
    ::TestEnvironment,
  )

private val defaultExecuteQueryResult =
  SqliteSequencedReference(
    null,
    ExecuteQueryResult.FromServer(ExecuteQueryResponse.getDefaultInstance())
  )

private val defaultExecuteMutationResult = ExecuteMutationResponse.getDefaultInstance()

private fun <T> T.sequenced(): SequencedReference<T> =
  SequencedReference(nextSequenceNumber(), this)
