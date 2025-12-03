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

@file:OptIn(DelicateKotest::class)

package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.testutil.property.arbitrary.listValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.scalarValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.value
import com.google.firebase.dataconnect.testutil.property.arbitrary.valueOfKind
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProtoTestUtilsUnitTest {

  @Test
  fun `walk Value scalars`() = runTest {
    checkAll(propTestConfig, Arb.proto.scalarValue()) { value: Value ->
      val walkResult = value.walk().toList()
      walkResult.shouldContainExactly(ProtoValuePathPair(path = emptyList(), value = value))
    }
  }

  @Test
  fun `walk Value LIST_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.valueOfKind(Value.KindCase.LIST_VALUE)) { value: Value ->
      val walkResult = value.walk().toList()

      val expectedWalkResult = buildList {
        add(ProtoValuePathPair(emptyList(), value))
        addAll(value.listValue.walk().toList())
      }
      walkResult shouldContainExactlyInAnyOrder expectedWalkResult
    }
  }

  @Test
  fun `walk Value STRUCT_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.proto.valueOfKind(Value.KindCase.STRUCT_VALUE)) { value: Value ->
      val walkResult = value.walk().toList()

      val expectedWalkResult = buildList {
        add(ProtoValuePathPair(emptyList(), value))
        addAll(value.structValue.walk().toList())
      }
      walkResult shouldContainExactlyInAnyOrder expectedWalkResult
    }
  }

  @Test
  fun `walk ListValue`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue()) { listValueSample ->
      val listValue: ListValue = listValueSample.listValue

      val walkResult = listValue.walk().toList()

      walkResult shouldContainExactlyInAnyOrder listValueSample.descendants
    }
  }

  @Test
  fun `walk Struct`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { structSample ->
      val struct: Struct = structSample.struct

      val walkResult = struct.walk().toList()

      walkResult shouldContainExactlyInAnyOrder structSample.descendants
    }
  }

  @Test
  fun `walkValues Value`() = runTest {
    checkAll(propTestConfig, Arb.proto.value()) { value ->
      val walkValuesResult = value.walkValues().toList()

      val walkResult = value.walk().map { it.value }.toList()
      walkValuesResult shouldContainExactlyInAnyOrder walkResult
    }
  }

  @Test
  fun `walkValues ListValue`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue()) { listValueSample ->
      val listValue: ListValue = listValueSample.listValue

      val walkValuesResult = listValue.walkValues().toList()

      val walkResult = listValue.walk().map { it.value }.toList()
      walkValuesResult shouldContainExactlyInAnyOrder walkResult
    }
  }

  @Test
  fun `walkValues Struct`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { structSample ->
      val struct: Struct = structSample.struct

      val walkValuesResult = struct.walkValues().toList()

      val walkResult = struct.walk().map { it.value }.toList()
      walkValuesResult shouldContainExactlyInAnyOrder walkResult
    }
  }

  @Test
  fun `walkPaths Value`() = runTest {
    checkAll(propTestConfig, Arb.proto.value()) { value ->
      val walkPathsResult = value.walkPaths().toList()

      val walkResult = value.walk().map { it.path }.toList()
      walkPathsResult shouldContainExactlyInAnyOrder walkResult
    }
  }

  @Test
  fun `walkPaths ListValue`() = runTest {
    checkAll(propTestConfig, Arb.proto.listValue()) { listValueSample ->
      val listValue: ListValue = listValueSample.listValue

      val walkPathsResult = listValue.walkPaths().toList()

      val walkResult = listValue.walk().map { it.path }.toList()
      walkPathsResult shouldContainExactlyInAnyOrder walkResult
    }
  }

  @Test
  fun `walkPaths Struct`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { structSample ->
      val struct: Struct = structSample.struct

      val walkPathsResult = struct.walkPaths().toList()

      val walkResult = struct.walk().map { it.path }.toList()
      walkPathsResult shouldContainExactlyInAnyOrder walkResult
    }
  }

  private companion object {
    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 200,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33),
        shrinkingMode = ShrinkingMode.Off,
      )
  }
}
