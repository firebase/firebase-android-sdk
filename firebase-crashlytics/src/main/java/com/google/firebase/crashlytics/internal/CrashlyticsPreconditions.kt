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

package com.google.firebase.crashlytics.internal

import android.os.Build
import android.os.Looper
import androidx.annotation.IntRange

/**
 * Convenient preconditions specific for Crashlytics concurrency.
 *
 * Use GMS Core's [com.google.android.gms.common.internal.Preconditions] for general preconditions.
 */
internal object CrashlyticsPreconditions {
  private val threadName
    get() = Thread.currentThread().name

  // TODO(mrober): Make this a build time configuration.
  @JvmStatic @IntRange(from = 0, to = 3) var strictLevel: Int = 0

  @JvmStatic
  fun checkMainThread() =
    checkThread(::isMainThread) { "Must be called on the main thread, was called on $threadName." }

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

  // TODO(mrober): Remove the Crashlytics thread when fully migrated to Firebase common threads.
  private fun isBackgroundThread() =
    threadName.contains("Firebase Background Thread #") ||
      threadName.contains("Crashlytics Exception Handler")

  private fun checkThread(isCorrectThread: () -> Boolean, failureMessage: () -> String) {
    if (strictLevel > 0 && !isCorrectThread()) {
      if (strictLevel >= 1) {
        Logger.getLogger().w(failureMessage()) // Log violations.
      }
      assert(strictLevel < 3, failureMessage) // Kill the process on violation.
      check(strictLevel < 2, failureMessage) // Throw an exception on violation.
    }
  }
}
