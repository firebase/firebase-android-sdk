/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.sessions.testing

import android.app.ActivityManager

/** Fake [ActivityManager.RunningAppProcessInfo] that is easy to construct. */
internal class FakeRunningAppProcessInfo(
  pid: Int = 0,
  uid: Int = 313,
  processName: String = "fake.process.name",
  importance: Int = 100,
) : ActivityManager.RunningAppProcessInfo() {
  init {
    this.pid = pid
    this.uid = uid
    this.processName = processName
    this.importance = importance
  }
}
