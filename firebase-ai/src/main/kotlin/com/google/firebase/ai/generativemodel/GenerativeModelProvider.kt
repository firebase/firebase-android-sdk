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

import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.CountTokensResponse
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerateObjectResponse
import com.google.firebase.ai.type.JsonSchema
import kotlinx.coroutines.flow.Flow

/**
 * Provides an interface for interacting with a generative AI model.
 *
 * The actual user visible UI is declared in [com.google.firebase.ai.GenerativeModel].
 */
internal interface GenerativeModelProvider {
  suspend fun generateContent(prompt: List<Content>): GenerateContentResponse

  suspend fun countTokens(prompt: List<Content>): CountTokensResponse

  fun generateContentStream(prompt: List<Content>): Flow<GenerateContentResponse>

  suspend fun <T : Any> generateObject(
    jsonSchema: JsonSchema<T>,
    prompt: List<Content>
  ): GenerateObjectResponse<T>
}
