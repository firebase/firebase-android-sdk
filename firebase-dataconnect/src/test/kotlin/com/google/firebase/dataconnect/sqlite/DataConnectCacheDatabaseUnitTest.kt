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

import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase.Companion.get
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase.Companion.set
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase.InvalidDatabaseException
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
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

  @Test
  fun `runReadWriteMetadataTransaction ReadWriteMetadata set String values`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), Arb.dataConnect.string()) { key, value ->
      withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
        db.runReadWriteMetadataTransaction { metadata -> metadata.set(key, value) }
      }
      val actualValue =
        withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
          db.runReadOnlyMetadataTransaction { metadata -> metadata.getString(key) }
        }
      actualValue shouldBe value
    }
  }

  @Test
  fun `runReadWriteMetadataTransaction ReadWriteMetadata set CharSequence values`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), Arb.dataConnect.string()) { key, value ->
      withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
        db.runReadWriteMetadataTransaction { metadata -> metadata.set(key, StringBuilder(value)) }
      }
      val actualValue =
        withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
          db.runReadOnlyMetadataTransaction { metadata -> metadata.getString(key) }
        }
      actualValue shouldBe value
    }
  }

  @Test
  fun `runReadWriteMetadataTransaction ReadWriteMetadata set Int values`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), Arb.int()) { key, value ->
      withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
        db.runReadWriteMetadataTransaction { metadata -> metadata.set(key, value) }
      }
      val actualValue =
        withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
          db.runReadOnlyMetadataTransaction { metadata -> metadata.getInt(key) }
        }
      actualValue shouldBe value
    }
  }

  @Test
  fun `runReadWriteMetadataTransaction ReadWriteMetadata set Blob metadata values`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), blobArb) { key, value ->
      withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
        db.runReadWriteMetadataTransaction { metadata -> metadata.set(key, value) }
      }
      val actualValue =
        withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
          db.runReadOnlyMetadataTransaction { metadata -> metadata.getBlob(key) }
        }
      actualValue shouldBe value
    }
  }

  @Test
  fun `runReadWriteMetadataTransaction ReadWriteMetadata set clears other types`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), Arb.list(metadataValueArb, 2..20)) {
      key,
      values ->
      withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
        values.forEach { value ->
          db.runReadWriteMetadataTransaction { metadata ->
            when (value) {
              is String -> metadata.set(key, value)
              is Int -> metadata.set(key, value)
              is ByteArray -> metadata.set(key, value)
              else -> throw IllegalArgumentException("unsupported value type: $value")
            }
          }
        }
      }
      val (actualStringValue, actualIntValue, actualBlobValue) =
        withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
          db.runReadOnlyMetadataTransaction { metadata ->
            Triple(
              metadata.getString(key),
              metadata.getInt(key),
              metadata.getBlob(key),
            )
          }
        }

      val lastValue = values.last()
      assertSoftly {
        withClue("string") { actualStringValue shouldBe (lastValue as? String) }
        withClue("int") { actualIntValue shouldBe (lastValue as? Int) }
        withClue("blob") { actualBlobValue shouldBe (lastValue as? ByteArray) }
      }
    }
  }

  @Test
  fun `runReadWriteMetadataTransaction ReadWriteMetadata set multiple keys`() = runTest {
    val arb =
      Arb.int(10..100).flatMap { count ->
        val keyArb = Arb.dataConnect.string()
        Arb.map(keyArb, metadataValueArb, minSize = 10, maxSize = 20, slippage = 100)
      }
    checkAll(propTestConfig, arb) { valueByKey ->
      withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
        valueByKey.entries.forEach { (key, value) ->
          db.runReadWriteMetadataTransaction { metadata ->
            when (value) {
              is String -> metadata.set(key, value)
              is Int -> metadata.set(key, value)
              is ByteArray -> metadata.set(key, value)
              else -> throw IllegalArgumentException("unsupported value type: $value")
            }
          }
        }
      }
      withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
        db.runReadOnlyMetadataTransaction { metadata ->
          valueByKey.entries.forEachIndexed { index, (key, expectedValue) ->
            val actualValue =
              when (expectedValue) {
                is String -> metadata.getString(key)
                is Int -> metadata.getInt(key)
                is ByteArray -> metadata.getBlob(key)
                else -> throw IllegalArgumentException("unsupported value type: $expectedValue")
              }
            withClue("index=$index") { actualValue shouldBe expectedValue }
          }
        }
      }
    }
  }

  @Test
  fun `runReadWriteMetadataTransaction ReadWriteMetadata set should throw after block`() = runTest {
    val key = Arb.dataConnect.string().next(rs)
    val stringValue = Arb.dataConnect.string().next(rs)
    val intValue = Arb.int().next(rs)
    val blobValue = blobArb.next(rs)

    withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
      val metadata = db.runReadWriteMetadataTransaction { metadata -> metadata }
      assertSoftly {
        withClue("string") { shouldThrow<IllegalStateException> { metadata.set(key, stringValue) } }
        withClue("int") { shouldThrow<IllegalStateException> { metadata.set(key, intValue) } }
        withClue("blob") { shouldThrow<IllegalStateException> { metadata.set(key, blobValue) } }
      }
    }
  }

  @Test
  fun `runReadWriteMetadataTransaction ReadWriteMetadata set extension String`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), Arb.dataConnect.string()) { key, value ->
      withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
        db.runReadWriteMetadataTransaction { metadata ->
          metadata.set(key, value)
          metadata.getString(key) shouldBe value
        }
      }
    }
  }

  @Test
  fun `runReadWriteMetadataTransaction ReadWriteMetadata set extension CharSequence`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), Arb.dataConnect.string()) { key, value ->
      withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
        db.runReadWriteMetadataTransaction { metadata ->
          metadata.set(key, StringBuilder(value))
          metadata.getString(key) shouldBe value
        }
      }
    }
  }

  @Test
  fun `runReadWriteMetadataTransaction ReadWriteMetadata set extension Int`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), Arb.int()) { key, value ->
      withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
        db.runReadWriteMetadataTransaction { metadata ->
          metadata.set(key, value)
          metadata.getInt(key) shouldBe value
        }
      }
    }
  }

  @Test
  fun `runReadWriteMetadataTransaction ReadWriteMetadata set extension Blob`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), blobArb) { key, value ->
      withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
        db.runReadWriteMetadataTransaction { metadata ->
          metadata.set(key, value)
          metadata.getBlob(key) shouldBe value
        }
      }
    }
  }

  @Test
  fun `runReadWriteMetadataTransaction ReadWriteMetadata set extension unsupported type throws`() =
    runTest {
      val key = Arb.dataConnect.string().next(rs)
      withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
        db.runReadWriteMetadataTransaction { metadata ->
          shouldThrow<IllegalArgumentException> { metadata.set(key, Unit) }
        }
      }
    }

  @Test
  fun `runReadOnlyMetadataTransaction ReadOnlyMetadata getXXX returns null if not set`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string()) { key ->
      withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
        db.runReadOnlyMetadataTransaction { metadata ->
          assertSoftly {
            withClue("getString") { metadata.getString(key).shouldBeNull() }
            withClue("getInt") { metadata.getInt(key).shouldBeNull() }
            withClue("getBlob") { metadata.getBlob(key).shouldBeNull() }
          }
        }
      }
    }
  }

  @Test
  fun `runReadOnlyMetadataTransaction ReadOnlyMetadata getXXX returns null if a different type is set`() =
    runTest {
      checkAll(propTestConfig, Arb.dataConnect.string(), metadataValueArb, metadataValueArb) {
        key,
        value1,
        value2 ->
        withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
          db.runReadWriteMetadataTransaction { metadata ->
            metadata.set(key, value1)
            metadata.set(key, value2)
          }
          db.runReadOnlyMetadataTransaction { metadata ->
            assertSoftly {
              withClue("getString") { metadata.getString(key) shouldBe (value2 as? String) }
              withClue("getInt") { metadata.getInt(key) shouldBe (value2 as? Int) }
              withClue("getBlob") { metadata.getBlob(key) shouldBe (value2 as? ByteArray) }
            }
          }
        }
      }
    }

  @Test
  fun `runReadOnlyMetadataTransaction ReadOnlyMetadata get should throw after block`() = runTest {
    val key = Arb.dataConnect.string().next(rs)

    withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
      val metadata = db.runReadOnlyMetadataTransaction { metadata -> metadata }
      assertSoftly {
        withClue("getString") { shouldThrow<IllegalStateException> { metadata.getString(key) } }
        withClue("getInt") { shouldThrow<IllegalStateException> { metadata.getInt(key) } }
        withClue("getBlob") { shouldThrow<IllegalStateException> { metadata.getBlob(key) } }
      }
    }
  }

  @Test
  fun `runReadOnlyMetadataTransaction ReadOnlyMetadata get extension String`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), Arb.dataConnect.string()) { key, value ->
      withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
        db.runReadWriteMetadataTransaction { metadata ->
          metadata.set(key, value)
          metadata.get<String>(key) shouldBe value
        }
      }
    }
  }

  @Test
  fun `runReadOnlyMetadataTransaction ReadOnlyMetadata get extension Int`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), Arb.int()) { key, value ->
      withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
        db.runReadWriteMetadataTransaction { metadata ->
          metadata.set(key, value)
          metadata.get<Int>(key) shouldBe value
        }
      }
    }
  }

  @Test
  fun `runReadOnlyMetadataTransaction ReadOnlyMetadata get extension Number`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), Arb.int()) { key, value ->
      withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
        db.runReadWriteMetadataTransaction { metadata ->
          metadata.set(key, value)
          metadata.get<Number>(key) shouldBe value
        }
      }
    }
  }

  @Test
  fun `runReadOnlyMetadataTransaction ReadOnlyMetadata get extension ByteArray`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.string(), blobArb) { key, value ->
      withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
        db.runReadWriteMetadataTransaction { metadata ->
          metadata.set(key, value)
          metadata.get<ByteArray>(key) shouldBe value
        }
      }
    }
  }

  @Test
  fun `runReadOnlyMetadataTransaction ReadOnlyMetadata get extension throws for unsupported types`() =
    runTest {
      val key = Arb.dataConnect.string().next(rs)
      withDataConnectCacheDatabase(dbFile) { db: DataConnectCacheDatabase ->
        db.runReadWriteMetadataTransaction { metadata ->
          shouldThrow<IllegalArgumentException> { metadata.get<Any>(key) }
        }
      }
    }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig = PropTestConfig(iterations = 10, shrinkingMode = ShrinkingMode.Off)

    val blobArb = Arb.byteArray(Arb.int(0..20), Arb.byte())

    val metadataValueArb = Arb.choice(Arb.dataConnect.string(), Arb.int(), blobArb)

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
