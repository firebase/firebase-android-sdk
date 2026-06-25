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

object ResultsBucketFormatter {
  private val BUCKET_NAME_REGEX = "^[a-z0-9_.-]+$".toRegex()

  /**
   * Cleans, validates, and formats a raw results bucket name input.
   *
   * @param projectNumber The project number to format with.
   * @param bucketInput The raw bucket name input.
   * @return The formatted bucket resource name.
   * @throws AppDistributionException if the bucket name is invalid or empty.
   */
  fun formatResultsBucket(projectNumber: String, bucketInput: String): String {
    val cleanBucketName = bucketInput.trim().removePrefix("gs://")
    if (cleanBucketName.isEmpty() || !cleanBucketName.matches(BUCKET_NAME_REGEX)) {
      throw AppDistributionException(
        AppDistributionException.Reason.INVALID_RESULTS_BUCKET,
        extraInformation = bucketInput
      )
    }
    return "projects/$projectNumber/buckets/$cleanBucketName"
  }
}
