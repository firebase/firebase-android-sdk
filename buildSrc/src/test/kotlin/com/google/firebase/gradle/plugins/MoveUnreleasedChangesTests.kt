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

import com.google.firebase.gradle.plugins.ChangeType.FIXED
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.string.shouldMatch
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MoveUnreleasedChangesTests : FunSpec() {

  @Test
  fun `Moves Unreleased changes to a new section`() {
    val newChangelog = buildWithChangelog(basicChangelog)
    validateChangelog(basicChangelog, newChangelog)
  }

  @Test
  fun `Handles KTX libs`() {
    val ktxEntry = ReleaseContent("Fixed some bugs:", listOf(Change(FIXED, "that annoying bug")))

    val changelog = basicChangelog.replaceUnreleasedWith { it.copy(ktx = ktxEntry) }

    val newChangelog = buildWithChangelog(changelog)
    validateChangelog(changelog, newChangelog)

    val releasedEntry = newChangelog.releases[1]

    releasedEntry.ktx shouldBe ktxEntry
  }

  @Test
  fun `Uses template text on KTX libs with no changes`() {
    basicChangelog.releases.first().ktx shouldBe null

    val newChangelog = buildWithChangelog(basicChangelog)
    validateChangelog(basicChangelog, newChangelog)

    val releasedEntry = newChangelog.releases[1]

    releasedEntry.ktx shouldNotBe null
    releasedEntry.ktx?.let {
      it.subtext shouldNot beEmpty()
      it.changes.shouldBeEmpty()
    }
  }

  @Test
  fun `Handles empty Unreleased sections`() {
    val changelog = basicChangelog.replaceUnreleasedWith { ReleaseEntry.Empty }

    val newChangelog = buildWithChangelog(changelog)

    newChangelog shouldBe changelog
  }

  @Test
  fun `Is idempotent`() {
    val firstBuild = buildWithChangelog(basicChangelog)
    val secondBuild = buildWithChangelog(firstBuild)

    firstBuild shouldBe secondBuild
  }

  private fun Changelog.replaceUnreleasedWith(
    transform: (ReleaseEntry) -> ReleaseEntry
  ): Changelog {
    val (unreleased, previousReleases) = releases.separateAt(1)
    val newUnreleased = transform(unreleased.single())

    return copy(releases = listOf(newUnreleased) + previousReleases)
  }

  private fun buildWithChangelog(changelog: Changelog): Changelog {
    val currentProject = testProjectDirectory.root.childFile("firebase-storage")
    currentProject.childFile("CHANGELOG.md").writeText(changelog.toString())
    buildProject()

    return Changelog.fromFile(currentProject.childFile("CHANGELOG.md"))
  }

  private fun buildProject() =
    GradleRunner.create()
      .withProjectDir(testProjectDirectory.root)
      .withPluginClasspath()
      .withArguments("moveUnreleasedChanges")
      .build()

  private fun validateChangelog(original: Changelog, newest: Changelog) {
    newest.releases shouldHaveAtLeastSize 2
    newest.releases shouldHaveSize original.releases.size + 1
    newest.releases.first().hasContent() shouldBe false

    val releasedEntry = newest.releases[1]
    val originalEntry = original.releases.first()

    releasedEntry.content.changes shouldContainExactly originalEntry.content.changes
    releasedEntry.content.subtext shouldMatch originalEntry.content.subtext
  }

  companion object {
    @ClassRule @JvmField val testProjectDirectory = TemporaryFolder()
    private val resourcesDirectory = File("src/test/resources")
    private val basicProject = resourcesDirectory.childFile("BasicProject")
    private val testResources = resourcesDirectory.childFile("MoveUnreleasedChanges")
    private val releaseJson = testResources.childFile("release.json")

    private val basicChangelog = Changelog.fromFile(testResources.childFile("basic.md"))

    @BeforeClass
    @JvmStatic
    fun setup() {
      copyFixtureToTempDirectory()
    }

    private fun copyFixtureToTempDirectory() {
      basicProject.copyRecursively(testProjectDirectory.root)
      releaseJson.copyToDirectory(testProjectDirectory.root)
    }
  }
}
