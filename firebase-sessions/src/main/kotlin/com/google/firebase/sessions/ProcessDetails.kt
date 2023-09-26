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

/** Provides details about the current process. */
internal interface ProcessDetails {
  /** Whether the current process is the app's default process or not. */
  val isDefaultProcess: Boolean

  /** Whether the current process is running in the foreground or not. */
  val isForegroundProcess: Boolean
}

/** Android implementation of [ProcessDetails]. */
internal class AndroidProcessDetails(context: Context) : ProcessDetails {
  /**
   * The default process name.
   *
   * This is the app's package name unless the app overrides the android:process attribute in the
   * application block of its Android manifest file.
   */
  private val defaultProcessName: String = context.applicationInfo.processName

  /** The name of the current process, or null if it couldn't be found. */
  private val currentProcessName: String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      Application.getProcessName()
    } else {
      findProcessName(context, Process.myPid())
    }

  override val isDefaultProcess: Boolean = currentProcessName == defaultProcessName

  override val isForegroundProcess: Boolean
    get() {
      val runningAppProcessInfo = RunningAppProcessInfo()
      ActivityManager.getMyMemoryState(runningAppProcessInfo)
      return runningAppProcessInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

  /** Finds the process name for the given pid, or returns null if not found. */
  private fun findProcessName(context: Context, pid: Int): String? {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return activityManager.runningAppProcesses?.find { it.pid == pid }?.processName
  }

  // TODO(mrober): Move this to somewhere session specific.
  internal companion object {
    /** Returns whether the current process should generate a new session or not. */
    fun shouldProcessGenerateNewSession(processDetails: ProcessDetails): Boolean =
      processDetails.isDefaultProcess && processDetails.isForegroundProcess
  }
}
