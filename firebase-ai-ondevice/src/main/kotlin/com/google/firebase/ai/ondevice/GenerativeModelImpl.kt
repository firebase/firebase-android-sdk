/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.ai.ondevice

import com.google.firebase.ai.ondevice.interop.CountTokensResponse
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceException
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceNotAvailableException
import com.google.firebase.ai.ondevice.interop.FirebaseAIOnDeviceUnknownException
import com.google.firebase.ai.ondevice.interop.FirebaseAiOnDeviceInvalidRequestException
import com.google.firebase.ai.ondevice.interop.GenerateContentRequest
import com.google.firebase.ai.ondevice.interop.GenerateContentResponse
import com.google.firebase.ai.ondevice.interop.GenerativeModel
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.common.GenAiException.ErrorCode
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Implementation of [GenerativeModel] backed by MLKit's genai prompt SDK. */
internal class GenerativeModelImpl(
  internal val mlkitModel: com.google.mlkit.genai.prompt.GenerativeModel = Generation.getClient()
) : GenerativeModel {

  /**
   * Check whether the model is available to be used.
   *
   * Models being actively downloaded are also considered unavailable.
   */
  override suspend fun isAvailable(): Boolean = mlkitModel.checkStatus() == FeatureStatus.AVAILABLE

  override suspend fun generateContent(request: GenerateContentRequest): GenerateContentResponse =
    try {
      val response = mlkitModel.generateContent(request.toMlKit())
      response.toInterop()
    } catch (e: GenAiException) {
      throw getMappingException(e)
    }

  override suspend fun countTokens(request: GenerateContentRequest): CountTokensResponse =
    try {
      val response = mlkitModel.countTokens(request.toMlKit())
      response.toInterop()
    } catch (e: GenAiException) {
      throw getMappingException(e)
    }

  override fun generateContentStream(
    request: GenerateContentRequest
  ): Flow<GenerateContentResponse> {
    return mlkitModel
      .generateContentStream(request.toMlKit())
      .catch { throw getMappingException(it) }
      .map { it.toInterop() }
  }

  override suspend fun getBaseModelName(): String = mlkitModel.getBaseModelName()

  override suspend fun getTokenLimit(): Int = mlkitModel.getTokenLimit()

  /**
   * Invokes the MLKit `warmup()` method. Catches [GenAiException] thrown by MLKit and re-throws
   * them as a [FirebaseAIOnDeviceException] for consistent error handling within the Firebase API.
   */
  override suspend fun warmup() =
    try {
      mlkitModel.warmup()
    } catch (e: GenAiException) {
      throw getMappingException(e)
    }

  /**
   * Throws the [FirebaseAIOnDeviceException] subclass that maps to the input exception.
   *
   * To simplify the developer experience, this method maps [GenAiException]s into the corresponding
   * [FirebaseAIOnDeviceException] subclasses.
   *
   * @param e The exception thrown by the MLKit SDK.
   */
  private fun getMappingException(e: Throwable): Exception {
    if (e !is GenAiException) throw FirebaseAIOnDeviceUnknownException("Unknown exception", e)
    return when (e.errorCode) {
      ErrorCode.REQUEST_TOO_LARGE,
      ErrorCode.REQUEST_TOO_SMALL,
      ErrorCode.INVALID_INPUT_IMAGE -> FirebaseAiOnDeviceInvalidRequestException(e)
      ErrorCode.NEEDS_SYSTEM_UPDATE,
      ErrorCode.NOT_AVAILABLE,
      ErrorCode.AICORE_INCOMPATIBLE -> FirebaseAIOnDeviceNotAvailableException(e.message ?: "", e)
      // BUSY, CANCELLED, NOT_ENOUGH_DISK_SPACE, PER_APP_BATTERY_USE_QUOTA_EXCEEDED,
      // BACKGROUND_USE_BLOCKED
      else -> FirebaseAIOnDeviceException.from(e)
    }
  }
}
