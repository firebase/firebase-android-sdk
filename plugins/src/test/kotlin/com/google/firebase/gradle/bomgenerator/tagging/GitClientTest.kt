/*
 * Copyright 2021 Google LLC
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

package com.google.firebase.gradle.bomgenerator.tagging

import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class GitClientTest {
  @Rule @JvmField val testGitDirectory = TemporaryFolder()
  private val branch = AtomicReference<String>()
  private val commit = AtomicReference<String>()

  private lateinit var executor: ShellExecutor
  private val handler: (List<String>) -> Unit = { it.forEach(System.out::println) }

  @Before
  fun setup() {
    testGitDirectory.newFile("hello.txt").writeText("hello git!")
    executor = ShellExecutor(testGitDirectory.root, System.out::println)

    executor.execute("git init", handler)
    executor.execute("git config user.email 'GitClientTest@example.com'", handler)
    executor.execute("git config user.name 'GitClientTest'", handler)
    executor.execute("git add .", handler)
    executor.execute("git commit -m 'init_commit'", handler)
    executor.execute("git status", handler)

    executor.execute("git rev-parse --abbrev-ref HEAD", handler) { branch.set(it[0]) }
    executor.execute("git rev-parse HEAD", handler) { commit.set(it[0]) }
  }

  @Test
  fun `tag M release version succeeds on local file system`() {
    val git = GitClient(branch.get(), commit.get(), executor, System.out::println)
    git.tagReleaseVersion()
    executor.execute("git tag --points-at HEAD", handler) {
      Assert.assertTrue(it.stream().anyMatch { x -> x.contains(branch.get()) })
    }
  }

  @Test
  fun `tag bom version succeeds on local file system`() {
    val git = GitClient(branch.get(), commit.get(), executor, System.out::println)
    git.tagBomVersion("1.2.3")
    executor.execute("git tag --points-at HEAD", handler) {
      Assert.assertTrue(it.stream().anyMatch { x -> x.contains("bom@1.2.3") })
    }
  }

  @Test
  fun `tag product version succeeds on local file system`() {
    val git = GitClient(branch.get(), commit.get(), executor, System.out::println)
    git.tagProductVersion("firebase-database", "1.2.3")
    executor.execute("git tag --points-at HEAD", handler) {
      Assert.assertTrue(it.stream().anyMatch { x -> x.contains("firebase-database@1.2.3") })
    }
  }

  @Test
  fun `tags are pushed to the remote repository`() {
    Assume.assumeTrue(System.getenv().containsKey("FIREBASE_CI"))

    val mockExecutor =
      object : ShellExecutor(testGitDirectory.root, System.out::println) {
        override fun execute(command: String, consumer: Consumer<List<String>>) {
          consumer.accept(listOf("Received command: $command"))
        }
      }

    val outputs = mutableListOf<String>()
    val git = GitClient(branch.get(), commit.get(), mockExecutor) { outputs.add(it) }
    git.tagBomVersion("1.2.3")
    git.tagProductVersion("firebase-functions", "1.2.3")
    git.pushCreatedTags()

    Assert.assertTrue(outputs.stream().anyMatch { it.contains("git push origin") })
  }
}
