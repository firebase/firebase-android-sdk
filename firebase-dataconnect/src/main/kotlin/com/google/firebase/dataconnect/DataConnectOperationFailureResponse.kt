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

// Googlers see go/dataconnect:sdk:partial-errors for design details.

/** The data and errors provided by the backend in the response message. */
public interface DataConnectOperationFailureResponse<Data> {

  /**
   * The raw, un-decoded data provided by the backend in the response message. Will be `null` if,
   * and only if, the backend explicitly sent null for the data or if the data was not present in
   * the response.
   *
   * Otherwise, the values in the map will be one of the following:
   * * `null`
   * * [String]
   * * [Boolean]
   * * [Double]
   * * [List] containing any of the types in this list of types
   * * [Map] with [String] keys and values of the types in this list of types
   */
  // TODO(b/446167496) Add a link to [toJson] in the kdoc comments above when the toJson extension
  //  function is implemented.
  public val rawData: Map<String, Any?>?

  /**
   * The list of errors provided by the backend in the response message; may be empty.
   *
   * See [https://spec.graphql.org/draft/#sec-Errors](https://spec.graphql.org/draft/#sec-Errors)
   * for details.
   */
  public val errors: List<ErrorInfo>

  /**
   * The successfully-decoded [rawData], if any.
   *
   * Will be `null` if [rawData] is `null`, or if decoding the [rawData] failed.
   */
  public val data: Data?

  /**
   * Returns a string representation of this object, useful for debugging.
   *
   * The string representation is _not_ guaranteed to be stable and may change without notice at any
   * time. Therefore, the only recommended usage of the returned string is debugging and/or logging.
   * Namely, parsing the returned string or storing the returned string in non-volatile storage
   * should generally be avoided in order to be robust in case that the string representation
   * changes.
   *
   * @return a string representation of this object, which includes the class name and the values of
   * all public properties.
   */
  override fun toString(): String

  /**
   * Information about the error, as provided in the response payload from the backend.
   *
   * See [https://spec.graphql.org/draft/#sec-Errors](https://spec.graphql.org/draft/#sec-Errors)
   * for details.
   */
  public interface ErrorInfo {
    /** The error's message. */
    public val message: String

    /** The path of the field in the response data to which this error relates. */
    public val path: List<DataConnectPathSegment>

    /**
     * Compares this object with another object for equality.
     *
     * @param other The object to compare to this for equality.
     * @return true if, and only if, the other object is an instance of the same implementation of
     * [ErrorInfo] whose public properties compare equal using the `==` operator to the
     * corresponding properties of this object.
     */
    override fun equals(other: Any?): Boolean

    /**
     * Calculates and returns the hash code for this object.
     *
     * The hash code is _not_ guaranteed to be stable across application restarts.
     *
     * @return the hash code for this object, that incorporates the values of this object's public
     * properties.
     */
    override fun hashCode(): Int

    /**
     * Returns a string representation of this object, useful for debugging.
     *
     * The string representation is _not_ guaranteed to be stable and may change without notice at
     * any time. Therefore, the only recommended usage of the returned string is debugging and/or
     * logging. Namely, parsing the returned string or storing the returned string in non-volatile
     * storage should generally be avoided in order to be robust in case that the string
     * representation changes.
     *
     * @return a string representation of this object, suitable for logging the error indicated by
     * this object; it will include the path formatted into a human-readable string (if the path is
     * not empty), and the message.
     */
    override fun toString(): String
  }
}
