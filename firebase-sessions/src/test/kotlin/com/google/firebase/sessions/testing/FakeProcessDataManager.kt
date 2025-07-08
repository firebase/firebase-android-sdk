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

import com.google.firebase.sessions.ProcessData
import com.google.firebase.sessions.ProcessDataManager

/**
 * Fake implementation of ProcessDataManager that returns the provided [coldStart] value for
 * [isColdStart], and similar for [isMyProcessStale], until [onSessionGenerated] gets called then
 * returns false.
 */
internal class FakeProcessDataManager(
  private val coldStart: Boolean = false,
  private var myProcessStale: Boolean = coldStart,
  override val myProcessName: String = "com.google.firebase.sessions.test",
  override var myPid: Int = 0,
  override var myUuid: String = FakeUuidGenerator.UUID_1,
) : ProcessDataManager {
  private var hasGeneratedSession: Boolean = false

  override fun isColdStart(processDataMap: Map<String, ProcessData>): Boolean {
    if (hasGeneratedSession) {
      return false
    }

    return coldStart
  }

  override fun isMyProcessStale(processDataMap: Map<String, ProcessData>): Boolean {
    if (hasGeneratedSession) {
      return false
    }

    return myProcessStale
  }

  override fun onSessionGenerated() {
    hasGeneratedSession = true
  }

  override fun updateProcessDataMap(
    processDataMap: Map<String, ProcessData>?
  ): Map<String, ProcessData> = processDataMap ?: emptyMap()
}
