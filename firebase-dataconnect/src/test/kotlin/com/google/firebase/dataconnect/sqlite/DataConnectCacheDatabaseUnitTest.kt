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

import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase.InvalidDatabaseException
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import io.kotest.assertions.throwables.shouldThrow
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
class DataConnectCacheDatabaseUnitTest {

  @get:Rule val temporaryFolder = TemporaryFolder()
  private val dbFile: File by lazy { File(temporaryFolder.newFolder(), "db.sqlite") }

  @get:Rule val randomSeedTestRule = RandomSeedTestRule()
  private val rs: RandomSource by randomSeedTestRule.rs

  @Test
  fun `onOpen should throw if application_id is invalid`() = runTest {
    withDataConnectCacheDatabase(dbFile) { db -> db.ensureOpen() }
    val invalidApplicationId = Arb.int().filter { it != 0x7f1bc816 }.next(rs)
    setDataConnectSqliteDatabaseApplicationId(dbFile, invalidApplicationId)

    val exception =
      withDataConnectCacheDatabase(dbFile) { db ->
        shouldThrow<InvalidDatabaseException> { db.ensureOpen() }
      }

    exception.message shouldContainWithNonAbuttingText "application_id"
    exception.message shouldContainWithNonAbuttingTextIgnoringCase "7f1bc816"
    exception.message shouldContainWithNonAbuttingTextIgnoringCase invalidApplicationId.toString(16)
  }

  @Test
  fun `onOpen should throw if user_version is invalid`() = runTest {
    withDataConnectCacheDatabase(dbFile) { db -> db.ensureOpen() }
    setDataConnectSqliteDatabaseUserVersion(dbFile, 2)

    val exception =
      withDataConnectCacheDatabase(dbFile) { db ->
        shouldThrow<InvalidDatabaseException> { db.ensureOpen() }
      }

    exception.message shouldContainWithNonAbuttingText "user_version"
    exception.message shouldContainWithNonAbuttingTextIgnoringCase "0 or 1"
    exception.message shouldContainWithNonAbuttingTextIgnoringCase "2"
  }

  private companion object {

    private suspend inline fun <T> withDataConnectCacheDatabase(
      dbFile: File,
      block: suspend (DataConnectCacheDatabase) -> T
    ): T {
      val db = DataConnectCacheDatabase(dbFile, Dispatchers.IO, mockk(relaxed = true))
      return try {
        block(db)
      } finally {
        db.close()
      }
    }
  }
}
