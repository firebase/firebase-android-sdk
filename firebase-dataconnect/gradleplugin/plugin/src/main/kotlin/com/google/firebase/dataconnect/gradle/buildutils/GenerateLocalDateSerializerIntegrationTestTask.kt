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

package com.google.firebase.dataconnect.gradle.buildutils

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@Suppress("unused")
abstract class GenerateLocalDateSerializerIntegrationTestTask : DefaultTask() {

  @get:InputFile abstract val srcFile: RegularFileProperty

  @get:OutputFile abstract val destFile: RegularFileProperty

  @get:Input abstract val destClassName: Property<String>

  @get:Input abstract val localDateFullyQualifiedClassName: Property<String>

  @get:Input abstract val localDateFactoryCall: Property<String>

  @get:Input abstract val convertFromDataConnectLocalDateFunctionName: Property<String>

  @get:Input abstract val serializerClassName: Property<String>

  @TaskAction
  fun run() {
    val srcFile: File = srcFile.get().asFile
    val destFile: File = destFile.get().asFile
    val destClassName: String = destClassName.get()
    val localDateFullyQualifiedClassName: String = localDateFullyQualifiedClassName.get()
    val localDateFactoryCall: String = localDateFactoryCall.get()
    val convertFromDataConnectLocalDateFunctionName: String =
      convertFromDataConnectLocalDateFunctionName.get()
    val serializerClassName: String = serializerClassName.get()

    logger.info("Reading {}", srcFile.absolutePath)
    val transformer = TextLinesTransformer(srcFile)

    val generatedFileWarningLines = TextLinesTransformer.getGeneratedFileWarningLines(srcFile)

    transformer.run {
      removeLine("import com.google.firebase.dataconnect.LocalDate")
      replaceLine(
        "@file:UseSerializers(UUIDSerializer::class)",
        "@file:UseSerializers(UUIDSerializer::class, $serializerClassName::class)"
      )
      atLineThatStartsWith("import ")
        .insertAbove("import com.google.firebase.dataconnect.serializers.$serializerClassName")
        .insertAbove("import io.kotest.property.arbitrary.map")
      replaceWord("LocalDate", localDateFullyQualifiedClassName) { !it.contains('`') }
      replaceWord("LocalDateIntegrationTest", destClassName)
      replaceWord(
        "Arb.dataConnect.localDate()",
        "Arb.dataConnect.localDate().map{it.$convertFromDataConnectLocalDateFunctionName()}"
      )
      replaceRegex("""\?\.date(\W|$)""", "?.date?.$convertFromDataConnectLocalDateFunctionName()$1")
      replaceRegex(
        """([^?])\.date(\W|${'$'})""",
        "$1.date.$convertFromDataConnectLocalDateFunctionName()$2"
      )
      replaceText(
        "$localDateFullyQualifiedClassName(",
        "$localDateFullyQualifiedClassName$localDateFactoryCall("
      )

      atLineThatStartsWith("package ")
        .deleteLinesAboveThatStartWith("//")
        .insertAbove(generatedFileWarningLines)

      atLineThatStartsWith("class ")
        .deleteLinesAboveThatStartWith("//")
        .insertAbove(generatedFileWarningLines)
    }

    logger.info("Writing {}", destFile.absolutePath)
    transformer.writeLines(destFile)
  }
}
