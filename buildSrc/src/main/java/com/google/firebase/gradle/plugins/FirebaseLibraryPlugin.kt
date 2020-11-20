// Copyright 2019 Google LLC
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

import com.android.build.gradle.LibraryExtension
import com.google.firebase.gradle.plugins.apiinfo.ApiInformationTask
import com.google.firebase.gradle.plugins.apiinfo.GenerateApiTxtFileTask
import com.google.firebase.gradle.plugins.apiinfo.GetMetalavaJarTask
import com.google.firebase.gradle.plugins.ci.configureCoverage
import com.google.firebase.gradle.plugins.ci.device.FirebaseTestServer
import java.io.File
import java.nio.file.Paths
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class FirebaseLibraryPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.apply(plugin = "com.android.library")
        val firebaseLibrary = project.extensions
            .create<FirebaseLibraryExtension>("firebaseLibrary", project, LibraryType.ANDROID)
        val android = project.extensions.getByType<LibraryExtension>()

        // In the case of and android library signing config only affects instrumentation test APK.
        // We need it signed with default debug credentials in order for FTL to accept the APK.
        android.buildTypes {
            named("release") {
                signingConfig = getByName("debug").signingConfig
            }
        }

        // see https://github.com/robolectric/robolectric/issues/5456
        android.testOptions {
            unitTests.all(
                closureOf {
                    systemProperty("robolectric.dependency.repo.id", "central")
                    systemProperty("robolectric.dependency.repo.url", "https://repo1.maven.org/maven2")
                    systemProperty("javax.net.ssl.trustStoreType", "JKS")
                }
            )
        }

        // skip debug tests in CI
        // TODO(vkryachko): provide ability for teams to control this if needed
        if (System.getenv().containsKey("FIREBASE_CI")) {
            android.testBuildType = "release"
            project.tasks.all {
                if ("testDebugUnitTest" == name) {
                    enabled = false
                }
            }
        }
        setupApiInformationAnalysis(project, android)
        android.testServer(FirebaseTestServer(project, firebaseLibrary.testLab))
        setupStaticAnalysis(project, firebaseLibrary)

        // reduce the likelihood of kotlin module files colliding.
        project.tasks.withType<KotlinCompile> {
            kotlinOptions.freeCompilerArgs = listOf("-module-name", kotlinModuleName(project))
        }
        Dokka.configure(project, android, firebaseLibrary)
    }

    companion object {
        private fun setupApiInformationAnalysis(project: Project, android: LibraryExtension) {
            val metalavaOutputJarFile = File(project.rootProject.buildDir, "metalava.jar")
            val mainSourceSet = android.sourceSets.getByName("main")
            val outputFile = project
                .rootProject
                .file(
                    Paths.get(
                        project.rootProject.buildDir.path,
                        "apiinfo",
                        project.path.substring(1).replace(":", "_")
                    )
                )
            val outputApiFile = File(outputFile.absolutePath + "_api.txt")
            project.tasks.register<GetMetalavaJarTask>("getMetalavaJar") {
                this.outputFile = metalavaOutputJarFile
            }

            val apiTxt = if (project.file("api.txt").exists())
                project.file("api.txt")
            else
                project.file(project.rootDir.toString() + "/empty-api.txt")

            val apiInfo = project.tasks.register<ApiInformationTask>("apiInformation") {
                this.apiTxt = apiTxt
                this.outputFile = outputFile
                this.outputApiFile = outputApiFile

                metalavaJarPath = metalavaOutputJarFile.absolutePath
                sourceSet = mainSourceSet
                baselineFile = project.file("baseline.txt")
                updateBaseline = project.hasProperty("updateBaseline")

                dependsOn("getMetalavaJar")
            }
            val generateApiTxt = project.tasks.register<GenerateApiTxtFileTask>("generateApiTxtFile") {
                this.apiTxt = project.file("api.txt")

                metalavaJarPath = metalavaOutputJarFile.absolutePath
                sourceSet = mainSourceSet
                baselineFile = project.file("baseline.txt")
                updateBaseline = project.hasProperty("updateBaseline")

                dependsOn("getMetalavaJar")
            }
            val docStubs = project.tasks.register<GenerateStubsTask>("docStubs") {
                sourceSet = mainSourceSet
            }
            project.tasks.getByName("check").dependsOn(docStubs)
            android.libraryVariants.all {
                if (name == "release") {
                    val jars = compileConfiguration.incoming.artifactView {
                        attributes {
                            attribute(Attribute.of("artifactType", String::class.java), "jar")
                        }
                    }.artifacts.artifactFiles

                    apiInfo.configure { classPath = jars }
                    generateApiTxt.configure { classPath = jars }
                    docStubs.configure { classPath = jars }
                }
            }
        }

        private fun setupStaticAnalysis(project: Project, library: FirebaseLibraryExtension) {
            project.afterEvaluate {
                configurations.all {
                    if ("annotationProcessor" == name) {
                        for (checkProject in library.staticAnalysis.errorproneCheckProjects) {
                            project
                                .dependencies
                                .add("annotationProcessor", project.project(checkProject))
                        }
                    }
                    if ("lintChecks" == name) {
                        for (checkProject in library.staticAnalysis.androidLintCheckProjects) {
                            project
                                .dependencies
                                .add("lintChecks", project.project(checkProject))
                        }
                    }
                }
            }
            project.tasks.register("firebaseLint") { dependsOn("lint") }
            library.configureCoverage()
        }

        private fun kotlinModuleName(project: Project): String {
            val fullyQualifiedProjectPath = project.path.replace(":".toRegex(), "-")
            return project.rootProject.name + fullyQualifiedProjectPath
        }
    }
}
