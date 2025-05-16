// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf.util

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import com.google.android.gms.common.util.ProcessUtils

/**
 * A singleton that contains helper functions to get relevant process details. TODO(b/418041083):
 * Explore using a common utility. See [com.google.firebase.sessions.ProcessDetailsProvider].
 */
object AppProcessesProvider {
  /** Gets the details for all of this app's running processes. */
  @JvmStatic
  fun getAppProcesses(context: Context): List<ActivityManager.RunningAppProcessInfo> {
    val appUid = context.applicationInfo.uid
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val runningAppProcesses = activityManager?.runningAppProcesses ?: listOf()

    return runningAppProcesses.filterNotNull().filter {
      // Only collect process info for this app's processes.
      it.uid == appUid
    }
  }

  /**
   * Gets this app's current process name.
   *
   * If the current process details are not found for whatever reason, returns an empty string.
   */
  @JvmStatic
  fun getProcessName(context: Context): String {
    val pid = Process.myPid()
    return getAppProcesses(context).find { it.pid == pid }?.processName ?: getProcessName()
  }

  /** Gets the app's current process name. If it could not be found, return the default. */
  private fun getProcessName(default: String = ""): String {
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

    // Returns default if nothing works.
    return default
  }
}
