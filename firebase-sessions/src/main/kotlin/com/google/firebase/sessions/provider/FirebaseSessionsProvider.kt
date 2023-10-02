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

package com.google.firebase.sessions.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Provider to detect cold starts based on the fact the [ContentProvider] will be created exactly
 * once per app instance, regardless of multiple processes.
 */
internal class FirebaseSessionsProvider : ContentProvider() {
  override fun onCreate(): Boolean {
    coldStart = AtomicBoolean(true)
    return false
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
    selectionArgs: Array<out String>?,
  ): Int = 0

  internal companion object {
    private lateinit var coldStart: AtomicBoolean

    /** Returns true exactly once per app instance, false otherwise. */
    fun isColdStart(): Boolean = ::coldStart.isInitialized && coldStart.getAndSet(false)
  }
}
