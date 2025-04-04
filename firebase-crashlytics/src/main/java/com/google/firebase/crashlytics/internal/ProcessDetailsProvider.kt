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

package com.google.firebase.crashlytics.internal

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event.Application.ProcessDetails

/**
 * Provider of ProcessDetails.
 *
 * @hide
 */
internal object ProcessDetailsProvider {
  // TODO(mrober): Merge this with [com.google.firebase.sessions.ProcessDetailsProvider].

  /** Gets the details for all of this app's running processes. */
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
        ProcessDetails.builder()
          .setProcessName(runningAppProcessInfo.processName)
          .setPid(runningAppProcessInfo.pid)
          .setImportance(runningAppProcessInfo.importance)
          .setDefaultProcess(runningAppProcessInfo.processName == defaultProcessName)
          .build()
      }
  }

  /**
   * Gets this app's current process details.
   *
   * If the current process details are not found for whatever reason, returns process details with
   * just the current process name and pid set.
   */
  fun getCurrentProcessDetails(context: Context): ProcessDetails {
    val pid = Process.myPid()
    return getAppProcessDetails(context).find { it.pid == pid }
      ?: buildProcessDetails(getProcessName(), pid)
  }

  /** Builds a ProcessDetails object. */
  @JvmOverloads
  fun buildProcessDetails(
    processName: String,
    pid: Int = 0,
    importance: Int = 0,
    isDefaultProcess: Boolean = false,
  ) =
    ProcessDetails.builder()
      .setProcessName(processName)
      .setPid(pid)
      .setImportance(importance)
      .setDefaultProcess(isDefaultProcess)
      .build()

  /** Gets the app's current process name. If the API is not available, returns an empty string. */
  private fun getProcessName(): String =
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
      Process.myProcessName()
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      Application.getProcessName() ?: ""
    } else {
      ""
    }
}
