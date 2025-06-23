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

package com.google.firebase.ai.common.util

/**
 * Ensures the model name provided has a `models/` prefix
 *
 * Models must be prepended with the `models/` prefix when communicating with the backend.
 */
internal fun fullModelName(name: String): String =
  name.takeIf { it.contains("/") } ?: "models/$name"

internal fun trimmedModelName(name: String): String = name.split("/").last()
