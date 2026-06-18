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

package com.google.mlkit.genai.prompt

/**
 * Compatibility stub for ML Kit GenAI Structured Output EAP (`generateTypedContentRequest`).
 *
 * Once upstream `com.google.mlkit:genai-prompt` releases structured output granules publicly, this
 * stub will be replaced by the official SDK implementation.
 */
public fun generateTypedContentRequest(
  generateContentRequest: GenerateContentRequest,
  outputClass: Class<*>,
  includeSchemaInPrompt: Boolean = true
): GenerateContentRequest = generateContentRequest
