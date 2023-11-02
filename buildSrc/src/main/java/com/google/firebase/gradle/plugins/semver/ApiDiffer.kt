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

package com.google.firebase.gradle.plugins.semver

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class ApiDiffer : DefaultTask() {
  @get:Input abstract val currentJar: Property<String>

  @get:Input abstract val previousJar: Property<String>

  @get:Input abstract val version: Property<String>

  @get:Input abstract val previousVersionString: Property<String>

  @TaskAction
  fun run() {
    if (version.get().contains("beta") || previousVersionString.get().isNullOrEmpty()) {
      return
    }
    val (pMajor, pMinor, _) = previousVersionString.get().split(".")
    val (major, minor, _) = version.get().split(".")
    val curVersionDelta: VersionDelta =
      if (major > pMajor) VersionDelta.MAJOR
      else if (minor > pMinor) VersionDelta.MINOR else VersionDelta.PATCH
    val afterJar = UtilityClass.readApi(currentJar.get())
    val beforeJar = UtilityClass.readApi(previousJar.get())
    val classKeys = afterJar.keys union beforeJar.keys
    val apiDeltas =
      classKeys
        .map { className -> Pair(beforeJar.get(className), afterJar.get(className)) }
        .flatMap { (before, after) ->
          DeltaType.values().flatMap { it.getViolations(before, after) }
        }
    val deltaViolations: List<Delta> =
      if (curVersionDelta == VersionDelta.MINOR)
        apiDeltas.filter { it.versionDelta == VersionDelta.MAJOR }
      else if (curVersionDelta == VersionDelta.PATCH) apiDeltas else mutableListOf()
    if (!apiDeltas.isEmpty()) {
      val printString =
        apiDeltas.joinToString(
          prefix =
            "Here is a list of all the minor/major version bump changes which are made since the last release.\n",
          separator = "\n"
        ) {
          "[${it.versionDelta}] ${it.description}"
        }
      println(printString)
    }
    if (!deltaViolations.isEmpty()) {
      val outputString =
        deltaViolations.joinToString(
          prefix =
            "Here is a list of all the violations which needs to be fixed before we could release.\n",
          separator = "\n"
        ) {
          "[${it.versionDelta}] ${it.description}"
        }
      throw GradleException(outputString)
    }
  }
}
