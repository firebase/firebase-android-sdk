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

/**
 * Performs various transformations on a mutable list of strings. This class is _not_ thread safe;
 * any concurrent use must be synchronized externally or else the behavior is undefined.
 *
 * @property lines the lines to mutate; this list is modified in-place.
 */
class TextLinesTransformer(val lines: MutableList<String>) {
  constructor(lines: Iterable<String>) : this(lines.toMutableList())

  constructor(file: File) : this(file.readLines(Charsets.UTF_8))

  fun writeLines(file: File) {
    file.writeText(lines.joinToString("\n"))
  }

  private fun indexOf(predicateDescription: String, predicate: (String) -> Boolean): Int {
    val index = lines.indexOfFirst(predicate)
    if (index < 0) {
      throw TextLinesTransformerException(
        "unable to find a line that $predicateDescription (error code 9w35p9cpn2)"
      )
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
    val unknownReplacementIds = mutableSetOf<String>()
    val missingReplacementIds = linesByReplacementId.keys.toMutableSet()

    for (index in lines.indices.reversed()) {
      val line = lines[index]
      val matchResult = replacementsRegex.matchEntire(line.trim()) ?: continue
      val lineDeleteCount = matchResult.groupValues[1].toInt() + 1
      val replacementId = matchResult.groupValues[2]

      val replacementLines = linesByReplacementId[replacementId]
      if (replacementLines === null) {
        unknownReplacementIds.add(replacementId)
        continue
      } else {
        missingReplacementIds.remove(replacementId)
      }

      repeat(lineDeleteCount) { lines.removeAt(index) }
      lines.addAll(index, replacementLines)
    }

    if (unknownReplacementIds.isNotEmpty() && missingReplacementIds.isNotEmpty()) {
      throw TextLinesTransformerException(
        "There were ${unknownReplacementIds.size} unknown replacement IDs found: " +
          unknownReplacementIds.sorted().joinToString(", ") { "\"$it\"" } +
          ". The known replacement IDs are: " +
          linesByReplacementId.keys.sorted().joinToString(", ") { "\"$it\"" } +
          ". There were also ${missingReplacementIds.size} replacement IDs that were expected " +
          "to be found but were _not_ found: " +
          missingReplacementIds.sorted().joinToString(", ") { "\"$it\"" } +
          ". (error code 2aap434gw7)"
      )
    } else if (unknownReplacementIds.isNotEmpty()) {
      throw TextLinesTransformerException(
        "There were ${unknownReplacementIds.size} unknown replacement IDs found: " +
          unknownReplacementIds.sorted().joinToString(", ") { "\"$it\"" } +
          ". The known replacement IDs are: " +
          linesByReplacementId.keys.sorted().joinToString(", ") { "\"$it\"" } +
          ". (error code 65f2aphm43)"
      )
    } else if (missingReplacementIds.isNotEmpty()) {
      throw TextLinesTransformerException(
        "There were ${missingReplacementIds.size} replacement IDs that were expected " +
          "to be found but were _not_ found: " +
          missingReplacementIds.sorted().joinToString(", ") { "\"$it\"" } +
          ". (error code ha37aja3z3)"
      )
    }
  }

  inner class IndexBasedOperations(private var index: Int) {
    fun deleteLinesAboveThatStartWith(prefix: String): IndexBasedOperations = apply {
      while (lines[index - 1].startsWith(prefix)) {
        lines.removeAt(index - 1)
        index--
      }
    }

    fun replaceWith(line: String): IndexBasedOperations = apply { lines[index] = line }

    fun insertAbove(line: String): IndexBasedOperations = apply { lines.add(index, line) }

    fun insertAbove(lines: Collection<String>): IndexBasedOperations = apply {
      this@TextLinesTransformer.lines.addAll(index, lines)
    }
  }

  private fun getGeneratedFileWarningLines(srcFile: File) =
    listOf(
      "/".repeat(80),
      "// WARNING: THIS FILE IS GENERATED FROM ${srcFile.name}",
      "// DO NOT MODIFY THIS FILE BY HAND BECAUSE MANUAL CHANGES WILL GET OVERWRITTEN",
      "// THE NEXT TIME THAT THIS FILE IS REGENERATED. TO REGENERATE THIS FILE, RUN:",
      "// ./gradlew generateDataConnectTestingSources",
      "/".repeat(80),
    )

  fun insertGeneratedFileWarningLines(srcFile: File, linePrefix: String) {
    val generatedFileWarningLines = getGeneratedFileWarningLines(srcFile)
    atLineThatStartsWith(linePrefix)
      .deleteLinesAboveThatStartWith("//")
      .insertAbove(generatedFileWarningLines)
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
  }
}
