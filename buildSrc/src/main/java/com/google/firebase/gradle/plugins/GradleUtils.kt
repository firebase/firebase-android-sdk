// Copyright 2022 Google LLC
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

import java.io.File
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy

fun Copy.fromDirectory(directory: Provider<File>) =
  from(directory) { into(directory.map { it.name }) }

/**
 * Creates a file at the buildDir for the given [Project].
 *
 * Syntax sugar for:
 *
 * ```
 * project.file("${project.buildDir}/$path)
 * ```
 */
fun Project.fileFromBuildDir(path: String) = file("$buildDir/$path")

/**
 * Maps a file provider to another file provider as a sub directory.
 *
 * Syntax sugar for:
 *
 * ```
 * fileProvider.map { project.file("${it.path}/$path") }
 * ```
 */
fun Project.childFile(provider: Provider<File>, childPath: String) =
  provider.map { file("${it.path}/$childPath") }
