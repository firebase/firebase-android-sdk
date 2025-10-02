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

package com.google.firebase.testing.sessions

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.testing.sessions.TestApplication.Companion.TAG
import com.google.firebase.testing.sessions.TestApplication.Companion.myProcessName

open class BaseActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setProcessAttribute()
    logProcessDetails()
    logFirebaseDetails()
    Log.i(TAG, "onCreate - $myProcessName - ${getImportance()}")
  }

  override fun onPause() {
    super.onPause()
    Log.i(TAG, "onPause - $myProcessName - ${getImportance()}")
  }

  override fun onStop() {
    super.onStop()
    Log.i(TAG, "onStop - $myProcessName - ${getImportance()}")
  }

  override fun onResume() {
    super.onResume()
    Log.i(TAG, "onResume - $myProcessName - ${getImportance()}")
  }

  override fun onStart() {
    super.onStart()
    Log.i(TAG, "onStart - $myProcessName - ${getImportance()}")
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.i(TAG, "onDestroy - $myProcessName - ${getImportance()}")
  }

  private fun getImportance(): Int {
    val processInfo = RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(processInfo)
    return processInfo.importance
  }

  private fun logProcessDetails() {
    val pid = android.os.Process.myPid()
    val uid = android.os.Process.myUid()
    val activity = javaClass.name
    Log.i(TAG, "activity: $activity process: $myProcessName, pid: $pid, uid: $uid")
  }

  private fun logFirebaseDetails() {
    val activity = javaClass.name
    val firebaseApps = FirebaseApp.getApps(this)
    val defaultFirebaseApp = FirebaseApp.getInstance()
    Log.i(
      TAG,
      "activity: $activity firebase: ${defaultFirebaseApp.name} appsCount: ${firebaseApps.count()}",
    )
  }

  private fun setProcessAttribute() {
    FirebasePerformance.getInstance().putAttribute("process_name", myProcessName)
  }
}
