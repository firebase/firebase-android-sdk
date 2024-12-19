/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.gradle.plugins

import com.google.firebase.gradle.plugins.PreReleaseVersionType.*
import com.google.firebase.gradle.plugins.VersionType.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.*
import org.junit.Test

class ModuleVersionTests : FunSpec() {

  val numbers = generateSequence(1) { it + 1 }
  val versionTypes = sequenceOf(*VersionType.values())
  val preReleaseVersionTypes = sequenceOf(*PreReleaseVersionType.values())

  @Test
  fun `ModuleVersion#fromString() handles standard valid versions`() {
    listOf("12.0.1", "1.0.0", "13.6.3-rc", "19.2.3", "11.4.3-beta06", "13.5.4-alpha11")
      .map { ModuleVersion.fromStringOrNull(it) }
      .forEach { it shouldNotBe null }
  }

  @Test
  fun `PreReleaseVersion#fromStrings() handles standard valid versions`() {
    listOf("alpha", "bEtA", "rC", "EAP")
      .map { PreReleaseVersion.fromStringsOrNull(it, "1") }
      .forEach { it shouldNotBe null }
  }

  @Test
  fun `Pre release versions default to build 1 on missing build`() {
    PreReleaseVersion(ALPHA).build shouldBe 1
    PreReleaseVersion.fromStringsOrNull("alpha", "")?.build shouldBe 1
  }

  @Test
  fun `Pre release versions are double digits`() {
    numbers.take(20).onEach {
      PreReleaseVersion(ALPHA, it).toString().takeLast(2).toIntOrNull() shouldBe it
    }
  }

  @Test
  fun `ModuleVersion returns null on invalid versions`() {
    listOf("12.0.", "1.0.-1", "13.6.3-", "19.2.c", "11.4.3-bet", "13.5.4-01", "")
      .map { ModuleVersion.fromStringOrNull(it) }
      .forEach { it shouldBe null }
  }

  @Test
  fun `PreReleaseVersion returns null on invalid versions`() {
    listOf("alph", "bEt", "r", "pea", "", "1", "!")
      .map { PreReleaseVersion.fromStringsOrNull(it, "1") }
      .forEach { it shouldBe null }
  }

  @Test
  fun `PreReleaseVersion's bump increases the build number`() {
    numbers.take(20).forEach { PreReleaseVersion(ALPHA, it).bump().build shouldBe it + 1 }
  }

  @Test
  fun `Bump handles all version types`() {
    ModuleVersion(1, 1, 1, PreReleaseVersion(ALPHA, 1)).apply {
      versionTypes.takeAll().forEach {
        bump(it).run {
          when (it) {
            MAJOR -> major shouldBe 2
            MINOR -> minor shouldBe 2
            PATCH -> patch shouldBe 2
            PRE -> pre?.build shouldBe 2
          }
        }
      }
    }
  }

  @Test
  fun `Bump doesn't incorrectly bump on undefined pre-release`() {
    ModuleVersion(1, 1, 1).apply { bump(PRE) shouldBe this }
  }

  @Test
  fun `Bump correctly chooses the smallest by default`() {
    ModuleVersion(1, 1, 1).bump().patch shouldBe 2
    ModuleVersion(1, 1, 1, PreReleaseVersion(ALPHA, 1)).bump().pre?.build shouldBe 2
  }
}
