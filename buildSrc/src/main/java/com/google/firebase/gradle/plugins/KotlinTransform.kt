// Copyright 2023 Google LLC
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

import com.google.firebase.gradle.plugins.semver.UtilityClass
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Ensures that pom dependencies are not accidently downgraded.
 *
 * Compares the latest pom at gmaven for the given artifact with the one generate for the current
 * release.
 *
 * @property pomFile The pom file for the current release
 * @property artifactId The artifactId for the pom parent
 * @property groupId The groupId for the pom parent
 * @throws GradleException if a dependency is found with a degraded version
 */
abstract class KotlinTransform : DefaultTask() {
  @get:Input abstract val artifactId: Property<String>
  @get:Input abstract val groupId: Property<String>
  @get:Input abstract val currentJar: Property<String>
  @get:Input abstract val projectPath: Property<String>

  @TaskAction
  fun run() {
    val classesJar = UtilityClass.readApi(currentJar.get())
    // Step 1 rename all the package names inside of src/main/java/{group-id}.artifact-id to .java
    val javaPath = "${projectPath.get()}/src/main/java"
    val packageName = "${groupId.get()}.${artifactId.get().split("-")[1]}"
    print(packageName)
    File(javaPath).walk().forEach {
      if (it.absolutePath.endsWith(".java")) {
        var lines = mutableListOf<String>()
        File(it.absolutePath).forEachLine {
          val regex = "package ${packageName}"
          if (regex in it) {
            lines.add(it.replace(regex, "${regex}.java"))
          } else {
            lines.add(it)
          }
        }
        File(it.absolutePath).writeText("".join(lines))
      }
    }
  }
}
