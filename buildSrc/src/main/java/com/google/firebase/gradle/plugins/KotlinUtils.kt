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

import java.io.BufferedReader
import java.io.File

/** Replaces all matching substrings with an empty string (nothing) */
fun String.remove(regex: Regex) = replace(regex, "")

/** Replaces all matching substrings with an empty string (nothing) */
fun String.remove(str: String) = replace(str, "")

/** The value of this string or an empty string if null. */
fun String?.orEmpty() = this ?: ""

/**
 * Represents a Diff in the context of two inputs.
 *
 * Every subclass overrides toString() to provide output similar to that of the UNIX diff command.
 *
 * @see FileChanged
 * @see ContentChanged
 * @see LineChanged
 */
interface DiffEntry

/** When a file is either added or removed. */
data class FileChanged(val file: File, val added: Boolean) : DiffEntry {
  override fun toString() = "${if (added) "+++" else "---"} ${file.name}"
}

/** When the contents of a file are changed. */
data class ContentChanged(val file: File, val lines: List<LineChanged>) : DiffEntry {
  override fun toString() =
    """
      == [ ${file.path} ] ==
       ${lines.joinToString("\n")}
       
    """
      .trimIndent()
}

/**
 * Represents an individual line change, providing the original and new strings.
 *
 * This exists to provide a type-safe way of organizing data, while also overriding toString() to
 * match the output of the UNIX diff command.
 *
 * @see ContentChanged
 */
data class LineChanged(val from: String, val to: String) {
  override fun toString() =
    """
        --- ${from.trim()}
        +++ ${to.trim()}
    """
      .trimEnd()
}

/**
 * Recursively compares two directories and returns their diff.
 *
 * You should call [File.diff] instead for individual files and non recursive needs.
 *
 * @throws RuntimeException when called from or on a non directory file.
 */
fun File.recursiveDiff(newDirectory: File): List<DiffEntry> {
  if (!isDirectory) throw RuntimeException("Called on a non directory file: $path")
  if (!newDirectory.isDirectory)
    throw RuntimeException("Called for a non directory file: ${newDirectory.path}")

  val changedFiles =
    walkTopDown().mapNotNull {
      val relativePath = it.toRelativeString(this)
      val newFile = File("${newDirectory.path}/$relativePath")

      if (!newFile.exists()) {
        FileChanged(it, false)
      } else {
        it.diff(newFile)
      }
    }

  val addedFiles =
    newDirectory.walkTopDown().mapNotNull {
      val relativePath = it.toRelativeString(newDirectory)
      val oldFile = File("$path/$relativePath")

      FileChanged(it, true).takeUnless { oldFile.exists() }
    }

  return (changedFiles + addedFiles).toList()
}

/**
 * Compares two files and returns their diff.
 *
 * While this can handle comparing directories, it will NOT recursively compare them. If that is the
 * behavior you are looking for, you should use [File.recursiveDiff] instead.
 *
 * @see [DiffEntry]
 */
fun File.diff(otherFile: File): DiffEntry? {
  if (isDirectory || otherFile.isDirectory) {
    return FileChanged(this, false).takeUnless { isDirectory && otherFile.isDirectory }
  }

  val otherFileReader = otherFile.bufferedReader()

  val changedLines =
    bufferedReader().useLines {
      it
        .mapNotNull {
          LineChanged(it, otherFileReader.safeReadLine().orEmpty()).takeIf { it.from != it.to }
        }
        .toList()
    }

  val addedLines = otherFileReader.useLines { it.map { LineChanged("", it) }.toList() }

  val diff = changedLines + addedLines

  return ContentChanged(otherFile, diff).takeUnless { diff.isEmpty() }
}

/**
 * A safe variant of [BufferedReader.readLine] that will catch [NullPointerException] and return
 * null instead.
 */
fun BufferedReader.safeReadLine(): String? =
  try {
    readLine()
  } catch (_: NullPointerException) {
    null
  }
