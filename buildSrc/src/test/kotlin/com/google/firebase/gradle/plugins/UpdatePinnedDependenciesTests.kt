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
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdatePinnedDependenciesTests : FunSpec() {

  @Rule @JvmField val testProjectDirectory = TemporaryFolder()
  private val controller = FirebaseTestController(testProjectDirectory)

  @Test
  fun `Replaces project level dependencies with pinned dependencies`() {
    with(controller) {
      val fakeFirestore =
        Project(name = "firebase-firestore", group = "com.google.firebase", version = "1.0.0")
      val testProject =
        Project(name = "testProject", version = "1.0.0", projectDependencies = setOf(fakeFirestore))

      withProjects(fakeFirestore, testProject)
      createReleaseWithProjects(testProject)
      buildProject()

      testProject.buildFile.readText() shouldNotContain fakeFirestore.toDependency(true)
    }
  }

  @Test
  fun `Does not replace dependencies in same library group`() {
    with(controller) {
      val fakeFirestore =
        Project(
          name = "firebase-firestore",
          libraryGroup = "test",
          group = "com.google.firebase",
          version = "1.0.0"
        )
      val testProject =
        Project(
          name = "testProject",
          libraryGroup = "test",
          version = "1.0.0",
          projectDependencies = setOf(fakeFirestore)
        )

      withProjects(fakeFirestore, testProject)
      createReleaseWithProjects(fakeFirestore, testProject)
      buildProject()

      testProject.buildFile.readText() shouldContain fakeFirestore.toDependency(true)
    }
  }

  @Test
  fun `Does not change already pinned dependencies`() {
    with(controller) {
      val fakeFirestore =
        Project(
          name = "firebase-firestore",
          libraryGroup = "test",
          group = "com.google.firebase",
          version = "1.0.0"
        )
      val testProject =
        Project(
          name = "testProject",
          libraryGroup = "test",
          version = "1.0.0",
          externalDependencies = setOf(fakeFirestore.toArtifact())
        )

      withProjects(fakeFirestore, testProject)
      createReleaseWithProjects(testProject, fakeFirestore)
      buildProject()

      testProject.buildFile.readText() shouldContain fakeFirestore.toDependency(false)
    }
  }

  private fun buildProject() =
    GradleRunner.create()
      .withProjectDir(testProjectDirectory.root)
      .withPluginClasspath()
      .withArguments("updatePinnedDependencies")
      .build()
}
