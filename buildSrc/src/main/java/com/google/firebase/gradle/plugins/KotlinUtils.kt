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

import org.w3c.dom.Element
import org.w3c.dom.NodeList

/** Replaces all matching substrings with an empty string (nothing) */
fun String.remove(regex: Regex) = replace(regex, "")

/** Replaces all matching substrings with an empty string (nothing) */
fun String.remove(str: String) = replace(str, "")

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
public fun <T> Sequence<T>.takeAll(): Sequence<T> = take(count())

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
 * that match the components of an Artifact string; groupId, artifactId, version.
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
 * that match the components of an Artifact string; groupId, artifactId, version.
 */
fun Element.toMavenName() = "${textByTag("groupId")}:${textByTag("artifactId")}"

/**
 * Finds a descendant [Element] by a given [tag], and returns the [textContent]
 * [Element.getTextContent] of it.
 *
 * @param tag the XML tag to filter for (the special value "*" matches all tags)
 * @throws NoSuchElementException if an [Element] with the given [tag] does not exist
 * @see findElementsByTag
 */
fun Element.textByTag(tag: String) = findElementsByTag(tag).first().textContent

/**
 * Finds a descendant [Element] by a given [tag], or creates a new one.
 *
 * If a new one is created, it is also appended to the [ownerDocument][Element.findOrCreate].
 *
 * @param tag the XML tag to filter for (the special value "*" matches all tags)
 * @see findElementsByTag
 */
fun Element.findOrCreate(tag: String) =
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
 * Yields the items of this [NodeList] as a [Sequence].
 *
 * [NodeList] does not typically offer an iterator. This extension method offers a means to loop
 * through a NodeList's [item][NodeList.item] method, while also taking into account its [length]
 * [NodeList.getLength] property to avoid an [IndexOutOfBoundsException].
 *
 * Additionally, this operation is _intermediate_ and _stateless_.
 */
fun NodeList.children() = sequence {
  for (index in 0..length) {
    yield(item(index))
  }
}

/**
 * Joins a variable amount of [strings][Any.toString] to a single [String] split by newlines (`\n`).
 *
 * For example:
 * ```kotlin
 * println(multiLine("Hello", "World", "!")) // "Hello\nWorld\n!"
 * ```
 */
fun multiLine(vararg strings: Any?) = strings.joinToString("\n")

/**
 * Returns the first match of a regular expression in the [input], beginning at the specified
 * [startIndex].
 *
 * @param input the string to search through
 * @param startIndex an index to start search with, by default zero. Must be not less than zero and
 * not greater than `input.length()`
 * @throws RuntimeException if a match is not found
 */
fun Regex.findOrThrow(input: CharSequence, startIndex: Int = 0) =
  find(input, startIndex)
    ?: throw RuntimeException(multiLine("No match found for the given input:", input.toString()))

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
 * the [transform] returns null, then the value remains unchanged.
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
 * Returns the value of the first capture group.
 *
 * Intended to be used in [MatchResult] that are only supposed to capture a single entry.
 */
val MatchResult.firstCapturedValue: String
  get() = groupValues[1]

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
