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

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.MISSING_PROJECT_NUMBER
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.MISSING_TESTER_EMAILS
import com.google.firebase.appdistribution.gradle.OptionsUtils.getValueFromStringOrFile
import com.google.firebase.appdistribution.gradle.OptionsUtils.splitCommaOrNewlineSeparatedString

/** Options for tester management actions, including AddTester and RemoveTester. */
class TesterManagementOptions(
  val projectNumber: Long,
  emailsValue: String? = null,
  emailsFile: String? = null,
  serviceCredentialsFile: String? = null,
  credentialsRetriever: CredentialsRetriever =
    CredentialsRetriever(
      GoogleNetHttpTransport.newTrustedTransport(),
      AppDistributionEnvironmentImpl()
    )
) {
  val emails = splitCommaOrNewlineSeparatedString(getValueFromStringOrFile(emailsValue, emailsFile))
  val credential = credentialsRetriever.getAuthCredential(serviceCredentialsFile)

  init {
    // If project number is set it would be greater than 0, the default primitive value
    if (projectNumber <= 0) {
      throw AppDistributionException(MISSING_PROJECT_NUMBER)
    }

    // There must be >= 1 email in the list to process
    if (emails.isEmpty()) {
      throw AppDistributionException(MISSING_TESTER_EMAILS)
    }
  }
}
