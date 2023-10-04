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
import com.google.firebase.FirebaseApp

/** Details about a process. */
internal data class ProcessDetails(
  val pid: Int,
  val processName: String,
  val importance: Int,
  val isDefaultProcess: Boolean,
) {
  internal companion object {
    fun currentProcess(firebaseApp: FirebaseApp): ProcessDetails {
      val context = firebaseApp.applicationContext
      val currentProcessName =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          Application.getProcessName()
        } else {
          findProcessName(context, Process.myPid())
        }
      val runningAppProcessInfo = ActivityManager.RunningAppProcessInfo()
      ActivityManager.getMyMemoryState(runningAppProcessInfo)

      return ProcessDetails(
        pid = Process.myPid(),
        processName = currentProcessName,
        importance = runningAppProcessInfo.importance,
        isDefaultProcess = (currentProcessName == context.applicationInfo.processName),
      )
    }

    fun allRunningAppProcesses(firebaseApp: FirebaseApp): List<ProcessDetails> {
      val context = firebaseApp.applicationContext
      val defaultProcessName = context.applicationInfo.processName
      val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
      val runningAppProcesses = activityManager?.runningAppProcesses ?: listOf()

      return runningAppProcesses.filterNotNull().map { runningAppProcessInfo ->
        ProcessDetails(
          pid = runningAppProcessInfo.pid,
          processName = runningAppProcessInfo.processName,
          importance = runningAppProcessInfo.importance,
          isDefaultProcess = (runningAppProcessInfo.processName == defaultProcessName),
        )
      }
    }

    /** Finds the process name for the given pid, or empty if not found. */
    private fun findProcessName(context: Context, pid: Int): String {
      val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
      return activityManager?.runningAppProcesses?.find { it.pid == pid }?.processName ?: ""
    }
  }
}
