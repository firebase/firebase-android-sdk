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

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpTransport
import com.google.common.base.Splitter
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.Companion.binaryNotFoundError
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.INVALID_APP_ID
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.MISSING_APP_ID
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.TEST_CASE_WITH_LOGIN_RESOURCES
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.TEST_LOGIN_CREDENTIAL_MISMATCH
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.TEST_LOGIN_CREDENTIAL_MISSING
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.TEST_LOGIN_CREDENTIAL_RESOURCE_MISMATCH
import com.google.firebase.appdistribution.gradle.OptionsUtils.getValueFromStringOrFile
import com.google.firebase.appdistribution.gradle.OptionsUtils.splitCommaOrNewlineSeparatedString
import com.google.firebase.appdistribution.gradle.OptionsUtils.splitSemicolonOrNewlineSeparatedString
import com.google.firebase.appdistribution.gradle.models.LoginCredential
import com.google.firebase.appdistribution.gradle.models.LoginCredentialFieldHints
import com.google.firebase.appdistribution.gradle.models.TestDevice
import java.util.regex.Pattern

/**
 * FirebaseAppDistributionOptions is a data class that stores the options that will control the rest
 * of the program. This includes things like the distribution path, credentials, release notes,
 * testers, etc. This class is immutable once created and provides a builder [Builder] and
 * [buildUploadDistributionOptions] to create new instances.
 */
class UploadDistributionOptions
internal constructor(
  val appId: String,
  val debug: Boolean = false,
  val testNonBlocking: Boolean = false,
  binaryPath: String,
  transport: HttpTransport = GoogleNetHttpTransport.newTrustedTransport(),
  appDistributionEnvironment: AppDistributionEnvironment = AppDistributionEnvironmentImpl(),
  serviceCredentialsFile: String? = null,
  releaseNotesValue: String? = null,
  releaseNotesPath: String? = null,
  testersValue: String? = null,
  testersPath: String? = null,
  groupsValue: String? = null,
  groupsPath: String? = null,
  testDevicesValue: String? = null,
  testDevicesPath: String? = null,
  testUsername: String? = null,
  testPassword: String? = null,
  testPasswordPath: String? = null,
  testUsernameResource: String? = null,
  testPasswordResource: String? = null,
  testCasesValue: String? = null,
  testCasesPath: String? = null,
) {
  val binary = getBinary(binaryPath)
  val releaseNotes: String? = getValueFromStringOrFile(releaseNotesValue, releaseNotesPath)
  val testers = extractValues(testersValue, testersPath)
  val groups = extractValues(groupsValue, groupsPath)
  val testDevices = extractTestDevices(testDevicesValue, testDevicesPath)
  val credential: Credential? =
    CredentialsRetriever(transport, appDistributionEnvironment)
      .getAuthCredential(serviceCredentialsFile)
  val testCases = extractValues(testCasesValue, testCasesPath)

  val testLoginCredential =
    buildLoginCredential(
      username = testUsername,
      passwordValue = testPassword,
      passwordPath = testPasswordPath,
      usernameResource = testUsernameResource,
      passwordResource = testPasswordResource
    )
  val binaryType: BinaryType = BinaryType.fromPath(binary.name)

  init {
    validateAppId()
    validateTestArguments()
  }

  private fun validateTestArguments() {
    if (testCases.isNotEmpty() && testLoginCredential?.fieldHints != null) {
      throw AppDistributionException(TEST_CASE_WITH_LOGIN_RESOURCES)
    }
  }

  private fun validateAppId() {
    if (appId.isEmpty()) {
      throw AppDistributionException(MISSING_APP_ID)
    }
    if (!APP_ID_PATTERN.matcher(appId).matches()) {
      throw AppDistributionException(INVALID_APP_ID)
    }
  }

  private fun getBinary(binaryPath: String) =
    OptionsUtils.ensureFileExists(binaryPath, binaryNotFoundError(BinaryType.fromPath(binaryPath)))

  private fun extractValues(value: String?, valuePath: String?): List<String> {
    val values = getValueFromStringOrFile(value, valuePath)
    return splitCommaOrNewlineSeparatedString(values)
  }

  private fun extractTestDevices(testDevices: String?, testDevicesFile: String?): List<TestDevice> {
    val value = getValueFromStringOrFile(testDevices, testDevicesFile)
    return splitSemicolonOrNewlineSeparatedString(value).map { s: String -> parseTestDevice(s) }
  }

  private fun buildLoginCredential(
    username: String?,
    passwordValue: String?,
    passwordPath: String?,
    usernameResource: String?,
    passwordResource: String?
  ): LoginCredential? {
    val password = extractPassword(passwordValue, passwordPath)
    if (
      username == null && password == null && usernameResource == null && passwordResource == null
    ) {
      return null
    }

    // validate hydrated login credentials
    if (isPresenceMismatched(username, password)) {
      throw AppDistributionException(TEST_LOGIN_CREDENTIAL_MISMATCH)
    }
    if (isPresenceMismatched(usernameResource, passwordResource)) {
      throw AppDistributionException(TEST_LOGIN_CREDENTIAL_RESOURCE_MISMATCH)
    }
    if (
      usernameResource != null && passwordResource != null && username == null && password == null
    ) {
      throw AppDistributionException(TEST_LOGIN_CREDENTIAL_MISSING)
    }

    var fieldHints: LoginCredentialFieldHints? = null
    if (usernameResource != null || passwordResource != null) {
      fieldHints =
        LoginCredentialFieldHints(
          usernameResourceName = usernameResource,
          passwordResourceName = passwordResource
        )
    }

    return LoginCredential(username = username, password = password, fieldHints = fieldHints)
  }

  private fun extractPassword(passwordValue: String?, passwordPath: String?): String? =
    // Remove any trailing or leading whitespace
    getValueFromStringOrFile(passwordValue, passwordPath)?.trim()

  companion object {
    private val APP_ID_PATTERN =
      Pattern.compile(
        "(?<version>\\d+):(?<projectNumber>\\d+):(?<platform>ios|android|web):(\\p{XDigit}+)"
      )

    private fun isPresenceMismatched(value1: String?, value2: String?): Boolean {
      return (value1 == null) xor (value2 == null)
    }

    private fun parseTestDevice(s: String): TestDevice {

      val attributes =
        Splitter.on(',').omitEmptyStrings().trimResults().withKeyValueSeparator('=').split(s)
      return TestDevice(
        model = attributes["model"],
        version = attributes["version"],
        orientation = attributes["orientation"],
        locale = attributes["locale"]
      )
    }
  }
}
