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

package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.dataConnectPath as dataConnectPathArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.fieldPathSegment as fieldPathSegmentArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.listIndexPathSegment as listIndexPathSegmentArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.pathSegment as dataConnectPathSegmentArb
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.next
import com.google.firebase.dataconnect.testutil.property.arbitrary.twoValues
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.string
import io.kotest.property.assume
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

private val propTestConfig =
  PropTestConfig(iterations = 20, edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.25))

/** Unit tests for [DataConnectPathSegment.Field] */
class DataConnectPathSegmentFieldUnitTest {

  @Test
  fun `constructor should set field property`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string()) { field ->
      val pathSegment = DataConnectPathSegment.Field(field)
      pathSegment.field shouldBeSameInstanceAs field
    }
  }

  @Test
  fun `toString() should return a string equal to the field property`() = runTest {
    checkAll(propTestConfig, fieldPathSegmentArb()) { pathSegment: DataConnectPathSegment.Field ->
      pathSegment.toString() shouldBeSameInstanceAs pathSegment.field
    }
  }

  @Test
  fun `equals() should return true for the exact same instance`() = runTest {
    checkAll(propTestConfig, fieldPathSegmentArb()) { pathSegment: DataConnectPathSegment.Field ->
      pathSegment.equals(pathSegment) shouldBe true
    }
  }

  @Test
  fun `equals() should return true for an equal instance`() = runTest {
    checkAll(propTestConfig, fieldPathSegmentArb()) { pathSegment1: DataConnectPathSegment.Field ->
      val pathSegment2 = DataConnectPathSegment.Field(pathSegment1.field)
      pathSegment1.equals(pathSegment2) shouldBe true
      pathSegment2.equals(pathSegment1) shouldBe true
    }
  }

  @Test
  fun `equals() should return false for null`() = runTest {
    checkAll(propTestConfig, fieldPathSegmentArb()) { pathSegment: DataConnectPathSegment.Field ->
      pathSegment.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals() should return false for a different type`() = runTest {
    val otherTypes = Arb.choice(Arb.string(), Arb.int(), listIndexPathSegmentArb())
    checkAll(propTestConfig, fieldPathSegmentArb(), otherTypes) {
      pathSegment: DataConnectPathSegment.Field,
      other ->
      pathSegment.equals(other) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when field differs`() = runTest {
    checkAll(propTestConfig, fieldPathSegmentArb(), fieldPathSegmentArb()) {
      pathSegment1: DataConnectPathSegment.Field,
      pathSegment2: DataConnectPathSegment.Field ->
      assume(pathSegment1.field != pathSegment2.field)
      pathSegment1.equals(pathSegment2) shouldBe false
    }
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() =
    runTest {
      checkAll(propTestConfig, fieldPathSegmentArb()) { pathSegment: DataConnectPathSegment.Field ->
        val hashCode1 = pathSegment.hashCode()
        pathSegment.hashCode() shouldBe hashCode1
        pathSegment.hashCode() shouldBe hashCode1
      }
    }

  @Test
  fun `hashCode() should return the same value on equal objects`() = runTest {
    checkAll(propTestConfig, fieldPathSegmentArb()) { pathSegment1: DataConnectPathSegment.Field ->
      val pathSegment2 = DataConnectPathSegment.Field(pathSegment1.field)
      pathSegment1.hashCode() shouldBe pathSegment2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return a different value if field is different`() = runTest {
    checkAll(propTestConfig, fieldPathSegmentArb(), fieldPathSegmentArb()) {
      pathSegment1: DataConnectPathSegment.Field,
      pathSegment2: DataConnectPathSegment.Field ->
      assume(pathSegment1.field.hashCode() != pathSegment2.field.hashCode())
      pathSegment1.hashCode() shouldNotBe pathSegment2.hashCode()
    }
  }
}

/** Unit tests for [DataConnectPathSegment.ListIndex] */
class DataConnectPathSegmentListIndexUnitTest {

  @Test
  fun `constructor should set index property`() = runTest {
    checkAll(propTestConfig, Arb.int()) { listIndex ->
      val pathSegment = DataConnectPathSegment.ListIndex(listIndex)
      pathSegment.index shouldBe listIndex
    }
  }

  @Test
  fun `toString() should return a string equal to the index property`() = runTest {
    checkAll(propTestConfig, listIndexPathSegmentArb()) {
      pathSegment: DataConnectPathSegment.ListIndex ->
      pathSegment.toString() shouldBe "${pathSegment.index}"
    }
  }

  @Test
  fun `equals() should return true for the exact same instance`() = runTest {
    checkAll(propTestConfig, listIndexPathSegmentArb()) {
      pathSegment: DataConnectPathSegment.ListIndex ->
      pathSegment.equals(pathSegment) shouldBe true
    }
  }

  @Test
  fun `equals() should return true for an equal instance`() = runTest {
    checkAll(propTestConfig, listIndexPathSegmentArb()) {
      pathSegment1: DataConnectPathSegment.ListIndex ->
      val pathSegment2 = DataConnectPathSegment.ListIndex(pathSegment1.index)
      pathSegment1.equals(pathSegment2) shouldBe true
      pathSegment2.equals(pathSegment1) shouldBe true
    }
  }

  @Test
  fun `equals() should return false for null`() = runTest {
    checkAll(propTestConfig, listIndexPathSegmentArb()) {
      pathSegment: DataConnectPathSegment.ListIndex ->
      pathSegment.equals(null) shouldBe false
    }
  }

  @Test
  fun `equals() should return false for a different type`() = runTest {
    val otherTypes = Arb.choice(Arb.string(), Arb.int(), fieldPathSegmentArb())
    checkAll(propTestConfig, listIndexPathSegmentArb(), otherTypes) {
      pathSegment: DataConnectPathSegment.ListIndex,
      other ->
      pathSegment.equals(other) shouldBe false
    }
  }

  @Test
  fun `equals() should return false when field differs`() = runTest {
    checkAll(propTestConfig, listIndexPathSegmentArb(), listIndexPathSegmentArb()) {
      pathSegment1: DataConnectPathSegment.ListIndex,
      pathSegment2: DataConnectPathSegment.ListIndex ->
      assume(pathSegment1.index != pathSegment2.index)
      pathSegment1.equals(pathSegment2) shouldBe false
    }
  }

  @Test
  fun `hashCode() should return the same value each time it is invoked on a given object`() =
    runTest {
      checkAll(propTestConfig, listIndexPathSegmentArb()) {
        pathSegment: DataConnectPathSegment.ListIndex ->
        val hashCode1 = pathSegment.hashCode()
        pathSegment.hashCode() shouldBe hashCode1
        pathSegment.hashCode() shouldBe hashCode1
      }
    }

  @Test
  fun `hashCode() should return the same value on equal objects`() = runTest {
    checkAll(propTestConfig, listIndexPathSegmentArb()) {
      pathSegment1: DataConnectPathSegment.ListIndex ->
      val pathSegment2 = DataConnectPathSegment.ListIndex(pathSegment1.index)
      pathSegment1.hashCode() shouldBe pathSegment2.hashCode()
    }
  }

  @Test
  fun `hashCode() should return a different value if index is different`() = runTest {
    checkAll(propTestConfig, listIndexPathSegmentArb(), listIndexPathSegmentArb()) {
      pathSegment1: DataConnectPathSegment.ListIndex,
      pathSegment2: DataConnectPathSegment.ListIndex ->
      assume(pathSegment1.index.hashCode() != pathSegment2.index.hashCode())
      pathSegment1.hashCode() shouldNotBe pathSegment2.hashCode()
    }
  }
}

/** Unit tests for extension functions of [DataConnectPathSegment] */
class DataConnectPathSegmentExtensionFunctionsUnitTest {

  @Test
  fun `toPathString on empty path`() {
    val emptyPath: DataConnectPath = emptyList()
    emptyPath.toPathString() shouldBe ""
  }

  @Test
  fun `toPathString on single field`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string()) { fieldName ->
      val path = listOf(DataConnectPathSegment.Field(fieldName))
      path.toPathString() shouldBe fieldName
    }
  }

  @Test
  fun `toPathString on single list index`() = runTest {
    checkAll(propTestConfig, Arb.int()) { listIndex ->
      val path = listOf(DataConnectPathSegment.ListIndex(listIndex))
      path.toPathString() shouldBe "[$listIndex]"
    }
  }

  @Test
  fun `toPathString on path of all fields`() = runTest {
    checkAll(propTestConfig, Arb.list(fieldPathSegmentArb(), 2..5)) { path ->
      path.toPathString() shouldBe path.joinToString(".") { it.field }
    }
  }

  @Test
  fun `toPathString on path of all list indexes`() = runTest {
    checkAll(propTestConfig, Arb.list(listIndexPathSegmentArb(), 2..5)) { path ->
      path.toPathString() shouldBe path.joinToString("") { "[${it.index}]" }
    }
  }

  @Test
  fun `toPathString on path of alternating fields and list indexes`() = runTest {
    val arb = Arb.list(Arb.pair(fieldPathSegmentArb(), listIndexPathSegmentArb()), 1..5)
    checkAll(propTestConfig, arb) { pairs ->
      val path = pairs.flatMap { it.toList() }

      val pathString = path.toPathString()

      val expectedPathString = buildString {
        pairs.forEachIndexed { index, (fieldSegment, indexSegment) ->
          if (index > 0) {
            append('.')
          }
          append(fieldSegment.field)
          append('[')
          append(indexSegment.index)
          append(']')
        }
      }
      pathString shouldBe expectedPathString
    }
  }

  @Test
  fun `toPathString on path of alternating list indexes and fields`() = runTest {
    val arb = Arb.list(Arb.pair(listIndexPathSegmentArb(), fieldPathSegmentArb()), 1..5)
    checkAll(propTestConfig, arb) { pairs ->
      val path = pairs.flatMap { it.toList() }

      val pathString = path.toPathString()

      val expectedPathString = buildString {
        pairs.forEach { (indexSegment, fieldSegment) ->
          append('[')
          append(indexSegment.index)
          append(']')
          append('.')
          append(fieldSegment.field)
        }
      }
      pathString shouldBe expectedPathString
    }
  }

  @Test
  fun `appendPathStringTo on empty StringBuilder`() = runTest {
    checkAll(propTestConfig, dataConnectPathArb()) { path ->
      val sb = StringBuilder()

      path.appendPathStringTo(sb)

      sb.toString() shouldBe path.toPathString()
    }
  }

  @Test
  fun `appendPathStringTo on non-empty StringBuilder`() = runTest {
    checkAll(propTestConfig, Arb.string(), dataConnectPathArb()) { prefix, path ->
      val sb = StringBuilder(prefix)

      path.appendPathStringTo(sb)

      sb.toString() shouldBe prefix + path.toPathString()
    }
  }

  @Test
  fun `MutableList addField should add a field path segment`() = runTest {
    checkAll(propTestConfig, dataConnectPathArb(), Arb.dataConnect.string()) { path, fieldName ->
      val mutablePath = path.toMutableList()

      mutablePath.addField(fieldName)

      val expected = buildList {
        addAll(path)
        add(DataConnectPathSegment.Field(fieldName))
      }
      mutablePath shouldContainExactly expected
    }
  }

  @Test
  fun `MutableList addField should return the added path segment`() = runTest {
    checkAll(propTestConfig, dataConnectPathArb(), Arb.dataConnect.string()) { path, fieldName ->
      val mutablePath = path.toMutableList()

      val returnValue = mutablePath.addField(fieldName)

      returnValue shouldBe DataConnectPathSegment.Field(fieldName)
    }
  }

  @Test
  fun `MutableList addListIndex should add a list index path segment`() = runTest {
    checkAll(propTestConfig, dataConnectPathArb(), Arb.int()) { path, listIndex ->
      val mutablePath = path.toMutableList()

      mutablePath.addListIndex(listIndex)

      val expected = buildList {
        addAll(path)
        add(DataConnectPathSegment.ListIndex(listIndex))
      }
      mutablePath shouldContainExactly expected
    }
  }

  @Test
  fun `MutableList addListIndex should return the added path segment`() = runTest {
    checkAll(propTestConfig, dataConnectPathArb(), Arb.int()) { path, listIndex ->
      val mutablePath = path.toMutableList()

      val returnValue = mutablePath.addListIndex(listIndex)

      returnValue shouldBe DataConnectPathSegment.ListIndex(listIndex)
    }
  }

  @Test
  fun `MutableList withAddedPathSegment should run the given block with the path segment added`() =
    runTest {
      checkAll(propTestConfig, dataConnectPathArb(), dataConnectPathSegmentArb()) {
        path,
        pathSegment ->
        val mutablePath = path.toMutableList()
        val expectedPathInBlock = buildList {
          addAll(path)
          add(pathSegment)
        }
        mutablePath.withAddedPathSegment(pathSegment) {
          mutablePath shouldContainExactly expectedPathInBlock
        }
      }
    }

  @Test
  fun `MutableList withAddedField should run the given block with the field added`() = runTest {
    checkAll(propTestConfig, dataConnectPathArb(), Arb.dataConnect.string()) { path, fieldName ->
      val mutablePath = path.toMutableList()

      mutablePath.withAddedField(fieldName) {
        mutablePath shouldContainExactly path.toMutableList().also { it.addField(fieldName) }
      }
    }
  }

  @Test
  fun `MutableList withAddedListIndex should run the given block with the list index added`() =
    runTest {
      checkAll(propTestConfig, dataConnectPathArb(), Arb.int()) { path, listIndex ->
        val mutablePath = path.toMutableList()

        mutablePath.withAddedListIndex(listIndex) {
          mutablePath shouldContainExactly path.toMutableList().also { it.addListIndex(listIndex) }
        }
      }
    }

  @Test
  fun `MutableList withAddedPathSegment should remove the added path segment before returning`() =
    runTest {
      checkAll(propTestConfig, dataConnectPathArb(), dataConnectPathSegmentArb()) {
        path,
        pathSegment ->
        val mutablePath = path.toMutableList()

        mutablePath.withAddedPathSegment(pathSegment) {}

        mutablePath shouldContainExactly path
      }
    }

  @Test
  fun `MutableList withAddedField should remove the added path segment before returning`() =
    runTest {
      checkAll(propTestConfig, dataConnectPathArb(), Arb.dataConnect.string()) { path, fieldName ->
        val mutablePath = path.toMutableList()

        mutablePath.withAddedField(fieldName) {}

        mutablePath shouldContainExactly path
      }
    }

  @Test
  fun `MutableList withAddedListIndex should remove the added path segment before returning`() =
    runTest {
      checkAll(propTestConfig, dataConnectPathArb(), Arb.int()) { path, listIndex ->
        val mutablePath = path.toMutableList()

        mutablePath.withAddedListIndex(listIndex) {}

        mutablePath shouldContainExactly path
      }
    }

  @Test
  fun `MutableList withAddedPathSegment should return whatever the block returns`() = runTest {
    data class ReturnValue(val value: Int)
    checkAll(
      propTestConfig,
      dataConnectPathArb(),
      dataConnectPathSegmentArb(),
      Arb.int().map(::ReturnValue)
    ) { path, pathSegment, returnValue ->
      val mutablePath = path.toMutableList()

      val actualReturnValue = mutablePath.withAddedPathSegment(pathSegment) { returnValue }

      actualReturnValue shouldBeSameInstanceAs returnValue
    }
  }

  @Test
  fun `MutableList withAddedField should return whatever the block returns`() = runTest {
    data class ReturnValue(val value: Int)
    checkAll(
      propTestConfig,
      dataConnectPathArb(),
      Arb.dataConnect.string(),
      Arb.int().map(::ReturnValue)
    ) { path, fieldName, returnValue ->
      val mutablePath = path.toMutableList()

      val actualReturnValue = mutablePath.withAddedField(fieldName) { returnValue }

      actualReturnValue shouldBeSameInstanceAs returnValue
    }
  }

  @Test
  fun `MutableList withAddedListIndex should return whatever the block returns`() = runTest {
    data class ReturnValue(val value: Int)
    checkAll(propTestConfig, dataConnectPathArb(), Arb.int(), Arb.int().map(::ReturnValue)) {
      path,
      listIndex,
      returnValue ->
      val mutablePath = path.toMutableList()

      val actualReturnValue = mutablePath.withAddedListIndex(listIndex) { returnValue }

      actualReturnValue shouldBeSameInstanceAs returnValue
    }
  }

  @Test
  fun `List withAddedPathSegment should return the receiving path with the given segment added`() =
    runTest {
      checkAll(propTestConfig, dataConnectPathArb(), dataConnectPathSegmentArb()) {
        path,
        pathSegment ->
        val result = path.withAddedPathSegment(pathSegment)

        val expected = buildList {
          addAll(path)
          add(pathSegment)
        }
        result shouldContainExactly expected
      }
    }

  @Test
  fun `List withAddedField should return the receiving path with a field segment added`() =
    runTest {
      checkAll(propTestConfig, dataConnectPathArb(), Arb.dataConnect.string()) { path, fieldName ->
        val result = path.withAddedField(fieldName)

        val expected = buildList {
          addAll(path)
          add(DataConnectPathSegment.Field(fieldName))
        }
        result shouldContainExactly expected
      }
    }

  @Test
  fun `List withAddedListIndex should return the receiving path with a list index segment added`() =
    runTest {
      checkAll(propTestConfig, dataConnectPathArb(), Arb.int()) { path, listIndex ->
        val result = path.withAddedListIndex(listIndex)

        val expected = buildList {
          addAll(path)
          add(DataConnectPathSegment.ListIndex(listIndex))
        }
        result shouldContainExactly expected
      }
    }

  @Test
  fun `emptyDataConnectPath() returns an empty path`() {
    emptyDataConnectPath() shouldBe emptyList()
  }

  @Test
  fun `emptyMutableDataConnectPath() returns an empty path`() {
    emptyMutableDataConnectPath().shouldBeEmpty()
  }

  @Test
  fun `emptyMutableDataConnectPath() should always return a new instance`() {
    val path1 = emptyMutableDataConnectPath()
    val path2 = emptyMutableDataConnectPath()

    path1 shouldNotBeSameInstanceAs path2
  }
}

/** Unit tests for extension functions of [DataConnectPathSegmentComparator] */
class DataConnectPathSegmentComparatorUnitTest {

  @Test
  fun `compare() returns 0 for same object`() = runTest {
    checkAll(propTestConfig, dataConnectPathSegmentArb()) { pathSegment ->
      DataConnectPathSegmentComparator.compare(pathSegment, pathSegment) shouldBe 0
    }
  }

  @Test
  fun `compare() returns 0 for equal objects`() = runTest {
    checkAll(propTestConfig, dataConnectPathSegmentArb()) { pathSegment1 ->
      val pathSegment2 =
        when (pathSegment1) {
          is DataConnectPathSegment.Field -> DataConnectPathSegment.Field(pathSegment1.field)
          is DataConnectPathSegment.ListIndex ->
            DataConnectPathSegment.ListIndex(pathSegment1.index)
        }
      DataConnectPathSegmentComparator.compare(pathSegment1, pathSegment2) shouldBe 0
    }
  }

  @Test
  fun `compare() is reflexive`() = runTest {
    checkAll(propTestConfig, Arb.twoValues(dataConnectPathSegmentArb())) {
      (pathSegment1, pathSegment2) ->
      val result1 = DataConnectPathSegmentComparator.compare(pathSegment1, pathSegment2)
      val result2 = DataConnectPathSegmentComparator.compare(pathSegment2, pathSegment1)
      result1 shouldBe -result2
    }
  }

  @Test
  fun `compare() is a deterministic pure function`() = runTest {
    checkAll(propTestConfig, Arb.list(dataConnectPathSegmentArb(), 0..20)) { pathSegments ->
      val pathSegmentsSorted1 = pathSegments.sortedWith(DataConnectPathSegmentComparator)
      val pathSegmentsSorted2 = pathSegments.sortedWith(DataConnectPathSegmentComparator)

      pathSegmentsSorted1 shouldBe pathSegmentsSorted2
    }
  }
}
/** Unit tests for extension functions of [DataConnectPathComparator] */
class DataConnectPathComparatorUnitTest {

  @Test
  fun `compare() returns 0 for same object`() = runTest {
    checkAll(propTestConfig, dataConnectPathArb()) { path ->
      DataConnectPathComparator.compare(path, path) shouldBe 0
    }
  }

  @Test
  fun `compare() returns 0 for equal objects`() = runTest {
    checkAll(propTestConfig, dataConnectPathArb()) { path1 ->
      val path2 = path1.toList()
      DataConnectPathComparator.compare(path1, path2) shouldBe 0
    }
  }

  @Test
  fun `compare() is reflexive`() = runTest {
    checkAll(propTestConfig, Arb.twoValues(dataConnectPathArb())) { (path1, path2) ->
      val result1 = DataConnectPathComparator.compare(path1, path2)
      val result2 = DataConnectPathComparator.compare(path2, path1)
      result1 shouldBe -result2
    }
  }

  @Test
  fun `compare() is a deterministic pure function`() = runTest {
    checkAll(propTestConfig, Arb.list(dataConnectPathArb(), 0..20)) { paths ->
      val pathsSorted1 = paths.sortedWith(DataConnectPathComparator)
      val pathsSorted2 = paths.sortedWith(DataConnectPathComparator)

      pathsSorted1 shouldBe pathsSorted2
    }
  }

  @Test
  fun `compare() orders prefix before suffix`() = runTest {
    checkAll(propTestConfig, dataConnectPathArb(), dataConnectPathArb(size = 1..5)) { prefix, suffix
      ->
      assertSoftly {
        withClue("compare(prefix, prefix + suffix)") {
          DataConnectPathComparator.compare(prefix, prefix + suffix) shouldBeLessThan 0
        }
        withClue("compare(prefix + suffix, prefix)") {
          DataConnectPathComparator.compare(prefix + suffix, prefix) shouldBeGreaterThan 0
        }
      }
    }
  }
}
