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

import android.util.Log
import java.io.File
import java.io.IOException

/** Reads the spans saved in a previous launch's span file, leaving that file unchanged. */
// TODO(jakeouellette): PersistenceContext currently owns the firebase-telemetry-persistence JNI
// bindings, and initialize_span_data erases a file on open, so this opens a temporary copy via
// PersistenceContext delegates. Once the C++ library supports read-only open, read in place and
// clean up PersistenceContext's native delegates and unused onRecovery path.
internal object MmapSpanLoader {
  private const val LOG_TAG = "MmapSpanLoader"

  /**
   * Returns the spans in [file], or an empty list if [file] doesn't exist. Returns `null` if the
   * loader can't read [file] right now, for example because the disk is full.
   */
  fun readSpans(file: File, mmapSize: MmapSize): List<Span>? {
    if (!file.exists()) return emptyList()
    val copy = File(file.parentFile, "${file.name}.read")
    try {
      file.copyTo(copy, overwrite = true)
      val contextPtr = PersistenceContext.openNative(copy.absolutePath, mmapSize)
      if (contextPtr == 0L) return null
      try {
        return PersistenceContext.recoverNative(contextPtr).toList()
      } finally {
        PersistenceContext.closeNative(contextPtr)
      }
    } catch (e: IOException) {
      Log.d(LOG_TAG, "Could not read spans from $file", e)
      return null
    } finally {
      copy.delete()
    }
  }
}
