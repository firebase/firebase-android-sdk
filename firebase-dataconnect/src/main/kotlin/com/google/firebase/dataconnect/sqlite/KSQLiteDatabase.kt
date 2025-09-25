/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.sqlite

import android.database.sqlite.SQLiteDatabase

/** A thin wrapper around [SQLiteDatabase] that provides a more Kotlin-friendly API. */
internal class KSQLiteDatabase(private val db: SQLiteDatabase) {

  /**
   * Retrieves the value of the user-version integer at offset 60 in the database header.
   *
   * According to the
   * [PRAGMA schema.user_version](https://www.sqlite.org/pragma.html#pragma_user_version)
   * documentation, the user-version is an integer that is available to applications to use however
   * they want. SQLite makes no use of the user-version itself.
   *
   * If the value has never been explicitly set then this method returns `0` (zero).
   *
   * Use [setUserVersion] to set the value.
   */
  fun getUserVersion(): Int = getIntPragmaValue("user_version")

  /**
   * Sets the value of the user-version integer at offset 60 in the database header.
   *
   * Although not strictly required, it is recommended to set a value that is strictly greater than
   * zero to avoid confusing an explicit value of zero with the value having never been set.
   *
   * See [getUserVersion] for details.
   */
  fun setUserVersion(newUserVersion: Int) {
    setIntPragmaValue("user_version", newUserVersion)
  }

  /**
   * Retrieves the value of the "Application ID" integer at offset 68 in the database header.
   *
   * According to the
   * [PRAGMA schema.application_id](https://www.sqlite.org/pragma.html#pragma_application_id)
   * documentation, applications that use SQLite as their application file-format should set the
   * Application ID integer to a unique integer so that utilities such as `file` can determine the
   * specific file type rather than just reporting "SQLite3 Database".
   *
   * If the value has never been explicitly set then this method returns `0` (zero).
   *
   * Use [setApplicationId] to set the value.
   */
  fun getApplicationId(): Int = getIntPragmaValue("application_id")

  /**
   * Sets the value of the "Application ID" integer at offset 68 in the database header.
   *
   * Although not strictly required, it is recommended to set a value that is not equal to zero to
   * avoid confusing an explicit value of zero with the value having never been set.
   *
   * See [getApplicationId] for details.
   */
  fun setApplicationId(newApplicationId: Int) {
    setIntPragmaValue("application_id", newApplicationId)
  }

  private fun getIntPragmaValue(pragma: String): Int =
    db.rawQuery("PRAGMA $pragma", null).use { cursor ->
      cursor.moveToNext()
      cursor.getInt(0)
    }

  private fun setIntPragmaValue(pragma: String, newValue: Int) {
    db.execSQL("PRAGMA $pragma = $newValue")
  }
}
