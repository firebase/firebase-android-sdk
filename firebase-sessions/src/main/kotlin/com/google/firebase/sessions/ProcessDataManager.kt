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
  /** This process's name. */
  val myProcessName: String

  /** This process's pid. */
  val myPid: Int

  /** An in-memory uuid to uniquely identify this instance of this process, not the uid. */
  val myUuid: String

  /** Checks if this is a cold app start, meaning all processes in the mapping table are stale. */
  fun isColdStart(processDataMap: Map<String, ProcessData>): Boolean

  /** Checks if this process is stale. */
  fun isMyProcessStale(processDataMap: Map<String, ProcessData>): Boolean

  /** Call to notify the process data manager that a session has been generated. */
  fun onSessionGenerated()

  /** Update the mapping of the current processes with data about this process. */
  fun updateProcessDataMap(processDataMap: Map<String, ProcessData>?): Map<String, ProcessData>

  /** Generate a new mapping of process data about this process only. */
  fun generateProcessDataMap(): Map<String, ProcessData> = updateProcessDataMap(emptyMap())
}

@Singleton
internal class ProcessDataManagerImpl
@Inject
constructor(private val appContext: Context, uuidGenerator: UuidGenerator) : ProcessDataManager {
  /**
   * This process's name.
   *
   * This value is cached, so will not reflect changes to the process name during runtime.
   */
  override val myProcessName: String by lazy { myProcessDetails.processName }

  override val myPid = Process.myPid()

  override val myUuid: String by lazy { uuidGenerator.next().toString() }

  private val myProcessDetails by lazy { ProcessDetailsProvider.getMyProcessDetails(appContext) }

  private var hasGeneratedSession: Boolean = false

  override fun isColdStart(processDataMap: Map<String, ProcessData>): Boolean {
    if (hasGeneratedSession) {
      // This process has been notified that a session was generated, so cannot be a cold start
      return false
    }

    // A cold start is when all app processes are stale
    return getAppProcessDetails()
      .mapNotNull { processDetails ->
        processDataMap[processDetails.processName]?.let { processData ->
          Pair(processDetails, processData)
        }
      }
      .all { (processDetails, processData) -> isProcessStale(processDetails, processData) }
  }

  override fun isMyProcessStale(processDataMap: Map<String, ProcessData>): Boolean {
    val myProcessData = processDataMap[myProcessName] ?: return true
    return myProcessData.pid != myPid || myProcessData.uuid != myUuid
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

  /** Gets the current details for all of the app's running processes. */
  private fun getAppProcessDetails() = ProcessDetailsProvider.getAppProcessDetails(appContext)

  /**
   * Returns true if the process is stale, meaning the persisted process data does not match the
   * running process details.
   *
   * The [processDetails] is the running process details, and [processData] is the persisted data.
   */
  private fun isProcessStale(processDetails: ProcessDetails, processData: ProcessData): Boolean =
    if (myProcessName == processDetails.processName) {
      // For this process, check pid and uuid
      processDetails.pid != processData.pid || myUuid != processData.uuid
    } else {
      // For other processes, only check pid to avoid inter-process communication
      // It is very unlikely for there to be a pid collision
      processDetails.pid != processData.pid
    }
}
