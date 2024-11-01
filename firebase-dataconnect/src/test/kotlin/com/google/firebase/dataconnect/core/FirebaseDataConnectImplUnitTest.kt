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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.FirebaseAppUnitTestingRule
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirebaseDataConnectImplUnitTest {

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()

  @get:Rule
  val firebaseAppFactory =
    FirebaseAppUnitTestingRule(
      appNameKey = "b7bf5mmx4x",
      applicationIdKey = "gwwftxw9y9",
      projectIdKey = "a988y548hz",
    )

  private val rs = RandomSource.default()
  private val dataConnect: FirebaseDataConnectImpl by lazy {
    val app = firebaseAppFactory.newInstance()

    FirebaseDataConnectImpl(
      context = app.applicationContext,
      app = app,
      projectId = app.options.projectId!!,
      config = Arb.dataConnect.connectorConfig().next(rs),
      blockingExecutor = mockk(relaxed = true),
      nonBlockingExecutor = mockk(relaxed = true),
      deferredAuthProvider = mockk(relaxed = true),
      deferredAppCheckProvider = mockk(relaxed = true),
      creator = mockk(relaxed = true),
      settings = Arb.dataConnect.dataConnectSettings().next(rs),
    )
  }

  @After
  fun closeDataConnect() {
    dataConnect.close()
  }

  @Test
  fun `query() with no options set should use null for each option`() = runTest {
    val operationName = Arb.dataConnect.operationName().next(rs)
    val variables = TestVariables(Arb.string(size = 8).next(rs))
    val dataDeserializer: DeserializationStrategy<TestData> = mockk()
    val variablesSerializer: SerializationStrategy<TestVariables> = mockk()

    val queryRef =
      dataConnect.query(
        operationName = operationName,
        variables = variables,
        dataDeserializer = dataDeserializer,
        variablesSerializer = variablesSerializer,
      )

    assertSoftly {
      queryRef.operationName shouldBe operationName
      queryRef.variables shouldBeSameInstanceAs variables
      queryRef.dataDeserializer shouldBeSameInstanceAs dataDeserializer
      queryRef.variablesSerializer shouldBeSameInstanceAs variablesSerializer
      queryRef.callerSdkType shouldBe CallerSdkType.Base
      queryRef.variablesSerializersModule.shouldBeNull()
      queryRef.dataSerializersModule.shouldBeNull()
    }
  }

  @Test
  fun `query() with all options specified should use the given options`() = runTest {
    val operationName = Arb.dataConnect.operationName().next(rs)
    val variables = TestVariables(Arb.string(size = 8).next(rs))
    val dataDeserializer: DeserializationStrategy<TestData> = mockk()
    val variablesSerializer: SerializationStrategy<TestVariables> = mockk()
    val callerSdkType = Arb.enum<CallerSdkType>().next()
    val dataSerializersModule: SerializersModule = mockk()
    val variablesSerializersModule: SerializersModule = mockk()

    val queryRef =
      dataConnect.query(
        operationName = operationName,
        variables = variables,
        dataDeserializer = dataDeserializer,
        variablesSerializer = variablesSerializer,
      ) {
        this.callerSdkType = callerSdkType
        this.dataSerializersModule = dataSerializersModule
        this.variablesSerializersModule = variablesSerializersModule
      }

    assertSoftly {
      queryRef.operationName shouldBe operationName
      queryRef.variables shouldBeSameInstanceAs variables
      queryRef.dataDeserializer shouldBeSameInstanceAs dataDeserializer
      queryRef.variablesSerializer shouldBeSameInstanceAs variablesSerializer
      queryRef.callerSdkType shouldBe callerSdkType
      queryRef.dataSerializersModule shouldBeSameInstanceAs dataSerializersModule
      queryRef.variablesSerializersModule shouldBeSameInstanceAs variablesSerializersModule
    }
  }

  @Test
  fun `mutation() with no options set should use null for each option`() = runTest {
    val operationName = Arb.dataConnect.operationName().next(rs)
    val variables = TestVariables(Arb.string(size = 8).next(rs))
    val dataDeserializer: DeserializationStrategy<TestData> = mockk()
    val variablesSerializer: SerializationStrategy<TestVariables> = mockk()

    val mutationRef =
      dataConnect.mutation(
        operationName = operationName,
        variables = variables,
        dataDeserializer = dataDeserializer,
        variablesSerializer = variablesSerializer,
      )

    assertSoftly {
      mutationRef.operationName shouldBe operationName
      mutationRef.variables shouldBeSameInstanceAs variables
      mutationRef.dataDeserializer shouldBeSameInstanceAs dataDeserializer
      mutationRef.variablesSerializer shouldBeSameInstanceAs variablesSerializer
      mutationRef.callerSdkType shouldBe CallerSdkType.Base
      mutationRef.variablesSerializersModule.shouldBeNull()
      mutationRef.dataSerializersModule.shouldBeNull()
    }
  }

  @Test
  fun `mutation() with all options specified should use the given options`() = runTest {
    val operationName = Arb.dataConnect.operationName().next(rs)
    val variables = TestVariables(Arb.string(size = 8).next(rs))
    val dataDeserializer: DeserializationStrategy<TestData> = mockk()
    val variablesSerializer: SerializationStrategy<TestVariables> = mockk()
    val callerSdkType = Arb.enum<CallerSdkType>().next()
    val dataSerializersModule: SerializersModule = mockk()
    val variablesSerializersModule: SerializersModule = mockk()

    val mutationRef =
      dataConnect.mutation(
        operationName = operationName,
        variables = variables,
        dataDeserializer = dataDeserializer,
        variablesSerializer = variablesSerializer,
      ) {
        this.callerSdkType = callerSdkType
        this.dataSerializersModule = dataSerializersModule
        this.variablesSerializersModule = variablesSerializersModule
      }

    assertSoftly {
      mutationRef.operationName shouldBe operationName
      mutationRef.variables shouldBeSameInstanceAs variables
      mutationRef.dataDeserializer shouldBeSameInstanceAs dataDeserializer
      mutationRef.variablesSerializer shouldBeSameInstanceAs variablesSerializer
      mutationRef.callerSdkType shouldBe callerSdkType
      mutationRef.dataSerializersModule shouldBeSameInstanceAs dataSerializersModule
      mutationRef.variablesSerializersModule shouldBeSameInstanceAs variablesSerializersModule
    }
  }

  private data class TestVariables(val foo: String)

  private data class TestData(val bar: String)
}
