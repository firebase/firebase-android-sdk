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

import com.google.api.client.http.HttpResponseException
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.APP_NOT_ONBOARDED_ERROR
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.Companion.processingBinaryError
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.Companion.uploadBinaryError
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.GET_APP_ERROR
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.STARTING_TEST_FAILED
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.UPLOAD_RELEASE_NOTES_ERROR
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.UPLOAD_TESTERS_ERROR
import com.google.firebase.appdistribution.gradle.models.AabInfo
import com.google.firebase.appdistribution.gradle.models.AabState
import com.google.firebase.appdistribution.gradle.models.uploadstatus.Release
import java.io.IOException
import org.gradle.api.logging.Logging

class FirebaseAppDistributionUpload
internal constructor(
  private val options: UploadDistributionOptions,
  private val apiService: ApiService,
  private val uploadService: UploadService,
  private val releaseLookup: ReleaseLookup = ReleaseLookup(apiService),
  private val testLookup: TestLookup = TestLookup()
) {
  fun uploadDistribution(): Boolean {
    val appName = getAppNameFromAppId(options.appId)

    // Get aab info for this app if the distribution is an app bundle
    var aabInfo: AabInfo? = null
    if (options.binaryType == BinaryType.AAB) {
      aabInfo = getAabInfo(appName)

      // If we're uploading an AAB and the app is not enabled for AABs, throw an error
      // If we cannot determine if this app is enabled for AABs, we assume that it is
      if (
        aabInfo.aabState != AabState.INTEGRATED &&
          aabInfo.aabState != AabState.AAB_STATE_UNAVAILABLE
      ) {
        throw AppDistributionException.forAabState(aabInfo.aabState)
      }
    }
    logger.info("Uploading the {}.", options.binaryType)
    // Upload the distribution
    val operationName =
      try {
        uploadService.uploadDistribution(appName, options.binary)
      } catch (e: HttpResponseException) {
        if (e.statusCode == 404) {
          throw AppDistributionException(APP_NOT_ONBOARDED_ERROR)
        } else {
          throw AppDistributionException.fromHttpResponseException(
            uploadBinaryError(options.binaryType),
            e
          )
        }
      } catch (e: IOException) {
        throw AppDistributionException.fromIoException(uploadBinaryError(options.binaryType), e)
      }

    var release: Release? = null
    var success = operationName != null
    // Get the release for the uploaded distribution
    if (operationName != null) {
      val releaseUploadResponse = releaseLookup.pollForRelease(operationName, options.binaryType)
      if (releaseUploadResponse.isDone && releaseUploadResponse.response != null) {
        release = releaseUploadResponse.response.release
      } else if (releaseUploadResponse.isDone && releaseUploadResponse.error != null) {
        throw AppDistributionException(
          processingBinaryError(options.binaryType),
          extraInformation = releaseUploadResponse.error.message
        )
      }
    }

    if (operationName != null && release != null) {
      aabInfo?.let { maybePrintAabCertificate(appName, it) }

      // Upload release notes
      val releaseNotes = options.releaseNotes
      if (!releaseNotes.isNullOrEmpty()) {
        success =
          try {
            apiService.createReleaseNotes(release.name, releaseNotes)
          } catch (e: HttpResponseException) {
            throw AppDistributionException.fromHttpResponseException(UPLOAD_RELEASE_NOTES_ERROR, e)
          } catch (e: IOException) {
            throw AppDistributionException.fromIoException(UPLOAD_RELEASE_NOTES_ERROR, e)
          }
      } else {
        logger.info("No release notes passed in. Skipping this step.")
      }

      // If testCases is empty, perform a single standard Robo test where testCase is equal to null
      val testCaseIds = options.testCases.ifEmpty { listOf(null) }
      val testDevices = options.testDevices

      if (testCaseIds.filterNotNull().isNotEmpty() && testDevices.isEmpty()) {
        logger.warn(
          "Test cases parameter is set but there is no value for test devices. Skipping automated tests."
        )
      }

      // Run automated tests
      if (testDevices.isNotEmpty()) {
        val releaseTestNames =
          testCaseIds
            .mapNotNull { testCaseId ->
              try {
                val testCase = testCaseId?.let { "${appName}/testCases/$it" }
                apiService
                  .testRelease(release.name, testDevices, options.testLoginCredential, testCase)
                  ?.name
              } catch (e: HttpResponseException) {
                throw AppDistributionException.fromHttpResponseException(STARTING_TEST_FAILED, e)
              } catch (e: IOException) {
                throw AppDistributionException.fromIoException(STARTING_TEST_FAILED, e)
              }
            }
            .toSet()

        if (releaseTestNames.isNotEmpty() && !options.testNonBlocking) {
          testLookup.pollForReleaseTests(apiService, releaseTestNames)
        }
      }

      // Enable access for testers and groups

      if (options.testers.isNotEmpty() || options.groups.isNotEmpty()) {
        success =
          try {
            apiService.distributeRelease(release.name, options.testers, options.groups)
          } catch (e: HttpResponseException) {
            throw AppDistributionException.fromHttpResponseException(UPLOAD_TESTERS_ERROR, e)
          } catch (e: IOException) {
            throw AppDistributionException.fromIoException(UPLOAD_TESTERS_ERROR, e)
          }
      } else {
        logger.warn("No testers or groups passed in. Skipping this step.")
      }

      logger.quiet("View this release in the Firebase console: {}", release.firebaseConsoleUri)
      logger.quiet("Share this release with testers who have access: {}", release.testingUri)
      logger.quiet(
        "Download the release binary (link expires in 1 hour): {}",
        release.binaryDownloadUri
      )
    }
    return success
  }

  private fun getAabInfo(appName: String): AabInfo {
    return try {
      apiService.getAabInfo(appName)
    } catch (e: HttpResponseException) {
      if (e.statusCode == 404) {
        throw AppDistributionException(APP_NOT_ONBOARDED_ERROR)
      } else {
        throw AppDistributionException.fromHttpResponseException(GET_APP_ERROR, e)
      }
    } catch (e: IOException) {
      throw AppDistributionException.fromIoException(GET_APP_ERROR, e)
    }
  }

  private fun maybePrintAabCertificate(appName: String, aabInfo: AabInfo) {
    // If this is an app bundle and the certificate was originally blank fetch the updated
    // certificate and print
    if (options.binaryType == BinaryType.AAB && aabInfo.aabCertificate == null) {
      val updatedAabInfo = getAabInfo(appName)
      if (updatedAabInfo.aabCertificate != null) {
        logger.quiet(
          """
          After you upload an AAB for the first time, App Distribution generates a new test 
          certificate. All AAB uploads are re-signed with this test certificate. Use the 
          certificate fingerprints below to register your app signing key with API providers, such 
          as Google Sign-In and Google Maps.
        """
            .trimIndent()
        )
        logger.quiet(
          "MD5 certificate fingerprint: {}",
          updatedAabInfo.aabCertificate.certificateHashMd5
        )
        logger.quiet(
          "SHA-1 certificate fingerprint: {}",
          updatedAabInfo.aabCertificate.certificateHashSha1
        )
        logger.quiet(
          "SHA-256 certificate fingerprint: {}",
          updatedAabInfo.aabCertificate.certificateHashSha256
        )
      }
    }
  }

  private fun getAppNameFromAppId(appId: String): String {
    // Parse the project number out from the app id
    val projectNumber = appId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
    return "projects/$projectNumber/apps/$appId"
  }

  companion object {
    private val logger = Logging.getLogger(this::class.java)
  }
}
