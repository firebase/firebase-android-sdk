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

import android.database.sqlite.SQLiteDatabase
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabaseMigrator.InvalidApplicationIdException
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabaseMigrator.UnsupportedUserVersionException
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.execSQL
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.getApplicationId
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.getLastInsertRowId
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.rawQuery
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.setApplicationId
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.cartesianProduct
import com.google.firebase.dataconnect.testutil.property.arbitrary.TestValueGenerator
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.semanticVersion
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.util.ImmutableByteArray
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
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
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

  @get:Rule(order = Int.MIN_VALUE) val randomSeedTestRule = RandomSeedTestRule()
  private val testValueGenerator by lazy {
    TestValueGenerator(randomSeedTestRule, edgeCaseProbability = 0.2)
  }

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
  fun `migrate() sets application_id on a new database`() {
    val mockLogger: Logger = mockk(relaxed = true)
    val applicationId =
      DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger).use { db ->
        DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
        db.getApplicationId(mockLogger)
      }
    applicationId shouldBe 0x7f1bc816
  }

  @Test
  fun `migrate() leaves application_id alone if already correct`() {
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
  fun `migrate() throws if application_id is invalid`() = runTest {
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
  fun `migrate() sets user_version on a new database`() {
    val mockLogger: Logger = mockk(relaxed = true)
    val userVersion =
      DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger).use { db ->
        DataConnectCacheDatabaseMigrator.migrate(db, mockLogger)
        db.version
      }
    withClue("userVersion=$userVersion") {
      userVersion.decodeSemanticVersion() shouldBe SemanticVersion(1, 1, 0)
    }
  }

  @Test
  fun `migrate() from 1_0_0 to 1_1_0 preserves data`() {
    val mockLogger: Logger = mockk(relaxed = true)
    repeat(50) {
      val dbFile = File(temporaryFolder.newFolder(), "db.sqlite")
      val seedData = createVersion100Database(dbFile, testValueGenerator, mockLogger)
      val startVersion = SemanticVersion(1, 0, 0)

      val actualData =
        DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger).use { db ->
          check(db.version == 1_000_000) {
            "internal error y4asg7a3wv: db.version==${db.version} but expected " +
              startVersion.encodeToInt()
          }

          DataConnectCacheDatabaseMigrator.migrateToVersionForTesting(
            db,
            SemanticVersion(1, 1, 0),
            mockLogger,
          )
          db.version shouldBe 1_001_000

          db.loadVersion110Database(mockLogger)
        }

      val actualDataSorted = actualData.sorted()
      val expectedDataSorted = seedData.toVersion110Database().sorted()
      actualDataSorted shouldBe expectedDataSorted
    }
  }

  @Test
  fun `migrate() should leave user_version alone if it is between 1_1_0 and 2_0_0`() = runTest {
    val semanticVersionArb =
      Arb.dataConnect.semanticVersion(
        major = Arb.constant(1),
        minor = Arb.int(1..999),
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
  fun `migrate() should throw if user_version is between 1_0_0 and 1_1_0`() = runTest {
    val semanticVersionArb =
      Arb.dataConnect.semanticVersion(
        major = Arb.constant(1),
        minor = Arb.constant(0),
        patch = Arb.int(1..999),
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
        exception.message shouldContainWithNonAbuttingText "vdzgyhdche"
        exception.message shouldContainWithNonAbuttingText userVersion.encodeToInt().toString()
        exception.message shouldContainWithNonAbuttingText userVersion.toString()
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "unsupported user_version"
        exception.message shouldContainWithNonAbuttingTextIgnoringCase "between 1.0.0 and 1.1.0"
      }
    }
  }

  @Test
  fun `migrate() should throw if user_version has a different major version`() = runTest {
    val semanticVersionArb =
      Arb.dataConnect
        .semanticVersion(
          major = Arb.int(0..999).filterNot { it == 1 },
          minor = Arb.int(0..999),
          patch = Arb.int(0..999),
        )
        .filterNot { it.major == 0 && it.minor == 0 && it.patch == 0 }
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

private data class Version100Database(
  val sequenceNumbers: List<Long>,
  val users: List<User>,
  val entities: List<Entity>,
  val queries: List<Query>,
) {
  data class User(val id: Long, val authUid: String?, val debugInfo: String?) : Comparable<User> {
    override fun compareTo(other: User) =
      compareValuesBy(this, other, User::id, User::authUid, User::debugInfo)
  }

  data class Entity(
    val id: Long,
    val userId: Long,
    val entityId: String,
    val data: ImmutableByteArray,
    val flags: Long,
    val debugInfo: String?,
  )

  data class Query(
    val id: Long,
    val userId: Long,
    val queryId: ImmutableByteArray,
    val data: ImmutableByteArray,
    val flags: Long,
    val expiry: ImmutableByteArray,
    val debugInfo: String?,
  )
}

private data class Version110Database(
  val sequenceNumbers: List<Long>,
  val users: List<Version100Database.User>,
  val entities: List<Entity>,
  val queries: List<Query>,
) {

  data class Entity(
    val id: Long,
    val userId: Long,
    val entityId: String,
    val data: ImmutableByteArray,
    val flags: Long,
    val debugInfo: String?,
    val lastUpdateSequenceNumber: Long?,
  ) : Comparable<Entity> {
    override fun compareTo(other: Entity) =
      compareValuesBy(
        this,
        other,
        Entity::id,
        Entity::userId,
        Entity::entityId,
        Entity::data,
        Entity::flags,
        Entity::debugInfo,
        Entity::lastUpdateSequenceNumber,
      )
  }

  data class Query(
    val id: Long,
    val userId: Long,
    val queryId: ImmutableByteArray,
    val data: ImmutableByteArray,
    val flags: Long,
    val expiry: ImmutableByteArray,
    val debugInfo: String?,
    val lastUpdateSequenceNumber: Long?,
  ) : Comparable<Query> {
    override fun compareTo(other: Query) =
      compareValuesBy(
        this,
        other,
        Query::id,
        Query::userId,
        Query::queryId,
        Query::data,
        Query::flags,
        Query::expiry,
        Query::debugInfo,
        Query::lastUpdateSequenceNumber,
      )
  }
}

private fun Version110Database.sorted(): Version110Database =
  Version110Database(
    sequenceNumbers = sequenceNumbers.sorted(),
    users = users.sorted(),
    entities = entities.sorted(),
    queries = queries.sorted(),
  )

private fun Version100Database.Entity.toVersion110DatabaseEntity(
  lastUpdateSequenceNumber: Long?
): Version110Database.Entity =
  Version110Database.Entity(
    id = id,
    userId = userId,
    entityId = entityId,
    data = data,
    flags = flags,
    debugInfo = debugInfo,
    lastUpdateSequenceNumber = lastUpdateSequenceNumber,
  )

private fun Version100Database.Query.toVersion110DatabaseQuery(
  lastUpdateSequenceNumber: Long?
): Version110Database.Query =
  Version110Database.Query(
    id = id,
    userId = userId,
    queryId = queryId,
    data = data,
    flags = flags,
    expiry = expiry,
    debugInfo = debugInfo,
    lastUpdateSequenceNumber = lastUpdateSequenceNumber,
  )

private fun Version100Database.toVersion110Database(): Version110Database =
  Version110Database(
    sequenceNumbers = sequenceNumbers,
    users = users,
    entities = entities.map { it.toVersion110DatabaseEntity(lastUpdateSequenceNumber = null) },
    queries = queries.map { it.toVersion110DatabaseQuery(lastUpdateSequenceNumber = null) },
  )

private fun SQLiteDatabase.loadVersion110Database(logger: Logger): Version110Database {
  val sequenceNumbers: List<Long> = buildList {
    rawQuery(logger, "SELECT id FROM sequence_number") { cursor ->
      while (cursor.moveToNext()) {
        add(cursor.getLong(0))
      }
    }
  }

  val users: List<Version100Database.User> = buildList {
    rawQuery(logger, "SELECT id, auth_uid, debug_info FROM users") { cursor ->
      while (cursor.moveToNext()) {
        val id = cursor.getLong(0)
        val authUid = if (cursor.isNull(1)) null else cursor.getString(1)
        val debugInfo = if (cursor.isNull(2)) null else cursor.getString(2)
        add(Version100Database.User(id = id, authUid = authUid, debugInfo = debugInfo))
      }
    }
  }

  val entities: List<Version110Database.Entity> = buildList {
    rawQuery(
      logger,
      """
        SELECT
          id, user_id, entity_id, data, flags, debug_info, last_update_sequence_number
        FROM
          entities
      """
    ) { cursor ->
      while (cursor.moveToNext()) {
        val id = cursor.getLong(0)
        val userId = cursor.getLong(1)
        val entityId = cursor.getString(2)
        val data = ImmutableByteArray.adopt(cursor.getBlob(3))
        val flags = cursor.getLong(4)
        val debugInfo = if (cursor.isNull(5)) null else cursor.getString(5)
        val lastUpdateSequenceNumber = if (cursor.isNull(6)) null else cursor.getLong(6)
        add(
          Version110Database.Entity(
            id = id,
            userId = userId,
            entityId = entityId,
            data = data,
            flags = flags,
            debugInfo = debugInfo,
            lastUpdateSequenceNumber = lastUpdateSequenceNumber,
          )
        )
      }
    }
  }

  val queries: List<Version110Database.Query> = buildList {
    rawQuery(
      logger,
      """
        SELECT
          id, user_id, query_id, data, flags, expiry, debug_info, last_update_sequence_number
        FROM
          queries
      """
    ) { cursor ->
      while (cursor.moveToNext()) {
        val id = cursor.getLong(0)
        val userId = cursor.getLong(1)
        val queryId = ImmutableByteArray.adopt(cursor.getBlob(2))
        val data = ImmutableByteArray.adopt(cursor.getBlob(3))
        val flags = cursor.getLong(4)
        val expiry = ImmutableByteArray.adopt(cursor.getBlob(5))
        val debugInfo = if (cursor.isNull(6)) null else cursor.getString(6)
        val lastUpdateSequenceNumber = if (cursor.isNull(7)) null else cursor.getLong(7)
        add(
          Version110Database.Query(
            id = id,
            userId = userId,
            queryId = queryId,
            data = data,
            flags = flags,
            expiry = expiry,
            debugInfo = debugInfo,
            lastUpdateSequenceNumber = lastUpdateSequenceNumber,
          )
        )
      }
    }
  }

  return Version110Database(
    sequenceNumbers = sequenceNumbers,
    users = users,
    entities = entities,
    queries = queries,
  )
}

private fun createVersion100Database(
  dbFile: File,
  testValueGenerator: TestValueGenerator,
  logger: Logger,
): Version100Database {
  val version100 = SemanticVersion(1, 0, 0)
  DataConnectSQLiteDatabaseOpener.open(dbFile, logger).use { db ->
    DataConnectCacheDatabaseMigrator.migrateToVersionForTesting(db, version100, logger)
    check(db.version == version100.encodeToInt()) {
      "internal error svadycq5bd: db.version==${db.version} " +
        "but expected ${version100.encodeToInt()}"
    }
    db.beginTransaction()
    try {
      val populatedData = db.populateVersion100Data(testValueGenerator, logger)
      db.setTransactionSuccessful()
      return populatedData
    } finally {
      db.endTransaction()
    }
  }
}

private fun SQLiteDatabase.populateVersion100Data(
  testValueGenerator: TestValueGenerator,
  logger: Logger,
): Version100Database =
  testValueGenerator.run {
    val stringArb: Arb<String> = Arb.dataConnect.alphabeticString(0..4)
    val nullableStringArb: Arb<String?> = stringArb.orNull(nullProbability = 0.2)
    val flagsArb = Arb.long()
    val immutableByteArrayArb: Arb<ImmutableByteArray> =
      Arb.byteArray(Arb.int(0..10), Arb.byte()).map(ImmutableByteArray::adopt)
    val entityIds = stringArb.generateDistinctValues(1..10)
    val queryIds = immutableByteArrayArb.generateDistinctValues(1..10)

    val sequenceNumber = Arb.long(min = 0).generateValue()
    execSQL(logger, "INSERT INTO sequence_number (id) VALUES ($sequenceNumber)")

    val users = run {
      val authUids = nullableStringArb.generateDistinctValues(1..5)
      val debugInfos = nullableStringArb.generateValues(authUids.size)
      authUids.zip(debugInfos) { authUid, debugInfo ->
        execSQL(
          logger,
          "INSERT INTO users (auth_uid, debug_info) VALUES (?, ?)",
          arrayOf(authUid, debugInfo)
        )
        val id = getLastInsertRowId(logger)
        Version100Database.User(id, authUid = authUid, debugInfo = debugInfo)
      }
    }

    val userEntityIdPairs = cartesianProduct(users, entityIds).randomDistinctValues()
    val entities =
      userEntityIdPairs.map { (user, entityId) ->
        val data = immutableByteArrayArb.generateValue()
        val debugInfo = nullableStringArb.generateValue()
        val flags = flagsArb.generateValue()
        execSQL(
          logger,
          "INSERT INTO entities (user_id, entity_id, data, flags, debug_info) VALUES (?, ?, ?, ?, ?)",
          arrayOf(user.id, entityId, data.peek(), flags, debugInfo)
        )
        val id = getLastInsertRowId(logger)
        Version100Database.Entity(
          id = id,
          userId = user.id,
          entityId = entityId,
          data = data,
          flags = flags,
          debugInfo = debugInfo,
        )
      }

    val userQueryIdPairs = cartesianProduct(users, queryIds).randomDistinctValues()
    val queries =
      userQueryIdPairs.map { (user, queryId) ->
        val data = immutableByteArrayArb.generateValue()
        val debugInfo = nullableStringArb.generateValue()
        val flags = flagsArb.generateValue()
        val expiry = immutableByteArrayArb.generateValue()

        execSQL(
          logger,
          "INSERT INTO queries (user_id, query_id, data, flags, expiry, debug_info) VALUES (?, ?, ?, ?, ?, ?)",
          arrayOf(user.id, queryId.peek(), data.peek(), flags, expiry.peek(), debugInfo)
        )
        val id = getLastInsertRowId(logger)
        Version100Database.Query(
          id = id,
          userId = user.id,
          queryId = queryId,
          data = data,
          flags = flags,
          expiry = expiry,
          debugInfo = debugInfo,
        )
      }

    Version100Database(
      sequenceNumbers = listOf(sequenceNumber),
      users = users,
      entities = entities,
      queries = queries,
    )
  }
