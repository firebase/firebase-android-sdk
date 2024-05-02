// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.dataconnect.connectors.demo

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.*
import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.connectors.demo.testutil.assertWith
import com.google.firebase.dataconnect.testutil.assertThrows
import com.google.firebase.dataconnect.testutil.randomAlphanumericString
import kotlinx.coroutines.test.*
import org.junit.Test

class OperationExecuteIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun insert_ShouldSucceedIfPrimaryKeyDoesNotExist() = runTest {
    val id = randomFooId()
    assertWith(connector).thatFooWithId(id).doesNotExist()
    val bar = randomBar()

    connector.insertFoo.execute(id = id) { this.bar = bar }

    assertWith(connector).thatFooWithId(id).existsWithBar(bar)
  }

  @Test
  fun insert_ShouldThrowIfPrimaryKeyExists() = runTest {
    val id = randomFooId()
    val bar = randomBar()
    connector.insertFoo.execute(id = id) { this.bar = bar }
    assertWith(connector).thatFooWithId(id).exists()

    connector.insertFoo.assertThrows(DataConnectException::class) {
      execute(id = id) { this.bar = bar }
    }
  }

  @Test
  fun insert_ShouldContainTheIdInTheResult() = runTest {
    val id = randomFooId()
    val bar = randomBar()

    val mutationResult = connector.insertFoo.execute(id = id) { this.bar = bar }

    assertThat(mutationResult.data.key).isEqualTo(FooKey(id))
  }

  @Test
  fun upsert_ShouldSucceedIfPrimaryKeyDoesNotExist() = runTest {
    val id = randomFooId()
    assertWith(connector).thatFooWithId(id).doesNotExist()
    val bar = randomBar()

    connector.upsertFoo.execute(id = id) { this.bar = bar }

    assertWith(connector).thatFooWithId(id).existsWithBar(bar)
  }

  @Test
  fun upsert_ShouldSucceedIfPrimaryKeyExists() = runTest {
    val id = randomFooId()
    val bar1 = randomBar()
    val bar2 = randomBar()
    connector.insertFoo.execute(id = id) { bar = bar1 }
    assertWith(connector).thatFooWithId(id).existsWithBar(bar1)

    connector.upsertFoo.execute(id = id) { bar = bar2 }

    assertWith(connector).thatFooWithId(id).existsWithBar(bar2)
  }

  @Test
  fun upsert_ShouldContainTheIdInTheResultOnInsert() = runTest {
    val id = randomFooId()

    val mutationResult = connector.upsertFoo.execute(id = id) { bar = randomBar() }

    assertThat(mutationResult.data.key).isEqualTo(FooKey(id))
  }

  @Test
  fun upsert_ShouldContainTheIdInTheResultOnUpdate() = runTest {
    val id = randomFooId()
    connector.insertFoo.execute(id = id) { bar = randomBar() }

    val mutationResult = connector.upsertFoo.execute(id = id) { bar = randomBar() }

    assertThat(mutationResult.data.key).isEqualTo(FooKey(id))
  }

  @Test
  fun delete_ShouldSucceedIfPrimaryKeyDoesNotExist() = runTest {
    val id = randomFooId()
    assertWith(connector).thatFooWithId(id).doesNotExist()

    connector.deleteFoo.execute(id = id)

    assertWith(connector).thatFooWithId(id).doesNotExist()
  }

  @Test
  fun delete_ShouldSucceedIfPrimaryKeyExists() = runTest {
    val id = randomFooId()
    val bar = randomBar()
    connector.insertFoo.execute(id = id) { this.bar = bar }
    assertWith(connector).thatFooWithId(id).existsWithBar(bar)

    connector.deleteFoo.execute(id = id)

    assertWith(connector).thatFooWithId(id).doesNotExist()
  }

  @Test
  fun delete_ShouldNotContainTheIdInTheResultIfNothingWasDeleted() = runTest {
    val id = randomFooId()
    assertWith(connector).thatFooWithId(id).doesNotExist()

    val mutationResult = connector.deleteFoo.execute(id = id)

    assertThat(mutationResult.data.key).isNull()
  }

  @Test
  fun delete_ShouldContainTheIdInTheResultIfTheRowWasDeleted() = runTest {
    val id = randomFooId()
    val bar = randomBar()
    connector.insertFoo.execute(id = id) { this.bar = bar }
    assertWith(connector).thatFooWithId(id).existsWithBar(bar)

    val mutationResult = connector.deleteFoo.execute(id = id)

    assertThat(mutationResult.data.key).isEqualTo(FooKey(id))
  }

  @Test
  fun deleteMany_ShouldSucceedIfNoMatches() = runTest {
    val bar = randomBar()
    assertWith(connector).thatFoosWithBar(bar).doNotExist()

    connector.deleteFoosByBar.execute(bar = bar)

    assertWith(connector).thatFoosWithBar(bar).doNotExist()
  }

  @Test
  fun deleteMany_ShouldSucceedIfMultipleMatches() = runTest {
    val bar = randomBar()
    repeat(5) { connector.insertFoo.execute(id = randomFooId()) { this.bar = bar } }
    assertWith(connector).thatFoosWithBar(bar).exist(expectedCount = 5)

    connector.deleteFoosByBar.execute(bar = bar)

    assertWith(connector).thatFoosWithBar(bar).doNotExist()
  }

  @Test
  fun deleteMany_ShouldReturnZeroIfNoMatches() = runTest {
    val bar = randomBar()
    assertWith(connector).thatFoosWithBar(bar).doNotExist()

    val mutationResult = connector.deleteFoosByBar.execute(bar = bar)

    assertThat(mutationResult.data.count).isEqualTo(0)
  }

  @Test
  fun deleteMany_ShouldReturn5If5Matches() = runTest {
    val bar = randomBar()
    repeat(5) { connector.insertFoo.execute(id = randomFooId()) { this.bar = bar } }
    assertWith(connector).thatFoosWithBar(bar).exist(expectedCount = 5)

    val mutationResult = connector.deleteFoosByBar.execute(bar = bar)

    assertThat(mutationResult.data.count).isEqualTo(5)
  }

  @Test
  fun update_ShouldSucceedIfPrimaryKeyDoesNotExist() = runTest {
    val id = randomFooId()
    assertWith(connector).thatFooWithId(id).doesNotExist()

    connector.updateFoo.execute(id = id) { newBar = randomBar() }

    assertWith(connector).thatFooWithId(id).doesNotExist()
  }

  @Test
  fun update_ShouldSucceedIfPrimaryKeyExists() = runTest {
    val id = randomFooId()
    val oldBar = randomBar()
    val newBar = randomBar()
    connector.insertFoo.execute(id = id) { bar = oldBar }
    assertWith(connector).thatFooWithId(id).existsWithBar(oldBar)

    connector.updateFoo.execute(id = id) { this.newBar = newBar }

    assertWith(connector).thatFooWithId(id).existsWithBar(newBar)
  }

  @Test
  fun update_ShouldNotContainTheIdInTheResultIfNotFound() = runTest {
    val id = randomFooId()
    assertWith(connector).thatFooWithId(id).doesNotExist()

    val mutationResult = connector.updateFoo.execute(id = id) { newBar = randomBar() }

    assertThat(mutationResult.data.key).isNull()
  }

  @Test
  fun update_ShouldContainTheIdInTheResultIfFound() = runTest {
    val id = randomFooId()
    val oldBar = randomBar()
    val newBar = randomBar()
    connector.insertFoo.execute(id = id) { bar = oldBar }
    assertWith(connector).thatFooWithId(id).existsWithBar(oldBar)

    val mutationResult = connector.updateFoo.execute(id = id) { this.newBar = newBar }

    assertThat(mutationResult.data.key).isEqualTo(FooKey(id))
  }

  @Test
  fun updateMany_ShouldSucceedIfNoMatches() = runTest {
    val oldBar = randomBar()
    val newBar = randomBar()
    assertWith(connector).thatFoosWithBar(oldBar).doNotExist()
    assertWith(connector).thatFoosWithBar(newBar).doNotExist()

    connector.updateFoosByBar.execute {
      this.oldBar = oldBar
      this.newBar = newBar
    }

    assertWith(connector).thatFoosWithBar(oldBar).doNotExist()
    assertWith(connector).thatFoosWithBar(newBar).doNotExist()
  }

  @Test
  fun updateMany_ShouldSucceedIfMultipleMatches() = runTest {
    val oldBar = randomBar()
    val newBar = randomBar()
    repeat(5) { connector.insertFoo.execute(id = randomFooId()) { bar = oldBar } }
    assertWith(connector).thatFoosWithBar(oldBar).exist(expectedCount = 5)
    assertWith(connector).thatFoosWithBar(newBar).doNotExist()

    connector.updateFoosByBar.execute {
      this.oldBar = oldBar
      this.newBar = newBar
    }

    assertWith(connector).thatFoosWithBar(oldBar).doNotExist()
    assertWith(connector).thatFoosWithBar(newBar).exist(expectedCount = 5)
  }

  @Test
  fun updateMany_ShouldReturnZeroIfNoMatches() = runTest {
    val oldBar = randomBar()
    assertWith(connector).thatFoosWithBar(oldBar).doNotExist()

    val mutationResult =
      connector.updateFoosByBar.execute {
        this.oldBar = oldBar
        newBar = randomBar()
      }

    assertThat(mutationResult.data.count).isEqualTo(0)
  }

  @Test
  fun updateMany_ShouldReturn5If5Matches() = runTest {
    val oldBar = randomBar()
    val newBar = randomBar()
    repeat(5) { connector.insertFoo.execute(id = randomFooId()) { bar = oldBar } }
    assertWith(connector).thatFoosWithBar(oldBar).exist(expectedCount = 5)
    assertWith(connector).thatFoosWithBar(newBar).doNotExist()

    val mutationResult =
      connector.updateFoosByBar.execute {
        this.oldBar = oldBar
        this.newBar = newBar
      }

    assertThat(mutationResult.data.count).isEqualTo(5)
  }

  private fun randomFooId() = randomAlphanumericString(prefix = "FooId", numRandomChars = 20)
  private fun randomBar() = randomAlphanumericString(prefix = "Bar", numRandomChars = 20)

  suspend fun DemoConnector.insertFooWithRandomId(): String {
    val id = randomFooId()
    insertFoo.execute(id = id) { bar = randomBar() }
    return id
  }
}
