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
