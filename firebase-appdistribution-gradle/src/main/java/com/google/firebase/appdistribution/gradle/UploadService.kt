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

import com.google.api.client.http.FileContent
import com.google.firebase.appdistribution.gradle.models.UploadResponse
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.File
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class UploadService(private val httpClient: AuthenticatedHttpClient) {

  /*
   * Try to upload a distribution for the given app and return an operation name for checking upload
   * status.
   *
   * @return The operation name if the upload was successful.
   * @throws IOException
   */
  fun uploadDistribution(appName: String, distributionFile: File): String? {
    val fileContent = FileContent("application/octet-stream", distributionFile)

    val uploadDistributionResponse =
      httpClient
        .newPostRequest(ApiEndpoints.getScottyUploadEndpoint(appName))
        .setContent(fileContent)
        .setHeader(X_GOOG_UPLOAD_FILE_NAME, distributionFile.name)
        .setHeader(X_GOOG_UPLOAD_PROTOCOL, RAW)
        .execute()

    if (uploadDistributionResponse.isSuccessStatusCode) {
      return try {
        val responseBody = uploadDistributionResponse.parseAsString()
        Gson().fromJson(responseBody, UploadResponse::class.java).name
      } catch (e: JsonSyntaxException) {
        logger.info("Failed to parse upload response. Message: {}", e.message)
        null
      }
    }

    logger.info(
      "Unable to upload {}. Response code: {}",
      BinaryType.fromPath(distributionFile.path),
      uploadDistributionResponse.statusCode
    )
    return null
  }

  companion object {
    const val X_GOOG_UPLOAD_FILE_NAME = "X-Goog-Upload-File-Name"
    const val X_GOOG_UPLOAD_PROTOCOL = "X-Goog-Upload-Protocol"
    const val RAW = "raw"
    private val logger: Logger = Logging.getLogger(this::class.java)
  }
}
