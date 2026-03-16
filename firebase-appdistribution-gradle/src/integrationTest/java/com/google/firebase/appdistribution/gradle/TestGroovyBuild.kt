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

import com.google.firebase.appdistribution.gradle.TestGradleProject.Companion.writeFile
import java.io.File
import java.io.IOException

/** Helper class for executing functional tests for the Firebase App Distribution Gradle Plugin. */
class TestGroovyBuild(private val testGradleProject: TestGradleProject) {

  private lateinit var buildFile: File
  private lateinit var settingsFile: File
  private lateinit var compileSdkVersion: String

  private var agpVersion = DEFAULT_AGP_VERSION
  private var googleServicesVersion = DEFAULT_GOOGLE_SERVICES_VERSION
  private var customAndroidBlock = DEFAULT_CUSTOM_ANDROID_BLOCK
  private var customProjectBlock = ""
  private var useGoogleServicesPlugin = false
  private var artifactType: String? = null
  private var artifactPath: String? = null
  private var testDevices: String? = null
  private var testCases: String? = null
  private var testersFile: String? = null

  /** Writes the groovy language `build.gradle` file for the test project. */
  @JvmOverloads
  @Throws(IOException::class)
  fun writeBuildFiles(
    agpVersion: String = DEFAULT_AGP_VERSION,
    compileSdkVersion: String = DEFAULT_COMPILE_SDK_VERSION,
    googleServicesVersion: String = DEFAULT_GOOGLE_SERVICES_VERSION,
    customAndroidBlock: String = DEFAULT_CUSTOM_ANDROID_BLOCK,
    customProjectBlock: String = "",
    artifactType: String? = null,
    artifactPath: String? = null,
    useGoogleServicesPlugin: Boolean = false,
    testDevices: String? = null,
    testCases: String? = null,
    testersFile: String? = null,
  ) {
    this.agpVersion = agpVersion
    this.compileSdkVersion = compileSdkVersion
    this.googleServicesVersion = googleServicesVersion
    this.customAndroidBlock = customAndroidBlock
    this.customProjectBlock = customProjectBlock
    this.artifactType = artifactType
    this.artifactPath = artifactPath
    this.useGoogleServicesPlugin = useGoogleServicesPlugin
    this.testCases = testCases
    this.testDevices = testDevices
    this.testersFile = testersFile
    buildFile = testGradleProject.projectDir.newFile("app/build.gradle")
    settingsFile = testGradleProject.projectDir.newFile("settings.gradle")
    val rootBuildFile = testGradleProject.projectDir.newFile("build.gradle")
    writeFile(settingsFile, "include ':app'")

    writeFile(
      rootBuildFile,
      """
      buildscript {
        repositories {
            google()
            mavenCentral()
        }

        dependencies {
            classpath 'com.android.tools.build:gradle:$agpVersion'
            classpath 'com.google.gms:google-services:$googleServicesVersion'
            classpath files(${testGradleProject.pluginClasspathString})
        }
      }
    """
        .trimIndent()
    )

    writeFile(
      buildFile,
      """
        apply plugin: 'com.android.application'
        apply plugin: 'com.google.gms.google-services'
        apply plugin: 'com.google.firebase.appdistribution'

        repositories {
          google()
          mavenCentral()
        }

        android {
          namespace '${testGradleProject.packageName}'
          compileSdkVersion $compileSdkVersion
        
          defaultConfig {
            applicationId '${testGradleProject.packageName}'
            minSdkVersion 19
            versionCode 5
          }

          ${generateBuildTypesContent()}

          ${customAndroidBlock}
        }
        ${customProjectBlock}
        """
        .trimIndent()
    )
  }

  /** Generates the content for the buildTypes block. */
  private fun generateBuildTypesContent() =
    """
      buildTypes {
        ${generateContentForBuildType("debug")}
        ${generateContentForBuildType("release")}
      }
    """
      .trimIndent()

  /** Generates the content for a specific build type. */
  private fun generateContentForBuildType(variant: String) =
    """ 
      $variant {
        firebaseAppDistribution {
            serviceCredentialsFile='${testGradleProject.serviceCredentialsFile.absolutePath}'
            ${if (useGoogleServicesPlugin) "" else "appId='${testGradleProject.appId}'"}
            ${getAssignment(artifactType, "artifactType")}
            ${getAssignment(artifactPath, "artifactPath")}
            ${getAssignment(testDevices, "testDevices")}
            ${getAssignment(testCases, "testCases")}
            ${getAssignment(testersFile, "testersFile")}
        }  
      }
      """
      .trimIndent()

  private fun getAssignment(value: String?, key: String) =
    if (value.isNullOrEmpty()) "" else "${key}='${value}'"

  companion object {
    private const val DEFAULT_COMPILE_SDK_VERSION = "27"
    private const val DEFAULT_AGP_VERSION = "7.0.0"
    private const val DEFAULT_GOOGLE_SERVICES_VERSION = "4.3.10"
    private const val DEFAULT_CUSTOM_ANDROID_BLOCK = ""
  }
}
