/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.internal.concurrency

import android.os.Build
import android.os.Looper
import com.google.firebase.crashlytics.internal.Logger
import com.google.firebase.crashlytics.internal.concurrency.CrashlyticsWorkers.StrictLevel.ASSERT
import com.google.firebase.crashlytics.internal.concurrency.CrashlyticsWorkers.StrictLevel.NONE
import com.google.firebase.crashlytics.internal.concurrency.CrashlyticsWorkers.StrictLevel.THROW
import com.google.firebase.crashlytics.internal.concurrency.CrashlyticsWorkers.StrictLevel.WARN
import java.util.concurrent.ExecutorService

/**
 * Container to hold the different Crashlytics workers.
 *
 * @suppress @hide
 */
class CrashlyticsWorkers(
  backgroundExecutorService: ExecutorService,
  blockingExecutorService: ExecutorService,
) {

  /**
   * The common worker is for common background tasks, like background init, user actions, and
   * processing uncaught exceptions. This is the main worker of the sdk.
   */
  @JvmField val common = CrashlyticsWorker(backgroundExecutorService)

  /**
   * The disk write worker is for background tasks that persisting data to local disk. Ideally, no
   * user action should require waiting on this (although still do).
   */
  @JvmField val diskWrite = CrashlyticsWorker(backgroundExecutorService)

  /**
   * The data collect worker is for any background tasks that send data remotely, like fetching fid,
   * settings, or uploading crash reports. This worker is blocked until permission is granted.
   */
  @JvmField val dataCollect = CrashlyticsWorker(backgroundExecutorService)

  /** The network worker is for making blocking network calls. */
  @JvmField val network = CrashlyticsWorker(blockingExecutorService)

  /** Convenient preconditions specific for Crashlytics worker threads. */
  companion object {
    private val threadName
      get() = Thread.currentThread().name

    @JvmStatic var strictLevel: StrictLevel = NONE

    @JvmStatic
    fun checkMainThread() =
      checkThread(::isMainThread) {
        "Must be called on the main thread, was called on $threadName."
      }

    @JvmStatic
    fun checkBlockingThread() =
      checkThread(::isBlockingThread) {
        "Must be called on a blocking thread, was called on $threadName."
      }

    @JvmStatic
    fun checkBackgroundThread() =
      checkThread(::isBackgroundThread) {
        "Must be called on a background thread, was called on $threadName."
      }

    private fun isMainThread() =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Looper.getMainLooper().isCurrentThread
      } else {
        Looper.getMainLooper() == Looper.myLooper()
      }

    private fun isBlockingThread() = threadName.contains("Firebase Blocking Thread #")

    private fun isBackgroundThread() = threadName.contains("Firebase Background Thread #")

    private fun checkThread(isCorrectThread: () -> Boolean, failureMessage: () -> String) {
      if (strictLevel.level >= WARN.level && !isCorrectThread()) {
        Logger.getLogger().w(failureMessage())
        assert(strictLevel.level < ASSERT.level, failureMessage)
        check(strictLevel.level < THROW.level, failureMessage)
      }
    }
  }

  enum class StrictLevel(val level: Int) : Comparable<StrictLevel> {
    /** Do not check for violations. */
    NONE(0),
    /** Log violations as warnings. */
    WARN(1),
    /** Throw an exception on violation. */
    THROW(2),
    /** Kill the process on violation. Useful for debugging. */
    ASSERT(3),
  }
}
