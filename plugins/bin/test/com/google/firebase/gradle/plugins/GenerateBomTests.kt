/*
 * Copyright 2025 Google LLC
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

import com.google.firebase.gradle.bomgenerator.GenerateBomTask
import com.google.firebase.gradle.plugins.datamodels.ArtifactDependency
import com.google.firebase.gradle.plugins.datamodels.DependencyManagementElement
import com.google.firebase.gradle.plugins.datamodels.PomElement
import com.google.firebase.gradle.plugins.services.GMavenService
import com.google.firebase.gradle.plugins.services.GroupIndexArtifact
import com.google.firebase.gradle.shouldBeText
import com.google.firebase.gradle.shouldThrowSubstring
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.file.shouldExist
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GenerateBomTests : FunSpec() {
  @Rule @JvmField val testProjectDir = TemporaryFolder()

  private val service = mockk<GMavenService>()

  @Before
  fun setup() {
    clearMocks(service)
  }

  @Test
  fun `ignores the configured ignored dependencies`() {
    val ignoredDependency =
      GroupIndexArtifact(
        groupId = "com.google.firebase",
        artifactId = "firebase-functions",
        versions = listOf("1.0.0"),
      )

    val dependencies =
      listOf(
        GroupIndexArtifact(
          groupId = "com.google.firebase",
          artifactId = "firebase-common",
          versions = listOf("21.0.0", "21.0.1"),
        )
      )

    makeOldBom(dependencies)
    linkGroupIndex(dependencies + ignoredDependency)

    val file =
      makeNewBom(
        bomArtifacts = listOf("com.google.firebase:firebase-common"),
        ignoredArtifacts = listOf("com.google.firebase:firebase-functions"),
      )

    val newPom = PomElement.fromFile(file)
    val deps = newPom.dependencyManagement?.dependencies.shouldNotBeEmpty()
    deps.shouldNotContain(ignoredDependency.toArtifactDependency())
  }

  @Test
  fun `major bumps the bom version when artifacts are removed`() {
    val dependencies =
      listOf(
        GroupIndexArtifact(
          groupId = "com.google.firebase",
          artifactId = "firebase-common",
          versions = listOf("21.0.0", "21.0.1"),
        ),
        GroupIndexArtifact(
          groupId = "com.google.firebase",
          artifactId = "firebase-functions",
          versions = listOf("1.0.0", "1.0.1"),
        ),
      )

    makeOldBom(dependencies)
    linkGroupIndex(dependencies)

    val file =
      makeNewBom(
        bomArtifacts = listOf("com.google.firebase:firebase-common"),
        ignoredArtifacts = listOf("com.google.firebase:firebase-functions"),
      )

    val newPom = PomElement.fromFile(file)
    val deps = newPom.dependencyManagement?.dependencies.shouldNotBeEmpty().map { it.artifactId }
    deps.shouldNotContain("firebase-functions")

    newPom.version shouldBeEqual "2.0.0"
  }

  @Test
  fun `minor bumps the bom version when artifacts are added`() {
    val dependencies =
      listOf(
        GroupIndexArtifact(
          groupId = "com.google.firebase",
          artifactId = "firebase-common",
          versions = listOf("21.0.0", "21.0.1"),
        )
      )

    val newArtifact =
      GroupIndexArtifact(
        groupId = "com.google.firebase",
        artifactId = "firebase-functions",
        versions = listOf("1.0.0"),
      )

    makeOldBom(dependencies)
    linkGroupIndex(dependencies + newArtifact)

    val file =
      makeNewBom(
        bomArtifacts =
          listOf("com.google.firebase:firebase-common", "com.google.firebase:firebase-functions")
      )

    val newPom = PomElement.fromFile(file)
    val deps = newPom.dependencyManagement?.dependencies.shouldNotBeEmpty().map { it.artifactId }
    deps.shouldContain("firebase-functions")

    newPom.version shouldBeEqual "1.1.0"
  }

  @Test
  fun `bumps the bom version per the biggest artifact bump`() {
    val dependencies =
      listOf(
        GroupIndexArtifact(
          groupId = "com.google.firebase",
          artifactId = "firebase-common",
          versions = listOf("21.0.0", "21.0.1"),
        ),
        GroupIndexArtifact(
          groupId = "com.google.firebase",
          artifactId = "firebase-functions",
          versions = listOf("10.1.2", "11.0.0"),
        ),
      )

    makeOldBom(dependencies)
    linkGroupIndex(dependencies)

    val file =
      makeNewBom(
        bomArtifacts =
          listOf("com.google.firebase:firebase-common", "com.google.firebase:firebase-functions")
      )

    val newPom = PomElement.fromFile(file)
    newPom.version shouldBeEqual "2.0.0"
  }

  @Test
  fun `allows versions to be overridden`() {
    val dependencies =
      listOf(
        GroupIndexArtifact(
          groupId = "com.google.firebase",
          artifactId = "firebase-common",
          versions = listOf("21.0.0", "21.0.1"),
        )
      )

    makeOldBom(dependencies)
    linkGroupIndex(dependencies)

    val file =
      makeNewBom(bomArtifacts = listOf("com.google.firebase:firebase-common")) {
        versionOverrides.set(mapOf("com.google.firebase:firebase-common" to "22.0.0"))
      }

    val newPom = PomElement.fromFile(file)
    val deps = newPom.dependencyManagement?.dependencies.shouldNotBeEmpty()
    deps.shouldContain(
      ArtifactDependency(
        groupId = "com.google.firebase",
        artifactId = "firebase-common",
        version = "22.0.0",
      )
    )
  }

  @Test
  fun `generates in the expected format`() {
    val dependencies =
      listOf(
        GroupIndexArtifact(
          groupId = "com.google.firebase",
          artifactId = "firebase-common",
          versions = listOf("21.0.0", "21.0.1"),
        ),
        GroupIndexArtifact(
          groupId = "com.google.firebase",
          artifactId = "firebase-functions",
          versions = listOf("1.0.0", "1.0.1"),
        ),
      )

    makeOldBom(dependencies)
    linkGroupIndex(dependencies)

    val file =
      makeNewBom(
        bomArtifacts =
          listOf("com.google.firebase:firebase-common", "com.google.firebase:firebase-functions")
      )

    file.readText().trim() shouldBeText
      """
      <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion>4.0.0</modelVersion>
        <groupId>com.google.firebase</groupId>
        <artifactId>firebase-bom</artifactId>
        <version>1.0.1</version>
        <packaging>pom</packaging>
        <licenses>
          <license>
            <name>The Apache Software License, Version 2.0</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <dependencyManagement>
          <dependencies>
            <dependency>
              <groupId>com.google.firebase</groupId>
              <artifactId>firebase-common</artifactId>
              <version>21.0.1</version>
            </dependency>
            <dependency>
              <groupId>com.google.firebase</groupId>
              <artifactId>firebase-functions</artifactId>
              <version>1.0.1</version>
            </dependency>
          </dependencies>
        </dependencyManagement>
      </project>
    """
        .trimIndent()
  }

  @Test
  fun `throws an error if artifacts are not live on gmaven yet`() {
    val dependencies =
      listOf(
        GroupIndexArtifact(
          groupId = "com.google.firebase",
          artifactId = "firebase-common",
          versions = listOf("21.0.0", "21.0.1"),
        )
      )

    makeOldBom(dependencies)
    linkGroupIndex(dependencies)

    shouldThrowSubstring("not live on gmaven yet", "com.google.firebase:firebase-functions") {
      makeNewBom(
        bomArtifacts =
          listOf("com.google.firebase:firebase-common", "com.google.firebase:firebase-functions")
      )
    }
  }

  @Test
  fun `throws an error if there are firebase artifacts missing`() {
    val dependencies =
      listOf(
        GroupIndexArtifact(
          groupId = "com.google.firebase",
          artifactId = "firebase-common",
          versions = listOf("21.0.0", "21.0.1"),
        ),
        GroupIndexArtifact(
          groupId = "com.google.firebase",
          artifactId = "firebase-functions",
          versions = listOf("1.0.0", "1.0.1"),
        ),
      )

    makeOldBom(dependencies)
    linkGroupIndex(dependencies)

    shouldThrowSubstring("artifacts missing", "com.google.firebase:firebase-functions") {
      makeNewBom(bomArtifacts = listOf("com.google.firebase:firebase-common"))
    }
  }

  private fun makeTask(configure: GenerateBomTask.() -> Unit): TaskProvider<GenerateBomTask> {
    val project = ProjectBuilder.builder().withProjectDir(testProjectDir.root).build()

    return project.tasks.register<GenerateBomTask>("generateBom") {
      outputDirectory.set(project.layout.buildDirectory.dir("bom"))
      gmaven.set(service)

      configure()
    }
  }

  private fun makeNewBom(
    bomArtifacts: List<String> = emptyList(),
    ignoredArtifacts: List<String> = emptyList(),
    configure: GenerateBomTask.() -> Unit = {},
  ): File {
    val task = makeTask {
      this.bomArtifacts.set(bomArtifacts)
      this.ignoredArtifacts.set(ignoredArtifacts)

      configure()
    }

    val file =
      task.get().let {
        it.generate()
        it.outputDirectory.get().nestedFile
      }

    file.shouldExist()

    return file
  }

  private fun linkGroupIndex(dependencies: List<GroupIndexArtifact>) {
    every { service.groupIndex("com.google.firebase") } answers { dependencies }
    every { service.groupIndexArtifactOrNull(any()) } answers { null }

    for (artifact in dependencies) {
      every {
        service.groupIndexArtifactOrNull("${artifact.groupId}:${artifact.artifactId}")
      } answers { artifact }
    }
  }

  private fun makeOldBom(dependencies: List<GroupIndexArtifact>): PomElement {
    val artifacts =
      dependencies.map { it.toArtifactDependency().copy(version = it.versions.first()) }
    val bom =
      PomElement.fromFile(emptyBom)
        .copy(dependencyManagement = DependencyManagementElement(artifacts))

    every { service.latestPom("com.google.firebase", "firebase-bom") } answers { bom }

    return bom
  }

  companion object {
    private val resourcesDirectory = File("src/test/resources")
    private val testResources = resourcesDirectory.childFile("Bom")
    private val emptyBom = testResources.childFile("empty-bom.pom")
  }
}
