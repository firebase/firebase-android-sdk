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

package com.google.firebase.dataconnect.sqlite

import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.protobuf.Struct
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.checkAll
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class QueryResultDehydratorUnitTest {

  @Before
  fun registerPrinters() {
    registerDataConnectKotestPrinters()
  }

  @Test
  fun `dehydrateQueryResult() with default getEntityIdForPath returns the receiver`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { structSample ->
      val struct: Struct = structSample.struct

      val result = dehydrateQueryResult(struct)

      result.shouldHaveStructAndEmptyEntities(struct)
    }
  }

  @Test
  fun `dehydrateQueryResult() with null getEntityIdForPath returns the receiver`() = runTest {
    checkAll(propTestConfig, Arb.proto.struct()) { structSample ->
      val struct: Struct = structSample.struct

      val result = dehydrateQueryResult(struct, null)

      result.shouldHaveStructAndEmptyEntities(struct)
    }
  }

  @Test
  fun `dehydrateQueryResult() with getEntityIdForPath returning null returns the receiver`() =
    runTest {
      checkAll(propTestConfig, Arb.proto.struct()) { structSample ->
        val struct: Struct = structSample.struct
        val getEntityIdForPath: GetEntityIdForPathFunction = mockk()
        every { getEntityIdForPath(any()) } returns null

        val result = dehydrateQueryResult(struct, getEntityIdForPath)

        result.shouldHaveStructAndEmptyEntities(struct)
      }
    }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 200,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33),
    shrinkingMode = ShrinkingMode.Off,
  )

private fun DehydratedQueryResult.shouldHaveStructAndEmptyEntities(expectedStruct: Struct) {
  assertSoftly {
    withClue("proto.struct") { proto.struct shouldBeSameInstanceAs expectedStruct }
    withClue("proto.entitiesList") { proto.entitiesList.shouldBeEmpty() }
    withClue("entities") { entities.shouldBeEmpty() }
  }
}
