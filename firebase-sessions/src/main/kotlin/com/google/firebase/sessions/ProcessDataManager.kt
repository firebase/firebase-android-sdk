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

package com.google.firebase.sessions

import android.content.Context
import android.os.Process
import javax.inject.Inject
import javax.inject.Singleton

/** Manage process data, used for detecting cold app starts. */
internal interface ProcessDataManager {
  /** An in-memory uuid to uniquely identify this instance of this process. */
  val myUuid: String

  /** Checks if this is a cold app start, meaning all processes in the mapping table are stale. */
  fun isColdStart(processDataMap: Map<String, ProcessData>): Boolean

  /** Call to notify the process data manager that a session has been generated. */
  fun onSessionGenerated()

  /** Update the mapping of the current processes with data about this process. */
  fun updateProcessDataMap(processDataMap: Map<String, ProcessData>?): Map<String, ProcessData>

  /** Generate a new mapping of process data with the current process only. */
  fun generateProcessDataMap() = updateProcessDataMap(mapOf())
}

/** Manage process data, used for detecting cold app starts. */
@Singleton
internal class ProcessDataManagerImpl
@Inject
constructor(private val appContext: Context, private val uuidGenerator: UuidGenerator) :
  ProcessDataManager {
  override val myUuid: String by lazy { uuidGenerator.next().toString() }

  private val myProcessName: String by lazy {
    ProcessDetailsProvider.getCurrentProcessDetails(appContext).processName
  }

  private var hasGeneratedSession: Boolean = false

  override fun isColdStart(processDataMap: Map<String, ProcessData>): Boolean {
    if (hasGeneratedSession) {
      // This process has been notified that a session was generated, so cannot be a cold start
      return false
    }

    return ProcessDetailsProvider.getAppProcessDetails(appContext)
      .mapNotNull { processDetails ->
        processDataMap[processDetails.processName]?.let { processData ->
          Pair(processDetails, processData)
        }
      }
      .all { (processDetails, processData) -> isProcessStale(processDetails, processData) }
  }

  override fun onSessionGenerated() {
    hasGeneratedSession = true
  }

  override fun updateProcessDataMap(
    processDataMap: Map<String, ProcessData>?
  ): Map<String, ProcessData> =
    processDataMap
      ?.toMutableMap()
      ?.apply { this[myProcessName] = ProcessData(Process.myPid(), myUuid) }
      ?.toMap()
      ?: mapOf(myProcessName to ProcessData(Process.myPid(), myUuid))

  /**
   * Returns true if the process is stale, meaning the persisted process data does not match the
   * running process details.
   */
  private fun isProcessStale(
    runningProcessDetails: ProcessDetails,
    persistedProcessData: ProcessData,
  ): Boolean =
    if (myProcessName == runningProcessDetails.processName) {
      runningProcessDetails.pid != persistedProcessData.pid || myUuid != persistedProcessData.uuid
    } else {
      runningProcessDetails.pid != persistedProcessData.pid
    }
}
