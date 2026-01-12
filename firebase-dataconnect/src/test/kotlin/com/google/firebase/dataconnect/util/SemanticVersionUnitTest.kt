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

import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.semanticVersion
import com.google.firebase.dataconnect.testutil.property.arbitrary.threeValues
import com.google.firebase.dataconnect.testutil.property.arbitrary.twoValues
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeSortedBy
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SemanticVersionUnitTest {

  @Test
  fun `toString() should return correct string`() = runTest {
    checkAll(propTestConfig, Arb.threeValues(Arb.int())) { (major, minor, patch) ->
      val semanticVersion = SemanticVersion(major, minor, patch)

      val expectedToStringResult = buildString {
        append(major)
        append('.')
        append(minor)
        append('.')
        append(patch)
      }
      semanticVersion.toString() shouldBe expectedToStringResult
    }
  }

  @Test
  fun `encodeToInt() round trips with decodeFromInt()`() = runTest {
    checkAll(propTestConfig, Arb.threeValues(Arb.int(0..999))) { (major, minor, patch) ->
      val semanticVersion = SemanticVersion(major, minor, patch)
      val intEncoding = semanticVersion.encodeToInt()
      val decodedSemanticVersion = SemanticVersion.decodeFromInt(intEncoding)
      withClue("intEncoding=$intEncoding") { decodedSemanticVersion shouldBe semanticVersion }
    }
  }

  @Test
  fun `decodeSemanticVersion() returns the correct value`() = runTest {
    checkAll(propTestConfig, Arb.int()) { intEncoding ->
      intEncoding.decodeSemanticVersion() shouldBe SemanticVersion.decodeFromInt(intEncoding)
    }
  }

  @Test
  fun `comparator orders first by major version`() = runTest {
    val semanticVersionArb = Arb.dataConnect.semanticVersion()
    checkAll(propTestConfig, Arb.int(2..10)) { versionCount ->
      val versions = List(versionCount) { semanticVersionArb.bind() }
      val sortedVersions = versions.sortedWith(SemanticVersion.comparator)
      sortedVersions shouldBeSortedBy { it.major }
    }
  }

  @Test
  fun `comparator orders second by minor version`() = runTest {
    checkAll(propTestConfig, Arb.int(), Arb.int(2..10)) { major, versionCount ->
      val semanticVersionArb = Arb.dataConnect.semanticVersion(major = Arb.constant(major))
      val versions = List(versionCount) { semanticVersionArb.bind() }
      val sortedVersions = versions.sortedWith(SemanticVersion.comparator)
      sortedVersions shouldBeSortedBy { it.minor }
    }
  }

  @Test
  fun `comparator orders third by patch version`() = runTest {
    checkAll(propTestConfig, Arb.int(), Arb.int(), Arb.int(2..10)) { major, minor, versionCount ->
      val semanticVersionArb =
        Arb.dataConnect.semanticVersion(
          major = Arb.constant(major),
          minor = Arb.constant(minor),
        )
      val versions = List(versionCount) { semanticVersionArb.bind() }
      val sortedVersions = versions.sortedWith(SemanticVersion.comparator)
      sortedVersions shouldBeSortedBy { it.patch }
    }
  }

  @Test
  fun `compareTo orders using comparator`() = runTest {
    checkAll(propTestConfig, Arb.twoValues(Arb.dataConnect.semanticVersion())) {
      (version1, version2) ->
      val compareToResult = version1.compareTo(version2)

      val comparatorResult = SemanticVersion.comparator.compare(version1, version2)
      compareToResult shouldBe comparatorResult
    }
  }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 1000,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2)
      )
  }
}
