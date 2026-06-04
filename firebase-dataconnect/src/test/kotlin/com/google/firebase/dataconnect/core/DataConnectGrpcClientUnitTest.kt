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
import com.google.firebase.dataconnect.sqlite.SqliteSequencedReference
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.newMockLogger
import com.google.firebase.dataconnect.testutil.property.arbitrary.TwoValues
import com.google.firebase.dataconnect.testutil.property.arbitrary.appCheckTokenResult
import com.google.firebase.dataconnect.testutil.property.arbitrary.authTokenResult
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.graphqlErrorProto
import com.google.firebase.dataconnect.testutil.property.arbitrary.pair
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.sqliteSequenceNumber
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.firebase.dataconnect.testutil.shouldHaveLoggedExactlyOneMessageContaining
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
import io.kotest.property.Exhaustive
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

private val propTestConfig =
  PropTestConfig(iterations = 20, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.25))

class DataConnectGrpcClientUnitTest {

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()
  @get:Rule val randomSeedTestRule = RandomSeedTestRule()

  private val rs: RandomSource by randomSeedTestRule.rs
  private val requestId = Arb.dataConnect.requestId().next(rs)
  private val operationName = Arb.dataConnect.operationName().next(rs)
  private val variables = Arb.proto.struct().next(rs).struct
  private val callerSdkType = Arb.enum<CallerSdkType>().next(rs)
  private val fetchPolicy = Arb.enum<FetchPolicy>().next(rs)

  private val mockDataConnectAuth: DataConnectAuth =
    mockk(relaxed = true, name = "mockDataConnectAuth-zfbhma6tyh") {
      coEvery { getToken(any()) } returns null
    }
  private val mockDataConnectAppCheck: DataConnectAppCheck =
    mockk(relaxed = true, name = "mockDataConnectAppCheck-zfbhma6tyh") {
      coEvery { getToken(any()) } returns null
    }

  private val mockDataConnectGrpcRPCs: DataConnectGrpcRPCs =
    mockk(relaxed = true, name = "mockDataConnectGrpcRPCs-zfbhma6tyh") {
      coEvery { executeQuery(any(), any(), any(), any(), any(), any(), any()) } returns
        SqliteSequencedReference(
          null,
          DataConnectGrpcRPCs.ExecuteQueryResult.FromServer(
            ExecuteQueryResponse.getDefaultInstance()
          )
        )
      coEvery { executeMutation(any(), any(), any(), any(), any(), any()) } returns
        ExecuteMutationResponse.getDefaultInstance()
    }

  private val mockLogger = newMockLogger("tmrrzrtqke")

  private val dataConnectGrpcClient =
    DataConnectGrpcClient(
      grpcRPCs = mockDataConnectGrpcRPCs,
      dataConnectAuth = mockDataConnectAuth,
      dataConnectAppCheck = mockDataConnectAppCheck,
      logger = mockLogger,
    )

  @Before
  fun registerPrinters() {
    registerDataConnectKotestPrinters()
  }

  @Test
  fun `executeQuery() should forward requestId, callerSdkType, and fetchPolicy`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.string(),
      Arb.enum<CallerSdkType>(),
      Arb.enum<FetchPolicy>()
    ) { requestId, callerSdkType, fetchPolicy ->
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
          any(),
          any(),
          callerSdkType,
          fetchPolicy,
          any(),
          any()
        )
      }
    }
  }

  @Test
  fun `executeQuery() should send the right ExecuteQueryRequest`() = runTest {
    checkAll(propTestConfig, Exhaustive.enum<FetchPolicy>()) {
      dataConnectGrpcClient.executeQuery(
        requestId,
        operationName,
        variables,
        callerSdkType,
        fetchPolicy
      )

      coVerify {
        mockDataConnectGrpcRPCs.executeQuery(
          any(),
          operationName,
          variables,
          any(),
          any(),
          any(),
          any(),
        )
      }
    }
  }

  @Test
  fun `executeMutation() should forward requestId and callerSdkType`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), Arb.enum<CallerSdkType>()) {
      requestId,
      callerSdkType ->
      dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)

      coVerify {
        mockDataConnectGrpcRPCs.executeMutation(
          requestId,
          any(),
          any(),
          callerSdkType,
          any(),
          any(),
        )
      }
    }
  }

  @Test
  fun `executeMutation() should send the right ExecuteMutationRequest`() = runTest {
    dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)

    coVerify {
      mockDataConnectGrpcRPCs.executeMutation(any(), operationName, variables, any(), any(), any())
    }
  }

  @Test
  fun `executeQuery() should return data and empty errors if response is from cache`() = runTest {
    checkAll(
      propTestConfig,
      Arb.proto.struct().map { it.struct},
      Arb.dataConnect.sqliteSequenceNumber().orNull(nullProbability = 0.2)
    ) { responseData, sqliteSequenceNumber ->
      coEvery {
        mockDataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any(), any(), any())
      } returns
        SqliteSequencedReference(
          sqliteSequenceNumber,
          DataConnectGrpcRPCs.ExecuteQueryResult.FromCache(responseData)
        )

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
          sqliteSequenceNumber,
          OperationResult(
            data = responseData,
            errors = emptyList(),
          ),
        )
    }
  }

  @Test
  fun `executeQuery() should return null data and empty errors if response is empty`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.sqliteSequenceNumber().orNull(nullProbability = 0.2)
    ) { sqliteSequenceNumber ->
      coEvery {
        mockDataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any(), any(), any())
      } returns
        SqliteSequencedReference(
          sqliteSequenceNumber,
          DataConnectGrpcRPCs.ExecuteQueryResult.FromServer(
            ExecuteQueryResponse.getDefaultInstance()
          )
        )

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
          sqliteSequenceNumber,
          OperationResult(data = null, errors = emptyList())
        )
    }
  }

  @Test
  fun `executeMutation() should return null data and empty errors if response is empty`() =
    runTest {
      coEvery {
        mockDataConnectGrpcRPCs.executeMutation(any(), any(), any(), any(), any(), any())
      } returns ExecuteMutationResponse.getDefaultInstance()

      val operationResult =
        dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)

      operationResult shouldBe OperationResult(data = null, errors = emptyList())
    }

  @Test
  fun `executeQuery() should return data and errors`() = runTest {
    checkAll(
      propTestConfig,
      Arb.proto.struct().map { it.struct },
      Arb.dataConnect.sqliteSequenceNumber().orNull(nullProbability = 0.2),
      Arb.list(Arb.dataConnect.graphqlErrorProto(), 1..3),
    ) { responseData, sqliteSequenceNumber, responseErrors ->
      coEvery {
        mockDataConnectGrpcRPCs.executeQuery(any(), any(), any(), any(), any(), any(), any())
      } returns
        SqliteSequencedReference(
          sqliteSequenceNumber,
          DataConnectGrpcRPCs.ExecuteQueryResult.FromServer(
            ExecuteQueryResponse.newBuilder()
              .setData(responseData)
              .addAllErrors(responseErrors)
              .build()
          )
        )

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
          sqliteSequenceNumber,
          OperationResult(
            data = responseData,
            errors = responseErrors,
          )
        )
    }
  }

  @Test
  fun `executeMutation() should return data and errors`() = runTest {
        checkAll(
      propTestConfig,
      Arb.proto.struct().map { it.struct },
      Arb.list(Arb.dataConnect.graphqlErrorProto(), 1..3),
    ) { responseData, responseErrors ->
    coEvery {
      mockDataConnectGrpcRPCs.executeMutation(any(), any(), any(), any(), any(), any())
    } returns
      ExecuteMutationResponse.newBuilder()
        .setData(responseData)
        .addAllErrors(responseErrors)
        .build()

    val operationResult =
      dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)

    operationResult shouldBe
      OperationResult(
        data = responseData,
        errors = responseErrors,
      )
        }
  }

  @Test
  fun `executeQuery() should propagate non-grpc exceptions`() = runTest {
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

  @Test
  fun `executeMutation() should propagate non-grpc exceptions`() = runTest {
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

  @Test
  fun `executeQuery() should retry with fresh auth and app check tokens on UNAUTHENTICATED`() =
    runTest {
    checkAll(
      propTestConfig,
      authTokenPairArb(),
      appCheckTokenPairArb(),
      Arb.proto.struct().map { it.struct },
      Arb.dataConnect.sqliteSequenceNumber().orNull(nullProbability = 0.2),
    ) { authTokens, appCheckTokens, responseData, sqliteSequenceNumber ->
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
          DataConnectGrpcRPCs.ExecuteQueryResult.FromServer(
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

      sourcedOperationResult shouldBe SourcedData(
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
      authTokenPairArb(),
      appCheckTokenPairArb(),
      Arb.proto.struct().map { it.struct },
    ) { authTokens, appCheckTokens, responseData ->
      mockDataConnectAuth.stubGetTokensSimulatingForceRefresh(authTokens)
      mockDataConnectAppCheck.stubGetTokensSimulatingForceRefresh(appCheckTokens)
      coEvery { mockDataConnectGrpcRPCs.executeMutation(any(), any(), any(), any(), any(), any()) }
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

  @Test
  fun `executeMutation() should NOT retry on error status other than UNAUTHENTICATED`() = runTest {
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

  @Test
  fun `executeQuery() should throw the exception from the retry if retry also fails with UNAUTHENTICATED`() =
    runTest {
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

  @Test
  fun `executeMutation() should throw the exception from the retry if retry also fails with UNAUTHENTICATED`() =
    runTest {
      val exception1 = StatusException(Status.UNAUTHENTICATED)
      val exception2 = StatusException(Status.UNAUTHENTICATED)
      coEvery {
        mockDataConnectGrpcRPCs.executeMutation(any(), any(), any(), any(), any(), any())
      } throwsMany (listOf(exception1, exception2))

      val thrownException =
        shouldThrow<StatusException> {
          dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)
        }

      thrownException shouldBeSameInstanceAs exception2
    }

  @Test
  fun `executeQuery() should throw the exception from the retry if retry fails with a code other than UNAUTHENTICATED`() =
    runTest {
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

  @Test
  fun `executeMutation() should throw the exception from the retry if retry fails with a code other than UNAUTHENTICATED`() =
    runTest {
      val exception1 = StatusException(Status.UNAUTHENTICATED)
      val exception2 = StatusException(Status.ABORTED)
      coEvery {
        mockDataConnectGrpcRPCs.executeMutation(any(), any(), any(), any(), any(), any())
      } throwsMany (listOf(exception1, exception2))

      val thrownException =
        shouldThrow<StatusException> {
          dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)
        }

      thrownException shouldBeSameInstanceAs exception2
    }

  @Test
  fun `executeQuery() should throw the exception from the retry if retry fails with some other exception`() =
    runTest {
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

  @Test
  fun `executeMutation() should throw the exception from the retry if retry fails with some other exception`() =
    runTest {
      val exception1 = StatusException(Status.UNAUTHENTICATED)
      val exception2 = TestException("qz2ykb8wa2")
      coEvery {
        mockDataConnectGrpcRPCs.executeMutation(any(), any(), any(), any(), any(), any())
      } throwsMany (listOf(exception1, exception2))

      val thrownException =
        shouldThrow<TestException> {
          dataConnectGrpcClient.executeMutation(requestId, operationName, variables, callerSdkType)
        }

      thrownException shouldBeSameInstanceAs exception2
    }

  private class TestException(message: String) : Exception(message)
}

private fun DataConnectAuth.stubGetTokensSimulatingForceRefresh(
  tokens: Iterable<GetAuthTokenResult?>
) {
  val tokenIterator = tokens.iterator()
  val currentToken = AtomicReference(tokenIterator.next())
  coEvery { getToken(any()) } answers { currentToken.get() }
  coEvery { forceRefresh() } answers { currentToken.set(tokenIterator.next()) }
}

private fun DataConnectAppCheck.stubGetTokensSimulatingForceRefresh(
  tokens: Iterable<GetAppCheckTokenResult?>
) {
  val tokenIterator = tokens.iterator()
  val currentToken = AtomicReference(tokenIterator.next())
  coEvery { getToken(any()) } answers { currentToken.get() }
  coEvery { forceRefresh() } answers { currentToken.set(tokenIterator.next()) }
}

private fun authTokenPairArb(): Arb<TwoValues<GetAuthTokenResult?>> =
  Arb.dataConnect.authTokenResult().orNull(nullProbability = 0.33).pair()

private fun appCheckTokenPairArb(): Arb<TwoValues<GetAppCheckTokenResult?>> =
  Arb.dataConnect.appCheckTokenResult().orNull(nullProbability = 0.33).pair()
