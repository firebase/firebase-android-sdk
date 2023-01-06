// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.gradle.plugins

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Ensures the current state of Dackka outputs what we expect from it.
 *
 * We do this by running the [DackkaPlugin] against a small project fixture and comparing the output
 * to a pre-compiled output that represents what we expect Dackka to generate.
 *
 * ## Resources
 *
 * The resources for the tests are stored under `src/test/resources/dackka-plugin-tests` with two
 * sub directories that will be explained below.
 *
 * ### project
 *
 * Directory containing a small gradle project, with various sub-projects. These exist to test edge
 * case scenarios in our doc generation- to ensure any changes do not break previous fixes.
 *
 * ### output
 *
 * Directory containing the **expected** output from running the [DackkaPlugin] against the
 * predefined project fixture.
 *
 * ## Updating Output
 *
 * Should the time come where Dackka behavior completely changes, the format changes, or maybe we
 * find a new edge-case; the output directory that gets compared during testing time should be
 * updated.
 *
 * Since the tests run on a project fixture, the easiest way to update the output is to add a clause
 * in the tests itself to overwrite the previous output. This behavior is not preferred, and should
 * be evaluated at a later date. You can see this in [DackkaPluginTests.updateDocs].
 *
 * You can trigger this function to run one of two ways; passing the `rebuildDackkaOutput` property
 * to gradle during the build, or calling the `updateDackkaTestsPlugin` task.
 *
 * Example:
 * ```
 * ./gradlew -b buildSrc/build.gradle.kts updateDackkaTestsPlugin
 * ```
 */
class DackkaPluginTests {

  companion object {
    @ClassRule @JvmField val testProjectDir = TemporaryFolder()

    private val resourcesDirectory = File("src/test/resources/dackka-plugin-tests/")
    private val outputDirectory = File("$resourcesDirectory/output/firebase-kotlindoc")

    @BeforeClass
    @JvmStatic
    fun setup() {
      copyFixtureToTempDirectory()
      buildDocs()
      if (System.getProperty("rebuildDackkaOutput") == "true") {
        updateDocs()
      }
    }

    /**
     * Updates the current docs and output to match an updated source.
     *
     * Unfortunately, we need GradleRunner to be able to do this automatically, and this was the
     * cleanest way I could accomplish such. I'm sure this can be fixed down the line should we
     * expand buildSrc into its own composite build.
     */
    private fun updateDocs() {
      removeOldOutputFiles()

      val docDirectory = File("${testProjectDir.root}/build/firebase-kotlindoc")

      docDirectory.copyRecursively(outputDirectory, true)
    }

    private fun removeOldOutputFiles() {
      outputDirectory.deleteRecursively()
    }

    private fun copyFixtureToTempDirectory() {
      val project = File("$resourcesDirectory/project")
      project.copyRecursively(testProjectDir.root)
    }

    private fun buildDocs() {
      GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withPluginClasspath()
        .withArguments("kotlindoc")
        .build()
    }
  }

  @Test
  fun `Transforms correctly`() {
    val buildDirectory = File("${testProjectDir.root}/build")
    val docDirectory = File("$buildDirectory/firebase-kotlindoc")

    val diff = docDirectory.recursiveDiff(outputDirectory)
    val diffAsString = diff.joinToString("\n")

    println(diffAsString)
    assertThat(diffAsString).isEmpty()
  }
}
