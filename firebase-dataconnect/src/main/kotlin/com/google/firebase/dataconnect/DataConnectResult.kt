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

import java.util.Objects

public sealed class DataConnectResult<Response, Variables>
private constructor(
  private val impl: Impl<Response, Variables>,
  internal val sequenceNumber: Long
) {
  protected constructor(
    data: Response,
    variables: Variables,
    ref: OperationRef<Response, Variables>,
    sequenceNumber: Long,
  ) : this(Impl(data = data, variables = variables, ref = ref), sequenceNumber)

  public val data: Response
    get() = impl.data
  public val variables: Variables
    get() = impl.variables

  public abstract val ref: OperationRef<Response, Variables>

  override fun hashCode(): Int = impl.hashCode()

  override fun equals(other: Any?): Boolean =
    (other is DataConnectResult<*, *>) && other.impl == impl

  override fun toString(): String = "DataConnectResult(data=$data, variables=$variables)"

  private data class Impl<Response, Variables>(
    val data: Response,
    val variables: Variables,
    val ref: OperationRef<Response, Variables>
  )
}

public class DataConnectQueryResult<Response, Variables>
internal constructor(
  data: Response,
  variables: Variables,
  query: QueryRef<Response, Variables>,
  sequenceNumber: Long
) :
  DataConnectResult<Response, Variables>(
    data = data,
    variables = variables,
    ref = query,
    sequenceNumber = sequenceNumber
  ) {
  override val ref: QueryRef<Response, Variables> = query

  override fun hashCode(): Int = Objects.hash("Query", super.hashCode())
  override fun equals(other: Any?): Boolean =
    (other is DataConnectQueryResult<*, *>) && super.equals(other)
  override fun toString(): String = "DataConnectQueryResult(data=$data, variables=$variables)"
}

public class DataConnectMutationResult<Response, Variables>
internal constructor(
  data: Response,
  variables: Variables,
  mutation: MutationRef<Response, Variables>,
  sequenceNumber: Long
) :
  DataConnectResult<Response, Variables>(
    data = data,
    variables = variables,
    ref = mutation,
    sequenceNumber = sequenceNumber
  ) {
  override val ref: MutationRef<Response, Variables> = mutation

  override fun hashCode(): Int = Objects.hash("Mutation", super.hashCode())
  override fun equals(other: Any?): Boolean =
    (other is DataConnectMutationResult<*, *>) && super.equals(other)
  override fun toString(): String = "DataConnectMutationResult(data=$data, variables=$variables)"
}

// See https://spec.graphql.org/draft/#sec-Errors
internal class DataConnectError private constructor(private val impl: Impl) {

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

  override fun hashCode(): Int = impl.hashCode()

  override fun equals(other: Any?): Boolean = (other is DataConnectError) && other.impl == impl

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
      override fun toString(): String = field
    }

    @JvmInline
    value class ListIndex(val index: Int) : PathSegment {
      override fun toString(): String = index.toString()
    }
  }

  private data class Impl(
    val message: String,
    val path: List<PathSegment>,
    val extensions: Map<String, Any?>,
  )
}
