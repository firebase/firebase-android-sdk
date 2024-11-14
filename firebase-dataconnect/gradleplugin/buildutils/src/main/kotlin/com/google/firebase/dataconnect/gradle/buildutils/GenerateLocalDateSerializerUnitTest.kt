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

/**
 * Performs various transformations on a mutable list of strings. This class is _not_ thread safe;
 * any concurrent use must be synchronized externally or else the behavior is undefined.
 * @property lines the lines to mutate; this list is modified in-place.
 */
class GenerateLocalDateSerializerUnitTest(val lines: MutableList<String>) {
  constructor(lines: Iterable<String>) : this(lines.toMutableList())

  fun indexOf(predicateDescription: String, predicate: (String) -> Boolean): Int {
    val index = lines.indexOfFirst(predicate)
    if (index < 0) {
      throw TextLinesTransformerException("unable to find a line that $predicateDescription")
    }
    return index
  }

  fun atLineThatStartsWith(prefix: String): IndexBasedOperations {
    val index = indexOf("starts with \"$prefix\"") { it.startsWith(prefix) }
    return IndexBasedOperations(index)
  }

  fun removeLine(line: String) {
    lines.removeAll { it.trim() == line }
  }

  fun replaceLine(line: String, replacementLine: String) {
    lines.replaceAll { originalLine -> originalLine.takeIf { it != line } ?: replacementLine }
  }

  fun replaceWord(
    original: String,
    replacement: String,
    predicate: (line: String) -> Boolean = { true }
  ) {
    val regex = Regex("""(\W|^)${Regex.escape(original)}(\W|$)""")
    lines.replaceAll { line ->
      if (!predicate(line)) {
        line
      } else {
        regex.replace(line) { matchResult ->
          val prefix = matchResult.groupValues[1]
          val suffix = matchResult.groupValues[2]
          "$prefix${Regex.escapeReplacement(replacement)}$suffix"
        }
      }
    }
  }

  fun replaceText(original: String, replacement: String) {
    lines.replaceAll { it.replace(original, replacement) }
  }

  fun replaceRegex(pattern: String, replacement: String) {
    val regex = Regex(pattern)
    lines.replaceAll { regex.replace(it, replacement) }
  }

  fun applyReplacements(linesByReplacementId: Map<String, List<String>>) {
    for (index in lines.indices.reversed()) {
      val line = lines[index]
      val matchResult = replacementsRegex.matchEntire(line.trim()) ?: continue
      val lineDeleteCount = matchResult.groupValues[1].toInt() + 1
      val replacementId = matchResult.groupValues[2]

      val replacementLines =
        linesByReplacementId[replacementId]
          ?: throw Exception(
            "Replacement ID \"$replacementId\" is not known; " +
                "there are ${linesByReplacementId.size} known replacementIds: " +
                linesByReplacementId.keys.sorted().joinToString(", ") +
                " (error code zgcc257b23)"
          )

      repeat(lineDeleteCount) { lines.removeAt(index) }
      lines.addAll(index, replacementLines)
    }
  }

  inner class IndexBasedOperations(private var index: Int) {
    fun deleteLinesAboveThatStartWith(prefix: String): IndexBasedOperations = apply {
      while (lines[index - 1].startsWith(prefix)) {
        lines.removeAt(index - 1)
        index--
      }
    }

    fun insertAbove(line: String): IndexBasedOperations = apply { lines.add(index, line) }

    fun insertAbove(lines: Collection<String>): IndexBasedOperations = apply {
      this@GenerateLocalDateSerializerUnitTest.lines.addAll(index, lines)
    }
  }

  private class TextLinesTransformerException(message: String) : Exception(message)

  companion object {
    private val replacementsRegex: Regex = run {
      fun StringBuilder.appendRegexEscaped(s: String) = append(Regex.escape(s))
      val pattern = buildString {
        appendRegexEscaped("//")
        append("""\s*""")
        appendRegexEscaped("""ReplaceLinesBelow(numLines=""")
        append("""\s*(\d+)\s*,\s*""")
        appendRegexEscaped("""replacementId=""")
        append("""(\w+)""")
        appendRegexEscaped(""")""")
      }
      Regex(pattern)
    }

    fun getGeneratedFileWarningLines(srcFile: File) =
      listOf(
        "/".repeat(80),
        "// WARNING: THIS FILE IS GENERATED FROM ${srcFile.name}",
        "// DO NOT MODIFY THIS FILE BY HAND BECAUSE MANUAL CHANGES WILL GET OVERWRITTEN",
        "// THE NEXT TIME THAT THIS FILE IS REGENERATED. TO REGENERATE THIS FILE, RUN:",
        "// ./gradlew :firebase-dataconnect:generateDataConnectTestingSources",
        "/".repeat(80),
      )
  }
}
fun generateLocalDateSerializerUnitTest(
  srcFile: File,
  classNameUnderTest: String,
  localDateFullyQualifiedClassName: String,
  localDateFactoryCall: String,
  logger: Logger,
) {
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

    atLineThatStartsWith("package ")
      .deleteLinesAboveThatStartWith("//")
      .insertAbove(generatedFileWarningLines)

    atLineThatStartsWith("class ")
      .deleteLinesAboveThatStartWith("//")
      .insertAbove(generatedFileWarningLines)
  }

  val destFile = File(srcFile.parentFile, "${classNameUnderTest}UnitTest.kt")
  logger.info("Writing {}", destFile.absolutePath)
  destFile.writeText(transformer.lines.joinToString("\n"))
}
