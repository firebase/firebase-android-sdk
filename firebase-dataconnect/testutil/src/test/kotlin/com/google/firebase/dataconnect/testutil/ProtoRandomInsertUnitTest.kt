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

import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.property.arbitrary.value
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.assertions.withClue
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainAnyOf
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProtoRandomInsertUnitTest {

  @Test
  fun `Struct withRandomlyInsertedStruct should insert the value`() = runTest {
    val structKeyArb = Arb.proto.structKey()
    checkAll(propTestConfig, Arb.proto.struct(), Arb.proto.value()) { sampleStruct, valueToInsert ->
      val insertResult =
        sampleStruct.struct.withRandomlyInsertedValue(
          valueToInsert,
          randomSource().random,
          { structKeyArb.bind() }
        )

      val foundPaths =
        withClue("foundPaths") { insertResult.findValue(valueToInsert).shouldNotBeEmpty() }
      val originalStructs =
        foundPaths.map { insertResult.withRemovedValue(it) }.filter { it == sampleStruct.struct }
      withClue("originalStructs") { originalStructs shouldHaveSize 1 }
    }
  }

  @Test
  fun `Struct withRandomlyInsertedStruct should use the given key generator`() = runTest {
    val structKeyArb = Arb.proto.structKey()
    checkAll(propTestConfig, Arb.proto.struct(), Arb.proto.value()) { sampleStruct, valueToInsert ->
      val generatedKeys: MutableList<String> = mutableListOf()

      val insertResult =
        sampleStruct.struct.withRandomlyInsertedValue(
          valueToInsert,
          randomSource().random,
          { structKeyArb.bind().also { generatedKeys.add(it) } }
        )

      val foundPaths =
        withClue("foundPaths") { insertResult.findValue(valueToInsert).shouldNotBeEmpty() }
      val foundKeys =
        foundPaths.mapNotNull { it.last() as? StructKeyProtoValuePathComponent }.map { it.field }
      generatedKeys shouldContainAnyOf foundKeys
    }
  }

  @Test
  fun `Struct withRandomlyInsertedStruct should use the given random`() = runTest {
    val structKeyArb = Arb.proto.structKey()
    checkAll(propTestConfig, Arb.proto.struct(), Arb.proto.value(), Arb.long()) {
      sampleStruct,
      valueToInsert,
      randomSeed ->
      fun doInsert(): Struct {
        val rs = RandomSource.seeded(randomSeed)
        return sampleStruct.struct
          .deepCopy()
          .withRandomlyInsertedValue(valueToInsert, rs.random, { structKeyArb.sample(rs).value })
      }

      val insertResult1 = doInsert()
      val insertResult2 = doInsert()

      insertResult1 shouldBe insertResult2
    }
  }

  private companion object {
    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33),
        shrinkingMode = ShrinkingMode.Off,
      )

    fun Struct.withRemovedValue(path: ProtoValuePath): Struct {
      var found = false
      val structWithRemovedValue = map { curPath, value ->
        if (curPath != path) {
          value
        } else {
          found = true
          null
        }
      }
      check(found) { "path $path not found in struct: $this [jr8we488nh]" }
      return structWithRemovedValue
    }

    fun Struct.findValue(value: Value): List<ProtoValuePath> =
      walk().filter { it.value === value }.map { it.path }.toList()
  }
}
