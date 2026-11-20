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

import android.os.Process
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps each launch's span file in its own folder under `active-sessions/`, named
 * `<12-hex-ms>_<4-hex-seq>_<8-hex-pid>_<uuid>`.
 *
 * The store keeps the previous launch's folder until Crashlytics says whether that launch crashed,
 * and deletes it if the launch was healthy. It marks a crashed launch's folder before reading it,
 * so Telemetry can still move the folder into [PreparedSessionStore] on the next launch if this
 * process dies or can't write to disk right now. The store keeps at most [MAX_EARLIER_SESSIONS]
 * crashed earlier folders, and none older than [MAX_EARLIER_SESSION_AGE_MILLIS].
 */
internal class ActiveSessionStore(
  storageRoot: File,
  private val mmapSize: MmapSize,
  private val clockMillis: () -> Long = System::currentTimeMillis,
  val currentSessionId: String = createSessionId(clockMillis(), Process.myPid()),
  private val openSession: (filePath: String, mmapSize: MmapSize) -> MutationContext =
    { filePath, mmapSize ->
      PersistenceContext.initialize(filePath, mmapSize)
    },
  private val readSessionSpans: (file: File, mmapSize: MmapSize) -> List<Span>? =
    MmapSpanLoader::readSpans,
) {
  private val activeDir = File(storageRoot, ACTIVE_SESSIONS_DIR_NAME)

  /** Creates the current launch's folder and opens its span file for writing. */
  fun openCurrentSession(): MutationContext {
    val dir = File(activeDir, currentSessionId)
    dir.mkdirs()
    return openSession(File(dir, SPAN_FILE_NAME).absolutePath, mmapSize)
  }

  /**
   * Applies Crashlytics's answer for the previous launch to the newest earlier folder, deletes
   * healthy, unmarked, or expired folders, and returns every remaining crashed session's spans.
   *
   * The store keeps each returned folder on disk until [deleteSession] is called once Telemetry
   * saves it into [PreparedSessionStore]. If the store can't read a folder right now, it skips that
   * folder and keeps it for the next launch.
   */
  fun processEarlierSessions(previousLaunchCrashed: Boolean): List<ActiveSession> {
    val earlier = earlierSessionDirs().toMutableList()
    val previousLaunch = earlier.lastOrNull()
    if (previousLaunch != null) {
      if (previousLaunchCrashed) {
        File(previousLaunch, CRASHED_MARKER_FILE_NAME).createNewFile()
      } else {
        previousLaunch.deleteRecursively()
        earlier.remove(previousLaunch)
      }
    }

    val oldestAllowed = clockMillis() - MAX_EARLIER_SESSION_AGE_MILLIS
    val crashedDirs =
      earlier
        .filter { dir ->
          val createdAtMillis = sessionTimestampMillis(dir.name)
          File(dir, CRASHED_MARKER_FILE_NAME).exists() &&
            createdAtMillis != null &&
            createdAtMillis >= oldestAllowed
        }
        .takeLast(MAX_EARLIER_SESSIONS)
    earlier.filterNot { it in crashedDirs }.forEach { it.deleteRecursively() }

    return crashedDirs.mapNotNull { dir ->
      val spans = readSessionSpans(File(dir, SPAN_FILE_NAME), mmapSize) ?: return@mapNotNull null
      if (spans.isEmpty()) {
        dir.deleteRecursively()
        null
      } else {
        ActiveSession(dir.name, spans)
      }
    }
  }

  /** Deletes an earlier session's folder once Telemetry has saved its spans for upload. */
  fun deleteSession(sessionId: String) {
    File(activeDir, sessionId).deleteRecursively()
  }

  /**
   * Returns earlier session folders, oldest first. [createSessionId] puts the fixed-width hex
   * timestamp first, so sorting by name sorts by creation time.
   */
  private fun earlierSessionDirs(): List<File> =
    activeDir
      .listFiles { file -> file.isDirectory && file.name != currentSessionId }
      .orEmpty()
      .sortedBy { it.name }

  /** The spans read from an earlier active session folder whose process crashed. */
  class ActiveSession(val sessionId: String, val spans: List<Span>)

  internal companion object {
    private const val ACTIVE_SESSIONS_DIR_NAME = "active-sessions"
    private const val SPAN_FILE_NAME = "telemetry.mmap"
    private const val CRASHED_MARKER_FILE_NAME = "crashed"
    private const val MAX_EARLIER_SESSIONS = 4
    private const val MAX_TIMESTAMP_MILLIS = 0xFFFF_FFFF_FFFFL
    private val MAX_EARLIER_SESSION_AGE_MILLIS = TimeUnit.DAYS.toMillis(7)
    private val nextSequence = AtomicInteger(0)

    /**
     * Builds a folder-safe session ID formatted as `<12-hex-ms>_<4-hex-seq>_<8-hex-pid>_<uuid>`.
     */
    fun createSessionId(nowMillis: Long, pid: Int): String {
      val timeHex =
        (nowMillis.coerceAtLeast(0L) and MAX_TIMESTAMP_MILLIS).toString(16).padStart(12, '0')
      val seqHex = (nextSequence.incrementAndGet() and 0xFFFF).toString(16).padStart(4, '0')
      val pidHex = (pid.toLong() and 0xFFFF_FFFFL).toString(16).padStart(8, '0')
      return "${timeHex}_${seqHex}_${pidHex}_${UUID.randomUUID()}"
    }

    /** Returns the creation time that [createSessionId] put at the start of [sessionId]. */
    fun sessionTimestampMillis(sessionId: String): Long? {
      val timeHex = sessionId.substringBefore('_', missingDelimiterValue = "")
      if (timeHex.length != 12 || !timeHex.all { it in '0'..'9' || it in 'a'..'f' }) {
        return null
      }
      return timeHex.toLongOrNull(16)
    }
  }
}
