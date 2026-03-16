/*
 * Copyright 2025 Google LLC
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
@file:Suppress("ReplaceCallWithBinaryOperator")

package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.core.DataConnectOperationFailureResponseImpl.ErrorInfoImpl
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.dataConnectPath as dataConnectPathArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.listIndexPathSegment as listIndexPathSegmentArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.operationData
import com.google.firebase.dataconnect.testutil.property.arbitrary.operationErrorInfo
import com.google.firebase.dataconnect.testutil.property.arbitrary.operationErrors
import com.google.firebase.dataconnect.testutil.property.arbitrary.operationFailureResponseImpl
import com.google.firebase.dataconnect.testutil.property.arbitrary.operationRawData
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.toPathString
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.assume
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

private val propTestConfig =
  PropTestConfig(
    iterations = 20,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.25),
    maxDiscardPercentage = 80,
  )

/** Unit tests for [DataConnectOperationFailureResponseImpl] */
class DataConnectOperationFailureResponseImplUnitTest {

  @Test
  fun `constructor should set properties to the given values`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.operationRawData(),
      Arb.dataConnect.operationData(),
      Arb.dataConnect.operationErrors()
    ) { rawData, data, errors ->
      val response = DataConnectOperationFailureResponseImpl(rawData, data, errors)
      assertSoftly {
        withClue("rawData") { response.rawData shouldBeSameInstanceAs rawData }
        withClue("data") { response.data shouldBeSameInstanceAs data }
        withClue("errors") { response.errors shouldBeSameInstanceAs errors }
      }
    }
  }

  @Test
  fun `toString() should incorporate property values`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationFailureResponseImpl()) {
      response: DataConnectOperationFailureResponseImpl<*> ->
      val toStringResult = response.toString()
      assertSoftly {
        toStringResult shouldStartWith "DataConnectOperationFailureResponseImpl("
        toStringResult shouldEndWith ")"
        toStringResult shouldContainWithNonAbuttingText "rawData=${response.rawData}"
        toStringResult shouldContainWithNonAbuttingText "data=${response.data}"
        toStringResult shouldContainWithNonAbuttingText "errors=${response.errors}"
      }
    }
  }
}

/** Unit tests for [DataConnectOperationFailureResponseImpl.ErrorInfoImpl] */
class DataConnectOperationFailureResponseImplErrorInfoImplUnitTest {

  @Test
  fun `constructor should set properties to the given values`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), dataConnectPathArb()) { message, path ->
      val errorInfo = ErrorInfoImpl(message, path)
      errorInfo.message shouldBeSameInstanceAs message
      errorInfo.path shouldBeSameInstanceAs path
    }
  }

  @Test
  fun `toString() should return an empty string if both message and path are empty`() {
    val errorInfo = ErrorInfoImpl("", emptyList())
    errorInfo.toString() shouldBe ""
  }

  @Test
  fun `toString() should return just the message if the path is empty`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string()) { message ->
      val errorInfo = ErrorInfoImpl(message, emptyList())
      errorInfo.toString() shouldBe message
    }
  }

  @Test
  fun `toString() should return the expected string when path is non-empty`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.string(),
      dataConnectPathArb().filterNot { it.toPathString().isEmpty() }
    ) { message, path ->
      val errorInfo = ErrorInfoImpl(message, path)
      errorInfo.toString() shouldBe path.toPathString() + ": " + message
    }
  }

  @Test
  fun `equals() should return true for the exact same instance`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationErrorInfo()) { errorInfo: ErrorInfoImpl ->
      errorInfo.equals(errorInfo) shouldBe true
    }
  }

  @Test
  fun `equals() should return true for an equal instance`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationErrorInfo()) { errorInfo1: ErrorInfoImpl ->
      val errorInfo2 = ErrorInfoImpl(errorInfo1.message, errorInfo1.path)
      errorInfo1.equals(errorInfo2) shouldBe true
      errorInfo2.equals(errorInfo1) shouldBe true
    }
  }

  @Test
  fun `equals() should return false for null`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationErrorInfo()) { errorInfo: ErrorInfoImpl ->
      errorInfo.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals() should return false for a different type`() = runTest {
    val otherTypes = Arb.choice(Arb.string(), Arb.int(), listIndexPathSegmentArb())
    checkAll(propTestConfig, Arb.dataConnect.operationErrorInfo(), otherTypes) {
      errorInfo: ErrorInfoImpl,
      other ->
      errorInfo.equals(other) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when message differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationErrorInfo(), Arb.dataConnect.string()) {
      errorInfo1: ErrorInfoImpl,
      otherMessage: String ->
      assume(errorInfo1.message != otherMessage)
      val errorInfo2 = ErrorInfoImpl(otherMessage, errorInfo1.path)
      errorInfo1.equals(errorInfo2) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when path differs`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationErrorInfo(), dataConnectPathArb()) {
      errorInfo1: ErrorInfoImpl,
      otherPath: List<DataConnectPathSegment> ->
      assume(errorInfo1.path != otherPath)
      val errorInfo2 = ErrorInfoImpl(errorInfo1.message, otherPath)
      errorInfo1.equals(errorInfo2) shouldBe false
    }
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() =
    runTest {
      checkAll(propTestConfig, Arb.dataConnect.operationErrorInfo()) { errorInfo: ErrorInfoImpl ->
        val hashCode1 = errorInfo.hashCode()
        errorInfo.hashCode() shouldBe hashCode1
        errorInfo.hashCode() shouldBe hashCode1
      }
    }

  @Test
  fun `hashCode() should return the same value on equal objects`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationErrorInfo()) { errorInfo1: ErrorInfoImpl ->
      val errorInfo2 = ErrorInfoImpl(errorInfo1.message, errorInfo1.path)
      errorInfo1.hashCode() shouldBe errorInfo2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return a different value if message is different`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationErrorInfo(), Arb.dataConnect.string()) {
      errorInfo1: ErrorInfoImpl,
      otherMessage: String ->
      assume(errorInfo1.message.hashCode() != otherMessage.hashCode())
      val errorInfo2 = ErrorInfoImpl(otherMessage, errorInfo1.path)
      errorInfo1.equals(errorInfo2) shouldBe false
    }
  }

  @Test
  fun `hashCode() should return a different value if path is different`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationErrorInfo(), dataConnectPathArb()) {
      errorInfo1: ErrorInfoImpl,
      otherPath: List<DataConnectPathSegment> ->
      assume(errorInfo1.path.hashCode() != otherPath.hashCode())
      val errorInfo2 = ErrorInfoImpl(errorInfo1.message, otherPath)
      errorInfo1.equals(errorInfo2) shouldBe false
    }
  }
}
