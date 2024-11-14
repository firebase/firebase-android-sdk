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

import org.gradle.api.logging.Logger
import java.io.File

fun generateLocalDateSerializerIntegrationTest(
  srcFile: File,
  destClassName: String,
  localDateFullyQualifiedClassName: String,
  localDateFactoryCall: String,
  convertFromDataConnectLocalDateFunctionName: String,
  serializerClassName: String,
  logger: Logger,
) {
  logger.info("Reading {}", srcFile.absolutePath)
  val transformer = TextLinesTransformer(srcFile.readLines(Charsets.UTF_8))

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

  val destFile = File(srcFile.parentFile, "$destClassName.kt")
  logger.info("Writing {}", destFile.absolutePath)
  destFile.writeText(transformer.lines.joinToString("\n"))
}
