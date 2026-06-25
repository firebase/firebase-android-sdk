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
package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.DataConnectOperationException
import com.google.firebase.dataconnect.DataConnectUntypedData
import com.google.firebase.dataconnect.core.DataConnectSerialization.Companion.toErrorInfoImpl
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.graphqlErrorProto
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.firebase.dataconnect.testutil.shouldSatisfy
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToStruct
import com.google.firebase.dataconnect.util.ProtoUtil.toMap
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.GraphqlError as GraphqlErrorProto
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.assume
import io.kotest.property.checkAll
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlin.reflect.KClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.junit.Before
import org.junit.Test

class DataConnectSerializationUnitTest {

  private val serialization = DataConnectSerialization(Dispatchers.Default)

  @Before
  fun registerPrinters() {
    registerDataConnectKotestPrinters()
  }

  @Test
  fun `decodeData() with DataConnectUntypedData ignores the non-null SerializersModule`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.proto.struct().map { it.struct },
        Arb.list(Arb.dataConnect.graphqlErrorProto(), 0..3)
      ) { data, errors ->
        val result =
          serialization.decodeData(
            data,
            errors,
            DataConnectUntypedData,
            serializersModule = mockk()
          )
        result.data.shouldNotBeNull() shouldContainExactly data.toMap()
        result.errors shouldContainExactlyInAnyOrder errors.map { it.toErrorInfoImpl() }
      }
    }

  @Test
  fun `decodeData() with DataConnectUntypedData ignores the null SerializersModule`() = runTest {
    checkAll(
      propTestConfig,
      Arb.proto.struct().map { it.struct },
      Arb.list(Arb.dataConnect.graphqlErrorProto(), 0..3)
    ) { data, errors ->
      val result =
        serialization.decodeData(data, errors, DataConnectUntypedData, serializersModule = null)
      result.data.shouldNotBeNull() shouldContainExactly data.toMap()
      result.errors shouldContainExactlyInAnyOrder errors.map { it.toErrorInfoImpl() }
    }
  }

  @Test
  fun `decodeData() with DataConnectUntypedData handles null data`() = runTest {
    checkAll(
      propTestConfig,
      Arb.list(Arb.dataConnect.graphqlErrorProto(), 0..3),
      Arb.dataConnect.serializersModule()
    ) { errors, serializersModule ->
      val result = serialization.decodeData(null, errors, DataConnectUntypedData, serializersModule)
      result.data.shouldBeNull()
      result.errors shouldContainExactlyInAnyOrder errors.map { it.toErrorInfoImpl() }
    }
  }

  @Test
  fun `decodeData() with DataConnectUntypedData handles non-null data`() = runTest {
    checkAll(
      propTestConfig,
      Arb.proto.struct().map { it.struct },
      Arb.list(Arb.dataConnect.graphqlErrorProto(), 0..3),
      Arb.dataConnect.serializersModule()
    ) { data, errors, serializersModule ->
      val result = serialization.decodeData(data, errors, DataConnectUntypedData, serializersModule)
      result.data.shouldNotBeNull() shouldContainExactly data.toMap()
      result.errors shouldContainExactlyInAnyOrder errors.map { it.toErrorInfoImpl() }
    }
  }

  @Test
  fun `decodeData() successfully deserializes`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string()) { fooValue ->
      val dataStruct = buildStructProto { put("foo", fooValue) }

      val result = serialization.decodeData(dataStruct, emptyList(), serializer<TestData>(), null)

      result shouldBe TestData(fooValue)
    }
  }

  @Test
  fun `decodeData() throws if one or more errors and data is null`() = runTest {
    checkAll(
      propTestConfig,
      Arb.list(Arb.dataConnect.graphqlErrorProto(), 1..3),
      Arb.dataConnect.serializersModule()
    ) { errors, serializersModule ->
      val exception: DataConnectOperationException = shouldThrow {
        serialization.decodeData<Nothing>(null, errors, mockk(), serializersModule)
      }
      exception.shouldSatisfy(
        expectedMessageSubstringCaseInsensitive = "operation encountered errors",
        expectedMessageSubstringCaseSensitive = errors.map { it.toErrorInfoImpl() }.toString(),
        expectedCause = null,
        expectedRawData = null,
        expectedData = null,
        expectedErrors = errors,
      )
    }
  }

  @Test
  fun `decodeData() throws if one or more errors, data is NOT null, and decoding fails`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.proto.struct().map { it.struct },
        Arb.list(Arb.dataConnect.graphqlErrorProto(), 1..3),
        Arb.dataConnect.serializersModule(),
      ) { data, errors, serializersModule ->
        val exception: DataConnectOperationException = shouldThrow {
          serialization.decodeData<Nothing>(data, errors, mockk(), serializersModule)
        }
        exception.shouldSatisfy(
          expectedMessageSubstringCaseInsensitive = "operation encountered errors",
          expectedMessageSubstringCaseSensitive = errors.map { it.toErrorInfoImpl() }.toString(),
          expectedCause = null,
          expectedRawData = data,
          expectedData = null,
          expectedErrors = errors,
        )
      }
    }

  @Test
  fun `decodeData() throws if one or more errors, data is NOT null, and decoding succeeds`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.dataConnect.string(),
        Arb.list(Arb.dataConnect.graphqlErrorProto(), 1..3),
        Arb.dataConnect.serializersModule(),
      ) { fooValue, errors, serializersModule ->
        val data = buildStructProto { put("foo", fooValue) }
        val exception: DataConnectOperationException = shouldThrow {
          serialization.decodeData(data, errors, serializer<TestData>(), serializersModule)
        }
        exception.shouldSatisfy(
          expectedMessageSubstringCaseInsensitive = "operation encountered errors",
          expectedMessageSubstringCaseSensitive = errors.map { it.toErrorInfoImpl() }.toString(),
          expectedCause = null,
          expectedRawData = data,
          expectedData = TestData(fooValue),
          expectedErrors = errors,
        )
      }
    }

  @Test
  fun `decodeData() throws if data is null and errors is empty`() = runTest {
    checkAll(
      propTestConfig,
      Arb.dataConnect.serializersModule(),
    ) { serializersModule ->
      val exception: DataConnectOperationException = shouldThrow {
        serialization.decodeData(null, emptyList(), mockk(), serializersModule)
      }
      exception.shouldSatisfy(
        expectedMessageSubstringCaseInsensitive = "no data was included",
        expectedCause = null,
        expectedRawData = null,
        expectedData = null,
        expectedErrors = emptyList(),
      )
    }
  }

  @Test
  fun `decodeData() throws if decoding fails and error list is empty`() = runTest {
    checkAll(
      propTestConfig,
      Arb.proto.struct().map { it.struct },
      Arb.dataConnect.serializersModule(),
    ) { data, serializersModule ->
      assume(!data.containsFields("foo"))
      val exception: DataConnectOperationException = shouldThrow {
        serialization.decodeData(data, emptyList(), serializer<TestData>(), serializersModule)
      }
      exception.shouldSatisfy(
        expectedMessageSubstringCaseInsensitive = "decoding data from the server's response failed",
        expectedCause = SerializationException::class,
        expectedRawData = data,
        expectedData = null,
        expectedErrors = emptyList(),
      )
    }
  }

  @Test
  fun `decodeData() should pass through the SerializersModule`() = runTest {
    val data = encodeToStruct(TestData("4jv7vkrs7a"))
    val serializersModule: SerializersModule = mockk()
    val deserializer: DeserializationStrategy<TestData> = spyk(serializer())

    serialization.decodeData(data, emptyList(), deserializer, serializersModule)

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
      expectedErrors: List<GraphqlErrorProto>,
    ) =
      shouldSatisfy(
        expectedMessageSubstringCaseInsensitive = expectedMessageSubstringCaseInsensitive,
        expectedMessageSubstringCaseSensitive = expectedMessageSubstringCaseSensitive,
        expectedCause = expectedCause,
        expectedRawData = expectedRawData?.toMap(),
        expectedData = expectedData,
        expectedErrors = expectedErrors.map { it.toErrorInfoImpl() },
      )
  }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(iterations = 20, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.25))
