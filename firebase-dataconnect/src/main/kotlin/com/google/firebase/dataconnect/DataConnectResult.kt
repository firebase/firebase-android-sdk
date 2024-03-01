// Copyright 2023 Google LLC
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
package com.google.firebase.dataconnect

public class DataConnectResult<VariablesType, DataType>
private constructor(
  private val impl: Impl<VariablesType, DataType>,
  internal val sequenceNumber: Long
) {

  internal constructor(
    variables: VariablesType,
    data: DataType,
    sequenceNumber: Long,
  ) : this(Impl(variables = variables, data = data), sequenceNumber)

  public val variables: VariablesType
    get() = impl.variables
  public val data: DataType
    get() = impl.data

  override fun hashCode(): Int = impl.hashCode()
  override fun equals(other: Any?): Boolean =
    (other as? DataConnectResult<*, *>)?.let { it.impl == impl } ?: false
  override fun toString(): String = "DataConnectResult(variables=$variables, data=$data)"

  private data class Impl<VariablesType, DataType>(
    val variables: VariablesType,
    val data: DataType,
  )
}

// See https://spec.graphql.org/draft/#sec-Errors
public class DataConnectError private constructor(private val impl: Impl) {

  internal constructor(
    message: String,
    path: List<PathSegment>,
    extensions: Map<String, Any?>
  ) : this(Impl(message = message, path = path, extensions = extensions))

  public val message: String
    get() = impl.message
  public val path: List<PathSegment>
    get() = impl.path
  public val extensions: Map<String, Any?>
    get() = impl.extensions

  override fun hashCode(): Int = impl.hashCode()
  override fun equals(other: Any?): Boolean =
    (other as? DataConnectError)?.let { it.impl == impl } ?: false

  override fun toString(): String {
    val sb = StringBuilder()

    if (path.isNotEmpty()) {
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
      sb.append(": ")
    }

    sb.append(message)

    if (extensions.isNotEmpty()) {
      sb.append(" (")
      extensions.keys.sorted().forEachIndexed { keyIndex, key ->
        if (keyIndex != 0) {
          sb.append(", ")
        }
        sb.append(key).append('=').append(extensions[key])
      }
      sb.append(')')
    }

    return sb.toString()
  }

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

  private data class Impl(
    val message: String,
    val path: List<PathSegment>,
    val extensions: Map<String, Any?>,
  )
}
