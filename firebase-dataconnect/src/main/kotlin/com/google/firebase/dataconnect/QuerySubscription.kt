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

import kotlinx.coroutines.flow.*

/**
 * A facility to subscribe to a query to be notified of updates to the query's data when the query
 * is executed.
 *
 * ### Realtime updates (May 2026)
 *
 * Starting with SDK version 17.3.0 (May 2026) query subscriptions gained support for realtime
 * updates. See
 * [Get real-time updates from SQL Connect](https://firebase.google.com/docs/sql-connect/realtime)
 * for details about this feature in the entire product.
 *
 * Prior to SDK version 17.3.0, updates were _not_ realtime, and were _not_ pushed from the server.
 * Instead, the notifications were sent whenever the query was explicitly executed by calling
 * [QueryRef.execute].
 *
 * ### Realtime updates setup and requirements
 *
 * In order for the server to send realtime updates, caching *must* be configured in your
 * `connector.yaml` file. For example:
 *
 * ```
 * connectorId: coolstuff
 * authMode: PUBLIC
 * generate:
 *   kotlinSdk:
 *     package: com.mycompany.coolstuff
 *     clientCache:
 *       maxAge: 1h
 * ```
 *
 * Without this setting the server simply will not send realtime updates.
 *
 * Then, in the `.gql` files for your connector that define the operations, you will need to add
 * `@refresh` directives to the queries for which you want realtime updates. Some very simple
 * queries will automatically gain an implicit `@refresh` directive; however, even mildly complex
 * queries will need the directive added in order to get realtime updates on clients.
 *
 * ### Safe for concurrent use
 *
 * All methods and properties of [QuerySubscription] are thread-safe and may be safely called and/or
 * accessed concurrently from multiple threads and/or coroutines.
 *
 * ### Not stable for inheritance
 *
 * The [QuerySubscription] interface is _not_ stable for inheritance in third-party libraries, as
 * new methods might be added to this interface or contracts of the existing methods can be changed.
 */
public interface QuerySubscription<Data, Variables> {

  /** The query whose results this object subscribes. */
  public val query: QueryRef<Data, Variables>

  /**
   * A cold flow that collects the query results as they become available.
   *
   * Starting with SDK version 17.3.0 (May 2026) query subscriptions gained support for realtime
   * updates. See [QuerySubscription] for details.
   */
  public val flow: Flow<QuerySubscriptionResult<Data, Variables>>

  /**
   * Compares this object with another object for equality, using the `===` operator.
   *
   * The implementation of this method simply uses referential equality. That is, two instances of
   * [QuerySubscription] compare equal using this method if, and only if, they refer to the same
   * object, as determined by the `===` operator.
   *
   * @param other The object to compare to this for equality.
   * @return `other === this`
   */
  override fun equals(other: Any?): Boolean

  /**
   * Calculates and returns the hash code for this object.
   *
   * @return the hash code for this object.
   */
  override fun hashCode(): Int

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
}

/**
 * The result of a query's execution, as notified to a [QuerySubscription].
 *
 * ### Safe for concurrent use
 *
 * All methods and properties of [QuerySubscriptionResult] are thread-safe and may be safely called
 * and/or accessed concurrently from multiple threads and/or coroutines.
 *
 * ### Not stable for inheritance
 *
 * The [QuerySubscriptionResult] interface is _not_ stable for inheritance in third-party libraries,
 * as new methods might be added to this interface or contracts of the existing methods can be
 * changed.
 */
public interface QuerySubscriptionResult<Data, Variables> {

  /** The query that was executed, whose result is captured in this object. */
  public val query: QueryRef<Data, Variables>

  /**
   * The result of the query execution: a successful result if the query was executed successfully,
   * or a failure if the query's execution failed.
   */
  public val result: Result<QueryResult<Data, Variables>>

  /**
   * Compares this object with another object for equality.
   *
   * @param other The object to compare to this for equality.
   * @return true if, and only if, the other object is an instance of the same implementation of
   * [QuerySubscriptionResult] whose public properties compare equal using the `==` operator to the
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
}
