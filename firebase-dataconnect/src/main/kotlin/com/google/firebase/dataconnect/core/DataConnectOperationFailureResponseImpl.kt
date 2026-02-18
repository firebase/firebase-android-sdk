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

package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.DataConnectOperationFailureResponse
import com.google.firebase.dataconnect.DataConnectOperationFailureResponse.ErrorInfo
import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.appendPathStringTo
import java.util.Objects

internal class DataConnectOperationFailureResponseImpl<Data>(
  override val rawData: Map<String, Any?>?,
  override val data: Data?,
  override val errors: List<ErrorInfoImpl>
) : DataConnectOperationFailureResponse<Data> {

  override fun toString(): String =
    "DataConnectOperationFailureResponseImpl(rawData=$rawData, data=$data, errors=$errors)"

  internal class ErrorInfoImpl(
    override val message: String,
    override val path: List<DataConnectPathSegment>,
  ) : ErrorInfo {

    override fun equals(other: Any?): Boolean =
      other is ErrorInfoImpl && other.message == message && other.path == path

    override fun hashCode(): Int = Objects.hash("ErrorInfoImpl", message, path)

    override fun toString(): String = buildString {
      path.appendPathStringTo(this)
      if (isNotEmpty()) {
        append(": ")
      }
      append(message)
    }
  }
}
