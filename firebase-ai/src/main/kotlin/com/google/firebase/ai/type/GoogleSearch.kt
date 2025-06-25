/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.ai.type

import kotlinx.serialization.Serializable

/**
 * A tool that allows the generative model to connect to Google Search to access and incorporate
 * up-to-date information from the web into its responses.
 *
 * When this tool is used, the model's responses may include "Grounded Results" which are subject to
 * the Grounding with Google Search terms outlined in the
 * [Service Specific Terms](https://cloud.google.com/terms/service-terms).
 */
public class GoogleSearch {
  @Serializable internal class Internal()

  internal fun toInternal() = Internal()
}
