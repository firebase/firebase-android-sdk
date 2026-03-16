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

package com.google.firebase.appdistribution.gradle.models

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.firebase.appdistribution.gradle.ApiEndpoints
import java.io.File
import java.nio.file.Files

class ServiceAccountCredentials private constructor(val googleCredential: GoogleCredential) {
  companion object {
    fun fromFile(credentials: File): ServiceAccountCredentials =
      ServiceAccountCredentials(
        GoogleCredential.fromStream(Files.newInputStream(credentials.toPath()))
          .createScoped(ApiEndpoints.SCOPES)
      )
  }
}
