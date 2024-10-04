/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dataconnect;

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class UpdateDataConnectExecutableVersionsTask : DefaultTask() {

  @get:InputFile abstract val jsonFile: RegularFileProperty

  @get:Input abstract val version: Property<String>

  @get:Internal abstract val workDirectory: DirectoryProperty

  @TaskAction
  fun run() {
    val jsonFile: File = jsonFile.get().asFile
    val version: String = version.get()
    val workDirectory: File = workDirectory.get().asFile

    logger.info("jsonFile={}", jsonFile.absolutePath)
    logger.info("version={}", version)
    logger.info("workDirectory={}", workDirectory)
  }
}
