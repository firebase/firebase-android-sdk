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

import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.core.Globals.copy
import com.google.firebase.dataconnect.querymgr.QueryManager
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnectError
import com.google.firebase.dataconnect.testutil.property.arbitrary.mock
import com.google.firebase.dataconnect.testutil.property.arbitrary.queryRefImpl
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.util.SequencedReference
import com.google.firebase.dataconnect.util.SuspendingLazy
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.retry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.junit.Test

@Suppress("ReplaceCallWithBinaryOperator")
class QueryRefImplUnitTest {

  @Serializable private class TestData(@Suppress("unused") val foo: String)
  @Serializable private class TestVariables(@Suppress("unused") val bar: String)

  @Test
  fun `execute() returns the result on success`() = runTest {
    val data = TestData("gy54w6f5be")
    val querySlot = slot<QueryRefImpl<TestData, TestVariables>>()
    val dataConnect = dataConnectWithQueryResult(Result.success(data), querySlot)
    val queryRefImpl = Arb.dataConnect.queryRefImpl(dataConnect).next()

    val queryResult = queryRefImpl.execute()

    assertSoftly {
      queryResult.ref shouldBeSameInstanceAs queryRefImpl
      queryResult.data shouldBe data
      querySlot.captured shouldBeSameInstanceAs queryRefImpl
    }
  }

  @Test
  fun `execute() throws on failure`() = runTest {
    val exception = Exception("forced exception h4sab92yy8")
    val querySlot = slot<QueryRefImpl<TestData, TestVariables>>()
    val dataConnect = dataConnectWithQueryResult(Result.failure(exception), querySlot)
    val queryRefImpl = Arb.dataConnect.queryRefImpl(dataConnect).next()

    val thrownException = shouldThrow<Exception> { queryRefImpl.execute() }

    assertSoftly {
      thrownException shouldBeSameInstanceAs exception
      querySlot.captured shouldBeSameInstanceAs queryRefImpl
    }
  }

  @Test
  fun `subscribe() should return a QuerySubscription`() = runTest {
    val queryRefImpl = Arb.dataConnect.queryRefImpl().next()

    val querySubscription = queryRefImpl.subscribe()

    querySubscription.query shouldBeSameInstanceAs queryRefImpl
  }

  @Test
  fun `subscribe() should always return a new object`() = runTest {
    val queryRefImpl = Arb.dataConnect.queryRefImpl().next()

    val querySubscription1 = queryRefImpl.subscribe()
    val querySubscription2 = queryRefImpl.subscribe()

    querySubscription1 shouldNotBeSameInstanceAs querySubscription2
  }

  @Test
  fun `constructor accepts non-null values`() {
    val values = Arb.dataConnect.queryRefImpl().next()
    val queryRefImpl =
      QueryRefImpl(
        dataConnect = values.dataConnect,
        operationName = values.operationName,
        variables = values.variables,
        dataDeserializer = values.dataDeserializer,
        variablesSerializer = values.variablesSerializer,
        callerSdkType = values.callerSdkType,
        variablesSerializersModule = values.variablesSerializersModule,
        dataSerializersModule = values.dataSerializersModule,
      )

    queryRefImpl.asClue {
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
    val values = Arb.dataConnect.queryRefImpl().next()
    val queryRefImpl =
      QueryRefImpl(
        dataConnect = values.dataConnect,
        operationName = values.operationName,
        variables = values.variables,
        dataDeserializer = values.dataDeserializer,
        variablesSerializer = values.variablesSerializer,
        callerSdkType = values.callerSdkType,
        variablesSerializersModule = null,
        dataSerializersModule = null,
      )

    queryRefImpl.asClue {
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
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl()) { queryRefImpl ->
      val hashCode1 = queryRefImpl.hashCode()
      repeat(3) { queryRefImpl.hashCode() shouldBe hashCode1 }
    }
  }

  @Test
  fun `hashCode() should return the same value when invoked on distinct, but equal, objects`() =
    runTest {
      checkAll(propTestConfig, Arb.dataConnect.queryRefImpl()) { queryRefImpl1 ->
        val queryRefImpl2 = queryRefImpl1.copy()
        queryRefImpl1.hashCode() shouldBe queryRefImpl2.hashCode()
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
      (other: QueryRefImpl<TestData?, TestVariables>) -> QueryRefImpl<TestData?, TestVariables>
  ) {
    val obj1: QueryRefImpl<TestData?, TestVariables> = Arb.dataConnect.queryRefImpl().next()
    retry(maxRetry = 50, timeout = Duration.INFINITE) {
      val obj2: QueryRefImpl<TestData?, TestVariables> = otherFactory(obj1)
      obj1.hashCode() shouldNotBe obj2.hashCode()
    }
  }

  @Test
  fun `equals(this) should return true`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl()) { queryRefImpl ->
      queryRefImpl.equals(queryRefImpl) shouldBe true
    }
  }

  @Test
  fun `equals(equal, but distinct, instance) should return true`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl()) { queryRefImpl1 ->
      val queryRefImpl2 = queryRefImpl1.copy()
      queryRefImpl1.equals(queryRefImpl2) shouldBe true
    }
  }

  @Test
  fun `equals(null) should return false`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl()) { queryRefImpl ->
      queryRefImpl.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals(an object of a different type) should return false`() = runTest {
    val others = Arb.choice(Arb.dataConnect.string(), Arb.int(), Arb.dataConnect.dataConnectError())
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), others) { queryRefImpl, other ->
      queryRefImpl.equals(other) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only dataConnect differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.queryRefImpl(),
      Arb.mock<FirebaseDataConnectInternal>()
    ) { queryRefImpl1, dataConnect ->
      dataConnect shouldNotBe queryRefImpl1.dataConnect // precondition check
      val queryRefImpl2 = queryRefImpl1.copy(dataConnect = dataConnect)
      queryRefImpl1.equals(queryRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only operationName differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), Arb.dataConnect.string()) {
      queryRefImpl1,
      operationName ->
      assume(operationName != queryRefImpl1.operationName)
      val queryRefImpl2 = queryRefImpl1.copy(operationName = operationName)
      queryRefImpl1.equals(queryRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only variables differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), Arb.dataConnect.testVariables()) {
      queryRefImpl1,
      variables ->
      assume(variables != queryRefImpl1.variables)
      val queryRefImpl2 = queryRefImpl1.copy(variables = variables)
      queryRefImpl1.equals(queryRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only dataDeserializer differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.queryRefImpl(),
      Arb.mock<DeserializationStrategy<TestData>>()
    ) { queryRefImpl1, dataDeserializer ->
      dataDeserializer shouldNotBe queryRefImpl1.dataDeserializer // precondition check
      val queryRefImpl2 = queryRefImpl1.copy(dataDeserializer = dataDeserializer)
      queryRefImpl1.equals(queryRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only variablesSerializer differs`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.queryRefImpl(),
      Arb.mock<SerializationStrategy<TestVariables>>()
    ) { queryRefImpl1, variablesSerializer ->
      variablesSerializer shouldNotBe queryRefImpl1.variablesSerializer // precondition check
      val queryRefImpl2 = queryRefImpl1.copy(variablesSerializer = variablesSerializer)
      queryRefImpl1.equals(queryRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only callerSdkType differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), Arb.enum<CallerSdkType>()) {
      queryRefImpl1,
      callerSdkType ->
      val queryRefImpl2 = queryRefImpl1.copy(callerSdkType = callerSdkType)
      queryRefImpl1.equals(queryRefImpl2) shouldBe (callerSdkType == queryRefImpl1.callerSdkType)
    }
  }

  @Test
  fun `equals() should return false when only variablesSerializersModule differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), Arb.mock<SerializersModule>()) {
      queryRefImpl1,
      variablesSerializersModule ->
      variablesSerializersModule shouldNotBe
        queryRefImpl1.variablesSerializersModule // precondition check
      val queryRefImpl2 =
        queryRefImpl1.copy(variablesSerializersModule = variablesSerializersModule)
      queryRefImpl1.equals(queryRefImpl2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when only dataSerializersModule differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl(), Arb.mock<SerializersModule>()) {
      queryRefImpl1,
      dataSerializersModule ->
      dataSerializersModule shouldNotBe queryRefImpl1.dataSerializersModule // precondition check
      val queryRefImpl2 = queryRefImpl1.copy(dataSerializersModule = dataSerializersModule)
      queryRefImpl1.equals(queryRefImpl2) shouldBe false
    }
  }

  @Test
  fun `toString() should incorporate the string representations of public properties`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.queryRefImpl()) { queryRefImpl ->
      val toStringResult = queryRefImpl.toString()
      assertSoftly {
        toStringResult shouldContainWithNonAbuttingText "dataConnect=${queryRefImpl.dataConnect}"
        toStringResult shouldContainWithNonAbuttingText
          "operationName=${queryRefImpl.operationName}"
        toStringResult shouldContainWithNonAbuttingText "variables=${queryRefImpl.variables}"
        toStringResult shouldContainWithNonAbuttingText
          "dataDeserializer=${queryRefImpl.dataDeserializer}"
        toStringResult shouldContainWithNonAbuttingText
          "variablesSerializer=${queryRefImpl.variablesSerializer}"
        toStringResult shouldContainWithNonAbuttingText
          "callerSdkType=${queryRefImpl.callerSdkType}"
        toStringResult shouldContainWithNonAbuttingText
          "dataSerializersModule=${queryRefImpl.dataSerializersModule}"
        toStringResult shouldContainWithNonAbuttingText
          "variablesSerializersModule=${queryRefImpl.variablesSerializersModule}"
      }
    }
  }

  private companion object {
    val propTestConfig = PropTestConfig(iterations = 20)

    fun DataConnectArb.testVariables(string: Arb<String> = string()): Arb<TestVariables> =
      arbitrary {
        TestVariables(string.bind())
      }

    fun DataConnectArb.queryRefImpl(): Arb<QueryRefImpl<TestData?, TestVariables>> =
      queryRefImpl(
        Arb.dataConnect.testVariables(),
        dataDeserializer = Arb.constant(serializer()),
        variablesSerializer = Arb.constant(serializer()),
      )

    fun DataConnectArb.queryRefImpl(
      dataConnect: FirebaseDataConnectInternal
    ): Arb<QueryRefImpl<TestData?, TestVariables>> =
      queryRefImpl().map { it.copy(dataConnect = dataConnect) }

    fun <Data, Variables> dataConnectWithQueryResult(
      result: Result<Data>,
      querySlot: CapturingSlot<QueryRefImpl<Data, Variables>>
    ): FirebaseDataConnectInternal =
      mockk<FirebaseDataConnectInternal>(relaxed = true) {
        every { lazyQueryManager } returns
          SuspendingLazy {
            mockk<QueryManager> {
              coEvery { execute(capture(querySlot)) } returns SequencedReference(123, result)
            }
          }
      }
  }
}
