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

package com.google.firebase.dataconnect.sqlite2

import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.sqlite2.DataConnectCacheDatabaseMigrator.InvalidApplicationIdException
import com.google.firebase.dataconnect.sqlite2.SQLiteDatabaseExts.getApplicationId
import com.google.firebase.dataconnect.sqlite2.SQLiteDatabaseExts.setApplicationId
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.util.StringUtil.to0xHexString
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import io.kotest.property.assume
import io.kotest.property.checkAll
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataConnectCacheDatabaseMigratorUnitTest {

  @get:Rule val temporaryFolder = TemporaryFolder()
  private val dbFile: File by lazy { File(temporaryFolder.newFolder(), "db.sqlite") }

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()

  @Test
  fun `migrate() idempotency`() {
    val mockLogger: Logger = mockk(relaxed = true)
    DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger).use { db ->
      DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
      DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
      DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
    }
  }

  @Test
  fun `migrate() application_id should set the value in a new database`() {
    val mockLogger: Logger = mockk(relaxed = true)
    val applicationId =
      DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger).use { db ->
        DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
        db.getApplicationId(mockLogger)
      }
    applicationId shouldBe 0x7f1bc816
  }

  @Test
  fun `migrate() application_id should leave value alone if already correct`() {
    val mockLogger: Logger = mockk(relaxed = true)
    val applicationId =
      DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger).use { db ->
        db.setApplicationId(mockLogger, 0x7f1bc816)
        DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
        db.getApplicationId(mockLogger)
      }
    applicationId shouldBe 0x7f1bc816
  }

  @Test
  fun `migrate() application_id should throw if the value is invalid`() = runTest {
    checkAll(propTestConfig, Arb.int()) { applicationId ->
      assume(applicationId != 0 && applicationId != 0x7f1bc816)
      val mockLogger: Logger = mockk(relaxed = true)

      val exception =
        DataConnectSQLiteDatabaseOpener.open(null, mockLogger).use { db ->
          db.setApplicationId(mockLogger, applicationId)
          shouldThrow<InvalidApplicationIdException> {
            DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
          }
        }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "application_id"
        exception.message shouldContainWithNonAbuttingText applicationId.toString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase applicationId.to0xHexString()
        exception.message shouldContainWithNonAbuttingText 0x7f1bc816.toString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase 0x7f1bc816.to0xHexString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "unknown"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "aborting"
      }
    }
  }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 10,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33)
      )
  }
}
