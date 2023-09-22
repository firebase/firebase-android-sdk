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

/** Utils related to processes. */
internal object ProcessUtils {
    /** Returns whether the current process is the app's default process or not. */
    fun isDefaultProcess(context: Context): Boolean =
        getMyProcessName(context) == getDefaultProcessName(context)

    /** Returns the name of the current process. */
    fun getMyProcessName(context: Context): String? =
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
}