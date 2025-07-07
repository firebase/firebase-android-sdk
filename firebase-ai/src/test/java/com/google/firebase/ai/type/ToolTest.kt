/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.ai.type

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

internal class ToolTest {
  @Test
  fun `googleSearch() creates a tool with a googleSearch property`() {
    val tool = Tool.googleSearch()

    tool.googleSearch.shouldNotBeNull()
    tool.functionDeclarations.shouldBeNull()
  }

  @Test
  fun `functionDeclarations() creates a tool with functionDeclarations`() {
    val functionDeclaration = FunctionDeclaration("test", "test", emptyMap())
    val tool = Tool.functionDeclarations(listOf(functionDeclaration))

    tool.functionDeclarations?.first() shouldBe functionDeclaration
    tool.googleSearch.shouldBeNull()
  }
}
