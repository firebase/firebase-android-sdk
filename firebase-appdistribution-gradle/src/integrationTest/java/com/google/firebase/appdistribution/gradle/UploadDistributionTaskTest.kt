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
import com.google.firebase.appdistribution.gradle.ApiStubs.Companion.WIRE_MOCK_PORT
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UploadDistributionTaskTest {
  @get:Rule val wireMockRule = WireMockRule(WIRE_MOCK_PORT)

  @get:Rule val testGradleProject = TestGradleProject()
  val testGroovyBuild = TestGroovyBuild(testGradleProject)
  private val apiStubs = ApiStubs(testGradleProject)

  @Before
  fun setup() {
    System.setProperty("FIREBASE_APP_DISTRIBUTION_API_URL", "http://localhost:${WIRE_MOCK_PORT}")
  }

  // *************************************************************************
  // Test matrix cases for testing on the latest and older gradle/AGP versions
  // *************************************************************************
  @Test
  fun testApkPathParsing_withApk_onOlderAgpAndGradleVersions() {
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = OLDER_AGP_VERSION,
      googleServicesVersion = OLDER_GOOGLE_SERVICES_VERSION,
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments("assembleDebug", "appDistributionUploadDebug", "--info", "--stacktrace")
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(OLDER_GRADLE_VERSION)
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Using APK"))
    assertThat(result.output, containsString("build/outputs/apk/debug/app-debug.apk"))
  }

  @Test
  fun testApkPathParsing_withApk_onLatestAgpAndGradleVersions() {
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = LATEST_AGP_VERSION,
      googleServicesVersion = LATEST_GOOGLE_SERVICES_VERSION,
      compileSdkVersion = COMPILE_SDK_VERSION_FOR_LATEST_TEST
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments("assembleDebug", "appDistributionUploadDebug", "--info", "--stacktrace")
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(LATEST_GRADLE_VERSION)
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Using APK"))
    assertThat(result.output, containsString("build/outputs/apk/debug/app-debug.apk"))
  }

  @Test
  fun testApkPathParsing_withApk_withConfigurationCache_onLatestAgpAndGradleVersions() {
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = LATEST_AGP_VERSION,
      googleServicesVersion = LATEST_GOOGLE_SERVICES_VERSION,
      compileSdkVersion = COMPILE_SDK_VERSION_FOR_LATEST_TEST
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments(
          "assembleDebug",
          "appDistributionUploadDebug",
          "--configuration-cache",
          "--info",
          "--stacktrace"
        )
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(LATEST_GRADLE_VERSION)
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Using APK"))
    assertThat(result.output, containsString("build/outputs/apk/debug/app-debug.apk"))
  }

  @Test
  fun testApkPathParsing_noApk_onOlderAgpAndGradleVersions() {
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = OLDER_AGP_VERSION,
      googleServicesVersion = OLDER_GOOGLE_SERVICES_VERSION,
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments("appDistributionUploadDebug", "--info", "--stacktrace")
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(OLDER_GRADLE_VERSION)
        .buildAndFail()

    assertEquals(FAILED, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Could not find an APK"))
  }

  @Test
  fun testApkPathParsing_noApk_onLatestAgpAndGradleVersions() {
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = LATEST_AGP_VERSION,
      googleServicesVersion = LATEST_GOOGLE_SERVICES_VERSION,
      compileSdkVersion = COMPILE_SDK_VERSION_FOR_LATEST_TEST
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments("appDistributionUploadDebug", "--info", "--stacktrace")
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(LATEST_GRADLE_VERSION)
        .buildAndFail()

    assertEquals(FAILED, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Could not find an APK"))
  }

  @Test
  fun testApkPathParsing_withCustomOutputName_onOlderAgpAndGradleVersions() {
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = OLDER_AGP_VERSION,
      googleServicesVersion = OLDER_GOOGLE_SERVICES_VERSION,
      customAndroidBlock =
        "" +
          "applicationVariants.all { variant ->" +
          "  variant.outputs.all { output ->" +
          "    outputFileName = \"app-\${variant.buildType.name}_GradleTest_\${variant.versionCode}.apk\"" +
          "  }" +
          "}",
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments("assembleDebug", "appDistributionUploadDebug", "--info", "--stacktrace")
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(OLDER_GRADLE_VERSION)
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Using APK"))
    assertThat(result.output, containsString("build/outputs/apk/debug/app-debug_GradleTest_5.apk"))
  }

  @Test
  fun testAabPathParsing_withAab_onOlderAgpAndGradleVersions() {
    apiStubs.stubGetAabInfoSuccess()
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = OLDER_AGP_VERSION,
      googleServicesVersion = OLDER_GOOGLE_SERVICES_VERSION,
      artifactType = "AAB"
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments("bundleDebug", "appDistributionUploadDebug", "--info", "--stacktrace")
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(OLDER_GRADLE_VERSION)
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Using AAB"))
    assertThat(result.output, containsString("build/outputs/bundle/debug/app-debug.aab"))
  }

  @Test
  fun testAabPathParsing_withAab_onLatestAgpAndGradleVersions() {
    apiStubs.stubGetAabInfoSuccess()
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = LATEST_AGP_VERSION,
      googleServicesVersion = LATEST_GOOGLE_SERVICES_VERSION,
      compileSdkVersion = COMPILE_SDK_VERSION_FOR_LATEST_TEST,
      artifactType = "AAB"
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments(
          ":app:bundleDebug",
          ":app:appDistributionUploadDebug",
          "--info",
          "--stacktrace"
        )
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(LATEST_GRADLE_VERSION)
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Using AAB"))
    assertThat(result.output, containsString("build/outputs/bundle/debug/app-debug.aab"))
  }

  @Test
  fun testAabPathParsing_withAab_withConfigurationCache_onLatestAgpAndGradleVersions() {
    apiStubs.stubGetAabInfoSuccess()
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = LATEST_AGP_VERSION,
      googleServicesVersion = LATEST_GOOGLE_SERVICES_VERSION,
      compileSdkVersion = COMPILE_SDK_VERSION_FOR_LATEST_TEST,
      artifactType = "AAB"
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments(
          "bundleDebug",
          "appDistributionUploadDebug",
          "--configuration-cache",
          "--info",
          "--stacktrace"
        )
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(LATEST_GRADLE_VERSION)
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Using AAB"))
    assertThat(result.output, containsString("build/outputs/bundle/debug/app-debug.aab"))
  }

  @Test
  fun testAabPathParsing_noAab_onOlderAgpAndGradleVersions() {
    apiStubs.stubGetAabInfoSuccess()
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = OLDER_AGP_VERSION,
      googleServicesVersion = OLDER_GOOGLE_SERVICES_VERSION,
      artifactType = "AAB"
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments("appDistributionUploadDebug", "--info", "--stacktrace")
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(OLDER_GRADLE_VERSION)
        .buildAndFail()

    assertEquals(FAILED, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Using AAB"))
    assertThat(result.output, containsString("build/outputs/bundle/debug/app-debug.aab"))
    assertThat(
      result.output,
      not(containsString("WARNING: API 'variantOutput.getPackageApplication()' is obsolete"))
    )
    assertThat(result.output, containsString("Could not find the AAB"))
  }

  @Test
  fun testAabPathParsing_noAab_onLatestAgpAndGradleVersions() {
    apiStubs.stubGetAabInfoSuccess()
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = LATEST_AGP_VERSION,
      googleServicesVersion = LATEST_GOOGLE_SERVICES_VERSION,
      compileSdkVersion = COMPILE_SDK_VERSION_FOR_LATEST_TEST,
      artifactType = "AAB"
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments("appDistributionUploadDebug", "--info", "--stacktrace")
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(LATEST_GRADLE_VERSION)
        .buildAndFail()

    assertEquals(FAILED, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Using AAB"))
    assertThat(result.output, containsString("build/outputs/bundle/debug/app-debug.aab"))
    assertThat(result.output, containsString("Could not find the AAB"))
  }

  @Test
  fun testAabPathParsing_withAabCommandLineOverride_onOlderAgpAndGradleVersions() {
    apiStubs.stubGetAabInfoSuccess()
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = OLDER_AGP_VERSION,
      googleServicesVersion = OLDER_GOOGLE_SERVICES_VERSION,
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments(
          "bundleDebug",
          "appDistributionUploadDebug",
          "--artifactType=AAB",
          "--info",
          "--stacktrace"
        )
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(OLDER_GRADLE_VERSION)
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Using AAB"))
    assertThat(result.output, containsString("build/outputs/bundle/debug/app-debug.aab"))
  }

  @Test
  fun testAabPathParsing_withAabCommandLineOverride_onLatestAgpAndGradleVersions() {
    apiStubs.stubGetAabInfoSuccess()
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = LATEST_AGP_VERSION,
      googleServicesVersion = LATEST_GOOGLE_SERVICES_VERSION,
      compileSdkVersion = COMPILE_SDK_VERSION_FOR_LATEST_TEST,
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments(
          "bundleDebug",
          "appDistributionUploadDebug",
          "--artifactType=AAB",
          "--info",
          "--stacktrace"
        )
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(LATEST_GRADLE_VERSION)
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Using AAB"))
    assertThat(result.output, containsString("build/outputs/bundle/debug/app-debug.aab"))
  }

  @Test
  fun testArtifactPath_withRelativePath_onLatestAgpAndGradleVersions() {
    apiStubs.stubGetAabInfoSuccess()
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = LATEST_AGP_VERSION,
      googleServicesVersion = LATEST_GOOGLE_SERVICES_VERSION,
      compileSdkVersion = COMPILE_SDK_VERSION_FOR_LATEST_TEST,
      artifactType = "APK",
      artifactPath = "app/build/outputs/apk/debug/app-debug.apk"
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments("assembleDebug", "appDistributionUploadDebug", "--info", "--stacktrace")
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(LATEST_GRADLE_VERSION)
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Using APK"))
    assertThat(result.output, containsString("build/outputs/apk/debug/app-debug.apk"))
  }

  @Test
  fun testArtifactPath_withRelativePath_withConfigurationCache_onLatestAgpAndGradleVersions() {
    apiStubs.stubGetAabInfoSuccess()
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = LATEST_AGP_VERSION,
      googleServicesVersion = LATEST_GOOGLE_SERVICES_VERSION,
      compileSdkVersion = COMPILE_SDK_VERSION_FOR_LATEST_TEST,
      artifactType = "APK",
      artifactPath = "app/build/outputs/apk/debug/app-debug.apk"
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments(
          "assembleDebug",
          "appDistributionUploadDebug",
          "--configuration-cache",
          "--info",
          "--stacktrace"
        )
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(LATEST_GRADLE_VERSION)
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Using APK"))
    assertThat(result.output, containsString("build/outputs/apk/debug/app-debug.apk"))
  }

  @Test
  fun testArtifactPath_withPathToNonexistentFile_onLatestAgpAndGradleVersions() {
    apiStubs.stubGetAabInfoSuccess()
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = LATEST_AGP_VERSION,
      googleServicesVersion = LATEST_GOOGLE_SERVICES_VERSION,
      compileSdkVersion = COMPILE_SDK_VERSION_FOR_LATEST_TEST,
      artifactType = "APK",
      artifactPath = "bad/artifact/path"
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments("assembleDebug", "appDistributionUploadDebug", "--info", "--stacktrace")
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(LATEST_GRADLE_VERSION)
        .buildAndFail()

    assertEquals(FAILED, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Could not find the APK"))
  }

  @Test
  fun testArtifactPath_withPathInvalidPath_onLatestAgpAndGradleVersions() {
    apiStubs.stubGetAabInfoSuccess()
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = LATEST_AGP_VERSION,
      googleServicesVersion = LATEST_GOOGLE_SERVICES_VERSION,
      compileSdkVersion = COMPILE_SDK_VERSION_FOR_LATEST_TEST,
      artifactType = "APK",
      artifactPath = "\\0"
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments("assembleDebug", "appDistributionUploadDebug", "--info", "--stacktrace")
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(LATEST_GRADLE_VERSION)
        .buildAndFail()

    assertThat(result.output, containsString("is an invalid path"))
  }

  // *************************************************************************
  // Specific AAB parsing test cases for nuanced AGP/gradle versioning
  // *************************************************************************
  @Test
  fun testAabPathParsing_forAgp713andGradle72() {
    apiStubs.stubGetAabInfoSuccess()
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = "7.1.3",
      artifactType = "AAB",
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments("bundleDebug", "appDistributionUploadDebug", "--info", "--stacktrace")
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion("7.2")
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Using AAB"))
    assertThat(result.output, containsString("build/outputs/bundle/debug/app-debug.aab"))
  }

  @Test
  fun testAabPathParsing_forAgp722andGradle733() {
    apiStubs.stubGetAabInfoSuccess()
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = "7.2.2",
      artifactType = "AAB",
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments("bundleDebug", "appDistributionUploadDebug", "--info", "--stacktrace")
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion("7.3.3")
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Using AAB"))
    assertThat(result.output, containsString("build/outputs/bundle/debug/app-debug.aab"))
  }

  @Test
  fun testAabPathParsing_forAgp731andGradle74() {
    apiStubs.stubGetAabInfoSuccess()
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = "7.3.1",
      googleServicesVersion = LATEST_GOOGLE_SERVICES_VERSION,
      artifactType = "AAB",
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments("bundleDebug", "appDistributionUploadDebug", "--info", "--stacktrace")
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion("7.4")
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Using AAB"))
    assertThat(result.output, containsString("build/outputs/bundle/debug/app-debug.aab"))
  }

  @Test
  fun testGoogleServices440AppIdParsing_forAgp730() {
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = "7.3.0", // Note: this fails with AGP 7.4.0
      googleServicesVersion = "4.4.0",
      useGoogleServicesPlugin = true,
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments("assembleDebug", "appDistributionUploadDebug", "--info", "--stacktrace")
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion("7.4")
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
  }

  // *************************************************************************
  // Miscellaneous integration tests
  // *************************************************************************
  @Test
  fun testGoogleServicesAppIdParsing_onLatestAgpAndGradleVersions() {
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = LATEST_AGP_VERSION,
      googleServicesVersion = LATEST_GOOGLE_SERVICES_VERSION,
      compileSdkVersion = COMPILE_SDK_VERSION_FOR_LATEST_TEST,
      useGoogleServicesPlugin = true,
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments("assembleDebug", "appDistributionUploadDebug", "--info", "--stacktrace")
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(LATEST_GRADLE_VERSION)
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
  }

  @Test
  fun testGoogleServicesAppIdParsing_onOlderAgpAndGradleVersions() {
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = OLDER_AGP_VERSION,
      googleServicesVersion = OLDER_GOOGLE_SERVICES_VERSION,
      useGoogleServicesPlugin = true,
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments("assembleDebug", "appDistributionUploadDebug", "--info", "--stacktrace")
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(OLDER_GRADLE_VERSION)
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
  }

  @Test
  fun testAutomatedTests_onOlderAgpAndGradleVersions() {
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    apiStubs.stubTestReleaseSuccess()
    apiStubs.stubReleaseTestLookupSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = OLDER_AGP_VERSION,
      googleServicesVersion = OLDER_GOOGLE_SERVICES_VERSION,
      useGoogleServicesPlugin = true,
      testDevices = "model=pixel",
      testCases = "test-case-1"
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments("assembleDebug", "appDistributionUploadDebug", "--info", "--stacktrace")
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(OLDER_GRADLE_VERSION)
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Started test case test-case-1 successfully"))
    assertThat(result.output, containsString("Automated test(s) passed!"))
  }

  @Test
  fun testers_withTestersFile_onLatestAgpAndGradleVersions() {
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    apiStubs.stubDistributeReleaseSuccess()
    val file = testGradleProject.createAndWriteFile("app/src/testers.txt", "a@a.com,b@b.com")
    testGroovyBuild.writeBuildFiles(
      agpVersion = LATEST_AGP_VERSION,
      googleServicesVersion = LATEST_GOOGLE_SERVICES_VERSION,
      compileSdkVersion = COMPILE_SDK_VERSION_FOR_LATEST_TEST,
      testersFile = file.absolutePath
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments("assembleDebug", "appDistributionUploadDebug", "--info", "--stacktrace")
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(LATEST_GRADLE_VERSION)
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Added testers/groups successfully"))
  }

  @Test
  fun testers_withTestersFile_usingCommandLineOverrides_onLatestAgpAndGradleVersions() {
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    apiStubs.stubDistributeReleaseSuccess()
    val file = testGradleProject.createAndWriteFile("app/src/testers.txt", "a@a.com,b@b.com")
    testGroovyBuild.writeBuildFiles(
      agpVersion = LATEST_AGP_VERSION,
      compileSdkVersion = COMPILE_SDK_VERSION_FOR_LATEST_TEST,
      googleServicesVersion = LATEST_GOOGLE_SERVICES_VERSION
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments(
          "assembleDebug",
          "appDistributionUploadDebug",
          "--info",
          "--testersFile=${file.absolutePath}",
          "--stacktrace"
        )
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(LATEST_GRADLE_VERSION)
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Added testers/groups successfully"))
  }

  companion object {
    // Latest gradle, AGP and google-services plugin versions. Update this when new releases come
    // out.
    // Also remember to update the latest AGP/gradle versions in BeePlusGradleProject.java.
    // firebase-appdistribution-gradle/src/prodTest/java/com/google/firebase/appdistribution/gradle/BeePlusGradleProject.java#L59-L60
    private val LATEST_GRADLE_VERSION = VersionUtils.fetchLatestGradleVersion()
    private val LATEST_AGP_VERSION = VersionUtils.fetchLatestAgpVersion()
    private val LATEST_GOOGLE_SERVICES_VERSION = VersionUtils.fetchLatestGoogleServicesVersion()
    // For tests against Gradle 9, we get the error:
    // "In order to compile Java 9+ source, please set compileSdkVersion to 30 or above"
    // when we don't set this to at least 30.
    private const val COMPILE_SDK_VERSION_FOR_LATEST_TEST = "30"

    // Gradle 7.3.3 was released in December 2023.
    private const val OLDER_GRADLE_VERSION = "7.3.3"

    // AGP 7.0 was released in July 2021.
    private const val OLDER_AGP_VERSION = "7.0.0"

    // google-services Gradle plugin 4.3.2 was released in September 2019.
    private const val OLDER_GOOGLE_SERVICES_VERSION = "4.3.2"

    private val LOGGER = java.util.logging.Logger.getLogger(UploadDistributionTaskTest::class.java.name)

    @org.junit.BeforeClass
    @JvmStatic
    fun logVersions() {
      LOGGER.info("Latest Gradle Version: $LATEST_GRADLE_VERSION")
      LOGGER.info("Latest AGP Version: $LATEST_AGP_VERSION")
      LOGGER.info("Latest Google Services Version: $LATEST_GOOGLE_SERVICES_VERSION")
    }
  }
}
