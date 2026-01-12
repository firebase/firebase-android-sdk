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

import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabaseMigrator.InvalidApplicationIdException
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabaseMigrator.UnsupportedUserVersionException
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.getApplicationId
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.setApplicationId
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.semanticVersion
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.util.SemanticVersion
import com.google.firebase.dataconnect.util.StringUtil.to0xHexString
import com.google.firebase.dataconnect.util.decodeSemanticVersion
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
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
  fun `migrate() should set application_id on a new database`() {
    val mockLogger: Logger = mockk(relaxed = true)
    val applicationId =
      DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger).use { db ->
        DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
        db.getApplicationId(mockLogger)
      }
    applicationId shouldBe 0x7f1bc816
  }

  @Test
  fun `migrate() leave application_id alone if already correct`() {
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
  fun `migrate() should throw if application_id is invalid`() = runTest {
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

  @Test
  fun `migrate() should set user_version on a new database`() {
    val mockLogger: Logger = mockk(relaxed = true)
    val userVersion =
      DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger).use { db ->
        DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
        db.version
      }
    withClue("userVersion=$userVersion") {
      userVersion.decodeSemanticVersion() shouldBe SemanticVersion(1, 0, 0)
    }
  }

  @Test
  fun `migrate() should leave user_version if major version is 1`() = runTest {
    val semanticVersionArb =
      Arb.dataConnect.semanticVersion(
        major = Arb.constant(1),
        minor = Arb.int(0..999),
        patch = Arb.int(0..999),
      )
    checkAll(propTestConfig, semanticVersionArb) { userVersion ->
      val mockLogger: Logger = mockk(relaxed = true)
      val userVersionAfter =
        DataConnectSQLiteDatabaseOpener.open(null, mockLogger).use { db ->
          db.version = userVersion.encodeToInt()
          DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
          db.version
        }
      userVersionAfter shouldBe userVersion.encodeToInt()
    }
  }

  @Test
  fun `migrate() should throw if user_version has a different major version`() = runTest {
    val semanticVersionArb =
      Arb.dataConnect.semanticVersion(
        major = Arb.int(0..999).filterNot { it == 1 },
        minor = Arb.int(0..999),
        patch = Arb.int(0..999),
      )
    checkAll(propTestConfig, semanticVersionArb) { userVersion ->
      val mockLogger: Logger = mockk(relaxed = true)
      val exception =
        DataConnectSQLiteDatabaseOpener.open(null, mockLogger).use { db ->
          db.version = userVersion.encodeToInt()
          shouldThrow<UnsupportedUserVersionException> {
            DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
          }
        }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "szetvza49k"
        exception.message shouldContainWithNonAbuttingText "user_version"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase userVersion.major.toString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase
          userVersion.encodeToInt().toString()
        exception.message shouldContainWithNonAbuttingText userVersion.toString()
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
