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
import com.google.firebase.appdistribution.gradle.models.AabState
import com.google.firebase.appdistribution.gradle.models.WrappedErrorResponse
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.IOException
import org.gradle.api.logging.Logging

/**
 * The AppDistributionException encapsulates the runtime exceptions thrown by the
 * firebase-app-distro JAR.
 */
class AppDistributionException
@JvmOverloads
constructor(
  val reason: Reason,
  cause: Throwable? = null,
  extraInformation: String? = null,
) : RuntimeException(formatMessage(reason, extraInformation), cause) {
  enum class Reason(val message: String) {
    MISSING_CREDENTIALS(
      "Missing credentials. Please check that a refresh token was set or service credentials were passed in and try again"
    ),
    APK_NOT_FOUND(
      "Could not find the APK. Make sure you build first by running ./gradlew assemble[Variant], or set the artifactPath parameter to point to your APK"
    ),
    AAB_NOT_FOUND(
      "Could not find the AAB. Make sure you build first by running ./gradlew bundle[Variant], or set the artifactPath parameter to point to your AAB"
    ),
    MISSING_PROJECT_NUMBER(
      "Missing project number. Please check that it was passed in and try again"
    ),
    MISSING_APP_ID("Missing app id. Please check that it was passed in and try again"),
    INVALID_APP_ID("Invalid app id. Please check that the correct id was passed in and try again"),
    MISSING_TESTER_EMAILS(
      "Missing tester emails. Please check that they were passed in and try again"
    ),
    SERVICE_CREDENTIALS_NOT_FOUND(
      "Service credentials file does not exist. Please check the service credentials path and try again"
    ),
    UPLOAD_RELEASE_NOTES_ERROR(
      "App Distribution halted because it had a problem uploading release notes"
    ),
    UPLOAD_TESTERS_ERROR("App Distribution halted because it had a problem adding testers/groups"),
    UPLOAD_AAB_ERROR("App Distribution halted because it had a problem uploading the AAB"),
    UPLOAD_APK_ERROR("App Distribution halted because it had a problem uploading the APK"),
    AAB_PROCESSING_ERROR("App Distribution failed to process the AAB"),
    APK_PROCESSING_ERROR("App Distribution failed to process the APK"),
    GET_RELEASE_TIMEOUT("App Distribution failed to fetch release information"),
    REFRESH_TOKEN_ERROR("Could not generate credentials from the refresh token specified"),
    GET_APP_ERROR("App Distribution failed to fetch app information"),
    APP_NOT_ONBOARDED_ERROR("App Distribution not onboarded"),
    AAB_APP_ERROR_APP_NOT_PUBLISHED("This app is not published in the Google Play console."),
    AAB_APP_ERROR_NO_APP_WITH_GIVEN_BUNDLE_ID_IN_PLAY_ACCOUNT(
      "App with matching package name does not exist in Google Play."
    ),
    AAB_APP_ERROR_PLAY_ACCOUNT_NOT_LINKED("This project is not linked to a Google Play account."),
    AAB_APP_ERROR_TOS_NOT_ACCEPTED(
      "You must accept the Play Internal App Sharing (IAS) terms to upload AABs."
    ),
    ADD_TESTERS_ERROR("App Distribution failed to add testers"),
    REMOVE_TESTERS_ERROR("App Distribution failed to remove testers"),
    TOO_MANY_TESTER_EMAILS(
      "App Distribution could not perform the operation, too many tester emails"
    ),
    TEST_LOGIN_CREDENTIAL_MISMATCH(
      "Username and password for automated tests need to be specified together."
    ),
    TEST_LOGIN_CREDENTIAL_RESOURCE_MISMATCH(
      "Username and password resource names for automated tests need to be specified together."
    ),
    TEST_LOGIN_CREDENTIAL_MISSING("Must specify username and password"),
    STARTING_TEST_FAILED("App Distribution could not perform the automated test."),
    GET_TEST_RETRIEVAL_FAILURE("App Distribution could not retrieve the test."),
    GET_TEST_TIMEOUT("It took longer than expected to process your test, please try again."),
    GET_TEST_FAILED("Automated test failed or was inconclusive."),
    TEST_CASE_NOT_FOUND("Could not find test case with ID"),
    TEST_CASE_WITH_LOGIN_RESOURCES(
      "Username or password resources for automatic login cannot be specified when test cases IDs are set"
    ),
    APPLICATION_DEFAULT_CREDENTIALS_NOT_FOUND(
      "Unable to create application default credentials in current environment."
    );

    companion object {
      fun uploadBinaryError(binaryType: BinaryType) =
        if (binaryType == BinaryType.AAB) UPLOAD_AAB_ERROR else UPLOAD_APK_ERROR

      fun processingBinaryError(binaryType: BinaryType) =
        if (binaryType == BinaryType.AAB) AAB_PROCESSING_ERROR else APK_PROCESSING_ERROR

      fun binaryNotFoundError(binaryType: BinaryType) =
        if (binaryType == BinaryType.AAB) AAB_NOT_FOUND else APK_NOT_FOUND
    }
  }

  private constructor(
    reason: Reason,
    subReason: Reason
  ) : this(reason, extraInformation = subReason.message)

  companion object {
    private val logger = Logging.getLogger(this::class.java)

    // Exposed for testing
    fun formatMessage(reason: Reason, extraInformation: String?): String {
      if (extraInformation == null) {
        return reason.message
      }

      return "${reason.message}: $extraInformation"
    }

    private fun getParsedMessageOrStatusMessage(exception: HttpResponseException): String? {
      var message = exception.statusMessage
      try {
        val response = Gson().fromJson(exception.content, WrappedErrorResponse::class.java)
        if (response?.error != null) {
          message = response.error.message
        }
      } catch (e: JsonSyntaxException) {
        logger.warn("Failed to parse error response: {}", exception.content)
      }
      return message
    }

    fun fromIoException(reason: Reason, cause: IOException) =
      AppDistributionException(reason, cause, cause.message)

    fun fromHttpResponseException(reason: Reason, cause: HttpResponseException) =
      AppDistributionException(
        reason,
        cause,
        "[${cause.statusCode}] ${getParsedMessageOrStatusMessage(cause)}"
      )

    fun forAabState(state: AabState?): AppDistributionException {
      if (state == null) {
        return AppDistributionException(Reason.AAB_PROCESSING_ERROR)
      }

      val reason =
        when (state) {
          AabState.APP_NOT_PUBLISHED -> Reason.AAB_APP_ERROR_APP_NOT_PUBLISHED
          AabState.NO_APP_WITH_GIVEN_BUNDLE_ID_IN_PLAY_ACCOUNT ->
            Reason.AAB_APP_ERROR_NO_APP_WITH_GIVEN_BUNDLE_ID_IN_PLAY_ACCOUNT
          AabState.PLAY_ACCOUNT_NOT_LINKED -> Reason.AAB_APP_ERROR_PLAY_ACCOUNT_NOT_LINKED
          AabState.PLAY_IAS_TERMS_NOT_ACCEPTED -> Reason.AAB_APP_ERROR_TOS_NOT_ACCEPTED
          else -> return AppDistributionException(Reason.AAB_PROCESSING_ERROR)
        }
      return AppDistributionException(Reason.AAB_PROCESSING_ERROR, reason)
    }
  }
}
