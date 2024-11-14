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

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

@Suppress("unused")
abstract class GenerateLocalDateSerializerUnitTestTask : DefaultTask() {

  @get:InputFile
  abstract val srcFile: RegularFileProperty

  @get:OutputFile
  abstract val destFile: RegularFileProperty

  @get:Input
  abstract val localDateFullyQualifiedClassName: Property<String>

  @get:Input
  abstract val localDateFactoryCall: Property<String>

  @get:Input
  abstract val classNameUnderTest: Property<String>

  @TaskAction
  fun run() {
    val srcFile: File = srcFile.get().asFile
    val destFile: File = destFile.get().asFile
    val localDateFullyQualifiedClassName: String = localDateFullyQualifiedClassName.get()
    val localDateFactoryCall: String = localDateFactoryCall.get()
    val classNameUnderTest: String = classNameUnderTest.get()

    logger.info("srcFile: {}", srcFile)
    logger.info("destFile: {}", destFile)
    logger.info("localDateFullyQualifiedClassName: {}", localDateFullyQualifiedClassName)
    logger.info("localDateFactoryCall: {}", localDateFactoryCall)
    logger.info("classNameUnderTest: {}", classNameUnderTest)

    logger.info("Reading {}", srcFile.absolutePath)
    val transformer = TextLinesTransformer(srcFile.readLines(Charsets.UTF_8))

    val linesByReplacementId =
      mapOf(
        "CoerceDayOfMonthIntoValidRangeFor" to
            listOf(
              "fun Int.coerceDayOfMonthIntoValidRangeFor(month: Int, year: Int): Int {",
              "  val monthObject = org.threeten.bp.Month.of(month)",
              "  val yearObject = org.threeten.bp.Year.of(year)",
              "  val dayRange = monthObject.dayRangeInYear(yearObject)",
              "  return coerceIn(dayRange)",
              "}",
            ),
        "LocalDateSample" to
            listOf(
              "val coercedDayInt = dayInt.coerceDayOfMonthIntoValidRangeFor(month=monthInt, year=yearInt)",
              "$localDateFullyQualifiedClassName$localDateFactoryCall(yearInt, monthInt, coercedDayInt)",
            ),
      )

    val generatedFileWarningLines = TextLinesTransformer.getGeneratedFileWarningLines(srcFile)

    transformer.run {
      atLineThatStartsWith("import ")
        .insertAbove("import com.google.firebase.dataconnect.testutil.dayRangeInYear")
      removeLine("import com.google.firebase.dataconnect.LocalDate")
      replaceWord("LocalDate", localDateFullyQualifiedClassName) { !it.contains('`') }
      replaceText("LocalDateSerializer", classNameUnderTest)
      replaceText(
        "val monthString = month.toZeroPaddedString(monthPadding)",
        "val monthString = month.value.toZeroPaddedString(monthPadding)"
      )
      replaceText(
        "val dayString = day.toZeroPaddedString(dayPadding)",
        "val dayString = dayOfMonth.toZeroPaddedString(dayPadding)"
      )
      replaceText(
        "year: Arb<Int> = intWithEvenNumDigitsDistribution()",
        "year: Arb<Int> = intWithEvenNumDigitsDistribution(java.time.Year.MIN_VALUE..java.time.Year.MAX_VALUE)"
      )
      replaceText(
        "month: Arb<Int> = intWithEvenNumDigitsDistribution()",
        "month: Arb<Int> = intWithEvenNumDigitsDistribution(1..12)"
      )
      replaceText(
        "day: Arb<Int> = intWithEvenNumDigitsDistribution()",
        "day: Arb<Int> = intWithEvenNumDigitsDistribution(1..31)"
      )

      applyReplacements(linesByReplacementId)

      listOf("package ", "class ").forEach { linePrefix ->
        atLineThatStartsWith(linePrefix)
          .deleteLinesAboveThatStartWith("//")
          .insertAbove(generatedFileWarningLines)
      }
    }

    logger.info("Writing {}", destFile.absolutePath)
    destFile.writeText(transformer.lines.joinToString("\n"))
  }

}
