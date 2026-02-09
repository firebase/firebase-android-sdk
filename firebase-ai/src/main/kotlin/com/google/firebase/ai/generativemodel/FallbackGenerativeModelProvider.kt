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

package com.google.firebase.ai.generativemodel

import android.util.Log
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.CountTokensResponse
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerateObjectResponse
import com.google.firebase.ai.type.JsonSchema
import kotlinx.coroutines.flow.Flow

/**
 * A [GenerativeModelProvider] that delegates requests to a `defaultModel` and falls back to a
 * `fallbackModel` under specified conditions.
 *
 * This class provides a mechanism to attempt an operation with a primary model, and if a
 * precondition is not met or a [FirebaseAIException] occurs (and configured to do so), it switches
 * to a secondary fallback model.
 *
 * @param defaultModel The primary [GenerativeModelProvider] to use for generating content.
 * @param fallbackModel The secondary [GenerativeModelProvider] to use if the `defaultModel` cannot
 * be used or encounters an exception.
 * @param precondition A lambda function that returns `true` if the `defaultModel` should be used,
 * or `false` to immediately use the `fallbackModel`. Defaults to always `true`.
 * @param shouldFallbackInException If `true`, the `fallbackModel` will be used when the
 * `defaultModel` throws a [FirebaseAIException]. If `false`, the exception will be rethrown.
 * Defaults to `true`.
 */
internal class FallbackGenerativeModelProvider(
  private val defaultModel: GenerativeModelProvider,
  private val fallbackModel: GenerativeModelProvider,
  private val precondition: () -> Boolean = { true },
  private val shouldFallbackInException: Boolean = true
) : GenerativeModelProvider {

  override suspend fun generateContent(prompt: List<Content>): GenerateContentResponse {
    return withFallback("generateContent") { generateContent(prompt) }
  }

  override suspend fun countTokens(prompt: List<Content>): CountTokensResponse {
    return withFallback("countTokens") { countTokens(prompt) }
  }

  override fun generateContentStream(prompt: List<Content>): Flow<GenerateContentResponse> {
    return withFallback("generateContentStream") { generateContentStream(prompt) }
  }

  override suspend fun <T : Any> generateObject(
    jsonSchema: JsonSchema<T>,
    prompt: List<Content>
  ): GenerateObjectResponse<T> {
    return withFallback("generateObject") { generateObject(jsonSchema, prompt) }
  }

  private inline fun <T> withFallback(
    methodName: String,
    block: GenerativeModelProvider.() -> T
  ): T {
    if (!precondition()) {
      Log.w(
        TAG,
        "Precondition was not met, switching to fallback model `${fallbackModel::javaClass.name}`"
      )
      return fallbackModel.block()
    }
    return try {
      defaultModel.block()
    } catch (e: FirebaseAIException) {
      if (shouldFallbackInException) {
        Log.w(
          TAG,
          "Error running `$methodName` using on `${defaultModel::javaClass.name}`. Switching to `${fallbackModel::javaClass.name}`",
          e
        )
        return fallbackModel.block()
      }
      throw e
    }
  }

  companion object {
    private val TAG = FallbackGenerativeModelProvider::class.java.simpleName
  }
}
