/*
 * Copyright 2022 Google LLC
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

import java.io.File
import java.io.InputStream
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList

/** Replaces all matching substrings with an empty string (nothing) */
fun String.remove(regex: Regex) = replace(regex, "")

/** Replaces all matching substrings with an empty string (nothing) */
fun String.remove(str: String) = replace(str, "")

/**
 * Joins a variable amount of [objects][Any.toString] to a single [String] split by newlines (`\n`).
 *
 * For example:
 * ```kotlin
 * multiLine("Hello", "World", "!") shouldBeText
 *  """
 *    Hello
 *    World
 *    !
 *  """.trimIndent()
 * ```
 *
 * If any of the elements are collections, their elements will be recursively joined instead.
 *
 * ```kotlin
 * multiLine(
 *   "Hello",
 *   listOf("World"),
 *   listOf("Goodbye", listOf("World", "!"),
 *   emptyList()
 * ) shouldBeText
 *  """
 *    Hello
 *    World
 *    Goodbye
 *    World
 *    !
 *  """.trimIndent()
 * ```
 *
 * _Note:_ Empty collections will not be rendered.
 */
fun multiLine(vararg strings: Any?): String =
  strings
    .filter { it !is Collection<*> || it.isNotEmpty() }
    .joinToString("\n") {
      if (it is Collection<*>) {
        multiLine(*it.toTypedArray())
      } else {
        it.toString()
      }
    }

/**
 * Converts an [Element] to an Artifact string.
 *
 * An Artifact string can be defined as a dependency with the following format:
 * ```
 * groupId:artifactId:version
 * ```
 *
 * For example, the following would be a valid [Element]:
 * ```
 * <mySuperCoolElement>
 *   <groupId>com.google.firebase</groupId>
 *   <artifactId>firebase-common</artifactId>
 *   <version>16.0.1</version>
 * </mySuperCoolElement>
 * ```
 *
 * @throws NoSuchElementException if the [Element] does not have descendant [Element]s with tags
 *   that match the components of an Artifact string; groupId, artifactId, version.
 */
fun Element.toArtifactString() =
  "${textByTag("groupId")}:${textByTag("artifactId")}:${textByTag("version")}"

/**
 * Converts an [Element] to a Maven name
 *
 * A Maven name can be defined as a dependency with the following format:
 * ```
 * groupId:artifactId
 * ```
 *
 * For example, the following would be a valid [Element]:
 * ```
 * <mySuperCoolElement>
 *   <groupId>com.google.firebase</groupId>
 *   <artifactId>firebase-common</artifactId>
 * </mySuperCoolElement>
 * ```
 *
 * @throws NoSuchElementException if the [Element] does not have descendant [Element]s with tags
 *   that match the components of an Artifact string; groupId and artifactId.
 */
fun Element.toMavenName() = "${textByTag("groupId")}:${textByTag("artifactId")}"

/**
 * Finds a descendant [Element] by a [tag], and returns the [textContent][Element.getTextContent] of
 * it.
 *
 * @param tag the XML tag to filter for (the special value "*" matches all tags)
 * @throws NoSuchElementException if an [Element] with the given [tag] does not exist
 * @see findElementsByTag
 * @see textByTagOrNull
 */
fun Element.textByTag(tag: String) =
  textByTagOrNull(tag) ?: throw RuntimeException("Element tag was missing: $tag")

/**
 * Finds a descendant [Element] by a [tag], and returns the [textContent][Element.getTextContent] of
 * it, or null if it couldn't be found.
 *
 * @param tag the XML tag to filter for (the special value "*" matches all tags)
 * @see textByTag
 */
fun Element.textByTagOrNull(tag: String) = findElementsByTag(tag).firstOrNull()?.textContent

/**
 * Finds a descendant [Element] by a given [tag], or creates a new one.
 *
 * If a new one is created, it is also appended to the [ownerDocument][Element.findOrCreate].
 *
 * @param tag the XML tag to filter for (the special value "*" matches all tags)
 * @see findElementsByTag
 */
fun Element.findOrCreate(tag: String): Element =
  findElementsByTag(tag).firstOrNull() ?: ownerDocument.createElement(tag).also { appendChild(it) }

/**
 * Returns a [Sequence] of all descendant [Element]s that match the given [tag].
 *
 * Essentially a rewrite of [Element.getElementsByTagName] that offers the elements as a [Sequence]
 * and properly converts them to [Element].
 *
 * @param tag the XML tag to filter for (the special value "*" matches all tags)
 * @see Element.getElementsByTagName
 */
fun Element.findElementsByTag(tag: String) =
  getElementsByTagName(tag).children().mapNotNull { it as? Element }

/**
 * Returns the text of an attribute, if it exists.
 *
 * @param name The name of the attribute to get the text for
 */
fun Node.textByAttributeOrNull(name: String) = attributes?.getNamedItem(name)?.textContent

/**
 * Yields the items of this [NodeList] as a [Sequence].
 *
 * [NodeList] does not typically offer an iterator. This extension method offers a means to loop
 * through a NodeList's [item][NodeList.item] method, while also taking into account the element's
 * [length][NodeList.getLength] property to avoid an [IndexOutOfBoundsException].
 *
 * Additionally, this operation is _intermediate_ and _stateless_.
 */
fun NodeList.children(removeDOMSections: Boolean = true) = sequence {
  for (index in 0 until length) {
    val child = item(index)

    if (!removeDOMSections || !child.nodeName.startsWith("#")) {
      yield(child)
    }
  }
}

/**
 * Returns the first match of a regular expression in the [input], beginning at the specified
 * [startIndex].
 *
 * @param input the string to search through
 * @param startIndex an index to start search with, by default zero. Must be not less than zero and
 *   not greater than `input.length()`
 * @throws RuntimeException if a match is not found
 */
fun Regex.findOrThrow(input: CharSequence, startIndex: Int = 0) =
  find(input, startIndex)
    ?: throw RuntimeException(multiLine("No match found for the given input:", input.toString()))

/**
 * Returns the value of the first capture group.
 *
 * Intended to be used in [MatchResult] that are only supposed to capture a single entry.
 */
val MatchResult.firstCapturedValue: String
  get() = groupValues[1]

/**
 * Returns a sequence containing all elements.
 *
 * The operation is _terminal_.
 *
 * Syntax sugar for:
 * ```
 * take(count())
 * ```
 */
fun <T> Sequence<T>.takeAll(): Sequence<T> = take(count())

/**
 * Creates a [Pair] out of an [Iterable] with only two elements.
 *
 * If the [Iterable] has more or less than two elements, the [second element][Pair.second] will be
 * null.
 *
 * For example:
 * ```kotlin
 * listOf(1,2).toPairOrNull() // (1,2)
 * listOf(1).toPairOrNull() // (1, null)
 * listOf(1,2,3).toPairOrNull() // (1, null)
 * ```
 *
 * @throws NoSuchElementException if the [Iterable] is empty
 */
fun <T : Any?> Iterable<T>.toPairOrFirst(): Pair<T, T?> = first() to last().takeIf { count() == 2 }

/**
 * Splits a list at the given [index].
 *
 * A [Pair] will be returned that contains the first and second part of the list respectively.
 *
 * The [second list][Pair.second] will be the one that contains the element at the split.
 *
 * For example:
 * ```kotlin
 * listOf("a","b","c","d","e").separateAt(3) // (["a", "b", "c"], ["d", "e"])
 * listOf("a","b","c","d","e").separateAt(2) // (["a", "b"], ["c", "d", "e"])
 * listOf("a","b").separateAt(1) // (["a"],["b"])
 * listOf("a").separateAt(1) // (["a"],[])
 * listOf("a").separateAt(0) // ([],["a"])
 * ```
 *
 * @param index the index to split the list at; zero being the first element
 */
fun <T> List<T>.separateAt(index: Int) = slice(0 until index) to slice(index..lastIndex)

/**
 * Maps any instances of the [regex] found in this list to the provided [transform].
 *
 * For example:
 * ```kotlin
 * listOf("mom", "mommy", "momma", "dad").replaceMatches(Regex(".*mom.*")) {
 *   it.value.takeUnless { it.contains("y") }?.drop(1)
 * } // ["om", "mommy", "omma", "dad"]
 * ```
 *
 * @param regex the [Regex] to use to match against values in this list
 * @param transform a callback to call with [MathResults][MatchResult] when matches are found. If
 *   the [transform] returns null, then the value remains unchanged.
 */
fun List<String>.replaceMatches(regex: Regex, transform: (MatchResult) -> String?) = map {
  val newValue = regex.find(it)?.let(transform)
  if (newValue != null) {
    it.replace(regex, newValue)
  } else {
    it
  }
}

/**
 * Creates a diff between two lists.
 *
 * For example:
 * ```kotlin
 * listOf(1,2,3,7,8) diff listOf(1,3,2,6) // [(2, 3), (3, 2), (7, 6), (8, null)]
 * ```
 */
infix fun <T> List<T>.diff(other: List<T>): List<Pair<T?, T?>> {
  val largestList = maxOf(size, other.size)

  val firstList = coerceToSize(largestList)
  val secondList = other.coerceToSize(largestList)

  return firstList.zip(secondList).filter { it.first != it.second }
}

/**
 * Creates a list of pairs between two lists, matching according to the provided [mapper].
 *
 * ```kotlin
 * data class Person(name: String, age: Int)
 *
 * val firstList = listOf(
 *   Person("Mike", 5),
 *   Person("Rachel", 6)
 * )
 *
 * val secondList = listOf(
 *   Person("Michael", 4),
 *   Person("Mike", 1)
 * )
 *
 * val diffList = firstList.pairBy(secondList) {
 *   it.name
 * }
 *
 * diffList shouldBeEqualTo listOf(
 *   Person("Mike", 5) to Person("Mike", 1)
 *   Person("Rachel", 6) to null
 *   null to Person("Mike", 1)
 * )
 * ```
 */
inline fun <T, R> List<T>.pairBy(other: List<T>, mapper: (T) -> R): List<Pair<T?, T?>> {
  val firstMap = associateBy { mapper(it) }
  val secondMap = other.associateBy { mapper(it) }

  val changedOrRemoved = firstMap.map { it.value to secondMap[it.key] }
  val added = secondMap.filterKeys { it !in firstMap }.map { null to it.value }

  return changedOrRemoved + added
}

/**
 * Creates a list that is forced to certain size.
 *
 * If the list is longer than the specified size, the extra elements will be cut. If the list is
 * shorter than the specified size, null will be padded to the end
 *
 * For example:
 * ```kotlin
 * listOf(1,2,3).coerceToSize(5) // [1,2,3,null,null,null]
 * listOf(1,2,3).coerceToSize(2) // [1,2]
 * ```
 */
fun <T> List<T>.coerceToSize(targetSize: Int) = List(targetSize) { getOrNull(it) }

/**
 * Writes the [InputStream] to this file.
 *
 * While this method _does_ close the generated output stream, it's the callers responsibility to
 * close the passed [stream].
 *
 * @return This [File] instance for chaining.
 */
fun File.writeStream(stream: InputStream): File {
  outputStream().use { stream.copyTo(it) }
  return this
}

/**
 * Creates the the path to a file if it doesn't already exist.
 *
 * This includes creating the directories for this file.
 *
 * @return This [File] instance for chaining.
 */
fun File.createIfAbsent(): File {
  parentFile?.mkdirs()
  createNewFile()
  return this
}

/**
 * The [path][File.path] represented as a qualified unix path.
 *
 * Useful when a system expects a unix path, but you need to be able to run it on non unix systems.
 *
 * @see absoluteUnixPath
 */
val File.unixPath: String
  get() = path.replace("\\", "/")

/**
 * The [absolutePath][File.getAbsolutePath] represented as a qualified unix path.
 *
 * Useful when a system expects a unix path, but you need to be able to run it on non unix systems.
 *
 * @see unixPath
 */
val File.absoluteUnixPath: String
  get() = absolutePath.replace("\\", "/")

/**
 * Partitions a map with nullable values into a map of non null values and a list of keys with null
 * values.
 *
 * For example:
 * ```
 * val weekdays = mapOf(
 *   "Monday" to 0,
 *   "Tuesday" to 1,
 *   "Wednesday" to null,
 *   "Thursday" to 3,
 *   "Friday" to null,
 * )
 *
 * val (validDays, invalidDays) = weekdays.partitionNotNull()
 *
 * validDays shouldEqual mapOf(
 *   "Monday" to 0,
 *   "Tuesday" to 1,
 *   "Thursday" to 3,
 * )
 * invalidDays shouldContainExactly listOf("Wednesday", "Friday")
 * ```
 *
 * @return A pair where the first component is a map of all the non null values and the second
 *   component is a list of the keys with null values.
 */
fun <K, V> Map<K, V?>.partitionNotNull(): Pair<Map<K, V>, List<K>> {
  val nonNullEntries = mutableMapOf<K, V>()
  val nullEntries = mutableListOf<K>()

  for ((key, value) in this) {
    if (value !== null) {
      nonNullEntries[key] = value
    } else {
      nullEntries.add(key)
    }
  }

  return nonNullEntries to nullEntries
}
