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
import com.google.firebase.appdistribution.gradle.TestGroovyBuild.Companion.OLDER_AGP_VERSION
import com.google.firebase.appdistribution.gradle.TestGroovyBuild.Companion.OLDER_COMPILE_SDK_VERSION
import com.google.firebase.appdistribution.gradle.TestGroovyBuild.Companion.OLDER_GOOGLE_SERVICES_VERSION
import com.google.firebase.appdistribution.gradle.TestGroovyBuild.Companion.OLDER_GRADLE_VERSION
import com.google.firebase.appdistribution.gradle.VersionUtils.Stability.BLEEDING_EDGE
import com.google.firebase.appdistribution.gradle.VersionUtils.Stability.RC
import com.google.firebase.appdistribution.gradle.VersionUtils.Stability.STABLE
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import kotlin.jvm.java
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
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class UploadDistributionTaskTest {
  @get:Rule val wireMockRule = WireMockRule(WIRE_MOCK_PORT)

  @get:Rule val testGradleProject = TestGradleProject()
  val testGroovyBuild = TestGroovyBuild(testGradleProject)
  private val apiStubs = ApiStubs(testGradleProject)

  data class Versions(
    val gradle: String,
    val agp: String,
    val compileSdk: String,
    val googleServices: String,
    val useConfigurationCache: Boolean,
  ) {
    fun additionalGradleArguments(): List<String> =
      if (useConfigurationCache) listOf("--configuration-cache") else listOf()
  }

  class VersionsProvider : TestParameterValuesProvider() {
    override fun provideValues(context: Context): List<Versions> {
      return listOf(
        Versions( // oldest supported versions (without configuration cache)
          OLDER_GRADLE_VERSION,
          OLDER_AGP_VERSION,
          OLDER_COMPILE_SDK_VERSION,
          OLDER_GOOGLE_SERVICES_VERSION,
          useConfigurationCache = false
        ),
        Versions( // latest stable versions (without configuration cache)
          LATEST_STABLE_GRADLE_VERSION,
          LATEST_STABLE_AGP_VERSION,
          COMPILE_SDK_VERSION_FOR_GRADLE9,
          LATEST_STABLE_GOOGLE_SERVICES_VERSION,
          useConfigurationCache = false
        ),
        Versions( // latest stable versions
          LATEST_STABLE_GRADLE_VERSION,
          LATEST_STABLE_AGP_VERSION,
          COMPILE_SDK_VERSION_FOR_GRADLE9,
          LATEST_STABLE_GOOGLE_SERVICES_VERSION,
          useConfigurationCache = true
        ),
        Versions( // AGP RC with latest stable version of gradle
          LATEST_STABLE_GRADLE_VERSION,
          LATEST_AGP_RC_VERSION,
          COMPILE_SDK_VERSION_FOR_GRADLE9,
          LATEST_STABLE_GOOGLE_SERVICES_VERSION,
          useConfigurationCache = true
        ),
        Versions( // Gradle RC with latest stable version of AGP
          LATEST_GRADLE_RC_VERSION,
          LATEST_STABLE_AGP_VERSION,
          COMPILE_SDK_VERSION_FOR_GRADLE9,
          LATEST_STABLE_GOOGLE_SERVICES_VERSION,
          useConfigurationCache = true
        ),
        Versions( // AGP bleeding-edge with Gradle RC
          LATEST_GRADLE_RC_VERSION,
          AGP_BLEEDING_EDGE_VERSION,
          COMPILE_SDK_VERSION_FOR_GRADLE9,
          LATEST_STABLE_GOOGLE_SERVICES_VERSION,
          useConfigurationCache = true
        ),
      )
    }
  }

  @Before
  fun setup() {
    System.setProperty("FIREBASE_APP_DISTRIBUTION_API_URL", "http://localhost:${WIRE_MOCK_PORT}")
  }

  @Test
  fun testApkPathParsing_withApk(
    @TestParameter(valuesProvider = VersionsProvider::class) versions: Versions,
  ) {
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = versions.agp,
      compileSdkVersion = versions.compileSdk,
      googleServicesVersion = versions.googleServices,
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments(
          listOf("assembleDebug", "appDistributionUploadDebug", "--info", "--stacktrace") +
            versions.additionalGradleArguments()
        )
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(versions.gradle)
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Using APK"))
    assertThat(result.output, containsString("build/outputs/apk/debug/app-debug.apk"))
  }

  @Test
  fun testApkPathParsing_noApk(
    @TestParameter(valuesProvider = VersionsProvider::class) versions: Versions,
  ) {
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = versions.agp,
      compileSdkVersion = versions.compileSdk,
      googleServicesVersion = versions.googleServices,
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments(
          listOf("appDistributionUploadDebug", "--info", "--stacktrace") +
            versions.additionalGradleArguments()
        )
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(versions.gradle)
        .buildAndFail()

    assertEquals(FAILED, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Could not find an APK"))
  }

  @Test
  fun testApkPathParsing_withCustomOutputName_onOlderAgpAndGradleVersions() {
    // fails with later versions
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
  fun testAabPathParsing_withAab(
    @TestParameter(valuesProvider = VersionsProvider::class) versions: Versions,
  ) {
    apiStubs.stubGetAabInfoSuccess()
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = versions.agp,
      compileSdkVersion = versions.compileSdk,
      googleServicesVersion = versions.googleServices,
      artifactType = "AAB"
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments(
          listOf("bundleDebug", "appDistributionUploadDebug", "--info", "--stacktrace") +
            versions.additionalGradleArguments()
        )
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(versions.gradle)
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Using AAB"))
    assertThat(result.output, containsString("build/outputs/bundle/debug/app-debug.aab"))
  }

  @Test
  fun testAabPathParsing_noAab(
    @TestParameter(valuesProvider = VersionsProvider::class) versions: Versions,
  ) {
    apiStubs.stubGetAabInfoSuccess()
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = versions.agp,
      compileSdkVersion = versions.compileSdk,
      googleServicesVersion = versions.googleServices,
      artifactType = "AAB"
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments(
          listOf("appDistributionUploadDebug", "--info", "--stacktrace") +
            versions.additionalGradleArguments()
        )
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(versions.gradle)
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
  fun testAabPathParsing_withAabCommandLineOverride(
    @TestParameter(valuesProvider = VersionsProvider::class) versions: Versions,
  ) {
    apiStubs.stubGetAabInfoSuccess()
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = versions.agp,
      compileSdkVersion = versions.compileSdk,
      googleServicesVersion = versions.googleServices,
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments(
          listOf(
            "bundleDebug",
            "appDistributionUploadDebug",
            "--artifactType=AAB",
            "--info",
            "--stacktrace"
          ) + versions.additionalGradleArguments()
        )
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(versions.gradle)
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Using AAB"))
    assertThat(result.output, containsString("build/outputs/bundle/debug/app-debug.aab"))
  }

  @Test
  fun testArtifactPath_withRelativePath(
    @TestParameter(valuesProvider = VersionsProvider::class) versions: Versions,
  ) {
    apiStubs.stubGetAabInfoSuccess()
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = versions.agp,
      compileSdkVersion = versions.compileSdk,
      googleServicesVersion = versions.googleServices,
      artifactType = "APK",
      artifactPath = "app/build/outputs/apk/debug/app-debug.apk"
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments(
          listOf("assembleDebug", "appDistributionUploadDebug", "--info", "--stacktrace") +
            versions.additionalGradleArguments()
        )
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(versions.gradle)
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Using APK"))
    assertThat(result.output, containsString("build/outputs/apk/debug/app-debug.apk"))
  }

  @Test
  fun testArtifactPath_withPathToNonexistentFile(
    @TestParameter(valuesProvider = VersionsProvider::class) versions: Versions,
  ) {
    apiStubs.stubGetAabInfoSuccess()
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = versions.agp,
      compileSdkVersion = versions.compileSdk,
      googleServicesVersion = versions.googleServices,
      artifactType = "APK",
      artifactPath = "bad/artifact/path"
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments(
          listOf("assembleDebug", "appDistributionUploadDebug", "--info", "--stacktrace") +
            versions.additionalGradleArguments()
        )
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(versions.gradle)
        .buildAndFail()

    assertEquals(FAILED, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Could not find the APK"))
  }

  @Test
  fun testArtifactPath_withPathInvalidPath(
    @TestParameter(valuesProvider = VersionsProvider::class) versions: Versions,
  ) {
    apiStubs.stubGetAabInfoSuccess()
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = versions.agp,
      compileSdkVersion = versions.compileSdk,
      googleServicesVersion = versions.googleServices,
      artifactType = "APK",
      artifactPath = "\\0"
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments(
          listOf("assembleDebug", "appDistributionUploadDebug", "--info", "--stacktrace") +
            versions.additionalGradleArguments()
        )
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(versions.gradle)
        .buildAndFail()

    assertThat(result.output, containsString("is an invalid path"))
  }

  // *************************************************************************
  // Specific AAB parsing test cases for nuanced AGP/gradle versioning
  // *************************************************************************
  @Test
  fun testAabPathParsing_forAgp713andGradle72() {
    testAabPathParsing_withAab(
      Versions(
        gradle = "7.2",
        agp = "7.1.3",
        OLDER_COMPILE_SDK_VERSION,
        OLDER_GOOGLE_SERVICES_VERSION,
        useConfigurationCache = false
      )
    )
  }

  @Test
  fun testAabPathParsing_forAgp722andGradle733() {
    testAabPathParsing_withAab(
      Versions(
        gradle = "7.3.3",
        agp = "7.2.2",
        OLDER_COMPILE_SDK_VERSION,
        OLDER_GOOGLE_SERVICES_VERSION,
        useConfigurationCache = false
      )
    )
  }

  @Test
  fun testAabPathParsing_forAgp731andGradle74() {
    testAabPathParsing_withAab(
      Versions(
        gradle = "7.4",
        agp = "7.3.1",
        OLDER_COMPILE_SDK_VERSION,
        LATEST_STABLE_GOOGLE_SERVICES_VERSION,
        useConfigurationCache = false
      )
    )
  }

  @Test
  fun testGoogleServices440AppIdParsing_forAgp730() {
    testAabPathParsing_withAab(
      Versions(
        gradle = "7.4",
        agp = "7.3.0", // Note: this fails with AGP 7.4.0
        OLDER_COMPILE_SDK_VERSION,
        "4.4.0",
        useConfigurationCache = false
      )
    )
  }

  // *************************************************************************
  // Miscellaneous integration tests
  // *************************************************************************
  @Test
  fun testGoogleServicesAppIdParsing(
    @TestParameter(valuesProvider = VersionsProvider::class) versions: Versions,
  ) {
    apiStubs.stubUploadDistributionSuccess()
    apiStubs.stubGetUploadStatusSuccess()
    testGroovyBuild.writeBuildFiles(
      agpVersion = versions.agp,
      googleServicesVersion = versions.googleServices,
      compileSdkVersion = versions.compileSdk,
      useGoogleServicesPlugin = true,
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments(
          listOf("assembleDebug", "appDistributionUploadDebug", "--info", "--stacktrace") +
            versions.additionalGradleArguments()
        )
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(versions.gradle)
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
      agpVersion = LATEST_STABLE_AGP_VERSION,
      googleServicesVersion = LATEST_STABLE_GOOGLE_SERVICES_VERSION,
      compileSdkVersion = COMPILE_SDK_VERSION_FOR_GRADLE9,
      testersFile = file.absolutePath
    )

    val result =
      GradleRunner.create()
        .withProjectDir(testGradleProject.projectDir.root)
        .withArguments("assembleDebug", "appDistributionUploadDebug", "--info", "--stacktrace")
        .withPluginClasspath(testGradleProject.pluginClasspathFiles)
        .withGradleVersion(LATEST_STABLE_GRADLE_VERSION)
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
      agpVersion = LATEST_STABLE_AGP_VERSION,
      compileSdkVersion = COMPILE_SDK_VERSION_FOR_GRADLE9,
      googleServicesVersion = LATEST_STABLE_GOOGLE_SERVICES_VERSION
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
        .withGradleVersion(LATEST_STABLE_GRADLE_VERSION)
        .build()

    assertEquals(SUCCESS, result.task(":app:appDistributionUploadDebug")?.outcome)
    assertThat(result.output, containsString("Added testers/groups successfully"))
  }

  companion object {
    const val COMPILE_SDK_VERSION_FOR_GRADLE9 = "30"

    val LATEST_STABLE_GRADLE_VERSION = VersionUtils.fetchLatestGradleVersion(STABLE)
    val LATEST_STABLE_AGP_VERSION = VersionUtils.fetchLatestAgpVersion(STABLE)
    val LATEST_STABLE_GOOGLE_SERVICES_VERSION =
      VersionUtils.fetchLatestGoogleServicesVersion(STABLE)

    val LATEST_GRADLE_RC_VERSION = VersionUtils.fetchLatestGradleVersion(RC)
    val LATEST_AGP_RC_VERSION = VersionUtils.fetchLatestAgpVersion(RC)

    val AGP_BLEEDING_EDGE_VERSION = VersionUtils.fetchLatestAgpVersion(BLEEDING_EDGE)

    private val LOGGER =
      java.util.logging.Logger.getLogger(UploadDistributionTaskTest::class.java.name)

    @org.junit.BeforeClass
    @JvmStatic
    fun logVersions() {
      LOGGER.info(
        "Integration tests using versions:\n" +
          "Gradle (stable: $LATEST_STABLE_GRADLE_VERSION, rc: $LATEST_GRADLE_RC_VERSION)\n" +
          "AGP (stable: $LATEST_STABLE_AGP_VERSION, rc: $LATEST_AGP_RC_VERSION, bleeding edge: $AGP_BLEEDING_EDGE_VERSION)\n" +
          "Google Services (stable: $LATEST_STABLE_GOOGLE_SERVICES_VERSION)"
      )
    }
  }
}
