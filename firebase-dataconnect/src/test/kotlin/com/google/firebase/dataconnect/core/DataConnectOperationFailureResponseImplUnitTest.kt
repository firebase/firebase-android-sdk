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
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.errorPath as errorPathArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.fieldPathSegment as fieldPathSegmentArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.listIndexPathSegment as listIndexPathSegmentArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.operationData
import com.google.firebase.dataconnect.testutil.property.arbitrary.operationErrorInfo
import com.google.firebase.dataconnect.testutil.property.arbitrary.operationErrors
import com.google.firebase.dataconnect.testutil.property.arbitrary.operationFailureResponseImpl
import com.google.firebase.dataconnect.testutil.property.arbitrary.operationRawData
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
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
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.assume
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

private val propTestConfig =
  PropTestConfig(iterations = 20, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.25))

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
    checkAll(propTestConfig, Arb.dataConnect.string(), errorPathArb()) { message, path ->
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
  fun `toString() should return the message if message is non-empty and path is empty`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string()) { message ->
      val errorInfo = ErrorInfoImpl(message, emptyList())
      errorInfo.toString() shouldBe message
    }
  }

  @Test
  fun `toString() should not do anything different with an empty message`() = runTest {
    checkAll(propTestConfig, errorPathArb()) { path ->
      assume(path.isNotEmpty())
      val errorInfo = ErrorInfoImpl("", path)
      val errorInfoToStringResult = errorInfo.toString()
      errorInfoToStringResult shouldEndWith ": "
      path.forEachIndexed { index, pathSegment ->
        withClue("path[$index]") {
          errorInfoToStringResult shouldContainWithNonAbuttingText pathSegment.toString()
        }
      }
    }
  }

  @Test
  fun `toString() should print field path segments separated by dots`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), Arb.list(fieldPathSegmentArb(), 1..10)) {
      message,
      path ->
      val errorInfo = ErrorInfoImpl(message, path)
      errorInfo.toString() shouldBe path.joinToString(".") + ": $message"
    }
  }

  @Test
  fun `toString() should print list index path segments separated by dots`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.string(),
      Arb.list(listIndexPathSegmentArb(), 1..10)
    ) { message, path ->
      val errorInfo = ErrorInfoImpl(message, path)
      errorInfo.toString() shouldBe path.joinToString("") { "[${it.index}]" } + ": $message"
    }
  }

  @Test
  fun `toString() for path is field, listIndex`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), MyArb.samplePathSegments()) {
      message,
      segments ->
      val path = listOf(segments.field1, segments.listIndex1)
      val errorInfo = ErrorInfoImpl(message, path)
      errorInfo.toString() shouldBe "${segments.field1.field}[${segments.listIndex1}]: $message"
    }
  }

  @Test
  fun `toString() for path is listIndex, field`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), MyArb.samplePathSegments()) {
      message,
      segments ->
      val path = listOf(segments.listIndex1, segments.field1)
      val errorInfo = ErrorInfoImpl(message, path)
      errorInfo.toString() shouldBe "[${segments.listIndex1}].${segments.field1.field}: $message"
    }
  }

  @Test
  fun `toString() for path is field, listIndex, field`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), MyArb.samplePathSegments()) {
      message,
      segments ->
      val path = listOf(segments.field1, segments.listIndex1, segments.field2)
      val errorInfo = ErrorInfoImpl(message, path)
      errorInfo.toString() shouldBe
        "${segments.field1.field}[${segments.listIndex1}].${segments.field2.field}: $message"
    }
  }

  @Test
  fun `toString() for path is field, listIndex, listIndex`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), MyArb.samplePathSegments()) {
      message,
      segments ->
      val path = listOf(segments.field1, segments.listIndex1, segments.listIndex2)
      val errorInfo = ErrorInfoImpl(message, path)
      errorInfo.toString() shouldBe
        "${segments.field1.field}[${segments.listIndex1}][${segments.listIndex2}]: $message"
    }
  }

  @Test
  fun `toString() for path is field, field, listIndex`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), MyArb.samplePathSegments()) {
      message,
      segments ->
      val path = listOf(segments.field1, segments.field2, segments.listIndex1)
      val errorInfo = ErrorInfoImpl(message, path)
      errorInfo.toString() shouldBe
        "${segments.field1.field}.${segments.field2.field}[${segments.listIndex1}]: $message"
    }
  }

  @Test
  fun `toString() for path is field, listIndex, field, listIndex`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), MyArb.samplePathSegments()) {
      message,
      segments ->
      val path = listOf(segments.field1, segments.listIndex1, segments.field2, segments.listIndex2)
      val errorInfo = ErrorInfoImpl(message, path)
      errorInfo.toString() shouldBe
        "${segments.field1.field}[${segments.listIndex1}].${segments.field2.field}[${segments.listIndex2}]: $message"
    }
  }

  @Test
  fun `toString() for path is field, listIndex, listIndex, field`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), MyArb.samplePathSegments()) {
      message,
      segments ->
      val path = listOf(segments.field1, segments.listIndex1, segments.listIndex2, segments.field2)
      val errorInfo = ErrorInfoImpl(message, path)
      errorInfo.toString() shouldBe
        "${segments.field1.field}[${segments.listIndex1}][${segments.listIndex2}].${segments.field2.field}: $message"
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
    checkAll(propTestConfig, Arb.dataConnect.operationErrorInfo(), errorPathArb()) {
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
    checkAll(propTestConfig, Arb.dataConnect.operationErrorInfo(), errorPathArb()) {
      errorInfo1: ErrorInfoImpl,
      otherPath: List<DataConnectPathSegment> ->
      assume(errorInfo1.path.hashCode() != otherPath.hashCode())
      val errorInfo2 = ErrorInfoImpl(errorInfo1.message, otherPath)
      errorInfo1.equals(errorInfo2) shouldBe false
    }
  }
}

private object MyArb {

  fun samplePathSegments(
    field: Arb<DataConnectPathSegment.Field> = fieldPathSegmentArb(),
    listIndex: Arb<DataConnectPathSegment.ListIndex> = listIndexPathSegmentArb(),
  ): Arb<SamplePathSegments> =
    Arb.bind(field, field, listIndex, listIndex) { field1, field2, listIndex1, listIndex2 ->
      SamplePathSegments(field1, field2, listIndex1, listIndex2)
    }

  data class SamplePathSegments(
    val field1: DataConnectPathSegment.Field,
    val field2: DataConnectPathSegment.Field,
    val listIndex1: DataConnectPathSegment.ListIndex,
    val listIndex2: DataConnectPathSegment.ListIndex,
  )
}
