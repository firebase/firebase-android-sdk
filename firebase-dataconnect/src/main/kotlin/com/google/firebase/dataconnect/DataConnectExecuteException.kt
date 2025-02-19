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

package com.google.firebase.dataconnect

import java.util.Objects

public open class DataConnectExecuteException(
  message: String,
  cause: Throwable? = null,
  public val errors: List<Error>,
  public val data: Map<String, Any?>? = null,
) : DataConnectException(message, cause) {

  // See https://spec.graphql.org/draft/#sec-Errors
  public class Error(
    public val message: String,
    public val path: List<PathSegment>,
    public val locations: List<SourceLocation>,
  ) {

    override fun hashCode(): Int = Objects.hash(message, path, locations)

    override fun equals(other: Any?): Boolean =
      (other is Error) &&
        other.message == message &&
        other.path == path &&
        other.locations == locations

    override fun toString(): String =
      StringBuilder()
        .also { sb ->
          path.forEachIndexed { segmentIndex, segment ->
            when (segment) {
              is PathSegment.Field -> {
                if (segmentIndex != 0) {
                  sb.append('.')
                }
                sb.append(segment.field)
              }
              is PathSegment.ListIndex -> {
                sb.append('[')
                sb.append(segment.index)
                sb.append(']')
              }
            }
          }

          if (locations.isNotEmpty()) {
            if (sb.isNotEmpty()) {
              sb.append(' ')
            }
            sb.append("at ")
            sb.append(locations.joinToString(", "))
          }

          if (path.isNotEmpty() || locations.isNotEmpty()) {
            sb.append(": ")
          }

          sb.append(message)
        }
        .toString()

    public sealed interface PathSegment {
      @JvmInline
      public value class Field(public val field: String) : PathSegment {
        override fun toString(): String = field
      }

      @JvmInline
      public value class ListIndex(public val index: Int) : PathSegment {
        override fun toString(): String = index.toString()
      }
    }

    public class SourceLocation(public val line: Int, public val column: Int) {
      override fun hashCode(): Int = Objects.hash(line, column)
      override fun equals(other: Any?): Boolean =
        other is SourceLocation && other.line == line && other.column == column

      override fun toString(): String = "$line:$column"
    }
  }
}
