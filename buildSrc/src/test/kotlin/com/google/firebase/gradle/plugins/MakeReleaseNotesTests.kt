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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.file.exist
import io.kotest.matchers.should
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MakeReleaseNotesTests : FunSpec() {

  @Test
  fun `Creates release notes that match the expected output`() {
    buildReleaseNotes()

    val expectedReleaseNoteFile = testResources.childFile("release-notes.md")
    val releaseNoteFile =
      testProjectDirectory.root.childFile(
        "firebase-storage/build/tmp/makeReleaseNotes/release_notes.md"
      )

    releaseNoteFile should exist()

    releaseNoteFile.readLines() diff expectedReleaseNoteFile.readLines() should beEmpty()
  }

  private fun buildReleaseNotes() =
    GradleRunner.create()
      .withProjectDir(testProjectDirectory.root)
      .withPluginClasspath()
      .withArguments("makeReleaseNotes")
      .build()

  companion object {
    @ClassRule @JvmField val testProjectDirectory = TemporaryFolder()
    private val resourcesDirectory = File("src/test/resources")
    private val basicProject = resourcesDirectory.childFile("BasicProject")
    private val testResources = resourcesDirectory.childFile("MakeReleaseNotes")

    @BeforeClass
    @JvmStatic
    fun setup() {
      copyFixtureToTempDirectory()
    }

    private fun copyFixtureToTempDirectory() {
      basicProject.copyRecursively(testProjectDirectory.root)
    }
  }
}
