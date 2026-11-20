/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.crashlytics.telemetry

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manages the active and previous launch's span files under `active-sessions/`.
 *
 * On startup, the store rotates `current_telemetry.mmap` to `previous_telemetry.mmap` and opens a
 * fresh `current_telemetry.mmap` for writing. Once Crashlytics says whether the previous launch
 * crashed, the store either deletes `previous_telemetry.mmap` (if healthy or older than
 * [MAX_PREVIOUS_SESSION_AGE_MILLIS]) or reads its spans so Telemetry can save them in
 * [PreparedSessionStore] before deleting `previous_telemetry.mmap`.
 */
internal class ActiveSessionStore(
  storageRoot: File,
  private val mmapSize: MmapSize,
  private val clockMillis: () -> Long = System::currentTimeMillis,
  private val openSession: (filePath: String, mmapSize: MmapSize) -> MutationContext =
    { filePath, mmapSize ->
      PersistenceContext.initialize(filePath, mmapSize)
    },
  private val readSessionSpans: (file: File, mmapSize: MmapSize) -> List<Span>? =
    MmapSpanLoader::readSpans,
) {
  private val activeDir = File(storageRoot, ACTIVE_SESSIONS_DIR_NAME)
  private val currentFile = File(activeDir, CURRENT_FILE_NAME)
  private val previousFile = File(activeDir, PREVIOUS_FILE_NAME)

  /**
   * Rotates any existing `current_telemetry.mmap` to `previous_telemetry.mmap` and opens a fresh
   * `current_telemetry.mmap` for writing.
   */
  fun openCurrentSession(): MutationContext {
    activeDir.mkdirs()
    if (currentFile.exists()) {
      previousFile.delete()
      if (!currentFile.renameTo(previousFile)) {
        currentFile.delete()
      }
    }
    return openSession(currentFile.absolutePath, mmapSize)
  }

  /**
   * Applies Crashlytics's answer for the previous launch to `previous_telemetry.mmap`.
   *
   * Deletes `previous_telemetry.mmap` if the previous launch was healthy, recorded no spans, or is
   * older than [MAX_PREVIOUS_SESSION_AGE_MILLIS]. Returns the previous launch's spans if it
   * crashed, keeping `previous_telemetry.mmap` on disk until [deletePreviousSession] is called.
   * Returns `null` if there are no spans to save or if the store can't read the file right now.
   */
  fun processPreviousSession(previousLaunchCrashed: Boolean): List<Span>? {
    if (!previousFile.exists()) return null

    val oldestAllowed = clockMillis() - MAX_PREVIOUS_SESSION_AGE_MILLIS
    if (!previousLaunchCrashed || previousFile.lastModified() < oldestAllowed) {
      previousFile.delete()
      return null
    }

    val spans = readSessionSpans(previousFile, mmapSize) ?: return null
    if (spans.isEmpty()) {
      previousFile.delete()
      return null
    }
    return spans
  }

  /** Deletes `previous_telemetry.mmap` once Telemetry has saved its spans for upload. */
  fun deletePreviousSession() {
    previousFile.delete()
  }

  private companion object {
    const val ACTIVE_SESSIONS_DIR_NAME = "active-sessions"
    const val CURRENT_FILE_NAME = "current_telemetry.mmap"
    const val PREVIOUS_FILE_NAME = "previous_telemetry.mmap"
    val MAX_PREVIOUS_SESSION_AGE_MILLIS = TimeUnit.DAYS.toMillis(7)
  }
}
