package com.google.firebase.ai.generativemodel

import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.CountTokensResponse
import com.google.firebase.ai.type.GenerateContentResponse
import kotlinx.coroutines.flow.Flow

internal interface GenerativeModelProvider {
  suspend fun generateContent(prompt: List<Content>): GenerateContentResponse

  suspend fun countTokens(prompt: List<Content>): CountTokensResponse

  fun generateContentStream(prompt: List<Content>): Flow<GenerateContentResponse>
}
