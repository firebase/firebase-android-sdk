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

import com.google.firebase.dataconnect.DataConnectOperationResponse.Error
import java.util.Objects
import kotlinx.serialization.json.JsonObject

public open class DataConnectExecuteException(
  message: String,
  cause: Throwable? = null,
  public val response: DataConnectOperationResponse<*>,
) : DataConnectException(message, cause)

internal class DataConnectOperationResponseImpl<T>(
  override val data: Map<String, Any?>?,
  override val errors: List<Error>,
  override val decodedData: T?,
) : DataConnectOperationResponse<T> {
  override fun toJson(): JsonObject = TODO()

  override fun hashCode(): Int = Objects.hash(data, errors, decodedData)

  override fun equals(other: Any?): Boolean =
    other is DataConnectOperationResponseImpl<*> &&
      other.data == data &&
      other.errors == errors &&
      other.decodedData == decodedData

  override fun toString(): String =
    "DataConnectOperationResponseImpl(" +
      "data=$data, " +
      "errors=$errors, " +
      "decodedData=$decodedData" +
      ")"

  class ErrorImpl(override val message: String, override val path: List<Error.PathSegment>) :
    Error {
    override fun hashCode(): Int = Objects.hash(message, path)

    override fun equals(other: Any?): Boolean =
      other is ErrorImpl && other.message == message && other.path == path

    // TODO: Re-write this to produce a more readable output, like "a.b[2].c: could not...".
    override fun toString(): String = "ErrorImpl(message=$message, path=$path)"
  }
}

public interface DataConnectOperationResponse<T> {
  // The raw, undecoded data provided by the backend in the response message.
  // Will be null if, and only if, the backend explicitly sent null for the data or
  // if the data was omitted from the response.
  public val data: Map<String, Any?>?

  // The list of errors provided by the backend in the response message.
  public val errors: List<Error>

  // If decoding the data succeeded, this will be the decoded data.
  // Will be null if no data was provided in the response or if decoding the data failed.
  public val decodedData: T?

  // If this function is called, customer app must take a Gradle dependency on
  // org.jetbrains.kotlinx:kotlinx-serialization-json.
  public fun toJson(): kotlinx.serialization.json.JsonObject

  override fun hashCode(): Int
  override fun equals(other: Any?): Boolean
  override fun toString(): String

  // Information about the error, as provided in the response payload from the backend.
  // See https://spec.graphql.org/draft/#sec-Errors
  public interface Error {
    // The error message.
    public val message: String

    // The path of the field in the response data to which this error relates.
    public val path: List<PathSegment>

    override fun hashCode(): Int
    override fun equals(other: Any?): Boolean
    override fun toString(): String

    public sealed interface PathSegment {
      // A named field in a path to a field in the response data.
      public class Field(public val field: String) : PathSegment {
        override fun hashCode(): Int = field.hashCode()

        override fun equals(other: Any?): Boolean = other is Field && other.field == field

        override fun toString(): String = "Field(field=$field)"
      }

      // An index of a list in a path to a field in the response data.
      public class ListIndex(public val index: Int) : PathSegment {
        override fun hashCode(): Int = index.hashCode()
        override fun equals(other: Any?): Boolean = other is ListIndex && other.index == index
        override fun toString(): String = "ListIndex(index=$index)"
      }
    }
  }
}
