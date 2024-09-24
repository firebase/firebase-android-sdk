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

import com.google.firebase.dataconnect.querymgr.QueryManager
import com.google.firebase.dataconnect.testutil.callerSdkType
import com.google.firebase.dataconnect.testutil.filterNotEqual
import com.google.firebase.dataconnect.testutil.queryRefImpl
import com.google.firebase.dataconnect.util.SequencedReference
import com.google.firebase.dataconnect.util.SuspendingLazy
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.retry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlin.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Test

@Suppress("ReplaceCallWithBinaryOperator")
class QueryRefImplUnitTest {

  private class TestData(val foo: String)
  private class TestVariables(val bar: String)

  @Test
  fun `execute() returns the result on success`() = runTest {
    val data = TestData("gy54w6f5be")
    val querySlot = slot<QueryRefImpl<TestData, TestVariables>>()
    val dataConnect = dataConnectWithQueryResult(Result.success(data), querySlot)
    val queryRefImpl = Arb.queryRefImpl().next().copy(dataConnect = dataConnect)

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
    val queryRefImpl = Arb.queryRefImpl().next().copy(dataConnect = dataConnect)

    val thrownException = shouldThrow<Exception> { queryRefImpl.execute() }

    assertSoftly {
      thrownException shouldBeSameInstanceAs exception
      querySlot.captured shouldBeSameInstanceAs queryRefImpl
    }
  }

  @Test
  fun `subscribe() should return a QuerySubscription`() = runTest {
    val queryRefImpl = Arb.queryRefImpl().next()

    val querySubscription = queryRefImpl.subscribe()

    querySubscription.query shouldBeSameInstanceAs queryRefImpl
  }

  @Test
  fun `subscribe() should always return a new object`() = runTest {
    val queryRefImpl = Arb.queryRefImpl().next()

    val querySubscription1 = queryRefImpl.subscribe()
    val querySubscription2 = queryRefImpl.subscribe()

    querySubscription1 shouldNotBeSameInstanceAs querySubscription2
  }

  @Test
  fun `constructor accepts non-null values`() {
    val values = Arb.queryRefImpl().next()
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
    val values = Arb.queryRefImpl().next()
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
  fun `hashCode() should return the same value when invoked repeatedly`() {
    val queryRefImpl: QueryRefImpl<*, *> = Arb.queryRefImpl().next()
    val hashCode = queryRefImpl.hashCode()
    repeat(10) { queryRefImpl.hashCode() shouldBe hashCode }
  }

  @Test
  fun `hashCode() should return the same value when invoked on distinct, but equal, objects`() {
    val queryRefImpl1: QueryRefImpl<*, *> = Arb.queryRefImpl().next()
    val queryRefImpl2: QueryRefImpl<*, *> = queryRefImpl1.copy()
    queryRefImpl1 shouldNotBeSameInstanceAs queryRefImpl2 // verify test precondition
    repeat(10) { queryRefImpl1.hashCode() shouldBe queryRefImpl2.hashCode() }
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
  fun `hashCode() should incorporate callerSdkType`() = runTest {
    verifyHashCodeEventuallyDiffers {
      it.copy(callerSdkType = Arb.callerSdkType().filterNotEqual(it.callerSdkType).next())
    }
  }

  @Test
  fun `hashCode() should incorporate variablesSerializersModule`() = runTest {
    verifyHashCodeEventuallyDiffers {
      it.copy(variablesSerializersModule = mockk(name = stringArb.next()))
    }
    verifyHashCodeEventuallyDiffers {
      it.copy(
        variablesSerializersModule =
          if (it.variablesSerializersModule === null) mockk(name = stringArb.next()) else null
      )
    }
  }

  @Test
  fun `hashCode() should incorporate dataSerializersModule`() = runTest {
    verifyHashCodeEventuallyDiffers {
      it.copy(dataSerializersModule = mockk(name = stringArb.next()))
    }
    verifyHashCodeEventuallyDiffers {
      it.copy(
        dataSerializersModule =
          if (it.dataSerializersModule === null) mockk(name = stringArb.next()) else null
      )
    }
  }

  private suspend fun verifyHashCodeEventuallyDiffers(
    otherFactory:
      (other: QueryRefImpl<TestData, TestVariables>) -> QueryRefImpl<TestData, TestVariables>
  ) {
    val obj1: QueryRefImpl<TestData, TestVariables> = Arb.queryRefImpl().next()
    retry(maxRetry = 50, timeout = Duration.INFINITE) {
      val obj2: QueryRefImpl<TestData, TestVariables> = otherFactory(obj1)
      obj1.hashCode() shouldNotBe obj2.hashCode()
    }
  }

  @Test
  fun `equals(this) should return true`() = runTest {
    val queryRefImpl: QueryRefImpl<TestData, TestVariables> = Arb.queryRefImpl().next()
    queryRefImpl.equals(queryRefImpl) shouldBe true
  }

  @Test
  fun `equals(equal, but distinct, instance) should return true`() = runTest {
    val queryRefImpl1: QueryRefImpl<TestData, TestVariables> = Arb.queryRefImpl().next()
    val queryRefImpl2: QueryRefImpl<TestData, TestVariables> = queryRefImpl1.copy()
    queryRefImpl1 shouldNotBeSameInstanceAs queryRefImpl2 // verify test precondition
    queryRefImpl1.equals(queryRefImpl2) shouldBe true
  }

  @Test
  fun `equals(null) should return false`() = runTest {
    val queryRefImpl: QueryRefImpl<TestData, TestVariables> = Arb.queryRefImpl().next()
    queryRefImpl.equals(null) shouldBe false
  }

  @Test
  fun `equals(an object of a different type) should return false`() = runTest {
    val queryRefImpl: QueryRefImpl<TestData, TestVariables> = Arb.queryRefImpl().next()
    queryRefImpl.equals("not a QueryRefImpl") shouldBe false
  }

  @Test
  fun `equals() should return false when only dataConnect differs`() = runTest {
    val queryRefImpl1: QueryRefImpl<TestData, TestVariables> = Arb.queryRefImpl().next()
    val queryRefImpl2 = queryRefImpl1.copy(dataConnect = mockk(stringArb.next()))
    queryRefImpl1.equals(queryRefImpl2) shouldBe false
  }

  @Test
  fun `equals() should return false when only operationName differs`() = runTest {
    val queryRefImpl1: QueryRefImpl<TestData, TestVariables> = Arb.queryRefImpl().next()
    val queryRefImpl2 = queryRefImpl1.copy(operationName = queryRefImpl1.operationName + "2")
    queryRefImpl1.equals(queryRefImpl2) shouldBe false
  }

  @Test
  fun `equals() should return false when only variables differs`() = runTest {
    val queryRefImpl1: QueryRefImpl<TestData, TestVariables> = Arb.queryRefImpl().next()
    val queryRefImpl2 =
      queryRefImpl1.copy(variables = TestVariables(queryRefImpl1.variables.bar + "2"))
    queryRefImpl1.equals(queryRefImpl2) shouldBe false
  }

  @Test
  fun `equals() should return false when only dataDeserializer differs`() = runTest {
    val queryRefImpl1: QueryRefImpl<TestData, TestVariables> = Arb.queryRefImpl().next()
    val queryRefImpl2 = queryRefImpl1.copy(dataDeserializer = mockk(stringArb.next()))
    queryRefImpl1.equals(queryRefImpl2) shouldBe false
  }

  @Test
  fun `equals() should return false when only variablesSerializer differs`() = runTest {
    val queryRefImpl1: QueryRefImpl<TestData, TestVariables> = Arb.queryRefImpl().next()
    val queryRefImpl2 = queryRefImpl1.copy(variablesSerializer = mockk(stringArb.next()))
    queryRefImpl1.equals(queryRefImpl2) shouldBe false
  }

  @Test
  fun `equals() should return false when only callerSdkType differs`() = runTest {
    val queryRefImpl1: QueryRefImpl<TestData, TestVariables> = Arb.queryRefImpl().next()
    val callerSdkType2 = Arb.callerSdkType().filterNotEqual(queryRefImpl1.callerSdkType).next()
    val queryRefImpl2 = queryRefImpl1.copy(callerSdkType = callerSdkType2)
    queryRefImpl1.equals(queryRefImpl2) shouldBe false
  }

  @Test
  fun `equals() should return false when only variablesSerializersModule differs`() = runTest {
    val queryRefImpl1: QueryRefImpl<TestData, TestVariables> = Arb.queryRefImpl().next()
    val queryRefImpl2 = queryRefImpl1.copy(variablesSerializersModule = mockk(stringArb.next()))
    val queryRefImplNull = queryRefImpl1.copy(variablesSerializersModule = null)
    queryRefImpl1.equals(queryRefImpl2) shouldBe false
    queryRefImplNull.equals(queryRefImpl1) shouldBe false
    queryRefImpl1.equals(queryRefImplNull) shouldBe false
  }

  @Test
  fun `equals() should return false when only dataSerializersModule differs`() = runTest {
    val queryRefImpl1: QueryRefImpl<TestData, TestVariables> = Arb.queryRefImpl().next()
    val queryRefImpl2 = queryRefImpl1.copy(dataSerializersModule = mockk(stringArb.next()))
    val queryRefImplNull = queryRefImpl1.copy(dataSerializersModule = null)
    queryRefImpl1.equals(queryRefImpl2) shouldBe false
    queryRefImplNull.equals(queryRefImpl1) shouldBe false
    queryRefImpl1.equals(queryRefImplNull) shouldBe false
  }

  @Test
  fun `toString() should incorporate the string representations of public properties`() = runTest {
    val queryRefImpl: QueryRefImpl<TestData, TestVariables> = Arb.queryRefImpl().next()
    val callerSdkType2 = Arb.callerSdkType().filterNotEqual(queryRefImpl.callerSdkType).next()
    val queryRefImpls =
      listOf(
        queryRefImpl,
        queryRefImpl.copy(callerSdkType = callerSdkType2),
        queryRefImpl.copy(dataSerializersModule = null),
        queryRefImpl.copy(variablesSerializersModule = null),
      )
    val toStringResult = queryRefImpl.toString()

    assertSoftly {
      queryRefImpls.forEach {
        it.asClue {
          toStringResult.shouldContain("dataConnect=${queryRefImpl.dataConnect}")
          toStringResult.shouldContain("operationName=${queryRefImpl.operationName}")
          toStringResult.shouldContain("variables=${queryRefImpl.variables}")
          toStringResult.shouldContain("dataDeserializer=${queryRefImpl.dataDeserializer}")
          toStringResult.shouldContain("variablesSerializer=${queryRefImpl.variablesSerializer}")
          toStringResult.shouldContain("callerSdkType=${queryRefImpl.callerSdkType}")
          toStringResult.shouldContain(
            "dataSerializersModule=${queryRefImpl.dataSerializersModule}"
          )
          toStringResult.shouldContain(
            "variablesSerializersModule=${queryRefImpl.variablesSerializersModule}"
          )
        }
      }
    }
  }

  @Test
  fun `toString() should include null when dataSerializersModule is null`() = runTest {
    val queryRefImpl: QueryRefImpl<TestData, TestVariables> =
      Arb.queryRefImpl().next().copy(dataSerializersModule = null)
    val toStringResult = queryRefImpl.toString()

    assertSoftly {
      toStringResult.shouldContain("dataConnect=${queryRefImpl.dataConnect}")
      toStringResult.shouldContain("operationName=${queryRefImpl.operationName}")
      toStringResult.shouldContain("variables=${queryRefImpl.variables}")
      toStringResult.shouldContain("dataDeserializer=${queryRefImpl.dataDeserializer}")
      toStringResult.shouldContain("variablesSerializer=${queryRefImpl.variablesSerializer}")
      toStringResult.shouldContain("callerSdkType=${queryRefImpl.callerSdkType}")
      toStringResult.shouldContain("dataSerializersModule=null")
      toStringResult.shouldContain(
        "variablesSerializersModule=${queryRefImpl.variablesSerializersModule}"
      )
    }
  }

  @Test
  fun `toString() should include null when variablesSerializersModule is null`() = runTest {
    val queryRefImpl: QueryRefImpl<TestData, TestVariables> =
      Arb.queryRefImpl().next().copy(variablesSerializersModule = null)
    val toStringResult = queryRefImpl.toString()

    assertSoftly {
      toStringResult.shouldContain("dataConnect=${queryRefImpl.dataConnect}")
      toStringResult.shouldContain("operationName=${queryRefImpl.operationName}")
      toStringResult.shouldContain("variables=${queryRefImpl.variables}")
      toStringResult.shouldContain("dataDeserializer=${queryRefImpl.dataDeserializer}")
      toStringResult.shouldContain("variablesSerializer=${queryRefImpl.variablesSerializer}")
      toStringResult.shouldContain("callerSdkType=${queryRefImpl.callerSdkType}")
      toStringResult.shouldContain("dataSerializersModule=${queryRefImpl.dataSerializersModule}")
      toStringResult.shouldContain("variablesSerializersModule=null")
    }
  }

  private companion object {
    val stringArb = Arb.string(6, codepoints = Codepoint.alphanumeric())

    fun Arb.Companion.testVariables(): Arb<TestVariables> = arbitrary {
      val stringArb = Arb.string(6, Codepoint.alphanumeric())
      TestVariables(stringArb.bind())
    }

    fun Arb.Companion.queryRefImpl(): Arb<QueryRefImpl<TestData, TestVariables>> =
      queryRefImpl(Arb.testVariables())

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
