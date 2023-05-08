// Copyright 2020 Google LLC
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
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PublishingPluginTests {
  @Rule @JvmField val testProjectDir = TemporaryFolder()

  private val subprojects = mutableListOf<Project>()
  private lateinit var rootBuildFile: File
  private lateinit var rootSettingsFile: File

  @Test
  fun `Publishing dependent projects succeeds`() {
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
    subprojectsDefined(project1, project2)
    val result = publish(project1, project2)
    assertThat(result.task(":firebasePublish")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    val pomOrNull1 = project1.getPublishedPom("${testProjectDir.root}/build/m2repository")
    val pomOrNull2 = project2.getPublishedPom("${testProjectDir.root}/build/m2repository")
    assertThat(pomOrNull1).isNotNull()
    assertThat(pomOrNull2).isNotNull()
    val pom1 = pomOrNull1!!
    val pom2 = pomOrNull2!!

    assertThat(pom1.artifact.version).isEqualTo(project1.version)
    assertThat(pom2.artifact.version).isEqualTo(project2.version)
    assertThat(pom1.license)
      .isEqualTo(
        License(
          "The Apache Software License, Version 2.0",
          "http://www.apache.org/licenses/LICENSE-2.0.txt"
        )
      )
    assertThat(pom2.license).isEqualTo(License("Hello", ""))

    assertThat(pom2.dependencies)
      .isEqualTo(
        listOf(
          Artifact(
            groupId = project1.group,
            artifactId = project1.name,
            version = project1.version,
            type = Type.AAR,
            scope = "compile"
          )
        )
      )
  }

  @Test
  fun `Publishing dependent projects one of which is a jar succeeds`() {
    val project1 = Project(name = "childProject1", version = "1.0", libraryType = LibraryType.JAVA)
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
    subprojectsDefined(project1, project2)
    val result = publish(project1, project2)
    assertThat(result.task(":firebasePublish")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    val pomOrNull1 = project1.getPublishedPom("${testProjectDir.root}/build/m2repository")
    val pomOrNull2 = project2.getPublishedPom("${testProjectDir.root}/build/m2repository")
    assertThat(pomOrNull1).isNotNull()
    assertThat(pomOrNull2).isNotNull()
    val pom1 = pomOrNull1!!
    val pom2 = pomOrNull2!!

    assertThat(pom1.artifact.version).isEqualTo(project1.version)
    assertThat(pom2.artifact.version).isEqualTo(project2.version)
    assertThat(pom1.license)
      .isEqualTo(
        License(
          "The Apache Software License, Version 2.0",
          "http://www.apache.org/licenses/LICENSE-2.0.txt"
        )
      )
    assertThat(pom2.license).isEqualTo(License("Hello", ""))

    assertThat(pom2.dependencies)
      .isEqualTo(
        listOf(
          Artifact(
            groupId = project1.group,
            artifactId = project1.name,
            version = project1.version,
            type = Type.JAR,
            scope = "compile"
          )
        )
      )
  }

  @Test
  fun `Publish with unreleased dependency`() {
    val project1 = Project(name = "childProject1", version = "1.0")
    val project2 =
      Project(name = "childProject2", version = "0.9", projectDependencies = setOf(project1))

    subprojectsDefined(project1, project2)
    val result = publishAndFail(project2)
    assertThat(result.task(":checkHeadDependencies")?.outcome).isEqualTo(TaskOutcome.FAILED)
  }

  @Test
  fun `Publish with released dependency`() {
    val project1 = Project(name = "childProject1", version = "1.0", latestReleasedVersion = "0.8")
    val project2 =
      Project(name = "childProject2", version = "0.9", projectDependencies = setOf(project1))
    subprojectsDefined(project1, project2)

    val result = publish(project2, project1)
    assertThat(result.task(":firebasePublish")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    val pomOrNull1 = project1.getPublishedPom("${testProjectDir.root}/build/m2repository")
    val pomOrNull2 = project2.getPublishedPom("${testProjectDir.root}/build/m2repository")
    assertThat(pomOrNull1).isNotNull()
    assertThat(pomOrNull2).isNotNull()

    val pom2 = pomOrNull2!!
    assertThat(pom2.dependencies)
      .isEqualTo(
        listOf(
          Artifact(
            groupId = project1.group,
            artifactId = project1.name,
            version = project1.version,
            type = Type.AAR,
            scope = "compile"
          )
        )
      )
  }

  @Test
  fun `Publish project should also publish coreleased projects`() {
    val project1 = Project(name = "childProject1", version = "1.0.0", libraryGroup = "test123")
    val project2 =
      Project(
        name = "childProject2",
        projectDependencies = setOf(project1),
        libraryGroup = "test123",
        expectedVersion = "1.0.0"
      )
    subprojectsDefined(project1, project2)

    val result = publish(project1)
    assertThat(result.task(":firebasePublish")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    val pomOrNull1 = project1.getPublishedPom("${testProjectDir.root}/build/m2repository")
    val pomOrNull2 = project2.getPublishedPom("${testProjectDir.root}/build/m2repository")
    assertThat(pomOrNull1).isNotNull()
    assertThat(pomOrNull2).isNotNull()

    val pom1 = pomOrNull1!!
    val pom2 = pomOrNull2!!

    assertThat(pom1.artifact.version).isEqualTo(project1.version)
    assertThat(pom2.artifact.version).isEqualTo(project1.version)
    assertThat(pom2.dependencies)
      .isEqualTo(
        listOf(
          Artifact(
            groupId = project1.group,
            artifactId = project1.name,
            version = project1.version,
            type = Type.AAR,
            scope = "compile"
          )
        )
      )
  }

  @Test
  fun `Publish project should correctly set dependency types`() {
    val project1 = Project(name = "childProject1", version = "1.0", latestReleasedVersion = "0.8")
    val project2 =
      Project(
        name = "childProject2",
        version = "0.9",
        projectDependencies = setOf(project1),
        externalDependencies =
          setOf(
            Artifact("com.google.dagger", "dagger", "2.22"),
            Artifact("com.google.dagger", "dagger-android-support", "2.22")
          )
      )
    subprojectsDefined(project1, project2)

    val result = publish(project2, project1)
    assertThat(result.task(":firebasePublish")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    val pomOrNull2 = project2.getPublishedPom("${testProjectDir.root}/build/m2repository")
    assertThat(pomOrNull2).isNotNull()

    val pom2 = pomOrNull2!!
    assertThat(pom2.artifact.version).isEqualTo(project2.version)
    assertThat(pom2.dependencies)
      .containsExactly(
        Artifact(
          groupId = project1.group,
          artifactId = project1.name,
          version = project1.version,
          type = Type.AAR,
          scope = "compile"
        ),
        Artifact(
          groupId = "com.google.dagger",
          artifactId = "dagger",
          version = "2.22",
          type = Type.JAR,
          scope = "compile"
        ),
        Artifact(
          groupId = "com.google.dagger",
          artifactId = "dagger-android-support",
          version = "2.22",
          type = Type.AAR,
          scope = "compile"
        )
      )
  }

  @Test
  fun `Publish project should ignore dependency versions`() {
    val externalAARLibrary = Artifact("com.google.dagger", "dagger-android-support", "2.21")

    val project1 =
      Project(
        name = "childProject1",
        version = "1.0",
        libraryType = LibraryType.ANDROID,
        externalDependencies = setOf(externalAARLibrary)
      )
    val project2 =
      Project(
        name = "childProject2",
        version = "1.0",
        libraryType = LibraryType.ANDROID,
        externalDependencies = setOf(externalAARLibrary.copy(version = "2.22"))
      )

    subprojectsDefined(project1, project2)
    val result = publish(project1, project2)
    assertThat(result.task(":firebasePublish")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    val pomOrNull2 = project2.getPublishedPom("${testProjectDir.root}/build/m2repository")
    assertThat(pomOrNull2).isNotNull()

    val pom2 = pomOrNull2!!

    assertThat(pom2.dependencies.first().type).isEqualTo(Type.AAR)
  }

  private fun publish(vararg projects: Project): BuildResult = makeGradleRunner(*projects).build()

  private fun publishAndFail(vararg projects: Project) = makeGradleRunner(*projects).buildAndFail()

  private fun makeGradleRunner(vararg projects: Project) =
    GradleRunner.create()
      .withProjectDir(testProjectDir.root)
      .withArguments(
        "-PprojectsToPublish=${projects.joinToString(",") { it.name }}",
        "firebasePublish"
      )
      .withPluginClasspath()

  private fun include(project: Project) {
    testProjectDir.newFolder(project.name, "src", "main")
    testProjectDir.newFile("${project.name}/build.gradle").writeText(project.generateBuildFile())
    testProjectDir.newFile("${project.name}/src/main/AndroidManifest.xml").writeText(MANIFEST)
    subprojects.add(project)
  }

  private fun subprojectsDefined(vararg projects: Project) {
    rootBuildFile = testProjectDir.newFile("build.gradle")
    rootSettingsFile = testProjectDir.newFile("settings.gradle")

    projects.forEach(this::include)

    rootBuildFile.writeText(ROOT_PROJECT)
    rootSettingsFile.writeText(projects.joinToString("\n") { "include ':${it.name}'" })
  }

  companion object {
    private const val ROOT_PROJECT =
      """
        buildscript {
            repositories {
                google()
                jcenter()
                maven {
                    url 'https://storage.googleapis.com/android-ci/mvn/'
                    metadataSources {
                        artifact()
                    }
                }
            }
        }
        plugins {
            id 'PublishingPlugin'
        }

        configure(subprojects) {
            repositories {
                google()
                jcenter()
                maven {
                    url 'https://storage.googleapis.com/android-ci/mvn/'
                    metadataSources {
                          artifact()
                    }
                }
            }
        }
        """
    private const val MANIFEST =
      """<?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  package="com.example">
            <uses-sdk android:minSdkVersion="14"/>
        </manifest>
        """
  }
}
