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

package com.google.firebase.appdistribution.gradle

import com.google.firebase.appdistribution.gradle.models.uploadstatus.UploadStatus
import com.google.firebase.appdistribution.gradle.models.uploadstatus.UploadStatusResponse
import org.gradle.api.logging.Logging

class ReleaseLookup(
  private val apiService: ApiService,
  private val threadSleeper: ThreadSleeper = ThreadSleeper(),
  private val maxPollingRetries: Int = MAX_POLLING_RETRIES
) {
  internal fun pollForRelease(operationName: String, binaryType: BinaryType): UploadStatusResponse {
    for (numRetries in 0 until maxPollingRetries) {
      val response = apiService.getUploadStatus(operationName, binaryType)
      if (!response.isDone) {
        try {
          threadSleeper.sleep(POLLING_INTERVAL)
        } catch (ie: InterruptedException) {
          throw RuntimeException(
            "App Distribution ran into an error while looking up the release",
            ie
          )
        }
      } else {
        response.response?.also {
          when (it.status) {
            UploadStatus.RELEASE_CREATED -> {
              logger.info(
                "Uploaded new release {} ({}) successfully",
                it.release.displayVersion,
                it.release.buildVersion
              )
            }
            UploadStatus.RELEASE_UNMODIFIED -> {
              // This scenario is usually unexpected, so log at WARN
              logger.warn(
                "Re-uploaded already existing release {} ({}) successfully",
                it.release.displayVersion,
                it.release.buildVersion
              )
            }
            UploadStatus.RELEASE_UPDATED, // This is only expected for iOS
            UploadStatus.UPLOAD_RELEASE_RESULT_UNSPECIFIED,
            null -> {
              logger.info(
                "Uploaded release {} ({}) successfully",
                it.release.displayVersion,
                it.release.buildVersion
              )
            }
          }
        }

        return response
      }
    }

    // If we reach this point, we have exceeded the timeout for polling
    throw AppDistributionException(
      reason = AppDistributionException.Reason.GET_RELEASE_TIMEOUT,
      extraInformation =
        "It took longer than expected to process your $binaryType, please try again",
    )
  }

  companion object {
    private const val POLLING_INTERVAL = 5000L
    private const val MAX_POLLING_RETRIES = 60
    private val logger = Logging.getLogger(this::class.java)
  }
}
