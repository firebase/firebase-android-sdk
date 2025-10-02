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

package com.google.firebase.dataconnect.sqlite

import com.google.firebase.dataconnect.sqlite.DataConnectSequenceNumberDatabase.InvalidDatabaseException
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataConnectSequenceNumberDatabaseUnitTest {

  @get:Rule val temporaryFolder = TemporaryFolder()
  private val dbFile: File by lazy { File(temporaryFolder.newFolder(), "db.sqlite") }

  @get:Rule val randomSeedTestRule = RandomSeedTestRule()
  private val rs: RandomSource by randomSeedTestRule.rs

  @Test
  fun `nextSequenceNumber should return distinct numbers`() = runTest {
    val sequenceNumbers: List<Long> =
      withDataConnectSequenceNumberDatabase(dbFile) { db: DataConnectSequenceNumberDatabase ->
        buildList { repeat(1000) { add(db.nextSequenceNumber()) } }
      }

    sequenceNumbers.distinct() shouldContainExactlyInAnyOrder sequenceNumbers
  }

  @Test
  fun `nextSequenceNumber should return montonically-increasing numbers`() = runTest {
    val sequenceNumbers: List<Long> =
      withDataConnectSequenceNumberDatabase(dbFile) { db: DataConnectSequenceNumberDatabase ->
        buildList { repeat(1000) { add(db.nextSequenceNumber()) } }
      }

    sequenceNumbers shouldContainExactly sequenceNumbers.sorted()
  }

  @Test
  fun `nextSequenceNumber should montonically increase even after database re-opens`() = runTest {
    val sequenceNumbers: List<Long> = buildList {
      repeat(5) {
        withDataConnectSequenceNumberDatabase(dbFile) { db: DataConnectSequenceNumberDatabase ->
          repeat(1000) { add(db.nextSequenceNumber()) }
        }
      }
    }

    sequenceNumbers shouldContainExactly sequenceNumbers.sorted()
  }

  @Test
  fun `onOpen should throw if application_id is invalid`() = runTest {
    withDataConnectSequenceNumberDatabase(dbFile) { db -> db.ensureOpen() }
    val invalidApplicationId = Arb.int().filter { it != 0x432d5d29 }.next(rs)
    setDataConnectSqliteDatabaseApplicationId(dbFile, invalidApplicationId)

    val exception =
      withDataConnectSequenceNumberDatabase(dbFile) { db ->
        shouldThrow<InvalidDatabaseException> { db.ensureOpen() }
      }

    exception.message shouldContainWithNonAbuttingText "application_id"
    exception.message shouldContainWithNonAbuttingTextIgnoringCase "432d5d29"
    exception.message shouldContainWithNonAbuttingTextIgnoringCase invalidApplicationId.toString(16)
  }

  @Test
  fun `onOpen should throw if user_version is invalid`() = runTest {
    withDataConnectSequenceNumberDatabase(dbFile) { db -> db.ensureOpen() }
    val invalidUserVersion = Arb.int().filter { it != 0x142a141e }.next(rs)
    setDataConnectSqliteDatabaseUserVersion(dbFile, invalidUserVersion)
    val db = DataConnectSequenceNumberDatabase(dbFile, Dispatchers.IO, mockk(relaxed = true))

    val exception =
      try {
        shouldThrow<InvalidDatabaseException> { db.ensureOpen() }
      } finally {
        db.close()
      }

    exception.message shouldContainWithNonAbuttingText "user_version"
    exception.message shouldContainWithNonAbuttingTextIgnoringCase "142a141e"
    exception.message shouldContainWithNonAbuttingTextIgnoringCase invalidUserVersion.toString(16)
  }

  private companion object {

    private suspend inline fun <T> withDataConnectSequenceNumberDatabase(
      dbFile: File,
      block: suspend (DataConnectSequenceNumberDatabase) -> T
    ): T {
      val db = DataConnectSequenceNumberDatabase(dbFile, Dispatchers.IO, mockk(relaxed = true))
      return try {
        block(db)
      } finally {
        db.close()
      }
    }
  }
}
