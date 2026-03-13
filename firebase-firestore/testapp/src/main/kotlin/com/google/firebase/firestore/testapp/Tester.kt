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

package com.google.firebase.firestore.testapp

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.snapshots
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.yield

object Tester {

  private val job = MutableStateFlow<Optional<Job>>(Optional.Empty)

  private val logger = Logger("Tester")

  private val coroutineScope =
    CoroutineScope(
      SupervisorJob() +
        Executors.newSingleThreadExecutor().asCoroutineDispatcher() +
        CoroutineName("firestore.testapp.Tester") +
        CoroutineExceptionHandler { context, throwable ->
          logger.warn(throwable) {
            "Unhandled exception in coroutine ${context[CoroutineName]} [v4qhtk5482]"
          }
        }
    )

  fun start(context: Context) {
    while (true) {
      val oldOptionalJob = job.value

      when (oldOptionalJob) {
        Optional.Empty -> {}
        is Optional.Value<Job> -> {
          val job = oldOptionalJob.value
          if (!job.isActive && !job.isCancelled && !job.isCompleted) {
            // The job is in the "new" state, so start it!
            job.start()
            return
          } else if (!job.isCompleted) {
            // The job is "in progress"; let it continue.
            return
          }
        }
      }

      val startTime = monotonicTimeMillis()
      val testLogger = Logger("RunTest")
      val newJob =
        coroutineScope.async(start = CoroutineStart.LAZY) {
          logger.info { "${testLogger.displayName} started by ${logger.displayName}" }
          RunTest(context.applicationContext, logger).run()
        }
      newJob.invokeOnCompletion { exception ->
        val endTime = monotonicTimeMillis()
        val elapsedTime = (endTime - startTime).milliseconds
        if (exception === null) {
          logger.info { "${testLogger.displayName} completed successfully after $elapsedTime" }
        } else {
          logger.info(exception) { "${testLogger.displayName} FAILED after $elapsedTime" }
        }
      }

      if (job.compareAndSet(oldOptionalJob, Optional.Value(newJob))) {
        newJob.start()
        return
      }
    }
  }
}

private class RunTest(private val context: Context, private val logger: Logger) {

  suspend fun run() {
    setupDatabases()

    FirebaseFirestore.setLoggingEnabled(true)
    val firestore = Firebase.firestore
    firestore.disableNetwork().await()

    val query =
      firestore
        .collection("offers_weeks")
        .whereGreaterThanOrEqualTo(FieldPath.of("visible", "until"), Timestamp.now())
        .orderBy(FieldPath.of("visible", "until"))
        .limit(2)

    val startTime = monotonicTimeMillis()
    val snapshotCount = AtomicInteger(0)
    logger.info { "Flow collection starting" }
    query.snapshots().collect { snapshot ->
      val snapshotNumber = snapshotCount.incrementAndGet()
      val timeSinceStart = (monotonicTimeMillis() - startTime).milliseconds
      logger.info {
        "Flow collection got snapshot $snapshotNumber " +
          "with ${snapshot.size()} documents after $timeSinceStart"
      }
    }
  }

  private suspend fun setupDatabases() {
    val databaseNames = listOf(DATABASE_NAME, DATABASE_JOURNAL_NAME)

    databaseNames.forEach { databaseName ->
      yield()
      context.deleteDatabase(databaseName)
    }

    databaseNames.forEach { databaseName ->
      val url = "http://10.0.2.2:8000/${Uri.encode(databaseName)}"
      val file = context.getDatabasePath(databaseName)
      val startTime = monotonicTimeMillis()
      logger.info { "Downloading $url to $file" }
      file.parentFile?.mkdirs()
      val byteCount =
        URL(url).openStream().use { input ->
          file.outputStream().use { output -> input.copyTo(output) }
        }
      val elapsedTime = (monotonicTimeMillis() - startTime).milliseconds
      logger.info { "Downloading $url ($byteCount bytes) completed in $elapsedTime" }
    }
  }
}

private fun monotonicTimeMillis(): Long = SystemClock.uptimeMillis()

private const val DATABASE_NAME = "firestore.%5BDEFAULT%5D.rd-penny-prod-v001-5e43f.%28default%29"
private const val DATABASE_JOURNAL_NAME =
  "firestore.%5BDEFAULT%5D.rd-penny-prod-v001-5e43f.%28default%29-journal"
