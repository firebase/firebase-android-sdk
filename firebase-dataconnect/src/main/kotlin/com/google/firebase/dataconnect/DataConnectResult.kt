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

class DataConnectResult<VariablesType, DataType>
private constructor(
  private val impl: Impl<VariablesType, DataType>,
  internal val sequenceNumber: Long
) {

  internal constructor(
    variables: VariablesType,
    data: DataType,
    errors: List<DataConnectError>,
    sequenceNumber: Long,
  ) : this(Impl(variables = variables, data = data, errors = errors), sequenceNumber)

  val variables: VariablesType
    get() = impl.variables
  val data: DataType
    get() = impl.data
  val errors: List<DataConnectError>
    get() = impl.errors

  override fun hashCode() = impl.hashCode()
  override fun equals(other: Any?) =
    (other as? DataConnectResult<*, *>)?.let { it.impl == impl } ?: false
  override fun toString() = "DataConnectResult(variables=$variables, data=$data, errors=$errors)"

  private data class Impl<VariablesType, DataType>(
    val variables: VariablesType,
    val data: DataType,
    val errors: List<DataConnectError>,
  )
}

// See https://spec.graphql.org/draft/#sec-Errors
class DataConnectError private constructor(private val impl: Impl) {

  internal constructor(
    message: String,
    path: List<PathSegment>,
    extensions: Map<String, Any?>
  ) : this(Impl(message = message, path = path, extensions = extensions))

  val message: String
    get() = impl.message
  val path: List<PathSegment>
    get() = impl.path
  val extensions: Map<String, Any?>
    get() = impl.extensions

  override fun hashCode() = impl.hashCode()
  override fun equals(other: Any?) = (other as? DataConnectError)?.let { it.impl == impl } ?: false

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

  sealed interface PathSegment {
    @JvmInline
    value class Field(val field: String) : PathSegment {
      override fun toString() = field
    }

    @JvmInline
    value class ListIndex(val index: Int) : PathSegment {
      override fun toString() = index.toString()
    }
  }

  private data class Impl(
    val message: String,
    val path: List<PathSegment>,
    val extensions: Map<String, Any?>,
  )
}
