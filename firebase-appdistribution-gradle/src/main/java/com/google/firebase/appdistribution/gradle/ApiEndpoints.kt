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

import com.google.api.client.http.GenericUrl

object ApiEndpoints {
  private val API_URL =
    System.getProperty(
      "FIREBASE_APP_DISTRIBUTION_API_URL",
      "https://firebaseappdistribution.googleapis.com"
    )

  @JvmStatic val SCOPES = listOf("https://www.googleapis.com/auth/cloud-platform")

  fun getDistributeReleaseEndpoint(releaseName: String) =
    GenericUrl("$API_URL/v1/$releaseName:distribute")

  fun getCreateReleaseNotesEndpoint(releaseName: String) =
    GenericUrl("$API_URL/v1/$releaseName?updateMask=release_notes.text")

  fun getUploadStatusEndpoint(operationName: String) = GenericUrl("$API_URL/v1/$operationName")

  fun getAabInfo(appName: String) = GenericUrl("$API_URL/v1/$appName/aabInfo")

  @JvmStatic
  fun getScottyUploadEndpoint(appName: String) =
    GenericUrl("$API_URL/upload/v1/$appName/releases:upload")

  fun getBatchAddTestersEndpoint(projectNumber: Long) =
    GenericUrl("$API_URL/v1/projects/$projectNumber/testers:batchAdd")

  fun getBatchRemoveTestersEndpoint(projectNumber: Long) =
    GenericUrl("$API_URL/v1/projects/$projectNumber/testers:batchRemove")

  fun getCreateReleaseTestEndpoint(releaseName: String) =
    GenericUrl("$API_URL/v1alpha/$releaseName/tests")

  fun getReleaseTestEndpoint(releaseTestName: String) =
    GenericUrl("$API_URL/v1alpha/$releaseTestName")
}
