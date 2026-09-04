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

package com.google.firebase.ai.type

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test

internal class GenerativeBackendTest {

  @Test
  fun `agentPlatform default location`() {
    val backend = GenerativeBackend.agentPlatform()
    backend.location shouldBe "global"
    backend.backend shouldBe GenerativeBackendEnum.AGENT_PLATFORM
  }

  @Test
  fun `agentPlatform custom location`() {
    val backend = GenerativeBackend.agentPlatform("europe-west1")
    backend.location shouldBe "europe-west1"
    backend.backend shouldBe GenerativeBackendEnum.AGENT_PLATFORM
  }

  @Test
  fun `agentPlatform invalid locations throw exception`() {
    shouldThrow<InvalidLocationException> { GenerativeBackend.agentPlatform("") }
    shouldThrow<InvalidLocationException> { GenerativeBackend.agentPlatform("   ") }
    shouldThrow<InvalidLocationException> { GenerativeBackend.agentPlatform("us/central1") }
  }

  @Test
  @Suppress("DEPRECATION")
  fun `vertexAI default location`() {
    val backend = GenerativeBackend.vertexAI()
    backend.location shouldBe "us-central1"
    backend.backend shouldBe GenerativeBackendEnum.VERTEX_AI
  }

  @Test
  fun `googleAI properties`() {
    val backend = GenerativeBackend.googleAI()
    backend.location shouldBe ""
    backend.backend shouldBe GenerativeBackendEnum.GOOGLE_AI
  }

  @Test
  @Suppress("DEPRECATION")
  fun `GenerativeBackend equality and hashcode`() {
    val agentPlatformGlobal1 = GenerativeBackend.agentPlatform("global")
    val agentPlatformGlobal2 = GenerativeBackend.agentPlatform("global")
    val agentPlatformUsCentral = GenerativeBackend.agentPlatform("us-central1")
    val vertexGlobal = GenerativeBackend.vertexAI("global")

    agentPlatformGlobal1 shouldBe agentPlatformGlobal2
    agentPlatformGlobal1.hashCode() shouldBe agentPlatformGlobal2.hashCode()

    agentPlatformGlobal1 shouldNotBe agentPlatformUsCentral
    agentPlatformGlobal1 shouldNotBe vertexGlobal
    agentPlatformGlobal1 shouldNotBe GenerativeBackend.googleAI()
  }
}
