package com.google.firebase.ai.generativemodel

import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.CountTokensResponse
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerateObjectResponse
import com.google.firebase.ai.type.JsonSchema
import kotlinx.coroutines.flow.Flow

internal interface GenerativeModelProvider {
  suspend fun generateContent(prompt: List<Content>): GenerateContentResponse

  suspend fun countTokens(prompt: List<Content>): CountTokensResponse

  fun generateContentStream(prompt: List<Content>): Flow<GenerateContentResponse>

  suspend fun <T : Any> generateObject(
    jsonSchema: JsonSchema<T>,
    prompt: List<Content>
  ): GenerateObjectResponse<T>
}
