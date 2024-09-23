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

package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.DataConnectException
import com.google.firebase.dataconnect.DataConnectUntypedData
import com.google.firebase.dataconnect.DataConnectUntypedVariables
import com.google.firebase.dataconnect.core.DataConnectGrpcClient.OperationResult
import com.google.firebase.dataconnect.testutil.dataConnectError
import com.google.firebase.dataconnect.testutil.mutationRefImpl
import com.google.firebase.dataconnect.util.SuspendingLazy
import com.google.firebase.dataconnect.util.buildStructProto
import com.google.firebase.dataconnect.util.encodeToStruct
import com.google.firebase.dataconnect.util.toStructProto
import com.google.protobuf.Struct
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.retry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Test

@Suppress("ReplaceCallWithBinaryOperator")
@OptIn(ExperimentalCoroutinesApi::class)
class MutationRefImplUnitTest {

  @Serializable private data class TestData(val foo: String)
  @Serializable private data class TestVariables(val bar: String)

  @Test
  fun `execute() returns the result on success`() = runTest {
    val data = Arb.testData().next()
    val operationResult = OperationResult(encodeToStruct(data), errors = emptyList())
    val dataConnect = dataConnectWithMutationResult(Result.success(operationResult))
    val mutationRefImpl = Arb.mutationRefImpl().next().copy(dataConnect = dataConnect)

    val mutationResult = mutationRefImpl.execute()

    assertSoftly {
      mutationResult.ref shouldBeSameInstanceAs mutationRefImpl
      mutationResult.data shouldBe data
    }
  }

  @Test
  fun `execute() calls executeMutation with the correct arguments`() = runTest {
    val data = Arb.testData().next()
    val operationResult = OperationResult(encodeToStruct(data), errors = emptyList())
    val requestIdSlot: CapturingSlot<String> = slot()
    val operationNameSlot: CapturingSlot<String> = slot()
    val variablesSlot: CapturingSlot<Struct> = slot()
    val dataConnect =
      dataConnectWithMutationResult(
        Result.success(operationResult),
        requestIdSlot,
        operationNameSlot,
        variablesSlot
      )
    val mutationRefImpl = Arb.mutationRefImpl().next().copy(dataConnect = dataConnect)

    mutationRefImpl.execute()
    val requestId1 = requestIdSlot.captured
    val operationName1 = operationNameSlot.captured
    val variables1 = variablesSlot.captured

    requestIdSlot.clear()
    operationNameSlot.clear()
    variablesSlot.clear()
    mutationRefImpl.execute()
    val requestId2 = requestIdSlot.captured
    val operationName2 = operationNameSlot.captured
    val variables2 = variablesSlot.captured

    assertSoftly {
      requestId1.shouldNotBeBlank()
      requestId2.shouldNotBeBlank()
      requestId1 shouldNotBe requestId2
      operationName1 shouldBe mutationRefImpl.operationName
      operationName2 shouldBe operationName1
      variables1 shouldBe encodeToStruct(mutationRefImpl.variables)
      variables2 shouldBe variables1
    }
  }

  @Test
  fun `execute() calls executeMutation with the correct isFromGeneratedSdk`() {
    assertSoftly {
      for (isFromGeneratedSdk in listOf(true, false)) {
        withClue("isFromGeneratedSdk=$isFromGeneratedSdk") {
          verifyIsFromGeneratedSdkRoundTrip(isFromGeneratedSdk)
        }
      }
    }
  }

  private fun verifyIsFromGeneratedSdkRoundTrip(isFromGeneratedSdk: Boolean) = runTest {
    val data = Arb.testData().next()
    val operationResult = OperationResult(encodeToStruct(data), errors = emptyList())
    val isFromGeneratedSdkSlot: CapturingSlot<Boolean> = slot()
    val dataConnect =
      dataConnectWithMutationResult(
        Result.success(operationResult),
        isFromGeneratedSdkSlot = isFromGeneratedSdkSlot
      )
    val mutationRefImpl =
      Arb.mutationRefImpl()
        .next()
        .copy(dataConnect = dataConnect, isFromGeneratedSdk = isFromGeneratedSdk)

    mutationRefImpl.execute()
    val isFromGeneratedSdk1 = isFromGeneratedSdkSlot.captured
    isFromGeneratedSdkSlot.clear()
    mutationRefImpl.execute()
    val isFromGeneratedSdk2 = isFromGeneratedSdkSlot.captured

    assertSoftly {
      isFromGeneratedSdk1 shouldBe isFromGeneratedSdk
      isFromGeneratedSdk2 shouldBe isFromGeneratedSdk
    }
  }

  @Test
  fun `execute() handles DataConnectUntypedVariables and DataConnectUntypedData`() = runTest {
    val variables = DataConnectUntypedVariables("foo" to 42.0)
    val errors = listOf(Arb.dataConnectError().next())
    val data = DataConnectUntypedData(mapOf("bar" to 24.0), errors)
    val variablesSlot: CapturingSlot<Struct> = slot()
    val operationResult = OperationResult(buildStructProto { put("bar", 24.0) }, errors)
    val dataConnect =
      dataConnectWithMutationResult(Result.success(operationResult), variablesSlot = variablesSlot)
    val mutationRefImpl =
      Arb.mutationRefImpl()
        .next()
        .copy(dataConnect = dataConnect)
        .withVariablesSerializer(variables, DataConnectUntypedVariables)
        .withDataDeserializer(DataConnectUntypedData)

    val mutationResult = mutationRefImpl.execute()

    assertSoftly {
      mutationResult.ref shouldBeSameInstanceAs mutationRefImpl
      mutationResult.data shouldBe data
      variablesSlot.captured shouldBe variables.variables.toStructProto()
    }
  }

  @Test
  fun `execute() throws when the data is null`() = runTest {
    val operationResult = OperationResult(data = null, errors = emptyList())
    val dataConnect = dataConnectWithMutationResult(Result.success(operationResult))
    val mutationRefImpl = Arb.mutationRefImpl().next().copy(dataConnect = dataConnect)

    shouldThrow<DataConnectException> { mutationRefImpl.execute() }
  }

  @Test
  fun `constructor assigns public properties to the given arguments`() {
    val values = Arb.mutationRefImpl().next()
    val mutationRefImpl =
      MutationRefImpl(
        dataConnect = values.dataConnect,
        operationName = values.operationName,
        variables = values.variables,
        dataDeserializer = values.dataDeserializer,
        variablesSerializer = values.variablesSerializer,
        isFromGeneratedSdk = values.isFromGeneratedSdk,
      )

    mutationRefImpl.asClue {
      assertSoftly {
        it.dataConnect shouldBeSameInstanceAs values.dataConnect
        it.operationName shouldBeSameInstanceAs values.operationName
        it.variables shouldBeSameInstanceAs values.variables
        it.dataDeserializer shouldBeSameInstanceAs values.dataDeserializer
        it.variablesSerializer shouldBeSameInstanceAs values.variablesSerializer
        it.isFromGeneratedSdk shouldBe values.isFromGeneratedSdk
      }
    }
  }

  @Test
  fun `hashCode() should return the same value when invoked repeatedly`() {
    val mutationRefImpl: MutationRefImpl<*, *> = Arb.mutationRefImpl().next()
    val hashCode = mutationRefImpl.hashCode()
    repeat(10) { mutationRefImpl.hashCode() shouldBe hashCode }
  }

  @Test
  fun `hashCode() should return the same value when invoked on distinct, but equal, objects`() {
    val mutationRefImpl1: MutationRefImpl<*, *> = Arb.mutationRefImpl().next()
    val mutationRefImpl2: MutationRefImpl<*, *> = mutationRefImpl1.copy()
    mutationRefImpl1 shouldNotBeSameInstanceAs mutationRefImpl2 // verify test precondition
    repeat(10) { mutationRefImpl1.hashCode() shouldBe mutationRefImpl2.hashCode() }
  }

  @Test
  fun `hashCode() should incorporate dataConnect`() = runTest {
    verifyHashCodeEventuallyDiffers { it.copy(dataConnect = mockk(name = stringArb.next())) }
  }

  @Test
  fun `hashCode() should incorporate operationName`() = runTest {
    verifyHashCodeEventuallyDiffers { it.copy(operationName = stringArb.next()) }
  }

  @Test
  fun `hashCode() should incorporate variables`() = runTest {
    verifyHashCodeEventuallyDiffers { it.copy(variables = TestVariables(stringArb.next())) }
  }

  @Test
  fun `hashCode() should incorporate dataDeserializer`() = runTest {
    verifyHashCodeEventuallyDiffers { it.copy(dataDeserializer = mockk(name = stringArb.next())) }
  }

  @Test
  fun `hashCode() should incorporate variablesSerializer`() = runTest {
    verifyHashCodeEventuallyDiffers {
      it.copy(variablesSerializer = mockk(name = stringArb.next()))
    }
  }

  @Test
  fun `hashCode() should NOT incorporate isFromGeneratedSdk`() = runTest {
    val mutationRef1 = Arb.mutationRefImpl().next()
    val mutationRef2 = mutationRef1.copy(isFromGeneratedSdk = !mutationRef1.isFromGeneratedSdk)
    mutationRef1.hashCode() shouldBe mutationRef2.hashCode()
  }

  private suspend fun verifyHashCodeEventuallyDiffers(
    otherFactory:
      (other: MutationRefImpl<TestData, TestVariables>) -> MutationRefImpl<TestData, TestVariables>
  ) {
    val obj1: MutationRefImpl<TestData, TestVariables> = Arb.mutationRefImpl().next()
    retry(maxRetry = 50, timeout = Duration.INFINITE) {
      val obj2: MutationRefImpl<TestData, TestVariables> = otherFactory(obj1)
      obj1.hashCode() shouldNotBe obj2.hashCode()
    }
  }

  @Test
  fun `equals(this) should return true`() = runTest {
    val mutationRefImpl: MutationRefImpl<TestData, TestVariables> = Arb.mutationRefImpl().next()
    mutationRefImpl.equals(mutationRefImpl) shouldBe true
  }

  @Test
  fun `equals(equal, but distinct, instance) should return true`() = runTest {
    val mutationRefImpl1: MutationRefImpl<TestData, TestVariables> = Arb.mutationRefImpl().next()
    val mutationRefImpl2: MutationRefImpl<TestData, TestVariables> = mutationRefImpl1.copy()
    mutationRefImpl1 shouldNotBeSameInstanceAs mutationRefImpl2 // verify test precondition
    mutationRefImpl1.equals(mutationRefImpl2) shouldBe true
  }

  @Test
  fun `equals(null) should return false`() = runTest {
    val mutationRefImpl: MutationRefImpl<TestData, TestVariables> = Arb.mutationRefImpl().next()
    mutationRefImpl.equals(null) shouldBe false
  }

  @Test
  fun `equals(an object of a different type) should return false`() = runTest {
    val mutationRefImpl: MutationRefImpl<TestData, TestVariables> = Arb.mutationRefImpl().next()
    mutationRefImpl.equals("not a MutationRefImpl") shouldBe false
  }

  @Test
  fun `equals() should return false when only dataConnect differs`() = runTest {
    val mutationRefImpl1: MutationRefImpl<TestData, TestVariables> = Arb.mutationRefImpl().next()
    val mutationRefImpl2 = mutationRefImpl1.copy(dataConnect = mockk(stringArb.next()))
    mutationRefImpl1.equals(mutationRefImpl2) shouldBe false
  }

  @Test
  fun `equals() should return false when only operationName differs`() = runTest {
    val mutationRefImpl1: MutationRefImpl<TestData, TestVariables> = Arb.mutationRefImpl().next()
    val mutationRefImpl2 =
      mutationRefImpl1.copy(operationName = mutationRefImpl1.operationName + "2")
    mutationRefImpl1.equals(mutationRefImpl2) shouldBe false
  }

  @Test
  fun `equals() should return false when only variables differs`() = runTest {
    val mutationRefImpl1: MutationRefImpl<TestData, TestVariables> = Arb.mutationRefImpl().next()
    val mutationRefImpl2 =
      mutationRefImpl1.copy(variables = TestVariables(mutationRefImpl1.variables.bar + "2"))
    mutationRefImpl1.equals(mutationRefImpl2) shouldBe false
  }

  @Test
  fun `equals() should return false when only dataDeserializer differs`() = runTest {
    val mutationRefImpl1: MutationRefImpl<TestData, TestVariables> = Arb.mutationRefImpl().next()
    val mutationRefImpl2 = mutationRefImpl1.copy(dataDeserializer = mockk(stringArb.next()))
    mutationRefImpl1.equals(mutationRefImpl2) shouldBe false
  }

  @Test
  fun `equals() should return false when only variablesSerializer differs`() = runTest {
    val mutationRefImpl1: MutationRefImpl<TestData, TestVariables> = Arb.mutationRefImpl().next()
    val mutationRefImpl2 = mutationRefImpl1.copy(variablesSerializer = mockk(stringArb.next()))
    mutationRefImpl1.equals(mutationRefImpl2) shouldBe false
  }

  @Test
  fun `equals() should return _TRUE_ when only isFromGeneratedSdk differs`() = runTest {
    val mutationRefImpl1: MutationRefImpl<TestData, TestVariables> = Arb.mutationRefImpl().next()
    val mutationRefImpl2 =
      mutationRefImpl1.copy(isFromGeneratedSdk = !mutationRefImpl1.isFromGeneratedSdk)
    mutationRefImpl1.equals(mutationRefImpl2) shouldBe true
  }

  @Test
  fun `toString() should incorporate the string representations of public properties`() = runTest {
    val mutationRefImpl: MutationRefImpl<TestData, TestVariables> = Arb.mutationRefImpl().next()
    val mutationRefImpls =
      listOf(
        mutationRefImpl,
        mutationRefImpl.copy(isFromGeneratedSdk = !mutationRefImpl.isFromGeneratedSdk),
      )
    val toStringResult = mutationRefImpl.toString()

    assertSoftly {
      mutationRefImpls.forEach {
        it.asClue {
          toStringResult.shouldContain("dataConnect=${mutationRefImpl.dataConnect}")
          toStringResult.shouldContain("operationName=${mutationRefImpl.operationName}")
          toStringResult.shouldContain("variables=${mutationRefImpl.variables}")
          toStringResult.shouldContain("dataDeserializer=${mutationRefImpl.dataDeserializer}")
          toStringResult.shouldContain("variablesSerializer=${mutationRefImpl.variablesSerializer}")
        }
      }
    }
  }

  private companion object {
    val stringArb = Arb.string(6, codepoints = Codepoint.alphanumeric())

    fun Arb.Companion.testVariables(): Arb<TestVariables> = arbitrary {
      val stringArb = Arb.string(6, Codepoint.alphanumeric())
      TestVariables(stringArb.bind())
    }

    fun Arb.Companion.testData(): Arb<TestData> = arbitrary {
      val stringArb = Arb.string(6, Codepoint.alphanumeric())
      TestData(stringArb.bind())
    }

    fun Arb.Companion.mutationRefImpl(): Arb<MutationRefImpl<TestData, TestVariables>> =
      mutationRefImpl<TestData, TestVariables>(Arb.testVariables()).map {
        it.copy(
          variablesSerializer = serializer<TestVariables>(),
          dataDeserializer = serializer<TestData>()
        )
      }

    fun TestScope.dataConnectWithMutationResult(
      result: Result<OperationResult>,
      requestIdSlot: CapturingSlot<String> = slot(),
      operationNameSlot: CapturingSlot<String> = slot(),
      variablesSlot: CapturingSlot<Struct> = slot(),
      isFromGeneratedSdkSlot: CapturingSlot<Boolean> = slot(),
    ): FirebaseDataConnectInternal =
      mockk<FirebaseDataConnectInternal>(relaxed = true) {
        every { blockingDispatcher } returns UnconfinedTestDispatcher(testScheduler)
        every { lazyGrpcClient } returns
          SuspendingLazy {
            mockk<DataConnectGrpcClient> {
              coEvery {
                executeMutation(
                  capture(requestIdSlot),
                  capture(operationNameSlot),
                  capture(variablesSlot),
                  capture(isFromGeneratedSdkSlot),
                )
              } returns result.getOrThrow()
            }
          }
      }
  }
}
