/*
 * Copyright 2024 Google LLC
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
package com.google.firebase.appdistribution.gradle

import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.google.common.collect.ImmutableList
import com.google.firebase.appdistribution.gradle.TestGradleProject.Companion.writeFile
import kotlin.test.assertFailsWith
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildResultException
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AddTestersTaskTest {
  @get:Rule val testGradleProject = TestGradleProject()
  val testGroovyBuild = TestGroovyBuild(testGradleProject)

  @get:Rule val wireMockRule = WireMockRule(ApiStubs.WIRE_MOCK_PORT)

  private val apiStubs = ApiStubs(testGradleProject)

  @Before
  fun setup() {
    System.setProperty(
      "FIREBASE_APP_DISTRIBUTION_API_URL",
      "http://localhost:${ApiStubs.WIRE_MOCK_PORT}"
    )
  }

  @Test
  fun testAddTesters_completesSuccessfully() {
    testGroovyBuild.writeBuildFiles()
    testGradleProject.writeServiceCredentialsFile()
    apiStubs.stubAddTestersSuccess(123, ImmutableList.of("a@e.mail", "b@e.mail", "c@e.mail"))
    val result =
      runGradleCommand(
        "appDistributionAddTesters",
        "--projectNumber",
        "123",
        "--emails",
        "a@e.mail,b@e.mail,c@e.mail",
        "--serviceCredentialsFile",
        testGradleProject.serviceCredentialsFile.absolutePath,
        "--info"
      )
    assertEquals(TaskOutcome.SUCCESS, result.task(":app:appDistributionAddTesters")!!.outcome)
    assertThat(result.output, containsString("Adding 3 testers to project 123..."))
    assertThat(result.output, containsString("Testers added successfully [200]"))
  }

  @Test
  fun testAddTesters_failsIfTooManyTesters() {
    testGroovyBuild.writeBuildFiles()
    testGradleProject.writeServiceCredentialsFile()
    val tooManyEmailsBuffer = StringBuffer()
    for (i in 0..1000) {
      tooManyEmailsBuffer.append("tester" + i + "e.mail\n")
    }
    val tooManyEmailsFile = testGradleProject.createFile("too-many-emails.txt")
    writeFile(tooManyEmailsFile, tooManyEmailsBuffer.toString())
    val e =
      assertFailsWith(UnexpectedBuildResultException::class) {
        runGradleCommand(
          "appDistributionAddTesters",
          "--projectNumber",
          "123",
          "--file",
          tooManyEmailsFile.absolutePath,
          "--serviceCredentialsFile",
          testGradleProject.serviceCredentialsFile.absolutePath
        )
      }
    assertThat(
      e.buildResult.output,
      containsString("Cannot add 1001 testers, 1000 is the maximum allowed")
    )
  }

  @Test
  fun testAddTesters_failsIfBackendRequestFails() {
    testGroovyBuild.writeBuildFiles()
    testGradleProject.writeServiceCredentialsFile()
    apiStubs.stubAddTestersFailure(123, 429)
    val e =
      assertFailsWith(UnexpectedBuildResultException::class) {
        runGradleCommand(
          "appDistributionAddTesters",
          "--projectNumber",
          "123",
          "--emails",
          "a@e.mail,b@e.mail,c@e.mail",
          "--serviceCredentialsFile",
          testGradleProject.serviceCredentialsFile.absolutePath
        )
      }
    assertThat(
      e.buildResult.output,
      containsString("App Distribution failed to add testers: [429]")
    )
  }

  @Test
  fun testAddTesters_failsIfProjectNumberIsInvalid() {
    testGroovyBuild.writeBuildFiles()
    testGradleProject.writeServiceCredentialsFile()
    val e =
      assertFailsWith(UnexpectedBuildResultException::class) {
        runGradleCommand(
          "appDistributionAddTesters",
          "--emails",
          "a@e.mail,b@e.mail,c@e.mail",
          "--serviceCredentialsFile",
          testGradleProject.serviceCredentialsFile.absolutePath
        )
      }
    assertThat(
      e.buildResult.output,
      containsString("property 'projectNumber' doesn't have a configured value")
    )
  }

  @Test
  fun testAddTesters_failsIfEmailsMissing() {
    testGroovyBuild.writeBuildFiles()
    testGradleProject.writeServiceCredentialsFile()
    val e =
      assertFailsWith(UnexpectedBuildResultException::class) {
        runGradleCommand(
          "appDistributionAddTesters",
          "--projectNumber",
          "123",
          "--serviceCredentialsFile",
          testGradleProject.serviceCredentialsFile.absolutePath
        )
      }
    assertThat(e.buildResult.output, containsString("Could not find tester emails"))
  }

  @Test
  fun testAddTesters_failsIfEmailsFileDoesNotExist() {
    testGroovyBuild.writeBuildFiles()
    testGradleProject.writeServiceCredentialsFile()
    val e =
      assertFailsWith(UnexpectedBuildResultException::class) {
        runGradleCommand(
          "appDistributionAddTesters",
          "--projectNumber",
          "123",
          "--file",
          "/path/does/not/exist.txt",
          "--serviceCredentialsFile",
          testGradleProject.serviceCredentialsFile.absolutePath
        )
      }
    assertThat(
      e.buildResult.output,
      containsString("Failed to read file \"/path/does/not/exist.txt\"")
    )
  }

  @Test
  fun testAddTesters_failsIfCredentialsMissing() {
    testGroovyBuild.writeBuildFiles()
    val e =
      assertFailsWith(UnexpectedBuildResultException::class) {
        runGradleCommand(
          "appDistributionAddTesters",
          "--projectNumber",
          "123",
          "--emails",
          "a@e.mail,b@e.mail,c@e.mail",
          "--serviceCredentialsFile",
          "/invalid/path",
          "--stacktrace"
        )
      }
    assertThat(e.buildResult.output, containsString("Service credentials file does not exist"))
  }

  private fun runGradleCommand(vararg args: String) =
    GradleRunner.create()
      .withProjectDir(testGradleProject.projectDir.root)
      .withArguments(*args)
      .withPluginClasspath(testGradleProject.pluginClasspathFiles)
      .build()
}
