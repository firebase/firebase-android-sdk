package com.google.firebase.perf.util

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import com.google.android.gms.common.util.ProcessUtils

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
   * Gets this app's current process details.
   *
   * If the current process details are not found for whatever reason, returns app's package name.
   */
  @JvmStatic
  fun getProcessName(context: Context): String {
    val pid = Process.myPid()
    return getAppProcesses(context).find { it.pid == pid }?.processName ?: getProcessName()
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
