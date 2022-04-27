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
import java.io.File
import java.nio.file.Paths
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private const val MANIFEST = """<?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  package="com.example">
            <uses-sdk android:minSdkVersion="14"/>
        </manifest>
        """

@RunWith(JUnit4::class)
class VendorPluginTests {
    @Rule
    @JvmField
    val testProjectDir = TemporaryFolder()

    @Test
    fun `vendor guava not excluding javax transitive deps should fail`() {
        val buildFailure = assertThrows(UnexpectedBuildFailure::class.java) {
            buildWith("vendor 'com.google.guava:guava:29.0-android'")
        }

        assertThat(buildFailure.message).contains("Vendoring java or javax packages is not supported.")
    }

    @Test
    fun `vendor guava excluding javax transitive deps should include subset of guava`() {
        val classes = buildWith("""
            vendor('com.google.guava:guava:29.0-android') {
              exclude group: 'com.google.code.findbugs', module: 'jsr305'
            }
        """.trimIndent(),
                SourceFile(name = "com/example/Hello.java", content = """
                    package com.example;

                    import com.google.common.base.Preconditions;

                    public class Hello {
                      public static void main(String[] args) {
                        Preconditions.checkNotNull(args);
                      }
                    }
                    """.trimIndent()))
        // expected to vendor preconditions and errorprone annotations from transitive dep.
        assertThat(classes).containsAtLeast(
                "com/example/Hello.class",
                "com/example/com/google/common/base/Preconditions.class",
                "com/example/com/google/errorprone/annotations/CanIgnoreReturnValue.class")

        // ImmutableList is not used, so it should be stripped out.
        assertThat(classes).doesNotContain("com/google/common/collect/ImmutableList.class")
    }

    @Test
    fun `vendor dagger excluding javax transitive deps and not using it should include dagger`() {
        val classes = buildWith("""
            vendor ('com.google.dagger:dagger:2.27') {
              exclude group: "javax.inject", module: "javax.inject"
            }
        """.trimIndent(),
                SourceFile(name = "com/example/Hello.java", content = """
                    package com.example;

                    public class Hello {
                      public static void main(String[] args) {}
                    }
                    """.trimIndent()))
        // expected classes
        assertThat(classes).containsAtLeast(
                "com/example/Hello.class",
                "com/example/BuildConfig.class",
                "com/example/dagger/Lazy.class")
    }

    @Test
    fun `vendor dagger excluding javax transitive deps should include dagger`() {
        val classes = buildWith("""
            vendor ('com.google.dagger:dagger:2.27') {
              exclude group: "javax.inject", module: "javax.inject"
            }
        """.trimIndent(),
                SourceFile(name = "com/example/Hello.java", content = """
                    package com.example;

                    import dagger.Module;

                    @Module
                    public class Hello {
                      public static void main(String[] args) {}
                    }
                    """.trimIndent()))
        // expected classes
        assertThat(classes).containsAtLeast(
                "com/example/Hello.class",
                "com/example/BuildConfig.class",
                "com/example/dagger/Module.class")
    }

    private fun buildWith(deps: String, vararg files: SourceFile): List<String> {
        testProjectDir.newFile("build.gradle").writeText("""
            buildscript {
                repositories {
                    google()
                    jcenter()
                }
            }
            plugins {
                id 'com.android.library'
                id 'firebase-vendor'
            }
            repositories {
                google()
                jcenter()
            }

            android.compileSdkVersion = 26

            dependencies {
                $deps
            }
        """.trimIndent())
        testProjectDir.newFile("settings.gradle")
                .writeText("rootProject.name = 'testlib'")

        testProjectDir.newFolder("src/main/java")
        testProjectDir
                .newFile("src/main/AndroidManifest.xml")
                .writeText(MANIFEST)

        for (file in files) {
            // if (1+1 == 2) {throw RuntimeException("src/main/java/${Paths.get(file.name).parent}")}
            testProjectDir.newFolder("src/main/java/${Paths.get(file.name).parent}")
            testProjectDir.newFile("src/main/java/${file.name}").writeText(file.content)
        }

        GradleRunner.create()
                .withArguments("assemble")
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath()
                .build()

        val aarFile = File(testProjectDir.root, "build/outputs/aar/testlib-release.aar")
        assertThat(aarFile.exists()).isTrue()

        val zipFile = ZipFile(aarFile)
        val classesJar = zipFile.entries().asSequence()
                .filter { it.name == "classes.jar" }
                .first()
        return ZipInputStream(zipFile.getInputStream(classesJar)).use {
            val entries = mutableListOf<String>()
            var currentEntry = it.nextEntry
            while (currentEntry != null) {
                if (!currentEntry.isDirectory) {
                    entries.add(currentEntry.name)
                }
                currentEntry = it.nextEntry
            }
            entries
        }
    }
}

data class SourceFile(val name: String, val content: String)
