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

import com.android.build.gradle.api.AndroidSourceSet
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction

val Project.metalavaConfig: Configuration
    get() =
        configurations.findByName("metalavaArtifacts")
                ?: configurations.create("metalavaArtifacts") {
                    this.dependencies.add(this@metalavaConfig.dependencies.create("com.android:metalava:1.3.0"))
                }

fun Project.runMetalavaWithArgs(
    arguments: List<String>
) {
    val allArgs = listOf(
            "--no-banner",
            "--hide",
            "HiddenSuperclass" // We allow having a hidden parent class
    ) + arguments

    project.javaexec {
        main = "com.android.tools.metalava.Driver"
        classpath = project.metalavaConfig
        args = allArgs
    }
}

abstract class GenerateStubsTask : DefaultTask() {
    /** Source files against which API signatures will be validated. */
    lateinit var sourceSet: Object

    @get:InputFiles
    lateinit var classPath: FileCollection

    @get:OutputDirectory
    val outputDir: File = File(project.buildDir, "doc-stubs")

    private val sourceDirs: Set<File>
        get() = with(sourceSet) {
            when (this) {
                is SourceSet -> java.srcDirs
                is AndroidSourceSet -> java.srcDirs
                else -> throw IllegalStateException("Unsupported sourceSet provided: $javaClass")
            }
        }

    @TaskAction
    fun run() {
        val sourcePath = sourceDirs.asSequence()
                .filter { it.exists() }
                .map { it.absolutePath }
                .joinToString(":")

        val classPath = classPath.files.asSequence()
                .map { it.absolutePath }.toMutableList()
        project.androidJar?.let {
            classPath += listOf(it.absolutePath)
        }

        project.runMetalavaWithArgs(
                listOf(
                        "--source-path",
                        sourcePath,
                        "--classpath",
                        classPath.joinToString(":"),
                        "--include-annotations",
                        "--doc-stubs",
                        outputDir.absolutePath
                ))
    }
}
