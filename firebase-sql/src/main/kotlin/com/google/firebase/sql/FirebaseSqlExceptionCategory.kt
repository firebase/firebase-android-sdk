/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.sql

/**
 * Where a [FirebaseSqlException] comes from, in [FirebaseSqlException.category]. See
 * [FirebaseSqlException] for examples.
 *
 * Later versions can add categories, so give a `when` expression over the category an `else`
 * branch.
 */
public class FirebaseSqlExceptionCategory private constructor(internal val rawValue: String) {
  /** Returns the category as text, for example `sql-postgrest`. */
  override fun toString(): String = rawValue

  public companion object {
    /**
     * PostgREST or PostgreSQL rejected the request. The error has the HTTP
     * [FirebaseSqlException.status], and its [FirebaseSqlException.code] is a PostgREST code, for
     * example `PGRST116`, or a PostgreSQL SQLSTATE, for example `23505`.
     */
    public val POSTGREST: FirebaseSqlExceptionCategory =
      FirebaseSqlExceptionCategory("sql-postgrest")

    /**
     * The Firebase SQL service in front of PostgREST rejected the request, for example because of a
     * token or a quota, or an HTTP error response did not come from PostgREST. The error has the
     * HTTP [FirebaseSqlException.status].
     */
    public val PLATFORM: FirebaseSqlExceptionCategory = FirebaseSqlExceptionCategory("sql-platform")

    /**
     * The SDK found the error itself. In a successful response, for example a `dryRun()` whose
     * rollback the server did not confirm, the error has the HTTP [FirebaseSqlException.status] of
     * the response, and so does the error of a response that the SDK could not read, whose cause is
     * the error of reading it.
     */
    public val CLIENT: FirebaseSqlExceptionCategory = FirebaseSqlExceptionCategory("sql-client")
  }
}
