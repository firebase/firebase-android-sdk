package com.google.firebase.gradle.plugins

object ChangelogFormatter {

  private fun wrapText(text: String, maxWidth: Int, indent: String): List<String> {
    val words = text.split(Regex("(?<=\\s)"))
    val lines = mutableListOf<String>()
    var currentLine = StringBuilder()

    for (word in words) {
      if (currentLine.length + word.length > maxWidth) {
        if (currentLine.isNotEmpty()) {
          lines.add(currentLine.toString().trimEnd())
          currentLine = StringBuilder(indent)
        }
      }
      currentLine.append(word)
    }
    if (currentLine.isNotEmpty()) {
      lines.add(currentLine.toString().trimEnd())
    }
    return lines
  }

  private fun wrapChange(change: Change): String {
    val lines = change.message.split("\n")
    val unwrapped = mutableListOf<String>()
    
    for (line in lines) {
      val trimmed = line.trim()
      if (trimmed.isEmpty()) {
        unwrapped.add("")
      } else if (trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.matches(Regex("^\\d+\\..*"))) {
        unwrapped.add(line)
      } else {
        if (unwrapped.isEmpty()) {
          unwrapped.add(trimmed)
        } else {
          val last = unwrapped.removeLast()
          if (last.isEmpty() || last.trimStart().startsWith("-") || last.trimStart().startsWith("*") || last.trimStart().matches(Regex("^\\d+\\..*"))) {
            unwrapped.add(last)
            unwrapped.add(trimmed)
          } else {
            unwrapped.add("$last $trimmed")
          }
        }
      }
    }

    val wrappedLines = mutableListOf<String>()
    var firstLine = true

    for (unwrappedLine in unwrapped) {
      if (firstLine) {
        val prefix = "- [${change.type.toString().lowercase()}] "
        val maxWidth = 80
        val wrapped = wrapText("$prefix$unwrappedLine", maxWidth, "  ")
        wrappedLines.addAll(wrapped)
        firstLine = false
      } else {
        if (unwrappedLine.isEmpty()) {
          wrappedLines.add("")
        } else {
          val indentMatch = Regex("^\\s*").find(unwrappedLine)
          val indent = indentMatch?.value ?: ""
          
          val isListItem = unwrappedLine.trimStart().startsWith("-") || unwrappedLine.trimStart().startsWith("*") || unwrappedLine.trimStart().matches(Regex("^\\d+\\..*"))
          val baseIndent = if (isListItem) indent + "  " else indent

          val wrapped = wrapText(unwrappedLine, 80, baseIndent)
          wrappedLines.addAll(wrapped)
        }
      }
    }
    return wrappedLines.joinToString("\n")
  }

  private fun formatContent(content: ReleaseContent): String {
    val changesStr = content.changes.joinToString("\n") { wrapChange(it) }
    return when {
      content.subtext.isNotBlank() && changesStr.isNotBlank() -> "${content.subtext}\n\n$changesStr"
      content.subtext.isNotBlank() -> content.subtext
      changesStr.isNotBlank() -> changesStr
      else -> ""
    }
  }

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
