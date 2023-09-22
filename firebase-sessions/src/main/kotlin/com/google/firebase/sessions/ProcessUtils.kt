/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.sessions

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process

/** Utils related to processes. */
internal object ProcessUtils {
  /** Returns whether the current process should generate a new session or not. */
  fun shouldProcessGenerateNewSession(context: Context): Boolean =
    isDefaultProcess(context) && isForegroundImportance()

  /** Returns whether the current process is the app's default process or not. */
  private fun isDefaultProcess(context: Context): Boolean =
    getCurrentProcessName(context) == getDefaultProcessName(context)

  /** Returns whether the current process or service is running in the foreground or not. */
  private fun isForegroundImportance(): Boolean {
    val runningAppProcessInfo = RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(runningAppProcessInfo)
    return isForegroundProcess(runningAppProcessInfo) || isForegroundService(runningAppProcessInfo)
  }

  /** Returns the name of the current process. */
  private fun getCurrentProcessName(context: Context): String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      Application.getProcessName()
    } else {
      findProcessName(context, Process.myPid())
    }

  /** Finds the process name for the given pid, or returns null if not found. */
  private fun findProcessName(context: Context, pid: Int): String? {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return activityManager.runningAppProcesses?.find { it.pid == pid }?.processName
  }

  /**
   * Gets the default process name.
   *
   * This is the app's package name unless the app overrides the android:process attribute in the
   * application block of its Android manifest file.
   */
  private fun getDefaultProcessName(context: Context): String = context.applicationInfo.processName

  /**
   * Returns if the current process is at the top of the screen that the user is interacting with.
   */
  private fun isForegroundProcess(runningAppProcessInfo: RunningAppProcessInfo): Boolean {
    return runningAppProcessInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND
  }

  /**
   * Returns if the current process is running as a foreground service.
   *
   * This generally indicates that the process is doing something the user actively cares about.
   */
  private fun isForegroundService(runningAppProcessInfo: RunningAppProcessInfo): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      runningAppProcessInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
    } else {
      // Default to false if the api level is too low to check.
      false
    }
}
