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

import android.database.sqlite.SQLiteDatabase
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.sqlite2.DataConnectCacheDatabaseMigrator.InvalidApplicationIdException
import com.google.firebase.dataconnect.sqlite2.DataConnectCacheDatabaseMigrator.InvalidSchemaVersionException
import com.google.firebase.dataconnect.sqlite2.DataConnectCacheDatabaseMigrator.InvalidUserVersionException
import com.google.firebase.dataconnect.sqlite2.SQLiteDatabaseExts.getApplicationId
import com.google.firebase.dataconnect.sqlite2.SQLiteDatabaseExts.setApplicationId
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.util.StringUtil.to0xHexString
import io.github.z4kn4fein.semver.toVersion
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.nonNegativeInt
import io.kotest.property.arbitrary.string
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

  @Test
  fun `migrate() user_version should set the value in a new database`() {
    val mockLogger: Logger = mockk(relaxed = true)
    val userVersion =
      DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger).use { db ->
        DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
        db.version
      }
    userVersion shouldBe 1
  }

  @Test
  fun `migrate() user_version should leave value alone if already correct`() {
    val mockLogger: Logger = mockk(relaxed = true)
    val userVersion =
      DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger).use { db ->
        DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
        DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
        db.version
      }
    userVersion shouldBe 1
  }

  @Test
  fun `migrate() user_version should throw if the value is invalid`() = runTest {
    checkAll(propTestConfig, Arb.int()) { userVersion ->
      assume(userVersion != 0 && userVersion != 1)
      val mockLogger: Logger = mockk(relaxed = true)

      val exception =
        DataConnectSQLiteDatabaseOpener.open(null, mockLogger).use { db ->
          db.version = userVersion
          shouldThrow<InvalidUserVersionException> {
            DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
          }
        }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "user_version"
        exception.message shouldContainWithNonAbuttingText "0"
        exception.message shouldContainWithNonAbuttingText "1"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase userVersion.to0xHexString()
        exception.message shouldContainWithNonAbuttingText userVersion.toString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "unknown"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "aborting"
      }
    }
  }

  @Test
  fun `migrate() schema_version should set the value in a new database`() {
    val mockLogger: Logger = mockk(relaxed = true)

    val schemaVersion =
      DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger).use { db ->
        DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
        getSchemaVersion(db)
      }

    val parsedSchemaVersion =
      withClue("schemaVersion") { schemaVersion.shouldNotBeNull().toVersion() }
    withClue("parsedSchemaVersion.major") { parsedSchemaVersion.major shouldBe 1 }
  }

  @Test
  fun `migrate() schema_version should leave value alone if already set`() {
    val mockLogger: Logger = mockk(relaxed = true)
    DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger).use { db ->
      DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
      val schemaVersion1 = withClue("getSchemaVersion1") { getSchemaVersion(db).shouldNotBeNull() }
      DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
      val schemaVersion2 = withClue("getSchemaVersion2") { getSchemaVersion(db).shouldNotBeNull() }
      schemaVersion1 shouldBe schemaVersion2
    }
  }

  @Test
  fun `migrate() schema_version should throw if the schema version is invalid`() = runTest {
    val invalidMajorVersionArb = Arb.int(2..Int.MAX_VALUE).map { "$it.2.3" }
    val invalidSemanticVersionArb =
      Arb.string(0..10, Codepoint.alphanumeric()).filterNot { it.startsWith("1.") }
    val invalidSchemaVersionArb = Arb.choice(invalidMajorVersionArb, invalidSemanticVersionArb)
    checkAll(propTestConfig, invalidSchemaVersionArb) { schemaVersion ->
      val mockLogger: Logger = mockk(relaxed = true)

      val exception =
        DataConnectSQLiteDatabaseOpener.open(null, mockLogger).use { db ->
          DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
          setSchemaVersion(db, schemaVersion)

          shouldThrow<InvalidSchemaVersionException> {
            DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
          }
        }

      assertSoftly {
        exception.message shouldContainWithNonAbuttingText "schema_version"
        exception.message shouldContainWithNonAbuttingText schemaVersion
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "unknown"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "aborting"
      }
    }
  }

  @Test
  fun `migrate() schema_version should throw if the schema version is not set`() {
    val mockLogger: Logger = mockk(relaxed = true)

    val exception =
      DataConnectSQLiteDatabaseOpener.open(null, mockLogger).use { db ->
        DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
        unsetSchemaVersion(db)

        shouldThrow<InvalidSchemaVersionException> {
          DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
        }
      }

    assertSoftly {
      exception.message shouldContainWithNonAbuttingText "schema_version"
      exception.message shouldContainWithNonAbuttingTextIgnoringCase "null"
      exception.message shouldContainWithNonAbuttingTextIgnoringCase "aborting"
    }
  }

  @Test
  fun `migrate() schema_version should accept higher minor and-or patch versions`() = runTest {
    val mockLogger: Logger = mockk(relaxed = true)
    DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger).use { db ->
      DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
      val originalSchemaVersion =
        withClue("getSchemaVersion") { getSchemaVersion(db).shouldNotBeNull() }
      checkAll(propTestConfig, higherMinorAndOrPatchVersionArb(originalSchemaVersion)) {
        newSchemaVersion ->
        setSchemaVersion(db, newSchemaVersion)

        DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)

        getSchemaVersion(db) shouldBe newSchemaVersion
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

    fun getSchemaVersion(db: SQLiteDatabase): String? {
      db.rawQuery("SELECT text FROM metadata WHERE name = 'schema_version'", null).use { cursor ->
        return if (cursor.moveToNext()) cursor.getString(0) else null
      }
    }

    fun setSchemaVersion(db: SQLiteDatabase, value: String) {
      db.execSQL(
        "INSERT OR REPLACE INTO metadata (name, text) VALUES ('schema_version', ?)",
        arrayOf(value)
      )
    }

    fun unsetSchemaVersion(db: SQLiteDatabase) {
      db.execSQL("DELETE FROM metadata WHERE name = 'schema_version'")
    }

    /**
     * Creates and returns an [Arb] that generates whose values are semantic versions that have the
     * same major version as the given version, but a higher minor and/or patch version.
     */
    fun higherMinorAndOrPatchVersionArb(version: String): Arb<String> {
      val parsedVersion = version.toVersion(strict = false)

      val higherMinorVersionArb =
        Arb.bind(Arb.int(parsedVersion.minor + 1..Int.MAX_VALUE), Arb.nonNegativeInt()) {
          minor,
          patch ->
          parsedVersion.copy(minor = minor, patch = patch).toString()
        }

      val higherPatchVersionArb =
        Arb.int(parsedVersion.patch + 1..Int.MAX_VALUE).map { patch ->
          parsedVersion.copy(patch = patch).toString()
        }

      return Arb.choice(higherMinorVersionArb, higherPatchVersionArb)
    }
  }
}
