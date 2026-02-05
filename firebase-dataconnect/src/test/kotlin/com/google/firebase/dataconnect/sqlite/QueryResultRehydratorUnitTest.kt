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

@file:OptIn(DelicateKotest::class)

package com.google.firebase.dataconnect.sqlite

import com.google.firebase.dataconnect.testutil.containWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.toPathString
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.kotlinsdk.QueryResult as QueryResultProto
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.or
import io.kotest.matchers.should
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class QueryResultRehydratorUnitTest {

  @Before
  fun registerPrinters() {
    registerDataConnectKotestPrinters()
  }

  @Test
  fun `rehydrateQueryResult() should return the Struct from QueryResultProto if entitiesList is empty`() =
    runTest {
      checkAll(propTestConfig, QueryResultArb(entityCountRange = 0..5)) { sample ->
        val struct: Struct = sample.hydratedStruct
        val queryResult = QueryResultProto.newBuilder().setStruct(struct).build()

        val result = rehydrateQueryResult(queryResult, sample.entityStructById)

        result shouldBeSameInstanceAs struct
      }
    }

  @Test
  fun `rehydrateQueryResult() should return the rehydrated Struct`() = runTest {
    checkAll(propTestConfig, QueryResultArb(entityCountRange = 1..5)) { sample ->
      check(sample.queryResultProto.entitiesCount > 0)

      val result = rehydrateQueryResult(sample.queryResultProto, sample.entityStructById)

      result shouldBe sample.hydratedStruct
    }
  }

  @Test
  fun `rehydrateQueryResult() should throw on missing entity ID`() = runTest {
    checkAll(propTestConfig, QueryResultArb(entityCountRange = 1..5)) { sample ->
      check(sample.queryResultProto.entitiesCount > 0)
      val missingEntityId = Arb.of(sample.entityStructById.keys.toList()).bind()
      val entityStructByIdWithMissingEntityId =
        sample.entityStructById.toMutableMap().run {
          remove(missingEntityId)
          toMap()
        }

      val exception =
        shouldThrow<EntityIdNotFoundException> {
          rehydrateQueryResult(sample.queryResultProto, entityStructByIdWithMissingEntityId)
        }

      val containPathOfMissingEntityIdMatcher =
        sample.entityByPath.entries
          .filter { it.value.entityId == missingEntityId }
          .map { containWithNonAbuttingText(it.key.toPathString()) }
          .reduce { acc, matcher -> acc or matcher }
      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "f9tcr9fvmg"
        exception.message shouldContainWithNonAbuttingText missingEntityId
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "not found"
        exception.message should containPathOfMissingEntityIdMatcher
      }
    }
  }

  @Test
  fun `rehydrateQueryResult() should throw on missing entity field`() = runTest {
    val queryResultWithAtLeastOneNonEmptyEntityArb =
      QueryResultArb(entityCountRange = 1..5).filter { sample ->
        sample.entityStructById.values.any { it.fieldsCount > 0 }
      }

    checkAll(propTestConfig, queryResultWithAtLeastOneNonEmptyEntityArb) { sample ->
      check(sample.queryResultProto.entitiesCount > 0)
      val (entityIdWithMissingField, entityStruct) =
        Arb.of(sample.entityByPath.values.filter { it.struct.fieldsCount > 0 }).bind()
      val missingEntityField = Arb.of(entityStruct.fieldsMap.keys.toList()).bind()
      val entityStructByIdWithMissingField =
        sample.entityStructById.toMutableMap().run {
          val entityStruct = checkNotNull(get(entityIdWithMissingField))
          val entityStructWithMissingField =
            entityStruct.toBuilder().removeFields(missingEntityField).build()
          check(entityStruct != entityStructWithMissingField)
          put(entityIdWithMissingField, entityStructWithMissingField)
          toMap()
        }

      val exception =
        shouldThrow<EntityMissingFieldsException> {
          rehydrateQueryResult(sample.queryResultProto, entityStructByIdWithMissingField)
        }

      val containPathOfEntityMissingFieldMatcher =
        sample.entityByPath.entries
          .filter { it.value.entityId == entityIdWithMissingField }
          .map { containWithNonAbuttingText(it.key.toPathString()) }
          .reduce { acc, matcher -> acc or matcher }
      assertSoftly {
        // "entity with ID j4VJ for path=vjlu[0] is missing 1 of 5 fields: JSaU (got 4 fields: cypS,
        // nokE, nvUB, ny2z) [nrtmqzdfy3]"
        exception.message shouldContainWithNonAbuttingText "nrtmqzdfy3"
        exception.message shouldContainWithNonAbuttingText entityIdWithMissingField
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "missing"
        exception.message should containPathOfEntityMissingFieldMatcher
      }
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
