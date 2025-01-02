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

import io.kotest.assertions.json.shouldBeValidJson
import io.kotest.assertions.json.shouldContainJsonKey
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@Serializable private data class ExportedLicense(val length: Int, val start: Int)

class LicenseResolverPluginTests : FunSpec() {
  @Rule @JvmField val testProjectDir = TemporaryFolder()

  private val controller = FirebaseTestController(testProjectDir)

  @Test
  fun `Generating licenses`() {
    val result = generateLicenses()
    result.task(":test-lib:generateLicenses")?.outcome shouldBe TaskOutcome.SUCCESS

    val output = File("${testProjectDir.root}/test-lib/build/generated/third_party_licenses/")

    val jsonFile = output.childFile("third_party_licenses.json")
    val txtFile = output.childFile("third_party_licenses.txt")

    jsonFile.shouldExist()
    txtFile.shouldExist()

    val jsonStr = jsonFile.readText()

    jsonStr.shouldBeValidJson()

    val json = Json.decodeFromString<Map<String, ExportedLicense>>(jsonStr)

    jsonStr shouldContainJsonKey "customLib"
    val customLib = json["customLib"].shouldNotBeNull()

    val txt = txtFile.readText()

    txt.shouldNotBeBlank()
    txt shouldContain "customLib"
    txt shouldContain "Test license"

    val licenseContent = txt.substring(customLib.start, customLib.start + customLib.length).trim()
    licenseContent shouldBeEqual "Test license"
  }

  @Test
  fun `License tasks throw useful exception if file URI not found`() {
    val result = shouldThrowAny { generateLicenses(File("non_existent_path.txt").absoluteUnixPath) }

    result.message shouldContain "License file not found"
  }

  private fun generateLicenses(license: String = defaultLicense): BuildResult {
    val project =
      TestProject(
        name = "test-lib",
        plugins =
          """
          |id("com.android.library")
          |id("LicenseResolverPlugin")
          """
            .trimMargin(),
        libraryType = LibraryType.ANDROID,
        extra =
          """
          |thirdPartyLicenses {
          |  add("customLib", "$license")
          |}
          """
            .trimMargin(),
      )

    controller.withProjects(project)
    controller.sourceFiles(
      project,
      SourceFile("com/example/Foo.java", "package com.example; class Foo {}"),
    )

    return runGradle(testProjectDir.root, "generateLicenses")
  }

  companion object {
    val defaultLicense = File("src/test/fixtures/license.txt").absoluteUnixPath
  }
}
