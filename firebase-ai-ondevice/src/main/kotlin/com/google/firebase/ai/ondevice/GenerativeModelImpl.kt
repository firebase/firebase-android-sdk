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
import com.google.firebase.ai.ondevice.interop.GenerateContentRequest
import com.google.firebase.ai.ondevice.interop.GenerateContentResponse
import com.google.firebase.ai.ondevice.interop.GenerativeModel
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.flow.Flow

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

  override suspend fun generateContent(request: GenerateContentRequest): GenerateContentResponse {
    TODO("Not yet implemented")
  }

  override suspend fun countTokens(request: GenerateContentRequest): CountTokensResponse {
    TODO("Not yet implemented")
  }

  override fun generateContentStream(
    request: GenerateContentRequest
  ): Flow<GenerateContentResponse> {
    TODO("Not yet implemented")
  }

  override suspend fun getBaseModelName(): String = mlkitModel.getBaseModelName()

  override suspend fun getTokenLimit(): Int = mlkitModel.getTokenLimit()

  /**
   * Invokes the MLKit `warmup()` method. Catches any exceptions thrown by MLKit during warmup and
   * re-throws them as a `FirebaseAIOnDeviceException` for consistent error handling within the
   * xFirebase API.
   */
  override suspend fun warmup() =
    try {
      mlkitModel.warmup()
    } catch (e: Exception) {
      throw FirebaseAIOnDeviceException.from(e)
    }
}
