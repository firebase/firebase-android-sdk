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

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

internal class ThinkingConfigTest {

  @Test
  fun `Basic ThinkingConfig`() {
    val thinkingConfig = ThinkingConfig.Builder().setThinkingBudget(1024).build()

    val expectedJson =
      """
      {
          "thinking_budget": 1024
      }
      """
        .trimIndent()

    Json.encodeToString(thinkingConfig.toInternal()).shouldEqualJson(expectedJson)
  }

  @Test
  fun `Include thought thinkingConfig`() {
    val thinkingConfig = ThinkingConfig.Builder().setIncludeThoughts(true).build()
    // CamelCase or snake_case work equally fine
    val expectedJson =
      """
      {
          "includeThoughts": true
      }
      """
        .trimIndent()

    Json.encodeToString(thinkingConfig.toInternal()).shouldEqualJson(expectedJson)
  }

  @Test
  fun `thinkingConfig DSL correctly delegates to ThinkingConfig#Builder`() {
    val thinkingConfig = ThinkingConfig.Builder().setThinkingBudget(1024).build()

    val thinkingConfigDsl = thinkingConfig { thinkingBudget = 1024 }

    thinkingConfig.thinkingBudget?.shouldBeEqual(thinkingConfigDsl.thinkingBudget as Int)
  }
}
