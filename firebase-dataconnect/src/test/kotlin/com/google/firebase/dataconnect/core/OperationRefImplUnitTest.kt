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
import com.google.firebase.dataconnect.testutil.StubOperationRefImpl
import com.google.firebase.dataconnect.testutil.copy
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.filterNotEqual
import com.google.firebase.dataconnect.testutil.property.arbitrary.operationRefImpl
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.retry
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.next
import io.mockk.mockk
import kotlin.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Test

@Suppress("ReplaceCallWithBinaryOperator")
class OperationRefImplUnitTest {

  private class TestData
  private class TestVariables(val bar: String)

  @Test
  fun `constructor accepts non-null values`() {
    val values = Arb.dataConnect.operationRefImpl().next()
    val operationRefImpl =
      object :
        OperationRefImpl<TestData, TestVariables>(
          dataConnect = values.dataConnect,
          operationName = values.operationName,
          variables = values.variables,
          dataDeserializer = values.dataDeserializer,
          variablesSerializer = values.variablesSerializer,
          callerSdkType = values.callerSdkType,
          variablesSerializersModule = values.variablesSerializersModule,
          dataSerializersModule = values.dataSerializersModule,
        ) {
        override suspend fun execute() = TODO()
      }

    operationRefImpl.asClue {
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
    val values = Arb.dataConnect.operationRefImpl().next()
    val operationRefImpl =
      object :
        OperationRefImpl<TestData, TestVariables>(
          dataConnect = values.dataConnect,
          operationName = values.operationName,
          variables = values.variables,
          dataDeserializer = values.dataDeserializer,
          variablesSerializer = values.variablesSerializer,
          callerSdkType = values.callerSdkType,
          variablesSerializersModule = null,
          dataSerializersModule = null,
        ) {
        override suspend fun execute() = TODO()
      }
    operationRefImpl.asClue {
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
    val operationRefImpl: OperationRefImpl<*, *> = Arb.dataConnect.operationRefImpl().next()
    val hashCode = operationRefImpl.hashCode()
    repeat(10) { operationRefImpl.hashCode() shouldBe hashCode }
  }

  @Test
  fun `hashCode() should return the same value when invoked on distinct, but equal, objects`() {
    val operationRefImpl1 = Arb.dataConnect.operationRefImpl().next()
    val operationRefImpl2 = operationRefImpl1.copy()
    operationRefImpl1 shouldNotBeSameInstanceAs operationRefImpl2 // verify test precondition
    repeat(10) { operationRefImpl1.hashCode() shouldBe operationRefImpl2.hashCode() }
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
    verifyHashCodeEventuallyDiffers {
      it.copy(variables = TestVariables(Arb.dataConnect.string().next()))
    }
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
    verifyHashCodeEventuallyDiffers {
      it.copy(callerSdkType = Arb.enum<CallerSdkType>().filterNotEqual(it.callerSdkType).next())
    }
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
      (other: StubOperationRefImpl<TestData, TestVariables>) -> StubOperationRefImpl<
          TestData, TestVariables
        >
  ) {
    val obj1: StubOperationRefImpl<TestData, TestVariables> =
      Arb.dataConnect.operationRefImpl().next()
    retry(maxRetry = 50, timeout = Duration.INFINITE) {
      val obj2 = otherFactory(obj1)
      obj1.hashCode() shouldNotBe obj2.hashCode()
    }
  }

  @Test
  fun `equals(this) should return true`() = runTest {
    val operationRefImpl = Arb.dataConnect.operationRefImpl().next()
    operationRefImpl.equals(operationRefImpl) shouldBe true
  }

  @Test
  fun `equals(equal, but distinct, instance) should return true`() = runTest {
    val operationRefImpl1 = Arb.dataConnect.operationRefImpl().next()
    val operationRefImpl2 = operationRefImpl1.copy()
    operationRefImpl1 shouldNotBeSameInstanceAs operationRefImpl2 // verify test precondition
    operationRefImpl1.equals(operationRefImpl2) shouldBe true
  }

  @Test
  fun `equals(null) should return false`() = runTest {
    val operationRefImpl = Arb.dataConnect.operationRefImpl().next()
    operationRefImpl.equals(null) shouldBe false
  }

  @Test
  fun `equals(an object of a different type) should return false`() = runTest {
    val operationRefImpl = Arb.dataConnect.operationRefImpl().next()
    operationRefImpl.equals("not an OperationRefImpl") shouldBe false
  }

  @Test
  fun `equals() should return false when only dataConnect differs`() = runTest {
    val operationRefImpl1 = Arb.dataConnect.operationRefImpl().next()
    val operationRefImpl2 =
      operationRefImpl1.copy(dataConnect = mockk(Arb.dataConnect.string().next()))
    operationRefImpl1.equals(operationRefImpl2) shouldBe false
  }

  @Test
  fun `equals() should return false when only operationName differs`() = runTest {
    val operationRefImpl1 = Arb.dataConnect.operationRefImpl().next()
    val operationRefImpl2 =
      operationRefImpl1.copy(operationName = operationRefImpl1.operationName + "2")
    operationRefImpl1.equals(operationRefImpl2) shouldBe false
  }

  @Test
  fun `equals() should return false when only variables differs`() = runTest {
    val operationRefImpl1 = Arb.dataConnect.operationRefImpl().next()
    val operationRefImpl2 =
      operationRefImpl1.copy(variables = TestVariables(operationRefImpl1.variables.bar + "2"))
    operationRefImpl1.equals(operationRefImpl2) shouldBe false
  }

  @Test
  fun `equals() should return false when only dataDeserializer differs`() = runTest {
    val operationRefImpl1 = Arb.dataConnect.operationRefImpl().next()
    val operationRefImpl2 =
      operationRefImpl1.copy(dataDeserializer = mockk(Arb.dataConnect.string().next()))
    operationRefImpl1.equals(operationRefImpl2) shouldBe false
  }

  @Test
  fun `equals() should return false when only variablesSerializer differs`() = runTest {
    val operationRefImpl1 = Arb.dataConnect.operationRefImpl().next()
    val operationRefImpl2 =
      operationRefImpl1.copy(variablesSerializer = mockk(Arb.dataConnect.string().next()))
    operationRefImpl1.equals(operationRefImpl2) shouldBe false
  }

  @Test
  fun `equals() should return false when only callerSdkType differs`() = runTest {
    val operationRefImpl1 = Arb.dataConnect.operationRefImpl().next()
    val callerSdkType2 =
      Arb.enum<CallerSdkType>().filterNotEqual(operationRefImpl1.callerSdkType).next()
    val operationRefImpl2 = operationRefImpl1.copy(callerSdkType = callerSdkType2)
    operationRefImpl1.equals(operationRefImpl2) shouldBe false
  }

  @Test
  fun `equals() should return false when only variablesSerializersModule differs`() = runTest {
    val operationRefImpl1 = Arb.dataConnect.operationRefImpl().next()
    val operationRefImpl2 =
      operationRefImpl1.copy(variablesSerializersModule = mockk(Arb.dataConnect.string().next()))
    val operationRefImplNull = operationRefImpl1.copy(variablesSerializersModule = null)
    operationRefImpl1.equals(operationRefImpl2) shouldBe false
    operationRefImplNull.equals(operationRefImpl2) shouldBe false
    operationRefImpl2.equals(operationRefImplNull) shouldBe false
  }

  @Test
  fun `equals() should return false when only dataSerializersModule differs`() = runTest {
    val operationRefImpl1 = Arb.dataConnect.operationRefImpl().next()
    val operationRefImpl2 =
      operationRefImpl1.copy(dataSerializersModule = mockk(Arb.dataConnect.string().next()))
    val operationRefImplNull = operationRefImpl1.copy(dataSerializersModule = null)
    operationRefImpl1.equals(operationRefImpl2) shouldBe false
    operationRefImplNull.equals(operationRefImpl1) shouldBe false
    operationRefImpl1.equals(operationRefImplNull) shouldBe false
  }

  @Test
  fun `toString() should incorporate the string representations of public properties`() = runTest {
    val operationRefImpl = Arb.dataConnect.operationRefImpl().next()
    val callerSdkType2 =
      Arb.enum<CallerSdkType>().filterNotEqual(operationRefImpl.callerSdkType).next()
    val operationRefImpls =
      listOf(
        operationRefImpl,
        operationRefImpl.copy(callerSdkType = callerSdkType2),
        operationRefImpl.copy(dataSerializersModule = null),
        operationRefImpl.copy(variablesSerializersModule = null),
      )
    val toStringResult = operationRefImpl.toString()

    assertSoftly {
      operationRefImpls.forEach {
        it.asClue {
          toStringResult.shouldContain("dataConnect=${operationRefImpl.dataConnect}")
          toStringResult.shouldContain("operationName=${operationRefImpl.operationName}")
          toStringResult.shouldContain("variables=${operationRefImpl.variables}")
          toStringResult.shouldContain("dataDeserializer=${operationRefImpl.dataDeserializer}")
          toStringResult.shouldContain(
            "variablesSerializer=${operationRefImpl.variablesSerializer}"
          )
          toStringResult.shouldContain("callerSdkType=${operationRefImpl.callerSdkType}")
          toStringResult.shouldContain(
            "dataSerializersModule=${operationRefImpl.dataSerializersModule}"
          )
          toStringResult.shouldContain(
            "variablesSerializersModule=${operationRefImpl.variablesSerializersModule}"
          )
        }
      }
    }
  }

  @Test
  fun `toString() should include null when dataSerializersModule is null`() = runTest {
    val operationRefImpl =
      Arb.dataConnect.operationRefImpl().next().copy(dataSerializersModule = null)
    val toStringResult = operationRefImpl.toString()

    assertSoftly {
      toStringResult.shouldContain("dataConnect=${operationRefImpl.dataConnect}")
      toStringResult.shouldContain("operationName=${operationRefImpl.operationName}")
      toStringResult.shouldContain("variables=${operationRefImpl.variables}")
      toStringResult.shouldContain("dataDeserializer=${operationRefImpl.dataDeserializer}")
      toStringResult.shouldContain("variablesSerializer=${operationRefImpl.variablesSerializer}")
      toStringResult.shouldContain("callerSdkType=${operationRefImpl.callerSdkType}")
      toStringResult.shouldContain("dataSerializersModule=null")
      toStringResult.shouldContain(
        "variablesSerializersModule=${operationRefImpl.variablesSerializersModule}"
      )
    }
  }

  @Test
  fun `toString() should include null when variablesSerializersModule is null`() = runTest {
    val operationRefImpl =
      Arb.dataConnect.operationRefImpl().next().copy(variablesSerializersModule = null)
    val toStringResult = operationRefImpl.toString()

    assertSoftly {
      toStringResult.shouldContain("dataConnect=${operationRefImpl.dataConnect}")
      toStringResult.shouldContain("operationName=${operationRefImpl.operationName}")
      toStringResult.shouldContain("variables=${operationRefImpl.variables}")
      toStringResult.shouldContain("dataDeserializer=${operationRefImpl.dataDeserializer}")
      toStringResult.shouldContain("variablesSerializer=${operationRefImpl.variablesSerializer}")
      toStringResult.shouldContain("callerSdkType=${operationRefImpl.callerSdkType}")
      toStringResult.shouldContain(
        "dataSerializersModule=${operationRefImpl.dataSerializersModule}"
      )
      toStringResult.shouldContain("variablesSerializersModule=null")
    }
  }

  private companion object {
    fun DataConnectArb.testVariables(string: Arb<String> = string()): Arb<TestVariables> =
      arbitrary {
        TestVariables(string.bind())
      }

    fun DataConnectArb.operationRefImpl(): Arb<StubOperationRefImpl<TestData, TestVariables>> =
      operationRefImpl(testVariables())
  }
}
