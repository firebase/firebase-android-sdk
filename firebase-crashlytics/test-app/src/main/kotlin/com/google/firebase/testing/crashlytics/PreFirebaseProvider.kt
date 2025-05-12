/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.testing.crashlytics

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import java.lang.Thread.UncaughtExceptionHandler
import kotlin.system.exitProcess

class PreFirebaseProvider : ContentProvider(), UncaughtExceptionHandler {
  private var defaultUncaughtExceptionHandler: UncaughtExceptionHandler? = null

  companion object {
    var expectedMessage: String? = null
    private var defaultUncaughtExceptionHandler: UncaughtExceptionHandler? = null

    fun initialize() {
      if (
        defaultUncaughtExceptionHandler == null ||
          defaultUncaughtExceptionHandler !is PreFirebaseExceptionHandler
      ) {
        defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(
          PreFirebaseExceptionHandler(defaultUncaughtExceptionHandler)
        )
      }
    }

    private class PreFirebaseExceptionHandler(private val delegate: UncaughtExceptionHandler?) :
      UncaughtExceptionHandler {
      override fun uncaughtException(thread: Thread, throwable: Throwable) {
        if (expectedMessage != null && throwable.message?.contains(expectedMessage!!) == true) {
          exitProcess(0)
        } else {
          delegate?.uncaughtException(thread, throwable)
        }
      }
    }
  }

  override fun onCreate(): Boolean {
    initialize()
    return false
  }

  /* Returns if the given exception contains the expectedMessage in its message. */
  private fun isExpected(throwable: Throwable): Boolean =
    expectedMessage?.run { throwable.message?.contains(this) } ?: false

  override fun uncaughtException(thread: Thread, throwable: Throwable) {
    if (isExpected(throwable)) {
      // Exit cleanly
      exitProcess(0)
    } else {
      // Propagate up to the default exception handler
      defaultUncaughtExceptionHandler?.uncaughtException(thread, throwable)
    }
  }

  override fun query(
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?
  ): Cursor? = null

  override fun getType(uri: Uri): String? = null

  override fun insert(uri: Uri, values: ContentValues?): Uri? = null

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<out String>?
  ): Int = 0
}
