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

import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests [FirebaseSqlException].
 *
 * Robolectric is needed because the constructor of [FirebaseException] checks its message with
 * `android.text.TextUtils`.
 */
@RunWith(RobolectricTestRunner::class)
class FirebaseSqlExceptionTest {

  @Test
  fun anExceptionIsAFirebaseExceptionWithoutResponseByDefault() {
    val withoutCause = FirebaseSqlException("Test message", FirebaseSqlExceptionCategory.CLIENT)
    val withCause =
      FirebaseSqlException(
        "Test message",
        FirebaseSqlExceptionCategory.CLIENT,
        cause = IllegalArgumentException("cause")
      )

    // Callers that handle the errors of every Firebase SDK in one place catch FirebaseException.
    assertThat(withoutCause).isInstanceOf(FirebaseException::class.java)
    assertThat(withoutCause.message).isEqualTo("Test message")
    assertThat(withoutCause.cause).isNull()
    assertThat(withoutCause.category).isEqualTo(FirebaseSqlExceptionCategory.CLIENT)
    assertThat(withoutCause.code).isNull()
    assertThat(withoutCause.status).isNull()
    assertThat(withoutCause.details).isNull()
    assertThat(withoutCause.hint).isNull()
    assertThat(withCause.message).isEqualTo("Test message")
    assertThat(withCause.cause).isInstanceOf(IllegalArgumentException::class.java)
    assertThat(withCause.category).isEqualTo(FirebaseSqlExceptionCategory.CLIENT)
    assertThat(withCause.code).isNull()
  }

  @Test
  fun anExceptionCanHaveACategoryAndACode() {
    val cause = IllegalStateException("cause")
    val exception =
      FirebaseSqlException("Test message", FirebaseSqlExceptionCategory.PLATFORM, "UNAVAILABLE")
    val exceptionWithCause =
      FirebaseSqlException(
        "Test message",
        FirebaseSqlExceptionCategory.PLATFORM,
        "UNAVAILABLE",
        cause = cause
      )

    assertThat(exception.category).isEqualTo(FirebaseSqlExceptionCategory.PLATFORM)
    assertThat(exception.code).isEqualTo("UNAVAILABLE")
    assertThat(exception.cause).isNull()
    assertThat(exceptionWithCause.category).isEqualTo(FirebaseSqlExceptionCategory.PLATFORM)
    assertThat(exceptionWithCause.code).isEqualTo("UNAVAILABLE")
    assertThat(exceptionWithCause.cause).isSameInstanceAs(cause)
  }

  @Test
  fun anErrorResponseKeepsTheStatusAndTheFieldsOfTheError() {
    val exception =
      FirebaseSqlException(
        message = "Postgrest error",
        category = FirebaseSqlExceptionCategory.POSTGREST,
        code = "PGRST205",
        status = 404,
        details = "Table not found",
        hint = "Check schema"
      )

    assertThat(exception.message).isEqualTo("Postgrest error")
    assertThat(exception.category).isEqualTo(FirebaseSqlExceptionCategory.POSTGREST)
    assertThat(exception.code).isEqualTo("PGRST205")
    assertThat(exception.status).isEqualTo(404)
    assertThat(exception.details).isEqualTo("Table not found")
    assertThat(exception.hint).isEqualTo("Check schema")
  }

  @Test
  fun anErrorResponseCanHaveOnlyAStatus() {
    // A proxy can answer with a body that is not a PostgREST error, which has no code.
    val exception =
      FirebaseSqlException(
        message = "HTTP 502: Bad Gateway",
        category = FirebaseSqlExceptionCategory.PLATFORM,
        status = 502
      )

    assertThat(exception.status).isEqualTo(502)
    assertThat(exception.code).isNull()
    assertThat(exception.details).isNull()
    assertThat(exception.hint).isNull()
    assertThat(exception.cause).isNull()
  }

  @Test
  fun anErrorResponseCanHaveACause() {
    val cause = IllegalStateException("cause")
    val exception =
      FirebaseSqlException(
        message = "Postgrest error",
        category = FirebaseSqlExceptionCategory.POSTGREST,
        code = "XX000",
        status = 500,
        cause = cause
      )

    assertThat(exception.status).isEqualTo(500)
    assertThat(exception.code).isEqualTo("XX000")
    assertThat(exception.cause).isSameInstanceAs(cause)
  }

  @Test
  fun appsCanCreateAReservedHeaderException() {
    val exception = ReservedHeaderException("Authorization", "Authorization is reserved")

    assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
    assertThat(exception.headerName).isEqualTo("Authorization")
    assertThat(exception.message).isEqualTo("Authorization is reserved")
  }
}
