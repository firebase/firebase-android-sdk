/*
 * Copyright 2025 Google LLC
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

/** A list of extension properties used by the App Distribution Plugin. */
interface AppDistributionExtension {

  /** Absolute path to the APK or AAB file you want to upload. */
  var artifactPath: String?
  /** Specifies your app's file type. Can be set to "AAB" or "APK". */
  var artifactType: String?
  /**
   * The path to your service account private key JSON file. Required only if you use service
   * account authentication.
   */
  var serviceCredentialsFile: String?
  /**
   * Your app's Firebase App ID. Required only if you don't have the Google Services Gradle plugin
   * installed. You can find the App ID in the google-services.json file or in the Firebase console
   * on the General Settings page. The value in your build.gradle file overrides the value output
   * from the google-services plugin.
   */
  var appId: String?
  /**
   * Release notes for this build, as a string.
   *
   * (You can either specify the release notes directly or the path to a plain text file.)
   */
  var releaseNotes: String?
  /**
   * Release notes for this build, as a file.
   *
   * (You can either specify the release notes directly or the path to a plain text file.)
   */
  var releaseNotesFile: String?
  /**
   * A comma-separated list of email addresses of the testers you want to distribute builds to.
   *
   * e.g., testers="ali@example.com, bri@example.com, cal@example.com"
   */
  var testers: String?
  /**
   * A file containing a comma-separated list of email addresses of the testers you want to
   * distribute builds to.
   *
   * e.g., testersFile="/path/to/testers.txt"
   */
  var testersFile: String?
  /**
   * A comma-separated list of group aliases you want to distribute the builds to.
   *
   * e.g., groups="qa-team, android-testers"
   */
  var groups: String?
  /**
   * A file containing a comma-separated list of group aliases you want to distribute the builds to.
   *
   * e.g., groupsFile="/path/to/tester-groups.txt"
   */
  var groupsFile: String?

  /**
   * A semicolon-separated list of device specifications for automated tests.
   *
   * (part of the Automated tester beta feature.)
   *
   * e.g.,
   * testDevices="model=shiba,version=34,locale=en,orientation=portrait;model=b0q,version=33,locale=en,orientation=portrait"
   */
  var testDevices: String?
  /**
   * A file containing a semicolon-separated list of device specifications for automated tests.
   *
   * (part of the Automated tester beta feature.)
   *
   * e.g., testDevicesFile="/path/to/testDevices.txt"
   */
  var testDevicesFile: String?
  /** The username for automatic login to be used during automated tests. */
  var testUsername: String?
  /** The password for automatic login to be used during automated tests. */
  var testPassword: String?
  /**
   * The path to a plain text file containing the password for automatic login to be used during
   * automated tests.
   *
   * e.g., testPasswordFile="/path/to/testPassword.txt"
   */
  var testPasswordFile: String?
  /** Resource name for the username field for automatic login to be used during automated tests. */
  var testUsernameResource: String?
  /** Resource name for the password field for automatic login to be used during automated tests. */
  var testPasswordResource: String?
  /**
   * Run automated tests asynchronously. Visit the Firebase console for the automatic test results.
   */
  var testNonBlocking: Boolean?
  /**
   * The comma-separated identifiers of the test cases you're executing.
   *
   * These identifiers can be retrieved from the automated testing dashboard of the App Distribution
   * product within the Firebase Console.
   *
   * e.g., testCases="checkout-test,add-to-cart-test"
   */
  var testCases: String?
  /**
   * The identifiers of the test cases you're executing
   *
   * These identifiers can be retrieved from the automated testing dashboard of the App Distribution
   * product within the Firebase Console.
   *
   * e.g., testCasesFile="/path/to/testCases.txt"
   */
  var testCasesFile: String?

  companion object {
    fun createDefault(): AppDistributionExtension {
      return object : AppDistributionExtension {
        override var artifactPath: String? = null
        override var artifactType: String? = null
        override var serviceCredentialsFile: String? = null
        override var appId: String? = null
        override var releaseNotes: String? = null
        override var releaseNotesFile: String? = null
        override var testers: String? = null
        override var testersFile: String? = null
        override var groups: String? = null
        override var groupsFile: String? = null
        override var testDevices: String? = null
        override var testDevicesFile: String? = null
        override var testUsername: String? = null
        override var testPassword: String? = null
        override var testPasswordFile: String? = null
        override var testUsernameResource: String? = null
        override var testPasswordResource: String? = null
        override var testNonBlocking: Boolean? = null
        override var testCases: String? = null
        override var testCasesFile: String? = null
      }
    }

    fun AppDistributionExtension.isDefault(): Boolean {
      return listOf(
          this.artifactPath,
          this.artifactType,
          this.appId,
          this.serviceCredentialsFile,
          this.releaseNotes,
          this.releaseNotesFile,
          this.testers,
          this.testersFile,
          this.groups,
          this.groupsFile,
          this.testDevices,
          this.testDevicesFile,
          this.testUsername,
          this.testPassword,
          this.testPasswordFile,
          this.testUsernameResource,
          this.testPasswordResource,
          this.testNonBlocking,
          this.testCases,
          this.testCasesFile
        )
        .all { it == null }
    }
  }

  public interface DeprecatedAppDistributionExtension : AppDistributionExtension {}

  public interface ProjectDefaultAppDistributionExtension : AppDistributionExtension {}
}
