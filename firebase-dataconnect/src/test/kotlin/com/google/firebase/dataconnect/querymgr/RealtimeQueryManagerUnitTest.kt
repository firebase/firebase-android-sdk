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

package com.google.firebase.dataconnect.querymgr

import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.core.DataConnectGrpcClient
import com.google.firebase.dataconnect.core.DataConnectSerialization
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.SuspendingCountDownLatch
import com.google.firebase.dataconnect.testutil.newMockLogger
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.util.IdStringGenerator
import com.google.firebase.dataconnect.util.SequencedReference.Companion.nextSequenceNumber
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.az
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Rule
import org.junit.Test

class RealtimeQueryManagerUnitTest {

  @get:Rule(order = Int.MIN_VALUE) val randomSeedTestRule = RandomSeedTestRule()
  private val rs: RandomSource by randomSeedTestRule.rs

  @Serializable private data class TestVariables(val string: String)

  private val stringArb: Arb<String> = Arb.string(size = 4, Codepoint.az())
  private val testVariablesArb: Arb<TestVariables> = stringArb.map(::TestVariables)

  private fun <T> Arb<T>.sample(): T = sample(rs).value

  @Test
  fun `subscribe() should allow subsequent calls to re-try the connection`() = runTest {
    val grpcClient: DataConnectGrpcClient = mockk()
    val realtimeQueryManager =
      RealtimeQueryManager(
        grpcClient = grpcClient,
        coroutineScope = backgroundScope,
        idStringGenerator = IdStringGenerator(rs.random),
        serialization = DataConnectSerialization(StandardTestDispatcher(testScheduler)),
        logger = newMockLogger("s78hgm6fff")
      )

    class TestException(message: String) : Exception(message)
    coEvery { grpcClient.connect(any(), any(), any()) } answers
      {
        throw TestException("simulated connection failure ${nextSequenceNumber()} mxrceswed8")
      }

    val exceptions =
      List(5) {
        shouldThrow<TestException> {
          realtimeQueryManager.subscribe(
            operationName = "operationName-${stringArb.sample()}",
            variables = testVariablesArb.sample(),
            dataDeserializer = serializer<Unit>(),
            variablesSerializer = serializer<TestVariables>(),
            callerSdkType = Arb.enum<CallerSdkType>().sample(),
            dataSerializersModule = Arb.dataConnect.serializersModule().sample(),
            variablesSerializersModule = Arb.dataConnect.serializersModule().sample(),
          )
        }
      }

    exceptions.map { it.message }.shouldBeUnique()
  }

  @Test
  fun `subscribe() should conflate concurrent failing calls`() = runTest {
    val grpcClient: DataConnectGrpcClient = mockk()
    val realtimeQueryManager =
      RealtimeQueryManager(
        grpcClient = grpcClient,
        coroutineScope = backgroundScope,
        idStringGenerator = IdStringGenerator(rs.random),
        serialization = DataConnectSerialization(StandardTestDispatcher(testScheduler)),
        logger = newMockLogger("yzrpk2m6tt")
      )

    class TestException(message: String) : Exception(message)
    coEvery { grpcClient.connect(any(), any(), any()) } answers
      {
        throw TestException("simulated connection failure ${nextSequenceNumber()} ddx8543vf9")
      }

    val latch = SuspendingCountDownLatch(50)
    val jobs =
      List(latch.count) {
        backgroundScope.async(Dispatchers.Default) {
          latch.countDown().await()
          shouldThrow<TestException> {
            realtimeQueryManager.subscribe(
              operationName = "operationName-${stringArb.sample()}",
              variables = testVariablesArb.sample(),
              dataDeserializer = serializer<Unit>(),
              variablesSerializer = serializer<TestVariables>(),
              callerSdkType = Arb.enum<CallerSdkType>().sample(),
              dataSerializersModule = Arb.dataConnect.serializersModule().sample(),
              variablesSerializersModule = Arb.dataConnect.serializersModule().sample(),
            )
          }
        }
      }

    // There should be fewer distinct exception messages than the number of jobs because some of
    // the subscribe() requests should conflate/share their calls to subscribe().
    val distinctExceptionMessages = jobs.awaitAll().map { it.message }.distinct()
    distinctExceptionMessages.size shouldBeLessThan jobs.size
  }
}
