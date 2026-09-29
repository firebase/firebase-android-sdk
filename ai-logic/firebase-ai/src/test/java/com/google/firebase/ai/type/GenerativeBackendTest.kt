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
    val backend = GenerativeBackend.enterprise()
    backend.location shouldBe "global"
    backend.backend shouldBe GenerativeBackendEnum.ENTERPRISE
  }

  @Test
  fun `agentPlatform custom location`() {
    val backend = GenerativeBackend.enterprise("europe-west1")
    backend.location shouldBe "europe-west1"
    backend.backend shouldBe GenerativeBackendEnum.ENTERPRISE
  }

  @Test
  fun `agentPlatform invalid locations throw exception`() {
    shouldThrow<InvalidLocationException> { GenerativeBackend.enterprise("") }
    shouldThrow<InvalidLocationException> { GenerativeBackend.enterprise("   ") }
    shouldThrow<InvalidLocationException> { GenerativeBackend.enterprise("us/central1") }
  }

  @Test
  fun `googleAI properties`() {
    val backend = GenerativeBackend.googleAI()
    backend.location shouldBe ""
    backend.backend shouldBe GenerativeBackendEnum.GOOGLE_AI
  }

  @Test
  fun `GenerativeBackend equality and hashcode`() {
    val agentPlatformGlobal1 = GenerativeBackend.enterprise("global")
    val agentPlatformGlobal2 = GenerativeBackend.enterprise("global")
    val agentPlatformUsCentral = GenerativeBackend.enterprise("us-central1")

    agentPlatformGlobal1 shouldBe agentPlatformGlobal2
    agentPlatformGlobal1.hashCode() shouldBe agentPlatformGlobal2.hashCode()

    agentPlatformGlobal1 shouldNotBe agentPlatformUsCentral
    agentPlatformGlobal1 shouldNotBe GenerativeBackend.googleAI()
  }
}
