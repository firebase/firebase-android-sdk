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

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.NO_LOCALIZED_COLLATORS
import android.database.sqlite.SQLiteDatabase.OPEN_READONLY
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.sqlite.KSQLiteDatabase.ReadOnlyTransaction.GetDatabasesResult
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.SuspendingCountDownLatch
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import io.kotest.property.arbs.wine.Vineyard
import io.kotest.property.arbs.wine.vineyards
import io.mockk.mockk
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataConnectSqliteDatabaseUnitTest {

  @get:Rule val temporaryFolder = TemporaryFolder()
  @get:Rule val randomSeedTestRule = RandomSeedTestRule()

  private val rs: RandomSource by randomSeedTestRule.rs

  @Test
  fun `constructor file argument should be used as the database file`() = runTest {
    val dbFile = File(temporaryFolder.newFolder(), "fqsywf8bdd.sqlite")
    val userVersion = Arb.int().next(rs)
    val getDatabasesResult = AtomicReference<List<GetDatabasesResult>>(null)
    val db =
      TestDataConnectSqliteDatabase(
        file = dbFile,
        onOpen = { kdb ->
          kdb.runReadWriteTransaction {
            getDatabasesResult.set(it.getDatabases())
            it.setUserVersion(userVersion)
          }
        }
      )

    try {
      db.ensureOpen()
    } finally {
      withContext(NonCancellable) { db.close() }
    }

    withClue("getDatabasesResult") {
      getDatabasesResult.get().first().filePath shouldBe dbFile.absolutePath
    }
    withClue("userVersion") { db.file!!.getSqliteUserVersion() shouldBe userVersion }
  }

  @Test
  fun `constructor file argument null should use an in-memory database`() = runTest {
    val getDatabasesResult = AtomicReference<List<GetDatabasesResult>>(null)
    val db =
      TestDataConnectSqliteDatabase(
        file = null,
        onOpen = { kdb -> kdb.runReadOnlyTransaction { getDatabasesResult.set(it.getDatabases()) } }
      )

    try {
      db.ensureOpen()
    } finally {
      withContext(NonCancellable) { db.close() }
    }

    withClue("getDatabasesResult") { getDatabasesResult.get().first().filePath shouldBe "" }
  }

  @Test
  fun `constructor CoroutineDispatcher argument is used`() = runTest {
    val executor = Executors.newSingleThreadExecutor()
    try {
      val dispatcher = executor.asCoroutineDispatcher()
      val dispatcherThread = withContext(dispatcher) { Thread.currentThread() }
      val onOpenThread = AtomicReference<Thread>(null)
      val withDbThread = AtomicReference<Thread>(null)
      val db =
        TestDataConnectSqliteDatabase(
          ioDispatcher = dispatcher,
          onOpen = { onOpenThread.set(Thread.currentThread()) },
          withDb = { withDbThread.set(Thread.currentThread()) }
        )

      try {
        db.callWithDb()
      } finally {
        withContext(NonCancellable) { db.close() }
      }

      assertSoftly {
        withClue("onOpenThread") { onOpenThread.get() shouldBeSameInstanceAs dispatcherThread }
        withClue("withDbThread") { withDbThread.get() shouldBeSameInstanceAs dispatcherThread }
      }
    } finally {
      executor.shutdown()
    }
  }

  @Test
  fun `close can be called on a new instance without throwing an exception`() = runTest {
    val db = TestDataConnectSqliteDatabase()

    db.close()
  }

  @Test
  fun `close can be called multiple times on a new instance without throwing an exception`() =
    runTest {
      val db = TestDataConnectSqliteDatabase()

      db.close()
      db.close()
      db.close()
      db.close()
      db.close()
    }

  @Test
  fun `close on a new instance causes withDb to throw and never call onOpen`() = runTest {
    val onOpenInvocationCount = AtomicInteger(0)
    val db = TestDataConnectSqliteDatabase(onOpen = { onOpenInvocationCount.incrementAndGet() })
    db.close()

    val exception = shouldThrow<IllegalStateException> { db.callWithDb() }

    assertSoftly {
      withClue("exception.message") {
        exception.message.shouldContainWithNonAbuttingTextIgnoringCase("closed")
      }
      withClue("onOpenInvocationCount") { onOpenInvocationCount.get() shouldBe 0 }
    }
  }

  @Test
  fun `close on an opened instance causes withDb to throw`() = runTest {
    val onOpenInvocationCount = AtomicInteger(0)
    val db = TestDataConnectSqliteDatabase(onOpen = { onOpenInvocationCount.incrementAndGet() })
    db.ensureOpen()
    db.close()

    val exception = shouldThrow<IllegalStateException> { db.callWithDb() }

    assertSoftly {
      withClue("exception.message") {
        exception.message.shouldContainWithNonAbuttingTextIgnoringCase("closed")
      }
      withClue("onOpenInvocationCount") { onOpenInvocationCount.get() shouldBe 1 }
    }
  }

  @Test
  fun `close causes an in-progress onOpen to be cancelled`() = runTest {
    val latch1 = SuspendingCountDownLatch(2)
    val latch2 = SuspendingCountDownLatch(2)
    val onOpenResultFlow = MutableStateFlow<Result<Unit>?>(null)
    val db =
      TestDataConnectSqliteDatabase(
        onOpen = {
          onOpenResultFlow.value = runCatching {
            latch1.countDown().await()
            latch2.countDown().await()
            coroutineContext.ensureActive() // should throw
          }
        }
      )
    async { db.ensureOpen() }

    latch1.countDown().await()
    db.close()
    latch2.countDown()

    val onOpenResult = onOpenResultFlow.filterNotNull().first()
    val exception = onOpenResult.exceptionOrNull().shouldNotBeNull()
    exception::class shouldBe CancellationException::class
  }

  @Test
  fun `close causes an in-progress withDb to be cancelled`() = runTest {
    val latch1 = SuspendingCountDownLatch(2)
    val latch2 = SuspendingCountDownLatch(2)
    val withDbResultFlow = MutableStateFlow<Result<Unit>?>(null)
    val db =
      TestDataConnectSqliteDatabase(
        withDb = {
          withDbResultFlow.value = runCatching {
            latch1.countDown().await()
            latch2.countDown().await()
            coroutineContext.ensureActive() // should throw
          }
        }
      )
    async { db.callWithDb() }

    latch1.countDown().await()
    db.close()
    latch2.countDown()

    val withDbResult = withDbResultFlow.filterNotNull().first()
    val exception = withDbResult.exceptionOrNull().shouldNotBeNull()
    exception::class shouldBe CancellationException::class
  }

  @Test
  fun `close suspends until onOpen completes`() = runTest {
    val events = CopyOnWriteArrayList<String>()
    val latch = SuspendingCountDownLatch(2)
    val db =
      TestDataConnectSqliteDatabase(
        onOpen = {
          withContext(NonCancellable) {
            events.add("onOpen1")
            latch.countDown().await()
            events.add("onOpen2")
            delay(200.milliseconds)
            events.add("onOpen3")
          }
        }
      )
    async { db.ensureOpen() }

    latch.countDown().await()
    db.close()
    events.add("closeReturned")

    events.shouldContainExactly("onOpen1", "onOpen2", "onOpen3", "closeReturned")
  }

  @Test
  fun `close concurrent calls all suspend until onOpen completes`() = runTest {
    val events = CopyOnWriteArrayList<String>()
    val latch1 = SuspendingCountDownLatch(2)
    val latch2 = SuspendingCountDownLatch(2)
    val db =
      TestDataConnectSqliteDatabase(
        onOpen = {
          withContext(NonCancellable) {
            latch1.countDown().await()
            latch2.countDown().await()
            delay(200.milliseconds)
            events.add("opened")
          }
        }
      )
    async { db.ensureOpen() }

    latch1.countDown().await()
    val closeJobLatch = SuspendingCountDownLatch(6)
    val jobs =
      List(closeJobLatch.count - 1) {
        launch(Dispatchers.IO) {
          closeJobLatch.countDown()
          db.close()
          events.add("closed")
        }
      }

    closeJobLatch.countDown().await()
    latch2.countDown().await()
    jobs.forEach { it.join() }
    events.shouldContainExactly("opened", "closed", "closed", "closed", "closed", "closed")
  }

  @Test
  fun `onOpen KSQLiteDatabase specified is closed upon return`() = runTest {
    val kdbFlow = MutableStateFlow<KSQLiteDatabase?>(null)
    val db = TestDataConnectSqliteDatabase(onOpen = { kdbFlow.value = it })

    val exception =
      try {
        db.ensureOpen()
        val kdb = kdbFlow.filterNotNull().first()
        shouldThrow<java.lang.IllegalStateException> { kdb.runReadOnlyTransaction {} }
      } finally {
        withContext(NonCancellable) { db.close() }
      }

    exception.message shouldContainWithNonAbuttingTextIgnoringCase "closed"
  }

  @Test
  fun `onOpen KSQLiteDatabase specified is closed upon throwing`() = runTest {
    val kdbFlow = MutableStateFlow<KSQLiteDatabase?>(null)
    val db =
      TestDataConnectSqliteDatabase(
        onOpen = {
          kdbFlow.value = it
          throw Exception("forced exception y8hrmkz3bb")
        }
      )

    val exception =
      try {
        db.runCatching { ensureOpen() }
        val kdb = kdbFlow.filterNotNull().first()
        shouldThrow<java.lang.IllegalStateException> { kdb.runReadOnlyTransaction {} }
      } finally {
        withContext(NonCancellable) { db.close() }
      }

    exception.message shouldContainWithNonAbuttingTextIgnoringCase "closed"
  }

  @Test
  fun `onOpen should only be called once when it completes normally`() = runTest {
    val onOpenInvocationCount = AtomicInteger(0)
    val db = TestDataConnectSqliteDatabase(onOpen = { onOpenInvocationCount.incrementAndGet() })

    try {
      repeat(5) {
        db.ensureOpen()
        db.callWithDb()
      }
    } finally {
      withContext(NonCancellable) { db.close() }
    }

    onOpenInvocationCount.get() shouldBe 1
  }

  @Test
  fun `onOpen should only be called once after it throws`() = runTest {
    class MyException : Exception()
    val onOpenInvocationCount = AtomicInteger(0)
    val db =
      TestDataConnectSqliteDatabase(
        onOpen = {
          onOpenInvocationCount.incrementAndGet()
          throw MyException()
        }
      )

    try {
      repeat(5) {
        db.runCatching { ensureOpen() }
        db.runCatching { callWithDb() }
      }
    } finally {
      withContext(NonCancellable) { db.close() }
    }

    onOpenInvocationCount.get() shouldBe 1
  }

  @Test
  fun `withDb KSQLiteDatabase specified is closed upon return`() = runTest {
    val kdbFlow = MutableStateFlow<KSQLiteDatabase?>(null)
    val db = TestDataConnectSqliteDatabase(withDb = { kdbFlow.value = it })

    val exception =
      try {
        db.callWithDb()
        val kdb = kdbFlow.filterNotNull().first()
        shouldThrow<java.lang.IllegalStateException> { kdb.runReadOnlyTransaction {} }
      } finally {
        withContext(NonCancellable) { db.close() }
      }

    exception.message shouldContainWithNonAbuttingTextIgnoringCase "closed"
  }

  @Test
  fun `withDb KSQLiteDatabase specified is closed upon throwing`() = runTest {
    val kdbFlow = MutableStateFlow<KSQLiteDatabase?>(null)
    val db =
      TestDataConnectSqliteDatabase(
        withDb = {
          kdbFlow.value = it
          throw Exception("forced exception t99h7jkvde")
        }
      )

    val exception =
      try {
        db.runCatching { callWithDb() }
        val kdb = kdbFlow.filterNotNull().first()
        shouldThrow<java.lang.IllegalStateException> { kdb.runReadOnlyTransaction {} }
      } finally {
        withContext(NonCancellable) { db.close() }
      }

    exception.message shouldContainWithNonAbuttingTextIgnoringCase "closed"
  }

  @Test
  fun `withDb returns whatever its block returns`() = runTest {
    val vineyard = Arb.vineyards().next(rs)
    val db =
      object : TestDataConnectSqliteDatabase() {
        suspend fun withDbReturns(): Vineyard = withDb { vineyard }
      }

    val withDbReturnValue =
      try {
        db.withDbReturns()
      } finally {
        withContext(NonCancellable) { db.close() }
      }

    withDbReturnValue shouldBeSameInstanceAs vineyard
  }

  @Test
  fun `withDb should throw whatever its block throws`() = runTest {
    class MyException : Exception()
    val db = TestDataConnectSqliteDatabase(withDb = { throw MyException() })

    try {
      shouldThrow<MyException> { db.callWithDb() }
    } finally {
      withContext(NonCancellable) { db.close() }
    }
  }

  @Test
  fun `withDb should throw whatever onOpen throws`() = runTest {
    class MyException : Exception()
    val db = TestDataConnectSqliteDatabase(onOpen = { throw MyException() })

    try {
      shouldThrow<MyException> { db.callWithDb() }
    } finally {
      withContext(NonCancellable) { db.close() }
    }
  }

  @Test
  fun `withDb should throw the same exception every time`() = runTest {
    class MyException(val key: String) : Exception()
    val db =
      TestDataConnectSqliteDatabase(onOpen = { throw MyException(Arb.vineyards().next(rs).value) })

    val exceptionKeys =
      try {
        buildSet {
          repeat(5) {
            add(shouldThrow<MyException> { db.callWithDb() }.key)
            add(shouldThrow<MyException> { db.ensureOpen() }.key)
          }
        }
      } finally {
        withContext(NonCancellable) { db.close() }
      }

    exceptionKeys shouldHaveSize 1
  }

  private open inner class TestDataConnectSqliteDatabase(
    file: File? = File(temporaryFolder.newFolder(), "db.sqlite"),
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    logger: Logger = mockk(relaxed = true),
    onOpen: suspend (KSQLiteDatabase) -> Unit = {},
    withDb: suspend CoroutineScope.(KSQLiteDatabase) -> Unit = {},
  ) : DataConnectSqliteDatabase(file = file, ioDispatcher = ioDispatcher, logger = logger) {

    private val _onOpen = onOpen
    private val _withDb = withDb

    override suspend fun onOpen(db: KSQLiteDatabase) {
      _onOpen(db)
    }

    suspend fun callWithDb(): Unit = withDb(block = _withDb)
  }

  private companion object {

    fun File.getSqliteUserVersion(): Int {
      val openFlags = OPEN_READONLY or NO_LOCALIZED_COLLATORS
      return SQLiteDatabase.openDatabase(this.absolutePath, null, openFlags).use { sqliteDatabase ->
        sqliteDatabase.version
      }
    }
  }
}
