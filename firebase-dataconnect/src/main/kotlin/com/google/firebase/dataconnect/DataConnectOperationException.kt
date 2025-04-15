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

/**
 * The exception thrown when an error occurs in the execution of a Firebase Data Connect operation
 * (that is, a query or mutation). This exception means that a response was, indeed, received from
 * the backend but either the response included one or more errors or the client could not
 * successfully process the result (for example, decoding the response data failed).
 */
public open class DataConnectOperationException(
  message: String,
  cause: Throwable? = null,
  public val response: DataConnectOperationFailureResponse<*>,
) : DataConnectException(message, cause)
