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

import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.appdistribution.gradle.ApiEndpoints
import java.io.File
import java.nio.file.Files

class ServiceAccountCredentials
private constructor(val credentialsAdapter: HttpCredentialsAdapter) {
  companion object {
    fun fromFile(credentials: File): ServiceAccountCredentials =
      ServiceAccountCredentials(
        HttpCredentialsAdapter(
          GoogleCredentials.fromStream(Files.newInputStream(credentials.toPath()))
            .createScoped(ApiEndpoints.SCOPES)
        )
      )
  }
}
