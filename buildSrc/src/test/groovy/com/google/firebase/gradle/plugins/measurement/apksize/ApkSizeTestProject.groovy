// Copyright 2018 Google LLC
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

package com.google.firebase.gradle.plugins.measurement.apksize

import org.apache.commons.io.FileUtils

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.rules.ExternalResource
import org.junit.rules.TemporaryFolder

/** Test rule for creating test Gradle projects for the APK size tooling. */
class ApkSizeTestProject extends ExternalResource {

    private static final String BUILD_GRADLE = """
        import com.google.firebase.gradle.plugins.measurement.apksize.GenerateMeasurementsTask
        import com.google.firebase.gradle.plugins.measurement.UploadMeasurementsTask

        buildscript {
            repositories {
                google()
                jcenter()
            }

            dependencies {
                classpath "com.android.tools.build:gradle:3.2.1"
            }
        }

        repositories {
            google()
            jcenter()
        }

        apply plugin: "com.android.application"

        android {
            compileSdkVersion 26
            defaultConfig {
                minSdkVersion 26
                targetSdkVersion 26
            }

            flavorDimensions "apkSize"

            buildTypes {
                aggressive {
                    debuggable false
                }
            }

            productFlavors {
                horseshoe {
                    dimension "apkSize"
                    applicationId "com.google.testapk.horseshoe"
                }

                vanilla {
                    dimension "apkSize"
                    applicationId "com.google.testapk.vanilla"
                }

                furball {
                    dimension "apkSize"
                    applicationId "com.google.testapk.furball"
                }
            }

            sourceSets {
                horseshoe {
                    java.srcDirs = ["src/horseshoe/java"]
                }

                vanilla {
                    java.srcDirs = ["src/vanilla/java"]
                }

                furball {
                    java.srcDirs = ["src/furball/java"]
                }
            }
        }

        task generate(type: GenerateMeasurementsTask) {
            sdkMapFile = file("test-sdk-map.csv")
            reportFile = file("test-report-file.json")
        }

        task upload(type: UploadMeasurementsTask) {
            dependsOn generate

            reportFile = file("test-report-file.json")
            uploader = "https://storage.googleapis.com/firebase-engprod-metrics/upload_tool.jar"
        }
    """

    private static final String ANDROID_XML = """<?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example">
            <uses-sdk android:minSdkVersion="14"/>
        </manifest>
    """

    private static final String JAVA_FILE = """
        package com.example;

        public class A {}
    """

    private static final String SDK_MAP = """horseshoe-aggressive:2
        vanilla-aggressive:1
        furball-aggressive:3"""

    private final TemporaryFolder projectDirectory = new TemporaryFolder()

    public Path getApkSizeReportPath() {
        return projectDirectory.getRoot().toPath().resolve("test-report-file.json")
    }

    @Override
    protected void before() {
        projectDirectory.create()
        createProjectFiles()
        copyBuildSrcFiles()
    }

    /** Builds the test project in the temporary directory. */
    BuildResult build(String... args) {
        def projectDir = projectDirectory.getRoot()
        return GradleRunner.create().withProjectDir(projectDir).withArguments(args).build()
    }

    @Override
    protected void after() {
        projectDirectory.delete()
    }

    /** Copies the buildSrc files for the APK size tooling into the temporary project. */
    private void copyBuildSrcFiles() {
        def src = Paths.get("src/main/groovy/com/google/firebase/gradle/plugins/measurement")
        def buildSrc = projectDirectory.getRoot().toPath().resolve("buildSrc")
        def dest = buildSrc.resolve(src)

        Files.createDirectories(dest)
        FileUtils.copyDirectory(src.toFile(), dest.toFile())
        FileUtils.deleteDirectory(dest.resolve("coverage").toFile())
    }

    /** Creates the fake project files for the temporary project. */
    private void createProjectFiles() {
        def root = projectDirectory.getRoot().toPath()

        root.resolve("build.gradle") << BUILD_GRADLE
        root.resolve("test-sdk-map.csv") << SDK_MAP
        def horseshoe = root.resolve("src/horseshoe/java/com/example")
        def vanilla = root.resolve("src/vanilla/java/com/example")
        def furball = root.resolve("src/furball/java/com/example")
        def main = root.resolve("src/main")

        Files.createDirectories(main)
        main.resolve("AndroidManifest.xml") << ANDROID_XML

        Files.createDirectories(horseshoe)
        horseshoe.resolve("A.java") << JAVA_FILE

        Files.createDirectories(vanilla)
        vanilla.resolve("A.java") << JAVA_FILE

        Files.createDirectories(furball)
        furball.resolve("A.java") << JAVA_FILE
    }
}
