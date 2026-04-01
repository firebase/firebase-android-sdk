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

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.DataConnectOperationException
import com.google.firebase.dataconnect.DataConnectOperationFailureResponse.ErrorInfo
import com.google.firebase.dataconnect.DataConnectUntypedData
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.operationErrors
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.shouldSatisfy
import com.google.firebase.dataconnect.util.DeserializeUtils.deserialize
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToStruct
import com.google.firebase.dataconnect.util.ProtoUtil.toMap
import com.google.protobuf.Struct
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.assume
import io.kotest.property.checkAll
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlin.reflect.KClass
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.junit.Test

class DeserializeUtilsUnitTest {

  @Test
  fun `deserialize() should ignore the module given with DataConnectUntypedData`() {
    val data = buildStructProto { put("foo", 42.0) }
    val errors = Arb.dataConnect.operationErrors().next()
    val result = deserialize(data, errors, DataConnectUntypedData, mockk<SerializersModule>())
    result.shouldHaveDataAndErrors(data, errors)
  }

  @Test
  fun `deserialize() with null data should treat DataConnectUntypedData specially`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationErrors()) { errors ->
      val result = deserialize(null, errors, DataConnectUntypedData, serializersModule = null)
      result.shouldHaveDataAndErrors(null, errors)
    }
  }

  @Test
  fun `deserialize() with non-null data should treat DataConnectUntypedData specially`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct(), Arb.dataConnect.operationErrors()) { data, errors
      ->
      val result =
        deserialize(data.struct, errors, DataConnectUntypedData, serializersModule = null)
      result.shouldHaveDataAndErrors(data.struct, errors)
    }
  }

  @Test
  fun `deserialize() successfully deserializes`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string()) { fooValue ->
      val dataStruct = buildStructProto { put("foo", fooValue) }
      val deserializedData = deserialize(dataStruct, emptyList(), serializer<TestData>(), null)
      deserializedData shouldBe TestData(fooValue)
    }
  }

  @Test
  fun `deserialize() should throw if one or more errors and data is null`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.operationErrors(range = 1..10)) { errors ->
      val exception: DataConnectOperationException =
        shouldThrow<DataConnectOperationException> {
          deserialize<Nothing>(null, errors, mockk(), serializersModule = null)
        }
      exception.shouldSatisfy(
        expectedMessageSubstringCaseInsensitive = "operation encountered errors",
        expectedMessageSubstringCaseSensitive = errors.toString(),
        expectedCause = null,
        expectedRawData = null,
        expectedData = null,
        expectedErrors = errors,
      )
    }
  }

  @Test
  fun `deserialize() should throw if one or more errors, data is NOT null, and decoding fails`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.proto.struct().map { it.struct },
        Arb.dataConnect.operationErrors(range = 1..10)
      ) { dataStruct, errors ->
        val exception: DataConnectOperationException =
          shouldThrow<DataConnectOperationException> {
            deserialize<Nothing>(dataStruct, errors, mockk(), serializersModule = null)
          }
        exception.shouldSatisfy(
          expectedMessageSubstringCaseInsensitive = "operation encountered errors",
          expectedMessageSubstringCaseSensitive = errors.toString(),
          expectedCause = null,
          expectedRawData = dataStruct,
          expectedData = null,
          expectedErrors = errors,
        )
      }
    }

  @Test
  fun `deserialize() should throw if one or more errors, data is NOT null, and decoding succeeds`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.dataConnect.string(),
        Arb.dataConnect.operationErrors(range = 1..10)
      ) { fooValue, errors ->
        val dataStruct = buildStructProto { put("foo", fooValue) }
        val exception: DataConnectOperationException =
          shouldThrow<DataConnectOperationException> {
            deserialize(dataStruct, errors, serializer<TestData>(), serializersModule = null)
          }
        exception.shouldSatisfy(
          expectedMessageSubstringCaseInsensitive = "operation encountered errors",
          expectedMessageSubstringCaseSensitive = errors.toString(),
          expectedCause = null,
          expectedRawData = dataStruct,
          expectedData = TestData(fooValue),
          expectedErrors = errors,
        )
      }
    }

  @Test
  fun `deserialize() should throw if data is null and errors is empty`() {
    val exception: DataConnectOperationException =
      shouldThrow<DataConnectOperationException> {
        deserialize(null, emptyList(), serializer<TestData>(), serializersModule = null)
      }
    exception.shouldSatisfy(
      expectedMessageSubstringCaseInsensitive = "no data was included",
      expectedCause = null,
      expectedRawData = null,
      expectedData = null,
      expectedErrors = emptyList(),
    )
  }

  @Test
  fun `deserialize() should throw if decoding fails and error list is empty`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct().map { it.struct }) { dataStruct ->
      assume(!dataStruct.containsFields("foo"))
      val exception: DataConnectOperationException =
        shouldThrow<DataConnectOperationException> {
          deserialize(dataStruct, emptyList(), serializer<TestData>(), serializersModule = null)
        }
      exception.shouldSatisfy(
        expectedMessageSubstringCaseInsensitive = "decoding data from the server's response failed",
        expectedCause = SerializationException::class,
        expectedRawData = dataStruct,
        expectedData = null,
        expectedErrors = emptyList(),
      )
    }
  }

  @Test
  fun `deserialize() should pass through the SerializersModule`() {
    val data = encodeToStruct(TestData("4jv7vkrs7a"))
    val serializersModule: SerializersModule = mockk()
    val deserializer: DeserializationStrategy<TestData> = spyk(serializer())

    deserialize(data, emptyList(), deserializer, serializersModule)

    val slot = slot<Decoder>()
    verify { deserializer.deserialize(capture(slot)) }
    slot.captured.serializersModule shouldBeSameInstanceAs serializersModule
  }

  @Serializable data class TestData(val foo: String)

  private companion object {

    fun <T> DataConnectOperationException.shouldSatisfy(
      expectedMessageSubstringCaseInsensitive: String,
      expectedMessageSubstringCaseSensitive: String? = null,
      expectedCause: KClass<*>?,
      expectedRawData: Struct?,
      expectedData: T?,
      expectedErrors: List<ErrorInfo>,
    ) =
      shouldSatisfy(
        expectedMessageSubstringCaseInsensitive = expectedMessageSubstringCaseInsensitive,
        expectedMessageSubstringCaseSensitive = expectedMessageSubstringCaseSensitive,
        expectedCause = expectedCause,
        expectedRawData = expectedRawData?.toMap(),
        expectedData = expectedData,
        expectedErrors = expectedErrors,
      )

    fun DataConnectUntypedData.shouldHaveDataAndErrors(
      expectedData: Map<String, Any?>,
      expectedErrors: List<ErrorInfo>,
    ) {
      assertSoftly {
        withClue("data") { data.shouldNotBeNull().shouldContainExactly(expectedData) }
        withClue("errors") { errors shouldContainExactly expectedErrors }
      }
    }

    fun DataConnectUntypedData.shouldHaveDataAndErrors(
      expectedData: Struct,
      expectedErrors: List<ErrorInfo>,
    ) = shouldHaveDataAndErrors(expectedData.toMap(), expectedErrors)

    fun DataConnectUntypedData.shouldHaveDataAndErrors(
      @Suppress("UNUSED_PARAMETER") expectedData: Nothing?,
      expectedErrors: List<ErrorInfo>,
    ) {
      assertSoftly {
        withClue("data") { data.shouldBeNull() }
        withClue("errors") { errors shouldContainExactly expectedErrors }
      }
    }
  }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(iterations = 1000, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2))
