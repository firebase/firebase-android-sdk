/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.dataconnect

/** The "segment" of a path to a field in the response data. */
public sealed interface DataConnectPathSegment {

  /** A named field in a path to a field in the response data. */
  @JvmInline
  public value class Field(public val field: String) : DataConnectPathSegment {

    /**
     * Returns a string representation of this object, useful for debugging.
     *
     * The string representation is _not_ guaranteed to be stable and may change without notice at
     * any time. Therefore, the only recommended usage of the returned string is debugging and/or
     * logging. Namely, parsing the returned string or storing the returned string in non-volatile
     * storage should generally be avoided in order to be robust in case that the string
     * representation changes.
     *
     * @return returns simply [field].
     */
    override fun toString(): String = field
  }

  /** An index of a list in a path to a field in the response data. */
  @JvmInline
  public value class ListIndex(public val index: Int) : DataConnectPathSegment {
    /**
     * Returns a string representation of this object, useful for debugging.
     *
     * The string representation is _not_ guaranteed to be stable and may change without notice at
     * any time. Therefore, the only recommended usage of the returned string is debugging and/or
     * logging. Namely, parsing the returned string or storing the returned string in non-volatile
     * storage should generally be avoided in order to be robust in case that the string
     * representation changes.
     *
     * @return returns simply the string representation of [index].
     */
    override fun toString(): String = index.toString()
  }
}

internal fun <T : DataConnectPathSegment> List<T>.toPathString(): String = buildString {
  appendPathStringTo(this)
}

internal fun <T : DataConnectPathSegment> List<T>.appendPathStringTo(sb: StringBuilder) {
  forEachIndexed { segmentIndex, segment ->
    when (segment) {
      is DataConnectPathSegment.Field -> {
        if (segmentIndex != 0) {
          sb.append('.')
        }
        sb.append(segment.field)
      }
      is DataConnectPathSegment.ListIndex -> {
        sb.append('[')
        sb.append(segment.index)
        sb.append(']')
      }
    }
  }
}

internal fun MutableList<in DataConnectPathSegment.Field>.addField(
  field: String
): DataConnectPathSegment.Field = DataConnectPathSegment.Field(field).also { add(it) }

internal fun MutableList<in DataConnectPathSegment.ListIndex>.addListIndex(
  index: Int
): DataConnectPathSegment.ListIndex = DataConnectPathSegment.ListIndex(index).also { add(it) }

internal inline fun <T> MutableList<in DataConnectPathSegment.Field>.withAddedField(
  field: String,
  block: () -> T
): T = withAddedPathSegment(DataConnectPathSegment.Field(field), block)

internal inline fun <T> MutableList<in DataConnectPathSegment.ListIndex>.withAddedListIndex(
  index: Int,
  block: () -> T
): T = withAddedPathSegment(DataConnectPathSegment.ListIndex(index), block)

internal inline fun <T, S : DataConnectPathSegment> MutableList<in S>.withAddedPathSegment(
  pathSegment: S,
  block: () -> T
): T {
  add(pathSegment)
  try {
    return block()
  } finally {
    val removedSegment = removeLastOrNull()
    check(removedSegment === pathSegment) {
      "internal error x6tzdsszmc: removed $removedSegment, but expected $pathSegment"
    }
  }
}
