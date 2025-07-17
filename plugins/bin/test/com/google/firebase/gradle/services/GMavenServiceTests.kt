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

package com.google.firebase.gradle.services

import com.google.firebase.gradle.endsWith
import com.google.firebase.gradle.plugins.childFile
import com.google.firebase.gradle.plugins.services.DocumentService
import com.google.firebase.gradle.plugins.services.GMavenServiceController
import io.kotest.assertions.asClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.file.shouldHaveSameContentAs
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GMavenServiceTests : FunSpec() {
  @Rule @JvmField val testProjectDir = TemporaryFolder()
  private val service = spyk(DocumentService())
  private val gmaven by lazy { GMavenServiceController(testProjectDir.root.toPath(), service) }

  @Before
  fun setup() {
    clearMocks(service)

    every { service.openStream(any()) } throws (FileNotFoundException())
    every { service.openStream(endsWith("group-index.xml")) } answers
      {
        testGroupIndex.inputStream()
      }
  }

  @Test
  fun `fetches group index`() {
    val artifacts = gmaven.forceGetGroupIndex("com.google.firebase")
    verify { service.openStream(endsWith("group-index.xml")) }

    artifacts.shouldNotBeEmpty()

    val artifact = artifacts.find { it.artifactId == "firebase-common" }

    artifact.shouldNotBeNull()
    artifact.asClue {
      it.versions.shouldContain("21.0.0")
      it.latestVersion shouldBeEqual "21.0.0"
    }
  }

  @Test
  fun `fetches a released pom file`() {
    every { service.openStream(endsWith("firebase-common-21.0.0.pom")) } answers
      {
        testPOM.inputStream()
      }

    val pomElement = gmaven.pomOrNull("com.google.firebase", "firebase-common", "21.0.0")

    verify { service.openStream(endsWith("firebase-common-21.0.0.pom")) }

    pomElement.shouldNotBeNull()
    pomElement.asClue {
      it.version shouldBe "21.0.0"
      it.artifactId shouldBe "firebase-common"
      it.packaging shouldBe "aar"
      it.dependencies.shouldNotBeEmpty()
    }
  }

  @Test
  fun `fetches a released aar file`() {
    every { service.openStream(endsWith("firebase-common-21.0.0.aar")) } answers
      {
        testAAR.inputStream()
      }

    val aar = gmaven.artifactOrNull("com.google.firebase", "firebase-common", "21.0.0")

    verify { service.openStream(endsWith("firebase-common-21.0.0.aar")) }

    aar.shouldNotBeNull()
    aar.shouldExist()
    aar.shouldHaveSameContentAs(testAAR)
  }

  @Test
  fun `fetches a released jar file`() {
    every { service.openStream(endsWith("firebase-common-21.0.0.jar")) } answers
      {
        testAAR.inputStream()
      }

    val jar = gmaven.artifactOrNull("com.google.firebase", "firebase-common", "21.0.0")

    verify { service.openStream(endsWith("firebase-common-21.0.0.jar")) }

    jar.shouldNotBeNull()
    jar.shouldExist()
    jar.shouldHaveSameContentAs(testAAR)
  }

  @Test
  fun `returns null when artifact files are not found`() {
    val artifact = gmaven.artifactOrNull("com.google.firebase", "firebase-common", "21.0.0")

    verify { service.openStream(endsWith("firebase-common-21.0.0.aar")) }

    artifact.shouldBeNull()
  }

  @Test
  fun `fetches the latest released version`() {
    val version = gmaven.latestVersionOrNull("com.google.firebase", "firebase-common")

    verify(exactly = 1) { service.openStream(any()) }

    version.shouldNotBeNull()
    version shouldBeEqual "21.0.0"
  }

  @Test
  fun `checks if an artifact has been published`() {
    gmaven.hasReleasedArtifact("com.google.firebase", "artifact-that-doesnt-exist") shouldBe false
    gmaven.hasReleasedArtifact("com.google.firebase", "firebase-common") shouldBe true

    verify(exactly = 1) { service.openStream(any()) }
  }

  @Test
  fun `checks if a version has been released`() {
    gmaven.hasReleasedVersion("com.google.firebase", "firebase-common", "21.0.0") shouldBe true
    gmaven.hasReleasedVersion("com.google.firebase", "firebase-common", "22.0.0") shouldBe false

    verify(exactly = 1) { service.openStream(any()) }
  }

  @Test
  fun `caches pom requests`() {
    every { service.openStream(endsWith("firebase-common-21.0.0.pom")) } answers
      {
        testPOM.inputStream()
      }

    val firstPom = gmaven.pomOrNull("com.google.firebase", "firebase-common", "21.0.0")
    val secondPom = gmaven.pomOrNull("com.google.firebase", "firebase-common", "21.0.0")

    verify(exactly = 1) { service.openStream(endsWith("firebase-common-21.0.0.pom")) }

    firstPom.shouldNotBeNull()
    secondPom.shouldNotBeNull()

    firstPom.shouldBeEqual(secondPom)
  }

  @Test
  fun `caches artifact requests`() {
    every { service.openStream(endsWith("firebase-common-21.0.0.aar")) } answers
      {
        testAAR.inputStream()
      }

    val firstArtifact = gmaven.artifactOrNull("com.google.firebase", "firebase-common", "21.0.0")
    val secondArtifact = gmaven.artifactOrNull("com.google.firebase", "firebase-common", "21.0.0")

    verify(exactly = 1) { service.openStream(endsWith("firebase-common-21.0.0.aar")) }

    firstArtifact.shouldNotBeNull()
    secondArtifact.shouldNotBeNull()

    firstArtifact.shouldBeEqual(secondArtifact)
  }

  @Test
  fun `does not overwrite previous force fetches`() {
    every { service.openStream(endsWith("firebase-common-21.0.0.aar")) } answers
      {
        testAAR.inputStream()
      }

    val firstArtifact = gmaven.forceGetArtifact("com.google.firebase", "firebase-common", "21.0.0")
    val secondArtifact = gmaven.forceGetArtifact("com.google.firebase", "firebase-common", "21.0.0")

    verify(exactly = 2) { service.openStream(endsWith("firebase-common-21.0.0.aar")) }

    firstArtifact.shouldNotBeNull()
    secondArtifact.shouldNotBeNull()

    firstArtifact.shouldExist()
    secondArtifact.shouldExist()

    firstArtifact.shouldNotBeEqual(secondArtifact)
  }

  @Test
  fun `should be thread safe`() {
    every { service.openStream(endsWith("firebase-common-21.0.0.aar")) } answers
      {
        testAAR.inputStream()
      }

    every { service.openStream(endsWith("firebase-common-20.0.0.aar")) } answers
      {
        testAAR.inputStream()
      }

    val executor = Executors.newFixedThreadPool(10)

    val firstBatch =
      (0 until 100).map {
        executor.queue { gmaven.artifactOrNull("com.google.firebase", "firebase-common", "21.0.0") }
      }

    val secondBatch =
      (0 until 100).map {
        executor.queue { gmaven.artifactOrNull("com.google.firebase", "firebase-common", "20.0.0") }
      }

    verify(exactly = 0) { service.openStream(any()) }

    executor.shutdown()
    executor.awaitTermination(5, TimeUnit.SECONDS)

    verify(exactly = 3) { service.openStream(any()) }

    val firstArtifact = gmaven.artifactOrNull("com.google.firebase", "firebase-common", "21.0.0")
    val secondArtifact = gmaven.artifactOrNull("com.google.firebase", "firebase-common", "20.0.0")

    firstArtifact.shouldNotBeNull()
    secondArtifact.shouldNotBeNull()

    firstBatch.forAll {
      it.isDone shouldBe true
      firstArtifact.shouldBeEqual(it.get().shouldNotBeNull())
    }

    secondBatch.forAll {
      it.isDone shouldBe true
      secondArtifact.shouldBeEqual(it.get().shouldNotBeNull())
    }
  }

  @Suppress("ControlFlowWithEmptyBody")
  private fun <T> ExecutorService.queue(task: () -> T) =
    submit<T> {
      while (!isShutdown) {}
      task()
    }

  companion object {
    private val resourcesDirectory = File("src/test/resources")
    private val testResources = resourcesDirectory.childFile("GMaven")
    private val testAAR = testResources.childFile("firebase-common.aar")
    private val testPOM = testResources.childFile("firebase-common.pom")
    private val testGroupIndex = testResources.childFile("group-index.xml")
  }
}
