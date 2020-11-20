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

import com.google.firebase.gradle.plugins.apiinfo.ApiInformationTask
import com.google.firebase.gradle.plugins.apiinfo.GenerateApiTxtFileTask
import com.google.firebase.gradle.plugins.apiinfo.GetMetalavaJarTask
import com.google.firebase.gradle.plugins.ci.configureCoverage
import java.io.File
import java.nio.file.Paths
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getPlugin
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// TODO(vkryachko): extract functionality common across Firebase{,Java}LibraryPlugin plugins.
class FirebaseJavaLibraryPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.apply(plugin = "java-library")
        val firebaseLibrary = project.extensions
            .create<FirebaseLibraryExtension>("firebaseLibrary", project, LibraryType.JAVA)

        // reduce the likelihood of kotlin module files colliding.
        project.tasks.withType<KotlinCompile> {
            kotlinOptions.freeCompilerArgs = listOf("-module-name", kotlinModuleName(project))
        }
        setupStaticAnalysis(project, firebaseLibrary)
        project.afterEvaluate {
            Dokka.configure(project, null, firebaseLibrary)
        }
    }

    companion object {
        private fun setupStaticAnalysis(project: Project, library: FirebaseLibraryExtension) {
            project.afterEvaluate {
                configurations.all {
                    if ("annotationProcessor" == name) {
                        for (checkProject in library.staticAnalysis.errorproneCheckProjects) {
                            project.dependencies.add("annotationProcessor", project.project(checkProject))
                        }
                    }
                    if ("lintChecks" == name) {
                        for (checkProject in library.staticAnalysis.androidLintCheckProjects) {
                            project.dependencies.add("lintChecks", project.project(checkProject))
                        }
                    }
                }
            }
            setupApiInformationAnalysis(project)
            project.tasks.register("firebaseLint") {
                dependsOn("lint")
            }
            library.configureCoverage()
        }

        private fun setupApiInformationAnalysis(project: Project) {
            val metalavaOutputJarFile = File(project.rootProject.buildDir, "metalava.jar")
            val mainSourceSet = project
                .convention
                .getPlugin<JavaPluginConvention>()
                .sourceSets
                .getByName("main")
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
                project.file("${project.rootDir}/empty-api.txt")

            val apiInfo = project.tasks
                .register<ApiInformationTask>("apiInformation") {
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
                dependsOn("getMetalavaJar")
            }
            project.tasks.getByName("check").dependsOn(docStubs)
            project.afterEvaluate {
                val jars = configurations.getByName("runtimeClasspath").incoming
                    .artifactView {
                        attributes {
                            attribute(Attribute.of("artifactType", String::class.java), "jar")
                        }
                    }
                    .artifacts
                    .artifactFiles
                apiInfo.configure { classPath = jars }
                generateApiTxt.configure { classPath = jars }
                docStubs.configure { classPath = jars }
            }
        }

        private fun kotlinModuleName(project: Project): String {
            val fullyQualifiedProjectPath = project.path.replace(":", "-")
            return project.rootProject.name + fullyQualifiedProjectPath
        }
    }
}
