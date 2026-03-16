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
import com.google.api.client.http.HttpResponseException
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.MISSING_CREDENTIALS
import java.io.IOException
import java.security.GeneralSecurityException

object FirebaseAppDistribution {
  @JvmStatic
  fun uploadDistribution(options: UploadDistributionOptions) {
    val googleHttpClient = getAuthenticatedHttpClient(getCredential(options))
    val apiService = ApiService(googleHttpClient)
    val uploadService = UploadService(googleHttpClient)
    val upload = FirebaseAppDistributionUpload(options, apiService, uploadService)
    upload.uploadDistribution()
  }

  @JvmStatic
  fun addTesters(options: TesterManagementOptions) {
    val googleHttpClient = getAuthenticatedHttpClient(getCredential(options))
    val apiService = ApiService(googleHttpClient)
    try {
      apiService.batchAddTesters(options.projectNumber, options.emails)
    } catch (e: HttpResponseException) {
      throw AppDistributionException.fromHttpResponseException(
        AppDistributionException.Reason.ADD_TESTERS_ERROR,
        e
      )
    } catch (e: IOException) {
      throw AppDistributionException.fromIoException(
        AppDistributionException.Reason.ADD_TESTERS_ERROR,
        e
      )
    }
  }

  @JvmStatic
  fun removeTesters(options: TesterManagementOptions) {
    val googleHttpClient = getAuthenticatedHttpClient(getCredential(options))
    val apiService = ApiService(googleHttpClient)
    try {
      apiService.batchRemoveTesters(options.projectNumber, options.emails)
    } catch (e: HttpResponseException) {
      throw AppDistributionException.fromHttpResponseException(
        AppDistributionException.Reason.REMOVE_TESTERS_ERROR,
        e
      )
    } catch (e: IOException) {
      throw AppDistributionException.fromIoException(
        AppDistributionException.Reason.REMOVE_TESTERS_ERROR,
        e
      )
    }
  }

  private fun getCredential(options: UploadDistributionOptions) =
    options.credential ?: throw AppDistributionException(MISSING_CREDENTIALS)

  private fun getCredential(options: TesterManagementOptions) =
    options.credential ?: throw AppDistributionException(MISSING_CREDENTIALS)

  private fun getAuthenticatedHttpClient(credential: Credential): AuthenticatedHttpClient {
    return try {
      AuthenticatedHttpClient(credential)
    } catch (e: Exception) {
      when (e) {
        is GeneralSecurityException,
        is IOException -> throw RuntimeException("There was a problem. Please try again.", e)
        else -> throw e
      }
    }
  }
}
