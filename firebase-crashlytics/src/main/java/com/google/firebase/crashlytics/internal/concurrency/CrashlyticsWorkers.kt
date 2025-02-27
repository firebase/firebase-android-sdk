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
   * processing uncaught exceptions. This is the main worker of the sdk. This worker will never
   * block on a disk write or network call.
   */
  @JvmField val common = CrashlyticsWorker(backgroundExecutorService)

  /**
   * The disk write worker is for background tasks that persisting data to local disk. No user
   * action should wait on this. Use for fire and forget, safe to ignore exceptions.
   */
  @JvmField val diskWrite = CrashlyticsWorker(backgroundExecutorService)

  /**
   * The data collect worker is for any background tasks that send data remotely, like fetching fid,
   * settings, or uploading crash reports. This worker is suspended until permission is granted.
   */
  @JvmField val dataCollect = CrashlyticsWorker(backgroundExecutorService)

  /** The network worker is for making blocking network calls. */
  @JvmField val network = CrashlyticsWorker(blockingExecutorService)

  /** Convenient preconditions specific for Crashlytics worker threads. */
  companion object {
    private val threadName
      get() = Thread.currentThread().name

    /** When enabled, failed preconditions will cause assertion errors for debugging. */
    @JvmStatic var enforcement: Boolean = false

    @JvmStatic
    fun checkNotMainThread() =
      checkThread(::isNotMainThread) {
        "Must not be called on a main thread, was called on $threadName."
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

    private fun isNotMainThread() =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        !Looper.getMainLooper().isCurrentThread
      } else {
        Looper.getMainLooper() != Looper.myLooper()
      }

    private fun isBlockingThread() = threadName.contains("Firebase Blocking Thread #")

    private fun isBackgroundThread() = threadName.contains("Firebase Background Thread #")

    private fun checkThread(isCorrectThread: () -> Boolean, failureMessage: () -> String) {
      if (!isCorrectThread()) {
        Logger.getLogger().d(failureMessage())
        assert(!enforcement, failureMessage)
      }
    }
  }
}
