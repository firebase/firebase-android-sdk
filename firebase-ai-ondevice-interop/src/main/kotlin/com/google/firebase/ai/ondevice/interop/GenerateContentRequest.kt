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

package com.google.firebase.ai.ondevice.api

/**
 * A request to generate content from the model.
 *
 * @property text The text prompt to generate content from.
 * @property image The image prompt to generate content from.
 * @property temperature A parameter controlling the degree of randomness in token selection.
 * @property topK The `topK` parameter changes how the model selects tokens for output. A `topK` of
 * 1 means the selected token is the most probable among all the tokens in the model's vocabulary,
 * while a `topK` of 3 means that the next token is selected from among the 3 most probable using
 * the `temperature`
 * @property seed The seed to use for generation.
 * @property candidateCount The number of candidates to generate.
 * @property maxOutputTokens Specifies the maximum number of tokens that can be generated in the
 * response.
 */
public class GenerateContentRequest(
  public val text: TextPart,
  public val image: ImagePart? = null,
  public val temperature: Float? = null,
  public val topK: Int? = null,
  public val seed: Int? = null,
  public val candidateCount: Int? = null,
  public val maxOutputTokens: Int? = null,
) {}
