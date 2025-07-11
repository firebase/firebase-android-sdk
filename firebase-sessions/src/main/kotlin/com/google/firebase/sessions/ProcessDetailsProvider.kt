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
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import com.google.android.gms.common.util.ProcessUtils

/** Provide [ProcessDetails] for all app processes. */
internal object ProcessDetailsProvider {
  /** Gets the details for all the app's running processes. */
  fun getAppProcessDetails(context: Context): List<ProcessDetails> {
    val appUid = context.applicationInfo.uid
    val defaultProcessName = context.applicationInfo.processName
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val runningAppProcesses = activityManager?.runningAppProcesses ?: listOf()

    return runningAppProcesses
      .filterNotNull()
      .filter {
        // Only collect process info for this app's processes.
        it.uid == appUid
      }
      .map { runningAppProcessInfo ->
        ProcessDetails(
          processName = runningAppProcessInfo.processName,
          pid = runningAppProcessInfo.pid,
          importance = runningAppProcessInfo.importance,
          isDefaultProcess = runningAppProcessInfo.processName == defaultProcessName,
        )
      }
  }

  /**
   * Gets this process's details.
   *
   * If this process's full details are not found for whatever reason, returns process details with
   * just the process name and pid set.
   */
  fun getMyProcessDetails(context: Context): ProcessDetails {
    val pid = Process.myPid()
    return getAppProcessDetails(context).find { it.pid == pid }
      ?: ProcessDetails(getProcessName(), pid, importance = 0, isDefaultProcess = false)
  }

  /** Gets the app's current process name. If it could not be found, returns an empty string. */
  private fun getProcessName(): String {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
      return Process.myProcessName()
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      Application.getProcessName()?.let {
        return it
      }
    }

    // GMS core has different ways to get the process name on old api levels.
    ProcessUtils.getMyProcessName()?.let {
      return it
    }

    // Default to an empty string if nothing works.
    return ""
  }
}
