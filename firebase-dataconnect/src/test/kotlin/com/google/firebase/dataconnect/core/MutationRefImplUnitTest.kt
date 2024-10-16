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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.DataConnectException
import com.google.firebase.dataconnect.DataConnectUntypedData
import com.google.firebase.dataconnect.DataConnectUntypedVariables
import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.core.DataConnectGrpcClient.OperationResult
import com.google.firebase.dataconnect.core.Globals.copy
import com.google.firebase.dataconnect.core.Globals.withDataDeserializer
import com.google.firebase.dataconnect.core.Globals.withVariablesSerializer
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnectError
import com.google.firebase.dataconnect.testutil.property.arbitrary.mock
import com.google.firebase.dataconnect.testutil.property.arbitrary.mutationRefImpl
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToStruct
import com.google.firebase.dataconnect.util.ProtoUtil.toStructProto
import com.google.firebase.dataconnect.util.SuspendingLazy
import com.google.protobuf.Struct
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.retry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.assume
import io.kotest.property.checkAll
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
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.junit.Test

@Suppress("ReplaceCallWithBinaryOperator")
class MutationRefImplUnitTest {

  @Serializable private data class TestData(val foo: String)
  @Serializable private data class TestVariables(val bar: String)

  @Test
  fun `execute() returns the result on success`() = runTest {
    val data = Arb.dataConnect.testData().next()
    val operationResult = OperationResult(encodeToStruct(data), errors = emptyList())
    val dataConnect = dataConnectWithMutationResult(Result.success(operationResult))
    val mutationRefImpl = Arb.dataConnect.mutationRefImpl(dataConnect).next()

    val mutationResult = mutationRefImpl.execute()

    assertSoftly {
      mutationResult.ref shouldBeSameInstanceAs mutationRefImpl
      mutationResult.data shouldBe data
    }
  }

  @Test
  fun `execute() calls executeMutation with the correct arguments`() = runTest {
    val data = Arb.dataConnect.testData().next()
    val operationResult = OperationResult(encodeToStruct(data), errors = emptyList())
    val requestIdSlot: CapturingSlot<String> = slot()
    val operationNameSlot: CapturingSlot<String> = slot()
    val variablesSlot: CapturingSlot<Struct> = slot()
    val callerSdkTypeSlot: CapturingSlot<CallerSdkType> = slot()
    val dataConnect =
      dataConnectWithMutationResult(
        Result.success(operationResult),
        requestIdSlot,
        operationNameSlot,
        variablesSlot,
        callerSdkTypeSlot,
      )
    val mutationRefImpl = Arb.dataConnect.mutationRefImpl(dataConnect).next()

    mutationRefImpl.execute()
    val requestId1 = requestIdSlot.captured
    val operationName1 = operationNameSlot.captured
    val variables1 = variablesSlot.captured
    val callerSdkType1 = callerSdkTypeSlot.captured

    requestIdSlot.clear()
    operationNameSlot.clear()
    variablesSlot.clear()
    callerSdkTypeSlot.clear()
    mutationRefImpl.execute()
    val requestId2 = requestIdSlot.captured
    val operationName2 = operationNameSlot.captured
    val variables2 = variablesSlot.captured
    val callerSdkType2 = callerSdkTypeSlot.captured

    assertSoftly {
      requestId1.shouldNotBeBlank()
      requestId2.shouldNotBeBlank()
      requestId1 shouldNotBe requestId2
      operationName1 shouldBe mutationRefImpl.operationName
      operationName2 shouldBe operationName1
      variables1 shouldBe encodeToStruct(mutationRefImpl.variables)
      variables2 shouldBe variables1
      callerSdkType1 shouldBe mutationRefImpl.callerSdkType
      callerSdkType2 shouldBe mutationRefImpl.callerSdkType
    }
  }

  @Test
  fun `execute() handles DataConnectUntypedVariables and DataConnectUntypedData`() = runTest {
    val variables = DataConnectUntypedVariables("foo" to 42.0)
    val errors = listOf(Arb.dataConnect.dataConnectError().next())
    val data = DataConnectUntypedData(mapOf("bar" to 24.0), errors)
    val variablesSlot: CapturingSlot<Struct> = slot()
    val operationResult = OperationResult(buildStructProto { put("bar", 24.0) }, errors)
    val dataConnect =
      dataConnectWithMutationResult(Result.success(operationResult), variablesSlot = variablesSlot)
    val mutationRefImpl =
      Arb.dataConnect
        .mutationRefImpl(dataConnect)
        .next()
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
    val mutationRefImpl = Arb.dataConnect.mutationRefImpl(dataConnect).next()

    shouldThrow<DataConnectException> { mutationRefImpl.execute() }
  }

  @Test
  fun `constructor accepts non-null values`() {
    val values = Arb.dataConnect.mutationRefImpl().next()
    val mutationRefImpl =
      MutationRefImpl(
        dataConnect = values.dataConnect,
        operationName = values.operationName,
        variables = values.variables,
        dataDeserializer = values.dataDeserializer,
        variablesSerializer = values.variablesSerializer,
        callerSdkType = values.callerSdkType,
        variablesSerializersModule = values.variablesSerializersModule,
        dataSerializersModule = values.dataSerializersModule,
      )

    mutationRefImpl.asClue {
      assertSoftly {
        it.dataConnect shouldBeSameInstanceAs values.dataConnect
        it.operationName shouldBeSameInstanceAs values.operationName
        it.variables shouldBeSameInstanceAs values.variables
        it.dataDeserializer shouldBeSameInstanceAs values.dataDeserializer
        it.variablesSerializer shouldBeSameInstanceAs values.variablesSerializer
        it.callerSdkType shouldBe values.callerSdkType
        it.variablesSerializersModule shouldBeSameInstanceAs values.variablesSerializersModule
        it.dataSerializersModule shouldBeSameInstanceAs values.dataSerializersModule
      }
    }
  }

  @Test
  fun `constructor accepts null values for nullable parameters`() {
    val values = Arb.dataConnect.mutationRefImpl().next()
    val mutationRefImpl =
      MutationRefImpl(
        dataConnect = values.dataConnect,
        operationName = values.operationName,
        variables = values.variables,
        dataDeserializer = values.dataDeserializer,
        variablesSerializer = values.variablesSerializer,
        callerSdkType = values.callerSdkType,
        variablesSerializersModule = null,
        dataSerializersModule = null,
      )

    mutationRefImpl.asClue {
      assertSoftly {
        it.dataConnect shouldBeSameInstanceAs values.dataConnect
        it.operationName shouldBeSameInstanceAs values.operationName
        it.variables shouldBeSameInstanceAs values.variables
        it.dataDeserializer shouldBeSameInstanceAs values.dataDeserializer
        it.variablesSerializer shouldBeSameInstanceAs values.variablesSerializer
        it.callerSdkType shouldBe values.callerSdkType
        it.variablesSerializersModule.shouldBeNull()
        it.dataSerializersModule.shouldBeNull()
      }
    }
  }

  @Test
  fun `hashCode() should return the same value when invoked repeatedly`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl()) { mutationRefImpl ->
      val hashCode1 = mutationRefImpl.hashCode()
      repeat(3) { mutationRefImpl.hashCode() shouldBe hashCode1 }
    }
  }

  @Test
  fun `hashCode() should return the same value when invoked on distinct, but equal, objects`() =
    runTest {
      checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl()) { mutationRefImpl1 ->
        val mutationRefImpl2 = mutationRefImpl1.copy()
        mutationRefImpl1.hashCode() shouldBe mutationRefImpl2.hashCode()
      }
    }

  @Test
  fun `hashCode() should incorporate dataConnect`() = runTest {
    verifyHashCodeEventuallyDiffers {
      it.copy(dataConnect = mockk(name = Arb.dataConnect.string().next()))
    }
  }

  @Test
  fun `hashCode() should incorporate operationName`() = runTest {
    verifyHashCodeEventuallyDiffers { it.copy(operationName = Arb.dataConnect.string().next()) }
  }

  @Test
  fun `hashCode() should incorporate variables`() = runTest {
    verifyHashCodeEventuallyDiffers { it.copy(variables = Arb.dataConnect.testVariables().next()) }
  }

  @Test
  fun `hashCode() should incorporate dataDeserializer`() = runTest {
    verifyHashCodeEventuallyDiffers {
      it.copy(dataDeserializer = mockk(name = Arb.dataConnect.string().next()))
    }
  }

  @Test
  fun `hashCode() should incorporate variablesSerializer`() = runTest {
    verifyHashCodeEventuallyDiffers {
      it.copy(variablesSerializer = mockk(name = Arb.dataConnect.string().next()))
    }
  }

  @Test
  fun `hashCode() should incorporate callerSdkType`() = runTest {
    verifyHashCodeEventuallyDiffers { it.copy(callerSdkType = Arb.enum<CallerSdkType>().next()) }
  }

  @Test
  fun `hashCode() should incorporate variablesSerializersModule`() = runTest {
    verifyHashCodeEventuallyDiffers {
      it.copy(variablesSerializersModule = mockk(name = Arb.dataConnect.string().next()))
    }
    verifyHashCodeEventuallyDiffers {
      it.copy(
        variablesSerializersModule =
          if (it.variablesSerializersModule === null) mockk(name = Arb.dataConnect.string().next())
          else null
      )
    }
  }

  @Test
  fun `hashCode() should incorporate dataSerializersModule`() = runTest {
    verifyHashCodeEventuallyDiffers {
      it.copy(dataSerializersModule = mockk(name = Arb.dataConnect.string().next()))
    }
    verifyHashCodeEventuallyDiffers {
      it.copy(
        dataSerializersModule =
          if (it.dataSerializersModule === null) mockk(name = Arb.dataConnect.string().next())
          else null
      )
    }
  }

  private suspend fun verifyHashCodeEventuallyDiffers(
    otherFactory:
      (other: MutationRefImpl<TestData?, TestVariables>) -> MutationRefImpl<
          TestData?, TestVariables
        >
  ) {
    val obj1: MutationRefImpl<TestData?, TestVariables> = Arb.dataConnect.mutationRefImpl().next()
    retry(maxRetry = 50, timeout = Duration.INFINITE) {
      val obj2: MutationRefImpl<TestData?, TestVariables> = otherFactory(obj1)
      obj1.hashCode() shouldNotBe obj2.hashCode()
    }
  }

  @Test
  fun `equals(this) should return true`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl()) { mutationRefImpl ->
      mutationRefImpl.equals(mutationRefImpl) shouldBe true
    }
  }

  @Test
  fun `equals(equal, but distinct, instance) should return true`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl()) { mutationRefImpl1 ->
      val mutationRefImpl2 = mutationRefImpl1.copy()
      mutationRefImpl1.equals(mutationRefImpl2) shouldBe true
    }
  }

  @Test
  fun `equals(null) should return false`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl()) { mutationRefImpl ->
      mutationRefImpl.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals(an object of a different type) should return false`() = runTest {
    val others = Arb.choice(Arb.dataConnect.string(), Arb.int(), Arb.dataConnect.dataConnectError())
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl(), others) { mutationRefImpl, other ->
      mutationRefImpl.equals(other) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only dataConnect differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.mutationRefImpl(),
      Arb.mock<FirebaseDataConnectInternal>()
    ) { mutationRefImpl1, dataConnect ->
      dataConnect shouldNotBe mutationRefImpl1.dataConnect // precondition check
      val mutationRefImpl2 = mutationRefImpl1.copy(dataConnect = dataConnect)
      mutationRefImpl1.equals(mutationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only operationName differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl(), Arb.dataConnect.string()) {
      mutationRefImpl1,
      operationName ->
      assume(operationName != mutationRefImpl1.operationName)
      val mutationRefImpl2 = mutationRefImpl1.copy(operationName = operationName)
      mutationRefImpl1.equals(mutationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only variables differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl(), Arb.dataConnect.testVariables()) {
      mutationRefImpl1,
      variables ->
      assume(variables != mutationRefImpl1.variables)
      val mutationRefImpl2 = mutationRefImpl1.copy(variables = variables)
      mutationRefImpl1.equals(mutationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only dataDeserializer differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.mutationRefImpl(),
      Arb.mock<DeserializationStrategy<TestData>>()
    ) { mutationRefImpl1, dataDeserializer ->
      dataDeserializer shouldNotBe mutationRefImpl1.dataDeserializer // precondition check
      val mutationRefImpl2 = mutationRefImpl1.copy(dataDeserializer = dataDeserializer)
      mutationRefImpl1.equals(mutationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only variablesSerializer differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.mutationRefImpl(),
      Arb.mock<SerializationStrategy<TestVariables>>()
    ) { mutationRefImpl1, variablesSerializer ->
      variablesSerializer shouldNotBe mutationRefImpl1.variablesSerializer // precondition check
      val mutationRefImpl2 = mutationRefImpl1.copy(variablesSerializer = variablesSerializer)
      mutationRefImpl1.equals(mutationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only callerSdkType differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl(), Arb.enum<CallerSdkType>()) {
      mutationRefImpl1,
      callerSdkType ->
      val mutationRefImpl2 = mutationRefImpl1.copy(callerSdkType = callerSdkType)
      mutationRefImpl1.equals(mutationRefImpl2) shouldBe
        (callerSdkType == mutationRefImpl1.callerSdkType)
    }
  }

  @Test
  fun `equals() should return false when only variablesSerializersModule differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl(), Arb.mock<SerializersModule>()) {
      mutationRefImpl1,
      variablesSerializersModule ->
      variablesSerializersModule shouldNotBe
        mutationRefImpl1.variablesSerializersModule // precondition check
      val mutationRefImpl2 =
        mutationRefImpl1.copy(variablesSerializersModule = variablesSerializersModule)
      mutationRefImpl1.equals(mutationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only dataSerializersModule differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl(), Arb.mock<SerializersModule>()) {
      mutationRefImpl1,
      dataSerializersModule ->
      dataSerializersModule shouldNotBe mutationRefImpl1.dataSerializersModule // precondition check
      val mutationRefImpl2 = mutationRefImpl1.copy(dataSerializersModule = dataSerializersModule)
      mutationRefImpl1.equals(mutationRefImpl2) shouldBe false
    }
  }

  @Test
  fun `toString() should incorporate the string representations of public properties`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.mutationRefImpl()) { mutationRefImpl ->
      val toStringResult = mutationRefImpl.toString()
      assertSoftly {
        toStringResult shouldContainWithNonAbuttingText "dataConnect=${mutationRefImpl.dataConnect}"
        toStringResult shouldContainWithNonAbuttingText
          "operationName=${mutationRefImpl.operationName}"
        toStringResult shouldContainWithNonAbuttingText "variables=${mutationRefImpl.variables}"
        toStringResult shouldContainWithNonAbuttingText
          "dataDeserializer=${mutationRefImpl.dataDeserializer}"
        toStringResult shouldContainWithNonAbuttingText
          "variablesSerializer=${mutationRefImpl.variablesSerializer}"
        toStringResult shouldContainWithNonAbuttingText
          "callerSdkType=${mutationRefImpl.callerSdkType}"
        toStringResult shouldContainWithNonAbuttingText
          "dataSerializersModule=${mutationRefImpl.dataSerializersModule}"
        toStringResult shouldContainWithNonAbuttingText
          "variablesSerializersModule=${mutationRefImpl.variablesSerializersModule}"
      }
    }
  }

  private companion object {
    val propTestConfig = PropTestConfig(iterations = 20)

    fun DataConnectArb.testVariables(string: Arb<String> = string()): Arb<TestVariables> =
      arbitrary {
        TestVariables(string.bind())
      }

    fun DataConnectArb.testData(string: Arb<String> = string()): Arb<TestData> = arbitrary {
      TestData(string.bind())
    }

    fun DataConnectArb.mutationRefImpl(): Arb<MutationRefImpl<TestData?, TestVariables>> =
      mutationRefImpl(
        Arb.dataConnect.testVariables(),
        dataDeserializer = Arb.constant(serializer()),
        variablesSerializer = Arb.constant(serializer()),
      )

    fun DataConnectArb.mutationRefImpl(
      dataConnect: FirebaseDataConnectInternal
    ): Arb<MutationRefImpl<TestData?, TestVariables>> =
      mutationRefImpl().map { it.copy(dataConnect = dataConnect) }

    fun TestScope.dataConnectWithMutationResult(
      result: Result<OperationResult>,
      requestIdSlot: CapturingSlot<String> = slot(),
      operationNameSlot: CapturingSlot<String> = slot(),
      variablesSlot: CapturingSlot<Struct> = slot(),
      callerSdkTypeSlot: CapturingSlot<CallerSdkType> = slot(),
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
                  capture(callerSdkTypeSlot),
                )
              } returns result.getOrThrow()
            }
          }
      }
  }
}
