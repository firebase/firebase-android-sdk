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
import com.google.firebase.gradle.plugins.publish.Mode
import java.io.File
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PublishingPluginTests {
    @Rule
    @JvmField
    val testProjectDir = TemporaryFolder()

    private val subprojects = mutableListOf<Project>()
    private lateinit var rootBuildFile: File
    private lateinit var rootSettingsFile: File

    @Test
    fun `Publishing dependent projects succeeds`() {
        val project1 = Project(name = "childProject1", version = "1.0")
        val project2 = Project(
                name = "childProject2",
                version = "0.9",
                projectDependencies = setOf(project1),
                customizePom = """
licenses {
  license {
    name = 'Hello'
  }
}
""")
        subprojectsDefined(project1, project2)
        val result = publish(Mode.RELEASE, project1, project2)
        assertThat(result.task(":firebasePublish")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val pomOrNull1 = project1.getPublishedPom("${testProjectDir.root}/build/m2repository")
        val pomOrNull2 = project2.getPublishedPom("${testProjectDir.root}/build/m2repository")
        assertThat(pomOrNull1).isNotNull()
        assertThat(pomOrNull2).isNotNull()
        val pom1 = pomOrNull1!!
        val pom2 = pomOrNull2!!

        assertThat(pom1.artifact.version).isEqualTo(project1.version)
        assertThat(pom2.artifact.version).isEqualTo(project2.version)
        assertThat(pom1.license).isEqualTo(License(
                "The Apache Software License, Version 2.0",
                "http://www.apache.org/licenses/LICENSE-2.0.txt"))
        assertThat(pom2.license).isEqualTo(License(
                "Hello",
                ""))

        assertThat(pom2.dependencies).isEqualTo(
                listOf(Artifact(
                        groupId = project1.group,
                        artifactId = project1.name,
                        version = project1.version,
                        type = Type.AAR,
                        scope = "compile")))
    }

    @Test
    fun `Publishing dependent projects one of which is a jar succeeds`() {
        val project1 = Project(name = "childProject1", version = "1.0", libraryType = LibraryType.JAVA)
        val project2 = Project(
                name = "childProject2",
                version = "0.9",
                projectDependencies = setOf(project1),
                customizePom = """
licenses {
  license {
    name = 'Hello'
  }
}
""")
        subprojectsDefined(project1, project2)
        val result = publish(Mode.RELEASE, project1, project2)
        assertThat(result.task(":firebasePublish")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val pomOrNull1 = project1.getPublishedPom("${testProjectDir.root}/build/m2repository")
        val pomOrNull2 = project2.getPublishedPom("${testProjectDir.root}/build/m2repository")
        assertThat(pomOrNull1).isNotNull()
        assertThat(pomOrNull2).isNotNull()
        val pom1 = pomOrNull1!!
        val pom2 = pomOrNull2!!

        assertThat(pom1.artifact.version).isEqualTo(project1.version)
        assertThat(pom2.artifact.version).isEqualTo(project2.version)
        assertThat(pom1.license).isEqualTo(License(
                "The Apache Software License, Version 2.0",
                "http://www.apache.org/licenses/LICENSE-2.0.txt"))
        assertThat(pom2.license).isEqualTo(License(
                "Hello",
                ""))

        assertThat(pom2.dependencies).isEqualTo(
                listOf(Artifact(
                        groupId = project1.group,
                        artifactId = project1.name,
                        version = project1.version,
                        type = Type.JAR,
                        scope = "compile")))
    }

    @Test
    fun `Publish with unreleased dependency`() {
        val project1 = Project(name = "childProject1", version = "1.0")
        val project2 = Project(
                name = "childProject2",
                version = "0.9",
                projectDependencies = setOf(project1))

        subprojectsDefined(project1, project2)
        val exception = Assert.assertThrows(UnexpectedBuildFailure::class.java) {
            publish(Mode.RELEASE, project2)
        }
        assertThat(exception.message).contains("Failed to release com.example:childProject2")
    }

    @Test
    fun `Publish with released dependency`() {
        val project1 = Project(name = "childProject1", version = "1.0", latestReleasedVersion = "0.8")
        val project2 = Project(
                name = "childProject2",
                version = "0.9",
                projectDependencies = setOf(project1))
        subprojectsDefined(project1, project2)

        val result = publish(Mode.RELEASE, project2)
        assertThat(result.task(":firebasePublish")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val pomOrNull1 = project1.getPublishedPom("${testProjectDir.root}/build/m2repository")
        val pomOrNull2 = project2.getPublishedPom("${testProjectDir.root}/build/m2repository")
        assertThat(pomOrNull1).isNull()
        assertThat(pomOrNull2).isNotNull()

        val pom2 = pomOrNull2!!
        assertThat(pom2.dependencies).isEqualTo(
                listOf(Artifact(
                        groupId = project1.group,
                        artifactId = project1.name,
                        version = project1.latestReleasedVersion!!,
                        type = Type.AAR,
                        scope = "compile")))
    }

    @Test
    fun `Publish all dependent snapshot projects succeeds`() {
        val project1 = Project(name = "childProject1", version = "1.0")
        val project2 = Project(
                name = "childProject2",
                version = "0.9",
                projectDependencies = setOf(project1))
        subprojectsDefined(project1, project2)
        val result = publish(Mode.SNAPSHOT, project1, project2)
        assertThat(result.task(":firebasePublish")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val pomOrNull1 = project1.getPublishedPom("${testProjectDir.root}/build/m2repository")
        val pomOrNull2 = project2.getPublishedPom("${testProjectDir.root}/build/m2repository")
        assertThat(pomOrNull1).isNotNull()
        assertThat(pomOrNull2).isNotNull()

        val pom1 = pomOrNull1!!
        val pom2 = pomOrNull2!!

        assertThat(pom1.artifact.version).isEqualTo("${project1.version}-SNAPSHOT")
        assertThat(pom2.artifact.version).isEqualTo("${project2.version}-SNAPSHOT")

        assertThat(pom2.dependencies).isEqualTo(
                listOf(Artifact(
                        groupId = project1.group,
                        artifactId = project1.name,
                        version = "${project1.version}-SNAPSHOT",
                        type = Type.AAR,
                        scope = "compile")))
    }

    @Test
    fun `Publish snapshots with released dependency`() {
        val project1 = Project(name = "childProject1", version = "1.0", latestReleasedVersion = "0.8")
        val project2 = Project(
                name = "childProject2",
                version = "0.9",
                projectDependencies = setOf(project1))
        subprojectsDefined(project1, project2)

        val result = publish(Mode.SNAPSHOT, project2)
        assertThat(result.task(":firebasePublish")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val pomOrNull1 = project1.getPublishedPom("${testProjectDir.root}/build/m2repository")
        val pomOrNull2 = project2.getPublishedPom("${testProjectDir.root}/build/m2repository")
        assertThat(pomOrNull1).isNull()
        assertThat(pomOrNull2).isNotNull()

        val pom2 = pomOrNull2!!

        assertThat(pom2.artifact.version).isEqualTo("${project2.version}-SNAPSHOT")
        assertThat(pom2.dependencies).isEqualTo(
                listOf(Artifact(
                        groupId = project1.group,
                        artifactId = project1.name,
                        version = project1.latestReleasedVersion!!,
                        type = Type.AAR,
                        scope = "compile")))
    }

    @Test
    fun `Publish project should also publish coreleased projects`() {
        val project1 = Project(name = "childProject1", version = "1.0")
        val project2 = Project(
                name = "childProject2",
                version = "0.9",
                projectDependencies = setOf(project1),
                releaseWith = project1)
        subprojectsDefined(project1, project2)

        val result = publish(Mode.RELEASE, project1)
        assertThat(result.task(":firebasePublish")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val pomOrNull1 = project1.getPublishedPom("${testProjectDir.root}/build/m2repository")
        val pomOrNull2 = project2.getPublishedPom("${testProjectDir.root}/build/m2repository")
        assertThat(pomOrNull1).isNotNull()
        assertThat(pomOrNull2).isNotNull()

        val pom1 = pomOrNull1!!
        val pom2 = pomOrNull2!!

        assertThat(pom1.artifact.version).isEqualTo(project1.version)
        assertThat(pom2.artifact.version).isEqualTo(project1.version)
        assertThat(pom2.dependencies).isEqualTo(
                listOf(Artifact(
                        groupId = project1.group,
                        artifactId = project1.name,
                        version = project1.version,
                        type = Type.AAR,
                        scope = "compile")))
    }

    @Test
    fun `Publish project should correctly set dependency types`() {
        val project1 = Project(name = "childProject1", version = "1.0", latestReleasedVersion = "0.8")
        val project2 = Project(
                name = "childProject2",
                version = "0.9",
                projectDependencies = setOf(project1),
                externalDependencies = setOf(
                        Artifact("com.google.dagger", "dagger", "2.22"),
                        Artifact("com.google.dagger", "dagger-android-support", "2.22"),
                        Artifact("com.android.support", "multidex", "1.0.3")
                ))
        subprojectsDefined(project1, project2)

        val result = publish(Mode.RELEASE, project2)
        assertThat(result.task(":firebasePublish")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val pomOrNull2 = project2.getPublishedPom("${testProjectDir.root}/build/m2repository")
        assertThat(pomOrNull2).isNotNull()

        val pom2 = pomOrNull2!!
        assertThat(pom2.artifact.version).isEqualTo(project2.version)
        assertThat(pom2.dependencies).containsExactly(
                Artifact(
                        groupId = project1.group,
                        artifactId = project1.name,
                        version = project1.latestReleasedVersion!!,
                        type = Type.AAR,
                        scope = "compile"),
                Artifact(
                        groupId = "com.google.dagger",
                        artifactId = "dagger",
                        version = "2.22",
                        type = Type.JAR,
                        scope = "compile"),
                Artifact(
                        groupId = "com.google.dagger",
                        artifactId = "dagger-android-support",
                        version = "2.22",
                        type = Type.AAR,
                        scope = "compile")
        )
    }

    private fun publish(mode: Mode, vararg projects: Project): BuildResult =
            GradleRunner.create()
                    .withProjectDir(testProjectDir.root)
                    .withArguments(
                            "-PprojectsToPublish=${projects.joinToString(",") { it.name }}",
                            "-PpublishMode=$mode",
                            "firebasePublish")
                    .withPluginClasspath()
                    .build()

    private fun include(project: Project) {
        testProjectDir.newFolder(project.name, "src", "main")
        testProjectDir
                .newFile("${project.name}/build.gradle")
                .writeText(project.generateBuildFile())
        testProjectDir
                .newFile("${project.name}/src/main/AndroidManifest.xml")
                .writeText(MANIFEST)
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
        private const val ROOT_PROJECT = """
        buildscript {
            repositories {
                google()
                jcenter()
                maven {
                    url 'https://storage.googleapis.com/android-ci/mvn/'
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
              }
          }
        }
        """
        private const val MANIFEST = """<?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  package="com.example">
            <uses-sdk android:minSdkVersion="14"/>
        </manifest>
        """
    }
}
