/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.gradle.plugins

/**
 * A utility class responsible for formatting markdown CHANGELOG.md files to adhere to our standard layout rules.
 *
 * This ensures that:
 * 1. All text wraps at exactly 80 characters per line.
 * 2. Markdown links, inline code snippets, and GitHub issue attribution tags are treated as unbreakable blocks
 *    and will not be split mid-word across multiple lines.
 * 3. GitHub issue attribution tags are forced onto their own line for easier review.
 * 4. Multi-line code blocks are preserved identically and bypass wrapping.
 * 5. Nested lists retain their relative indentation.
 */
object ChangelogFormatter {

  /**
   * Wraps the provided text to a specified [maxWidth] using the specified [indent] for continuation lines.
   *
   * To prevent certain markdown structures (like links or code blocks) from being shattered across the 80-character
   * limit, this method replaces spaces inside these protected segments with a null character (`\u0000`).
   * By doing this, the wrap algorithm treats the entire segment as a single, indivisible word. After determining
   * the layout, the null characters are reverted back to standard spaces.
   */
  private fun wrapText(text: String, maxWidth: Int, indent: String): List<String> {
    val hardLines = text.split("\n")
    val finalLines = mutableListOf<String>()
    
    for (hardLine in hardLines) {
      // Encode spaces inside markdown links so they aren't split across lines
      var encodedText = Regex("\\[[^\\]]+\\]\\([^)]+\\)(?:\\{[^}]+\\})?").replace(hardLine) { matchResult ->
        matchResult.value.replace(" ", "\u0000")
      }
      // Encode spaces inside GitHub issue tags so they aren't split across lines
      encodedText = Regex("\\(GitHub \\[[^\\]]+\\]\\([^)]+\\)(?:\\{[^}]+\\})?\\)").replace(encodedText) { matchResult ->
        matchResult.value.replace(" ", "\u0000")
      }
      // Encode spaces inside inline code so they aren't split across lines
      encodedText = Regex("`[^`]+`").replace(encodedText) { matchResult ->
        matchResult.value.replace(" ", "\u0000")
      }

      val words = encodedText.split(Regex("(?<=\\s)"))
      var currentLine = StringBuilder()

      for (word in words) {
        val wordText = word.replace("\u0000", " ")
        // We strip trailing spaces to ensure they don't artificially trigger a line break
        val wordLengthWithoutTrailingSpace = wordText.trimEnd().length
        val currentLength = currentLine.toString().replace("\u0000", " ").length
        
        if (currentLength + wordLengthWithoutTrailingSpace > maxWidth) {
          if (currentLine.toString().trim().isNotEmpty()) {
            finalLines.add(currentLine.toString().replace("\u0000", " ").trimEnd())
            currentLine = StringBuilder(indent)
          }
        }
        currentLine.append(word)
      }
      if (currentLine.isNotEmpty()) {
        finalLines.add(currentLine.toString().replace("\u0000", " ").trimEnd())
      }
    }
    return finalLines
  }

  /**
   * Processes a single [Change] by first unwrapping its lines into logical blocks, and then
   * systematically re-wrapping them according to our layout rules.
   *
   * This handles preserving indentation for nested lists, skipping wrapper logic for multi-line
   * code blocks, and extracting GitHub tags onto their own line.
   */
  private fun wrapChange(change: Change): String {
    val lines = change.message.split("\n")
    val unwrapped = mutableListOf<String>()
    
    var inCodeBlock = false

    // Phase 1: Unwrap the message into single, contiguous logical lines while preserving structures
    for (line in lines) {
      if (line.trimStart().startsWith("```")) {
        inCodeBlock = !inCodeBlock
      }

      val trimmed = line.trimStart()
      val isListItem = trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.matches(Regex("^\\d+\\..*"))
      
      if (inCodeBlock || line.trimStart().startsWith("```")) {
        // Leave code block lines entirely alone
        unwrapped.add(line.trimEnd())
      } else if (line.trim().isEmpty()) {
        unwrapped.add("")
      } else if (isListItem) {
        // Start a new logical line for list items
        unwrapped.add(line.trimEnd())
      } else {
        if (unwrapped.isEmpty()) {
          unwrapped.add(trimmed.trimEnd())
        } else {
          val last = unwrapped.removeLast()
          val lastTrimmed = last.trimStart()
          val lastIsListItem = lastTrimmed.startsWith("-") || lastTrimmed.startsWith("*") || lastTrimmed.matches(Regex("^\\d+\\..*"))
          
          if (last.isEmpty() || lastIsListItem || last.trimStart().startsWith("```")) {
            // If the previous line was a boundary, don't merge into it
            unwrapped.add(last)
            unwrapped.add(line.trimEnd())
          } else {
            // Merge the line into the previous contiguous text block
            unwrapped.add("$last ${trimmed.trimEnd()}")
          }
        }
      }
    }

    val wrappedLines = mutableListOf<String>()
    var firstLine = true
    var inCodeBlock2 = false

    // Phase 2: Re-wrap the contiguous logical lines into 80-character bounds
    for (unwrappedLine in unwrapped) {
      if (unwrappedLine.trimStart().startsWith("```")) {
        inCodeBlock2 = !inCodeBlock2
        wrappedLines.add(unwrappedLine)
        firstLine = false
        continue
      }
      
      if (inCodeBlock2) {
        // Bypass wrapping inside code blocks
        wrappedLines.add(unwrappedLine)
        firstLine = false
        continue
      }

      if (firstLine) {
        // The first line of a change receives the change type prefix (e.g. "- [changed] ")
        val prefix = "- [${change.type.toString().lowercase()}] "
        val maxWidth = 80
        
        var textToWrap = unwrappedLine.trimStart()
        // Extract GitHub tags onto a new line with the base indentation
        textToWrap = textToWrap.replace(
            Regex("\\s*(\\(GitHub \\[[^\\]]+\\]\\([^)]+\\)(?:\\{[^}]+\\})?\\))"), 
            "\n  $1"
        )
        val wrapped = wrapText("$prefix$textToWrap", maxWidth, "  ")
        wrappedLines.addAll(wrapped)
        firstLine = false
      } else {
        if (unwrappedLine.trim().isEmpty()) {
          wrappedLines.add("")
        } else {
          // Identify the current list indentation so we can correctly align wrapped text
          val indentMatch = Regex("^\\s*").find(unwrappedLine)
          val indent = indentMatch?.value ?: ""
          
          val isListItem = unwrappedLine.trimStart().startsWith("-") || unwrappedLine.trimStart().startsWith("*") || unwrappedLine.trimStart().matches(Regex("^\\d+\\..*"))
          val baseIndent = if (isListItem) indent + "  " else indent

          var textToWrap = unwrappedLine
          // Extract GitHub tags onto a new line with the specific nested indentation
          textToWrap = textToWrap.replace(
              Regex("\\s*(\\(GitHub \\[[^\\]]+\\]\\([^)]+\\)(?:\\{[^}]+\\})?\\))"), 
              "\n$baseIndent$1"
          )

          val wrapped = wrapText(textToWrap, 80, baseIndent)
          wrappedLines.addAll(wrapped)
        }
      }
    }
    return wrappedLines.joinToString("\n")
  }

  /**
   * Combines the subtext and formatted changes into a unified block for a single [ReleaseContent].
   */
  private fun formatContent(content: ReleaseContent): String {
    val changesStr = content.changes.joinToString("\n") { wrapChange(it) }
    return when {
      content.subtext.isNotBlank() && changesStr.isNotBlank() -> "${content.subtext}\n\n$changesStr"
      content.subtext.isNotBlank() -> content.subtext
      changesStr.isNotBlank() -> changesStr
      else -> ""
    }
  }

  /**
   * Main entrypoint for formatting a raw CHANGELOG file's string contents.
   *
   * Reconstructs the file ensuring a single blank line between the header, entries, and EOF.
   */
  fun format(content: String): String {
    val header = content.split(Regex("^#\\s", RegexOption.MULTILINE)).first()
    val changelog = Changelog.fromString(content)

    val sb = StringBuilder()
    sb.append(header)
    if (!header.endsWith("\n") && header.isNotEmpty()) {
        sb.append("\n")
    }
    // Ensure header is followed by a blank line if it's not empty
    if (!sb.endsWith("\n\n") && sb.isNotEmpty()) {
        sb.append("\n")
    }

    val releasesStr = changelog.releases.joinToString("\n\n") { release ->
      val releaseContentStr = formatContent(release.content)
      val ktxStr = release.ktx?.let { "\n\n## Kotlin\n\n${formatContent(it)}" } ?: ""
      
      if (releaseContentStr.isEmpty() && ktxStr.isEmpty()) {
        "# ${release.version?.toString() ?: "Unreleased"}"
      } else {
        """|# ${release.version?.toString() ?: "Unreleased"}
           |
           |$releaseContentStr$ktxStr""".trimMargin()
      }
    }
    
    sb.append(releasesStr).append("\n")
    return sb.toString()
  }
}
