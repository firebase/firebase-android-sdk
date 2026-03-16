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

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.HttpTransport
import com.google.firebase.appdistribution.gradle.models.FirebaseCliConfig
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import java.io.FileReader
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import org.gradle.api.logging.Logging

class AppDistributionEnvironmentImpl : AppDistributionEnvironment {
  override fun getFirebaseCliLoginCredentials(transport: HttpTransport): GoogleCredential? {
    val gson =
      GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()

    // Step 1: Find where the Firebase CLI config is stored
    val configPath: Path =
      if (System.getenv("XDG_CONFIG_HOME") != null) {
        Paths.get(System.getenv("XDG_CONFIG_HOME"), "configstore", "firebase-tools.json")
      } else {
        Paths.get(System.getProperty("user.home"), ".config", "configstore", "firebase-tools.json")
      }

    try {
      // Step 2: Parse the config JSON file to get token information
      val config = gson.fromJson(FileReader(configPath.toString()), FirebaseCliConfig::class.java)

      // Step 3: Generate new credential using refresh token
      if (config?.tokens?.refreshToken != null) {
        val refreshToken = RefreshToken(config.tokens.refreshToken, transport)
        return refreshToken.generateNewCredentials()
      }
    } catch (e: IOException) {
      logger.debug("Failed to authenticate with Firebase CLI credentials", e)
    }

    return null
  }

  companion object {
    private val logger = Logging.getLogger(this::class.java)
  }
}
