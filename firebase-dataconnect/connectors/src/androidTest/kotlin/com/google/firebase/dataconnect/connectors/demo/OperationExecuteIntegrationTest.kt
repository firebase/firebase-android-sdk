/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.DataConnectException
import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OperationExecuteIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun insert_ShouldSucceedIfPrimaryKeyDoesNotExist() = runTest {
    val id = Arb.fooId().next(rs)
    fooWithId(id).shouldNotExist()
    val bar = Arb.bar().next(rs)

    connector.insertFoo.execute(id = id) { this.bar = bar }

    fooWithId(id) shouldExistWithBar bar
  }

  @Test
  fun insert_ShouldThrowIfPrimaryKeyExists() = runTest {
    val id = insertFooWithRandomId()

    shouldThrow<DataConnectException> {
      connector.insertFoo.execute(id = id) { bar = Arb.bar().next(rs) }
    }
  }

  @Test
  fun insert_ShouldContainTheIdInTheResult() = runTest {
    val id = Arb.fooId().next(rs)
    val bar = Arb.bar().next(rs)

    val mutationResult = connector.insertFoo.execute(id = id) { this.bar = bar }

    mutationResult.data.key shouldBe FooKey(id)
  }

  @Test
  fun upsert_ShouldSucceedIfPrimaryKeyDoesNotExist() = runTest {
    val id = Arb.fooId().next(rs)
    fooWithId(id).shouldNotExist()
    val bar = Arb.bar().next(rs)

    connector.upsertFoo.execute(id = id) { this.bar = bar }

    fooWithId(id) shouldExistWithBar bar
  }

  @Test
  fun upsert_ShouldSucceedIfPrimaryKeyExists() = runTest {
    val id = Arb.fooId().next(rs)
    val bar1 = Arb.bar().next(rs)
    val bar2 = Arb.bar().next(rs)
    connector.insertFoo.execute(id = id) { bar = bar1 }
    fooWithId(id) shouldExistWithBar bar1

    connector.upsertFoo.execute(id = id) { bar = bar2 }

    fooWithId(id) shouldExistWithBar bar2
  }

  @Test
  fun upsert_ShouldContainTheIdInTheResultOnInsert() = runTest {
    val id = Arb.fooId().next(rs)

    val mutationResult = connector.upsertFoo.execute(id = id) { bar = Arb.bar().next(rs) }

    mutationResult.data.key shouldBe FooKey(id)
  }

  @Test
  fun upsert_ShouldContainTheIdInTheResultOnUpdate() = runTest {
    val id = Arb.fooId().next(rs)
    connector.insertFoo.execute(id = id) { bar = Arb.bar().next(rs) }

    val mutationResult = connector.upsertFoo.execute(id = id) { bar = Arb.bar().next(rs) }

    mutationResult.data.key shouldBe FooKey(id)
  }

  @Test
  fun delete_ShouldSucceedIfPrimaryKeyDoesNotExist() = runTest {
    val id = Arb.fooId().next(rs)
    fooWithId(id).shouldNotExist()

    connector.deleteFoo.execute(id = id)

    fooWithId(id).shouldNotExist()
  }

  @Test
  fun delete_ShouldSucceedIfPrimaryKeyExists() = runTest {
    val id = insertFooWithRandomId()

    connector.deleteFoo.execute(id = id)

    fooWithId(id).shouldNotExist()
  }

  @Test
  fun delete_ShouldNotContainTheIdInTheResultIfNothingWasDeleted() = runTest {
    val id = Arb.fooId().next(rs)
    fooWithId(id).shouldNotExist()

    val mutationResult = connector.deleteFoo.execute(id = id)

    mutationResult.data.key.shouldBeNull()
  }

  @Test
  fun delete_ShouldContainTheIdInTheResultIfTheRowWasDeleted() = runTest {
    val id = insertFooWithRandomId()

    val mutationResult = connector.deleteFoo.execute(id = id)

    mutationResult.data.key shouldBe FooKey(id)
  }

  @Test
  fun deleteMany_ShouldSucceedIfNoMatches() = runTest {
    val bar = Arb.bar().next(rs)
    foosWithBar(bar).shouldNotExist()

    connector.deleteFoosByBar.execute(bar = bar)

    foosWithBar(bar).shouldNotExist()
  }

  @Test
  fun deleteMany_ShouldSucceedIfMultipleMatches() = runTest {
    val bar = Arb.bar().next(rs)
    repeat(5) { connector.insertFoo.execute(id = Arb.fooId().next(rs)) { this.bar = bar } }
    foosWithBar(bar).shouldExist()

    connector.deleteFoosByBar.execute(bar = bar)

    foosWithBar(bar).shouldNotExist()
  }

  @Test
  fun deleteMany_ShouldReturnZeroIfNoMatches() = runTest {
    val bar = Arb.bar().next(rs)
    foosWithBar(bar).shouldNotExist()

    val mutationResult = connector.deleteFoosByBar.execute(bar = bar)

    mutationResult.data.count shouldBe 0
  }

  @Test
  fun deleteMany_ShouldReturn5If5Matches() = runTest {
    val bar = Arb.bar().next(rs)
    repeat(5) { connector.insertFoo.execute(id = Arb.fooId().next(rs)) { this.bar = bar } }
    foosWithBar(bar).shouldExist()

    val mutationResult = connector.deleteFoosByBar.execute(bar = bar)

    mutationResult.data.count shouldBe 5
  }

  @Test
  fun update_ShouldSucceedIfPrimaryKeyDoesNotExist() = runTest {
    val id = Arb.fooId().next(rs)
    fooWithId(id).shouldNotExist()

    connector.updateFoo.execute(id = id) { newBar = Arb.bar().next(rs) }

    fooWithId(id).shouldNotExist()
  }

  @Test
  fun update_ShouldSucceedIfPrimaryKeyExists() = runTest {
    val id = Arb.fooId().next(rs)
    val oldBar = Arb.bar().next(rs)
    val newBar = Arb.bar().next(rs)
    connector.insertFoo.execute(id = id) { bar = oldBar }
    fooWithId(id) shouldExistWithBar oldBar

    connector.updateFoo.execute(id = id) { this.newBar = newBar }

    fooWithId(id) shouldExistWithBar newBar
  }

  @Test
  fun update_ShouldNotContainTheIdInTheResultIfNotFound() = runTest {
    val id = Arb.fooId().next(rs)
    fooWithId(id).shouldNotExist()

    val mutationResult = connector.updateFoo.execute(id = id) { newBar = Arb.bar().next(rs) }

    mutationResult.data.key.shouldBeNull()
  }

  @Test
  fun update_ShouldContainTheIdInTheResultIfFound() = runTest {
    val id = Arb.fooId().next(rs)
    val oldBar = Arb.bar().next(rs)
    val newBar = Arb.bar().next(rs)
    connector.insertFoo.execute(id = id) { bar = oldBar }
    fooWithId(id) shouldExistWithBar oldBar

    val mutationResult = connector.updateFoo.execute(id = id) { this.newBar = newBar }

    mutationResult.data.key shouldBe FooKey(id)
  }

  @Test
  fun updateMany_ShouldSucceedIfNoMatches() = runTest {
    val oldBar = Arb.bar().next(rs)
    val newBar = Arb.bar().next(rs)
    foosWithBar(oldBar).shouldNotExist()
    foosWithBar(newBar).shouldNotExist()

    connector.updateFoosByBar.execute {
      this.oldBar = oldBar
      this.newBar = newBar
    }

    foosWithBar(oldBar).shouldNotExist()
    foosWithBar(newBar).shouldNotExist()
  }

  @Test
  fun updateMany_ShouldSucceedIfMultipleMatches() = runTest {
    val oldBar = Arb.bar().next(rs)
    val newBar = Arb.bar().next(rs)
    repeat(5) { connector.insertFoo.execute(id = Arb.fooId().next(rs)) { bar = oldBar } }
    foosWithBar(oldBar).shouldExist()
    foosWithBar(newBar).shouldNotExist()

    connector.updateFoosByBar.execute {
      this.oldBar = oldBar
      this.newBar = newBar
    }

    assertSoftly {
      foosWithBar(oldBar).shouldNotExist()
      foosWithBar(newBar).shouldExist()
    }
  }

  @Test
  fun updateMany_ShouldReturnZeroIfNoMatches() = runTest {
    val oldBar = Arb.bar().next(rs)
    foosWithBar(oldBar).shouldNotExist()

    val mutationResult =
      connector.updateFoosByBar.execute {
        this.oldBar = oldBar
        newBar = Arb.bar().next(rs)
      }

    mutationResult.data.count shouldBe 0
  }

  @Test
  fun updateMany_ShouldReturn5If5Matches() = runTest {
    val oldBar = Arb.bar().next(rs)
    val newBar = Arb.bar().next(rs)
    repeat(5) { connector.insertFoo.execute(id = Arb.fooId().next(rs)) { bar = oldBar } }
    foosWithBar(oldBar).shouldExist()
    foosWithBar(newBar).shouldNotExist()

    val mutationResult =
      connector.updateFoosByBar.execute {
        this.oldBar = oldBar
        this.newBar = newBar
      }

    mutationResult.data.count shouldBe 5
  }
}
