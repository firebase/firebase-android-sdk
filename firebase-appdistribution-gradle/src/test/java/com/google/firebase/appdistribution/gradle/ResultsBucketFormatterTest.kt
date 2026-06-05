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

package com.google.firebase.appdistribution.gradle

import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultsBucketFormatterTest {

  @Test
  fun formatResultsBucket_withGsPrefix_formatsCorrectly() {
    val result = ResultsBucketFormatter.formatResultsBucket("123", "gs://my-bucket")
    assertEquals("projects/123/buckets/my-bucket", result)
  }

  @Test
  fun formatResultsBucket_withoutGsPrefix_formatsCorrectly() {
    val result = ResultsBucketFormatter.formatResultsBucket("123", "my-bucket")
    assertEquals("projects/123/buckets/my-bucket", result)
  }

  @Test
  fun formatResultsBucket_withTrailingSlashes_throwsException() {
    val exception =
      assertFailsWith(AppDistributionException::class) {
        ResultsBucketFormatter.formatResultsBucket("123", "gs://my-bucket/")
      }
    assertEquals(AppDistributionException.Reason.INVALID_RESULTS_BUCKET, exception.reason)
    assertContains(exception.message!!, "gs://my-bucket/")
  }

  @Test
  fun formatResultsBucket_withInvalidCharacters_throwsException() {
    val invalidNames = listOf("gs://My-Bucket", "my bucket", "gs://my-bucket!", "my_bucket?")
    for (name in invalidNames) {
      val exception =
        assertFailsWith(AppDistributionException::class) {
          ResultsBucketFormatter.formatResultsBucket("123", name)
        }
      assertEquals(AppDistributionException.Reason.INVALID_RESULTS_BUCKET, exception.reason)
      assertContains(exception.message!!, name)
    }
  }

  @Test
  fun formatResultsBucket_withEmptyBlankInputs_throwsException() {
    val invalidInputs = listOf("", "   ", "gs://")
    for (input in invalidInputs) {
      val exception =
        assertFailsWith(AppDistributionException::class) {
          ResultsBucketFormatter.formatResultsBucket("123", input)
        }
      assertEquals(AppDistributionException.Reason.INVALID_RESULTS_BUCKET, exception.reason)
      assertContains(exception.message!!, input)
    }
  }
}
