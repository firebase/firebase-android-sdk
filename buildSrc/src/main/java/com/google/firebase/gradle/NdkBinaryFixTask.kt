/*
 * Copyright 2021 Google LLC
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

package com.google.firebase.gradle

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class NdkBinaryFixTask : DefaultTask() {
  @get:InputFile abstract val inputFile: RegularFileProperty

  @get:OutputFile
  val outputFile: File
    get() = inputFile.get().asFile.let { File(it.parentFile, "lib${it.name}.so") }

  @get:Internal
  val into: String
    get() = "jni/${outputFile.parentFile.name}"

  @TaskAction
  fun run() {
    Files.copy(
      inputFile.get().asFile.toPath(),
      outputFile.toPath(),
      StandardCopyOption.REPLACE_EXISTING
    )
  }
}
