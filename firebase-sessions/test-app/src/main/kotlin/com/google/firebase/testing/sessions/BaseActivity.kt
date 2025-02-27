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
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp

open class BaseActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    FirebaseApp.initializeApp(this)
    Log.i(TAG, "onCreate - ${getProcessName()} - ${getImportance()}")
  }

  override fun onPause() {
    super.onPause()
    Log.i(TAG, "onPause - ${getProcessName()} - ${getImportance()}")
  }

  override fun onStop() {
    super.onStop()
    Log.i(TAG, "onStop - ${getProcessName()} - ${getImportance()}")
  }

  override fun onResume() {
    super.onResume()
    Log.i(TAG, "onResume - ${getProcessName()} - ${getImportance()}")
  }

  override fun onStart() {
    super.onStart()
    Log.i(TAG, "onStart - ${getProcessName()} - ${getImportance()}")
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.i(TAG, "onDestroy - ${getProcessName()} - ${getImportance()}")
  }

  private fun getImportance(): Int {
    val processInfo = RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(processInfo)
    return processInfo.importance
  }

  private fun getProcessName(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName() else "unknown"

  companion object {
    val TAG = "BaseActivity"
  }
}
