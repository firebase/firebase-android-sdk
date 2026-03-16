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
package com.google.firebase.appdistribution.gradle.models

import com.google.firebase.appdistribution.gradle.FixtureUtils
import com.google.gson.Gson
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class WrappedErrorResponseTest {
  @Test
  fun deserialization_succeedsWithFullDetails() {
    val failedPreconditionResponse =
      FixtureUtils.getFixtureAsString("failed_precondition_response.json")
    val (error) = Gson().fromJson(failedPreconditionResponse, WrappedErrorResponse::class.java)
    assertNotNull(error)
    assertEquals("OOPS!", error.message)
    assertEquals(400, error.code.toLong())
    assertEquals("FAILED_PRECONDITION", error.status)
  }

  @Test
  fun deserialize_succeedsWithMissingFields() {
    val missingFieldsResponse =
      FixtureUtils.getFixtureAsString("missing_fields_error_response.json")
    val (error) = Gson().fromJson(missingFieldsResponse, WrappedErrorResponse::class.java)
    assertNotNull(error)
    assertEquals("OOPS!", error.message)
    assertEquals(0, error.code.toLong())
    assertNull(error.status)
  }
}
