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

import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.dataConnectPath as dataConnectPathArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.listValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.util.ProtoPrune.withDescendantStructsPruned
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProtoPruneUnitTest {

  @Test
  fun `Struct withDescendantStructsPruned() should return null if nothing is pruned`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct(), dataConnectPathArb()) { structSample, path ->
      val struct: Struct = structSample.struct
      val result = struct.withDescendantStructsPruned(path, predicate = { _, _ -> false })

      result.shouldBeNull()
    }
  }

  @Test
  fun `ListValue withDescendantStructsPruned() should return null if nothing is pruned`() =
    runTest {
      checkAll(propTestConfig, Arb.proto.listValue(), dataConnectPathArb()) { listValueSample, path
        ->
        val listValue: ListValue = listValueSample.listValue
        val result = listValue.withDescendantStructsPruned(path, predicate = { _, _ -> false })

        result.shouldBeNull()
      }
    }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(iterations = 200, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2))
