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
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps the spans of crashed sessions in small files under `prepared/` until Telemetry uploads
 * them.
 *
 * Each file is named `<12-hex-ms>_<4-hex-seq>_<8-hex-pid>_<uuid>.spans`, so sorting by name sorts
 * by creation time and encodes when the crash was saved. The store keeps at most
 * [MAX_PREPARED_SESSIONS] sessions, and none older than [MAX_PREPARED_SESSION_AGE_MILLIS].
 */
internal class PreparedSessionStore(
  storageRoot: File,
  private val clockMillis: () -> Long = System::currentTimeMillis,
  private val pidProvider: () -> Int = Process::myPid,
) {
  private val preparedDir = File(storageRoot, PREPARED_DIR_NAME)

  /**
   * Saves [spans] under a new prepared session ID and returns whether the file is safely on disk.
   *
   * The store writes to a temporary file, calls `sync`, and renames it, so a process death partway
   * through never leaves a partial `.spans` file that [loadSessions] would read.
   */
  fun save(spans: List<Span>): Boolean {
    val sessionId = createSessionId(clockMillis(), pidProvider())
    val temp = File(preparedDir, sessionId + TEMP_SUFFIX)
    try {
      preparedDir.mkdirs()
      FileOutputStream(temp).use { file ->
        val out = DataOutputStream(BufferedOutputStream(file))
        out.writeInt(spans.size)
        for (span in spans) {
          out.writeUTF(span.traceId)
          out.writeUTF(span.spanId)
          out.writeUTF(span.parentSpanId)
          out.writeLong(span.startTime)
          out.writeUTF(span.name)
          out.writeInt(span.attributes.size)
          for ((key, value) in span.attributes) {
            out.writeUTF(key)
            out.writeUTF(value)
          }
        }
        out.flush()
        file.fd.sync()
      }
      if (temp.renameTo(File(preparedDir, sessionId + SPANS_SUFFIX))) return true
    } catch (e: IOException) {
      Log.d(LOG_TAG, "Could not save prepared session $sessionId", e)
    }
    temp.delete()
    return false
  }

  /**
   * Deletes saved sessions that are past the limits or unreadable, and returns the rest, newest
   * first.
   *
   * [createSessionId] puts the fixed-width hex timestamp first, so sorting by name sorts by time.
   */
  fun loadSessions(): List<PreparedSession> {
    val (saved, leftovers) =
      preparedDir.listFiles().orEmpty().partition { it.name.endsWith(SPANS_SUFFIX) }
    leftovers.forEach { it.delete() }

    val oldestAllowed = clockMillis() - MAX_PREPARED_SESSION_AGE_MILLIS
    val newestFirst = saved.sortedByDescending { it.name }
    val kept =
      newestFirst.take(MAX_PREPARED_SESSIONS).filter { file ->
        val createdAtMillis = sessionTimestampMillis(file.name.removeSuffix(SPANS_SUFFIX))
        createdAtMillis != null && createdAtMillis >= oldestAllowed
      }
    newestFirst.filterNot { it in kept }.forEach { it.delete() }

    return kept.mapNotNull { file ->
      val sessionId = file.name.removeSuffix(SPANS_SUFFIX)
      try {
        PreparedSession(sessionId, readSpans(file))
      } catch (e: IOException) {
        Log.d(LOG_TAG, "Could not read ${file.name}, deleting it", e)
        file.delete()
        null
      } catch (e: IllegalArgumentException) {
        Log.d(LOG_TAG, "Invalid prepared file ${file.name}, deleting it", e)
        file.delete()
        null
      }
    }
  }

  /** Deletes a saved session once Telemetry has uploaded it. */
  fun delete(sessionId: String) {
    File(preparedDir, sessionId + SPANS_SUFFIX).delete()
  }

  private fun readSpans(file: File): List<Span> =
    DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
      val spanCount = input.readInt()
      require(spanCount in 0..MmapSize.LARGE.config.maxSpans) { "Invalid span count: $spanCount" }
      List(spanCount) {
        Span(
          traceId = input.readUTF(),
          spanId = input.readUTF(),
          parentSpanId = input.readUTF(),
          startTime = input.readLong(),
          name = input.readUTF(),
          attributes =
            buildMap {
              val attributeCount = input.readInt()
              require(attributeCount in 0..MmapSize.LARGE.config.maxAttributes) {
                "Invalid attribute count: $attributeCount"
              }
              repeat(attributeCount) { put(input.readUTF(), input.readUTF()) }
            },
        )
      }
    }

  /** Spans saved under `prepared/` for an earlier session whose process crashed. */
  class PreparedSession(val sessionId: String, val spans: List<Span>)

  internal companion object {
    private const val LOG_TAG = "PreparedSessionStore"
    private const val PREPARED_DIR_NAME = "prepared"
    private const val SPANS_SUFFIX = ".spans"
    private const val TEMP_SUFFIX = ".tmp"
    private const val MAX_PREPARED_SESSIONS = 4
    private const val MAX_TIMESTAMP_MILLIS = 0xFFFF_FFFF_FFFFL
    private val MAX_PREPARED_SESSION_AGE_MILLIS = TimeUnit.DAYS.toMillis(14)
    private val nextSequence = AtomicInteger(0)

    /** Builds a filename-safe ID formatted as `<12-hex-ms>_<4-hex-seq>_<8-hex-pid>_<uuid>`. */
    fun createSessionId(nowMillis: Long, pid: Int): String {
      val timeHex =
        (nowMillis.coerceAtLeast(0L) and MAX_TIMESTAMP_MILLIS).toString(16).padStart(12, '0')
      val seqHex = (nextSequence.incrementAndGet() and 0xFFFF).toString(16).padStart(4, '0')
      val pidHex = (pid.toLong() and 0xFFFF_FFFFL).toString(16).padStart(8, '0')
      return "${timeHex}_${seqHex}_${pidHex}_${UUID.randomUUID()}"
    }

    private fun sessionTimestampMillis(sessionId: String): Long? {
      val timeHex = sessionId.substringBefore('_', missingDelimiterValue = "")
      if (timeHex.length != 12 || !timeHex.all { it in '0'..'9' || it in 'a'..'f' }) {
        return null
      }
      return timeHex.toLongOrNull(16)
    }
  }
}
