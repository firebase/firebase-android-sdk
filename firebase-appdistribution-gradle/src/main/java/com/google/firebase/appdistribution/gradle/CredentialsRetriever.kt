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

import com.google.firebase.appdistribution.gradle.AppDistributionEnvironment.Companion.ENV_FIREBASE_TOKEN
import com.google.firebase.appdistribution.gradle.AppDistributionEnvironment.Companion.ENV_GOOGLE_APPLICATION_CREDENTIALS
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.REFRESH_TOKEN_ERROR
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.SERVICE_CREDENTIALS_NOT_FOUND
import com.google.firebase.appdistribution.gradle.OptionsUtils.ensureFileExists
import com.google.firebase.appdistribution.gradle.models.ServiceAccountCredentials
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import java.io.IOException
import org.gradle.api.logging.Logging

/** Helper class for retrieving auth credentials from one of the four possible auth mechanisms. */
class CredentialsRetriever(
  private val appDistributionEnvironment: AppDistributionEnvironment =
    AppDistributionEnvironmentImpl(),
  private val adcCredentialsProvider: () -> GoogleCredentials = { GoogleCredentials.getApplicationDefault() }
) {

  /** Returns the auth credential, if found. Otherwise returns null. */
  fun getAuthCredential(serviceCredentialsPath: String? = null): HttpCredentialsAdapter? {
    // Check the explicitly passed in service credentials first
    if (serviceCredentialsPath != null) {
      val serviceCredentialsFile =
        ensureFileExists(serviceCredentialsPath, SERVICE_CREDENTIALS_NOT_FOUND)
      return try {
        ServiceAccountCredentials.fromFile(serviceCredentialsFile).credentialsAdapter
      } catch (e: IOException) {
        throw AppDistributionException(
          SERVICE_CREDENTIALS_NOT_FOUND,
          cause = e,
          extraInformation = serviceCredentialsPath
        )
      }
    }

    // Then check for an oauth token in the environment
    val envRefreshToken = System.getenv(ENV_FIREBASE_TOKEN)
    if (envRefreshToken != null) {
      return try {
        logger.info(
          "Using credentials token specified by environment variable {}",
          ENV_FIREBASE_TOKEN
        )
        RefreshToken(envRefreshToken).generateNewCredentials()
      } catch (e: Exception) {
        throw AppDistributionException(
          REFRESH_TOKEN_ERROR,
          extraInformation =
            "The refresh token set as the environment variable $ENV_FIREBASE_TOKEN is not valid"
        )
      }
    }

    // Then check for cached Firebase CLI tokens
    val firebaseCliLoginCreds = appDistributionEnvironment.getFirebaseCliLoginCredentials()
    if (firebaseCliLoginCreds != null) {
      logger.info("Using cached Firebase CLI credentials")
      return firebaseCliLoginCreds
    }

    // Lastly, check for a credentials file in the environment
    val envGoogleCredentialsPath = System.getenv(ENV_GOOGLE_APPLICATION_CREDENTIALS)
    if (envGoogleCredentialsPath != null) {
      logger.info(
        "Using credentials file specified by environment variable {}: {}",
        ENV_GOOGLE_APPLICATION_CREDENTIALS,
        envGoogleCredentialsPath
      )
      val serviceCredentialsFile =
        ensureFileExists(envGoogleCredentialsPath, SERVICE_CREDENTIALS_NOT_FOUND)
      return try {
        val envServiceAccountCredentials =
          ServiceAccountCredentials.fromFile(serviceCredentialsFile)
        envServiceAccountCredentials.credentialsAdapter
      } catch (e: IOException) {
        throw AppDistributionException(
          SERVICE_CREDENTIALS_NOT_FOUND,
          e,
          extraInformation = envGoogleCredentialsPath
        )
      }
    }

    // Lastly, check for standard Application Default Credentials (ADC) as a fallback
    try {
      val credentials = adcCredentialsProvider().createScoped(ApiEndpoints.SCOPES)
      logger.info("Using Application Default Credentials (ADC)")
      return HttpCredentialsAdapter(credentials)
    } catch (e: IOException) {
      logger.debug("Failed to load Application Default Credentials (ADC)", e)
    }

    // If we reach this point, we were unable to find valid credentials
    return null
  }

  companion object {
    private val logger = Logging.getLogger(this::class.java)
  }
}
