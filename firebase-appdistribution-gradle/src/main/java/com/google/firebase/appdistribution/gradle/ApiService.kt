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

import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.HttpResponseException
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.Companion.processingBinaryError
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.TEST_CASE_NOT_FOUND
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.TOO_MANY_TESTER_EMAILS
import com.google.firebase.appdistribution.gradle.NameUtils.extractResourceId
import com.google.firebase.appdistribution.gradle.models.AabInfo
import com.google.firebase.appdistribution.gradle.models.DeviceExecution
import com.google.firebase.appdistribution.gradle.models.LoginCredential
import com.google.firebase.appdistribution.gradle.models.ReleaseTest
import com.google.firebase.appdistribution.gradle.models.TestDevice
import com.google.firebase.appdistribution.gradle.models.uploadstatus.UploadStatusResponse
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.io.IOException
import java.util.function.Consumer
import kotlin.text.Charsets.UTF_8
import org.gradle.api.logging.Logging

class ApiService(private val httpClient: AuthenticatedHttpClient) {
  fun getUploadStatus(operationName: String, binaryType: BinaryType): UploadStatusResponse {
    val endpoint = ApiEndpoints.getUploadStatusEndpoint(operationName)

    return try {
      val response = httpClient.newGetRequest(endpoint).execute()
      Gson().fromJson(response.parseAsString(), UploadStatusResponse::class.java)
    } catch (e: HttpResponseException) {
      throw AppDistributionException.fromHttpResponseException(processingBinaryError(binaryType), e)
    } catch (e: IOException) {
      throw AppDistributionException.fromIoException(processingBinaryError(binaryType), e)
    }
  }

  fun createReleaseNotes(releaseName: String, releaseNotes: String): Boolean {
    val jsonBody = buildCreateReleaseNotesJson(releaseName, releaseNotes)
    val content = ByteArrayContent("application/json", jsonBody.toString().toByteArray(UTF_8))

    val createReleaseNotesResponse =
      httpClient
        .newPatchRequest(ApiEndpoints.getCreateReleaseNotesEndpoint(releaseName), content)
        .execute()

    return logAndReturnSuccess(
      createReleaseNotesResponse.isSuccessStatusCode,
      successMessage = "Added release notes successfully ${createReleaseNotesResponse.statusCode}",
      failureMessage =
        "Unable to add release notes. Response code: ${createReleaseNotesResponse.statusCode}"
    )
  }

  fun distributeRelease(
    releaseName: String,
    emails: List<String>,
    groupIds: List<String>
  ): Boolean {
    val jsonBody = buildDistributeReleaseJson(releaseName, emails, groupIds)

    val distributeReleaseResponse =
      httpClient
        .newPostRequest(
          ApiEndpoints.getDistributeReleaseEndpoint(releaseName),
          buildHttpContent(jsonBody)
        )
        .execute()

    return logAndReturnSuccess(
      distributeReleaseResponse.isSuccessStatusCode,
      successMessage = "Added testers/groups successfully ${distributeReleaseResponse.statusCode}",
      failureMessage =
        "Unable to add testers/groups. Response code: ${distributeReleaseResponse.statusCode}"
    )
  }

  fun testRelease(
    releaseName: String,
    testDevices: List<TestDevice>,
    testLoginCredential: LoginCredential?,
    testCaseName: String? = null,
  ): ReleaseTest? {
    val releaseTest =
      ReleaseTest(
        loginCredential = testLoginCredential,
        deviceExecutions = testDevices.map { DeviceExecution(device = it) },
        testCase = testCaseName
      )
    try {
      val response =
        httpClient
          .newPostRequest(
            ApiEndpoints.getCreateReleaseTestEndpoint(releaseName),
            buildHttpContent(Gson().toJsonTree(releaseTest))
          )
          .execute()

      return if (response.isSuccessStatusCode) {
        val prefix =
          if (testCaseName != null) "Started test case ${extractResourceId(testCaseName)}"
          else "Started test"
        logger.lifecycle(
          "{} successfully [{}]. Note: This feature is in beta.",
          prefix,
          response.statusCode
        )
        Gson().fromJson(response.parseAsString(), ReleaseTest::class.java)
      } else {
        logger.warn("Unable to start test. Response code: {}", response.statusCode)
        null
      }
    } catch (e: HttpResponseException) {
      if (e.statusCode == 404) {
        throw AppDistributionException(TEST_CASE_NOT_FOUND, extraInformation = testCaseName)
      }

      throw e
    }
  }

  fun getReleaseTest(releaseTestName: String): ReleaseTest? {
    val response =
      httpClient.newGetRequest(ApiEndpoints.getReleaseTestEndpoint(releaseTestName)).execute()
    return if (response.isSuccessStatusCode) {
      Gson().fromJson(response.parseAsString(), ReleaseTest::class.java)
    } else {
      logger.warn("Unable to retrieve test. Response code: {}", response.statusCode)
      null
    }
  }

  /**
   * Get app
   *
   * @param appName the app which to fetch aab info for
   */
  fun getAabInfo(appName: String): AabInfo {
    val appResponse = httpClient.newGetRequest(ApiEndpoints.getAabInfo(appName)).execute()
    return Gson().fromJson(appResponse.parseAsString(), AabInfo::class.java)
  }

  fun batchAddTesters(projectNumber: Long, emails: Collection<String>): Boolean {
    if (emails.size > MAX_TESTER_EMAILS) {
      throw AppDistributionException(
        TOO_MANY_TESTER_EMAILS,
        extraInformation =
          "Cannot add ${emails.size} testers, $MAX_TESTER_EMAILS is the maximum allowed",
      )
    }
    logger.info("Adding {} testers to project {}...", emails.size, projectNumber)
    val jsonBody = buildTesterEmailsJson(emails)
    val response =
      httpClient
        .newPostRequest(ApiEndpoints.getBatchAddTestersEndpoint(projectNumber))
        .setContent(buildHttpContent(jsonBody))
        .execute()

    return logAndReturnSuccess(
      response.isSuccessStatusCode,
      successMessage = "Testers added successfully [${response.statusCode}]",
      failureMessage = "Unable to add testers. Response code: ${response.statusCode}"
    )
  }

  fun batchRemoveTesters(projectNumber: Long, emails: Collection<String>): Boolean {
    if (emails.size > MAX_TESTER_EMAILS) {
      throw AppDistributionException(
        TOO_MANY_TESTER_EMAILS,
        extraInformation =
          "Cannot remove ${emails.size} testers, $MAX_TESTER_EMAILS is the maximum allowed",
      )
    }
    logger.info("Removing {} testers from project {}...", emails.size, projectNumber)
    val jsonBody = buildTesterEmailsJson(emails)
    val response =
      httpClient
        .newPostRequest(ApiEndpoints.getBatchRemoveTestersEndpoint(projectNumber))
        .setContent(buildHttpContent(jsonBody))
        .execute()

    return if (response.isSuccessStatusCode) {
      val json = JsonParser.parseString(response.parseAsString()).asJsonObject
      val emailsResponse = json["emails"]
      val emailsRemoved = if (emailsResponse == null) JsonArray() else emailsResponse.asJsonArray
      logger.info("{} testers removed successfully [{}]", emailsRemoved.size(), response.statusCode)
      logger.debug("Testers removed: {}", emailsRemoved)
      true
    } else {
      logger.warn("Unable to remove testers. Response code: {}", response.statusCode)
      false
    }
  }

  fun buildCreateReleaseNotesJson(releaseName: String, releaseNotes: String): JsonObject {
    val jsonBody = JsonObject()
    jsonBody.add("name", JsonPrimitive(releaseName))
    val releaseNotesJson = JsonObject()
    releaseNotesJson.add("text", JsonPrimitive(releaseNotes))
    jsonBody.add("releaseNotes", releaseNotesJson)
    return jsonBody
  }

  fun buildDistributeReleaseJson(
    releaseName: String,
    emails: List<String>,
    groupIds: List<String>
  ): JsonObject {
    val jsonBody = JsonObject()
    jsonBody.add("name", JsonPrimitive(releaseName))
    jsonBody.add("testerEmails", buildJsonArray(emails))
    jsonBody.add("groupAliases", buildJsonArray(groupIds))
    return jsonBody
  }

  // Will be used by both batchAddTesters and batchRemoveTesters
  fun buildTesterEmailsJson(emails: Collection<String>): JsonObject {
    val jsonBody = JsonObject()
    jsonBody.add("emails", buildJsonArray(emails))
    return jsonBody
  }

  private fun logAndReturnSuccess(
    success: Boolean,
    successMessage: String,
    failureMessage: String
  ): Boolean {
    return if (success) {
      logger.info(successMessage)
      true
    } else {
      logger.warn(failureMessage)
      false
    }
  }

  private fun buildJsonArray(entities: Collection<String>): JsonArray {
    val jsonArray = JsonArray()
    entities.forEach(Consumer { string: String -> jsonArray.add(string) })
    return jsonArray
  }

  private fun buildHttpContent(json: JsonElement): ByteArrayContent =
    ByteArrayContent("application/json", json.toString().toByteArray(UTF_8))

  companion object {
    private const val MAX_TESTER_EMAILS = 1000
    private val logger = Logging.getLogger(this::class.java)
  }
}
