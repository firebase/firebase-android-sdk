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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LicenseResolverPluginTests {

    @Rule
    @JvmField
    val testProjectDir = TemporaryFolder()
    private lateinit var buildFile: File

    val idempotentBuild: (taskName: String) -> BuildResult
        get() = this::build.memoize()

    @Before
    fun setup() {
        buildFile = testProjectDir.newFile("build.gradle")
        testProjectDir.newFolder("src", "main", "java", "com", "example")
        testProjectDir.newFile("src/main/java/com/example/Foo.java").writeText("package com.example; class Foo {}")
        testProjectDir.newFile("src/main/AndroidManifest.xml").writeText(MANIFEST)

        buildFile.writeText(BUILD_CONFIG)
    }

    @Test
    fun `Generating licenses`() {
        val result = idempotentBuild("generateLicenses")
        assertThat(result.task(":generateLicenses")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val json = getLicenseJson()
        val txt = getLicenseText()

        assertThat(txt).isNotEmpty()

        assertThat(json).containsKey("customLib1")

        assertThat(txt).contains("customLib1")
        assertThat(txt).contains("Test license")
        val (start, length) = json["customLib1"]!!
        assertThat(txt.substring(start, start + length).trim()).isEqualTo("Test license")
    }

    @Test
    fun `License tasks throw useful exception if file URI not found`() {
        buildFile.writeText("""
            plugins {
                id 'com.android.library'
                id 'LicenseResolverPlugin'
            }
            android.compileSdkVersion = 26

            thirdPartyLicenses {
                add 'customLib', "file:///${File("non_existent_path.txt").absolutePath}"
            }
        """)

        val thrown = Assert.assertThrows(UnexpectedBuildFailure::class.java) {
            build("generateLicenses")
        }

        assertThat(thrown.message).contains("License file not found")
    }

    data class FileOffset(val start: Int, val length: Int)

    private fun getLicenseJson(): Map<String, FileOffset> =
            Gson().fromJson(
                    File("${testProjectDir.root}/build/generated/third_party_licenses/",
                            "third_party_licenses.json").readText(),
                    object : TypeToken<Map<String, FileOffset>>() {}.type)

    private fun getLicenseText(): String =
            File("${testProjectDir.root}/build/generated/third_party_licenses/",
                    "third_party_licenses.txt").readText()

    private fun build(taskName: String): BuildResult = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments(taskName, "--stacktrace")
            .withPluginClasspath()
            .build()

    companion object {
        const val MANIFEST = """<?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  package="com.example">
            <uses-sdk android:minSdkVersion="14"/>
        </manifest>
    """
        val BUILD_CONFIG = """
        buildscript {
            repositories {
                google()
                jcenter()
            }
        }

        plugins {
            id 'com.android.library'
            id 'LicenseResolverPlugin'
        }

        android.compileSdkVersion = 26

        repositories {
            jcenter()
            google()
        }
        dependencies {
            implementation 'com.squareup.picasso:picasso:2.71828'
            implementation 'com.squareup.okhttp:okhttp:2.7.5'
        }

        thirdPartyLicenses {
            add 'customLib1', "file:///${File("src/test/fixtures/license.txt").absolutePath}"
        }
        """
    }
}
