/*
 * Copyright 2020 Google LLC
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

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PublishingPluginTests {
  @Rule @JvmField val testProjectDir = TemporaryFolder()
  private val controller = FirebaseTestController(testProjectDir)

  @Test
  fun `Publishing dependent projects succeeds`() {
    with(controller) {
      val project1 = Project(name = "childProject1", version = "1.0")
      val project2 =
        Project(
          name = "childProject2",
          version = "0.9",
          projectDependencies = setOf(project1),
          customizePom = """
  licenses {
    license {
      name = 'Hello'
    }
  }
  """
        )

      withProjects(project1, project2)
      publish(project1, project2)

      project1.pom.let {
        it.artifact.version shouldBe project1.version
        it.license shouldBe
          License(
            "The Apache Software License, Version 2.0",
            "http://www.apache.org/licenses/LICENSE-2.0.txt"
          )
      }
      project2.pom.let {
        it.artifact.version shouldBe project2.version
        it.license shouldBe License("Hello", "")
        it.dependencies shouldHaveSingleElement project1.toArtifact()
      }
    }
  }

  @Test
  fun `Publishing dependent projects one of which is a jar succeeds`() {
    with(controller) {
      val project1 =
        Project(name = "childProject1", version = "1.0", libraryType = LibraryType.JAVA)
      val project2 =
        Project(
          name = "childProject2",
          version = "0.9",
          projectDependencies = setOf(project1),
          customizePom = """
  licenses {
    license {
      name = 'Hello'
    }
  }
  """
        )

      withProjects(project1, project2)
      publish(project1, project2)

      project1.pom.let {
        it.artifact.version shouldBe project1.version
        it.license shouldBe
          License(
            "The Apache Software License, Version 2.0",
            "http://www.apache.org/licenses/LICENSE-2.0.txt"
          )
      }
      project2.pom.let {
        it.artifact.version shouldBe project2.version
        it.license shouldBe License("Hello", "")
        it.dependencies shouldHaveSingleElement project1.toArtifact()
      }
    }
  }

  @Test
  fun `Publish with unreleased dependency`() {
    with(controller) {
      val project1 = Project(name = "childProject1", version = "1.0")
      val project2 =
        Project(name = "childProject2", version = "0.9", projectDependencies = setOf(project1))

      withProjects(project1, project2)
      publish(project2)

      project1.pomOrNull().shouldNotBeNull()
      project2.pom.dependencies shouldHaveSingleElement project1.toArtifact()
    }
  }

  @Test
  fun `Publish with very transitive dependency`() {
    with(controller) {
      val project1 = Project(name = "childProject1", version = "1.0", libraryGroup = "libraryGroup")
      val project2 =
        Project(name = "childProject2", version = "0.9", projectDependencies = setOf(project1))
      val project3 = Project(name = "childProject3", version = "1.0", libraryGroup = "libraryGroup")

      withProjects(project1, project2, project3)
      publish(project2)

      project1.pomOrNull().shouldNotBeNull()
      project3.pomOrNull().shouldNotBeNull()
      project2.pom.dependencies shouldHaveSingleElement project1.toArtifact()
    }
  }

  @Test
  fun `Publish with released dependency`() {
    with(controller) {
      val project1 = Project(name = "childProject1", version = "1.0", latestReleasedVersion = "0.8")
      val project2 =
        Project(name = "childProject2", version = "0.9", projectDependencies = setOf(project1))

      withProjects(project1, project2)
      publish(project2, project1)

      project1.pomOrNull().shouldNotBeNull()
      project2.pom.dependencies shouldHaveSingleElement project1.toArtifact()
    }
  }

  @Test
  fun `Publish project should also publish coreleased projects`() {
    with(controller) {
      val project1 = Project(name = "childProject1", version = "1.0.0", libraryGroup = "test123")
      val project2 =
        Project(
          name = "childProject2",
          projectDependencies = setOf(project1),
          libraryGroup = "test123",
          expectedVersion = "1.0.0"
        )

      withProjects(project1, project2)
      publish(project1)

      project1.pom.artifact.version shouldBe project1.version
      project2.pom.let {
        it.artifact.version shouldBe project1.version
        it.dependencies shouldHaveSingleElement project1.toArtifact()
      }
    }
  }

  @Test
  fun `Publish project should correctly set dependency types`() {
    with(controller) {
      val dagger = Artifact("com.google.dagger", "dagger", "2.22", scope = "runtime")
      val daggerAndroid =
        Artifact("com.google.dagger", "dagger-android-support", "2.22", Type.AAR, "compile")

      val project1 = Project(name = "childProject1", version = "1.0", latestReleasedVersion = "0.8")
      val project2 =
        Project(
          name = "childProject2",
          version = "0.9",
          projectDependencies = setOf(project1),
          externalDependencies = setOf(dagger, daggerAndroid)
        )

      withProjects(project1, project2)
      publish(project2, project1)

      project2.pom.let {
        it.artifact.version shouldBe project2.version
        it.dependencies shouldContainExactlyInAnyOrder
          listOf(project1.toArtifact(), dagger, daggerAndroid)
      }
    }
  }

  @Test
  fun `Publish project should ignore dependency versions`() {
    with(controller) {
      val externalAARLibrary = Artifact("com.google.dagger", "dagger-android-support", "2.21")

      val project1 =
        Project(
          name = "childProject1",
          version = "1.0",
          externalDependencies = setOf(externalAARLibrary)
        )
      val project2 =
        Project(
          name = "childProject2",
          version = "1.0",
          externalDependencies = setOf(externalAARLibrary.copy(version = "2.22"))
        )

      withProjects(project1, project2)
      publish(project1, project2)

      project2.pom.dependencies.first().type shouldBe Type.AAR
    }
  }

  private fun publish(vararg projects: Project): BuildResult =
    makeGradleRunner(*projects).build().also {
      it.task(":firebasePublish")?.outcome shouldBe SUCCESS
    }

  private fun publishAndFail(vararg projects: Project) =
    makeGradleRunner(*projects).buildAndFail().also {
      it.task(":checkHeadDependencies")?.outcome shouldBe FAILED
    }

  private fun makeGradleRunner(vararg projects: Project) =
    GradleRunner.create()
      .withProjectDir(testProjectDir.root)
      .withArguments(
        "-PprojectsToPublish=${projects.joinToString(",") { it.name }}",
        "-PreleaseName=m123",
        "firebasePublish"
      )
      .withPluginClasspath()
}
