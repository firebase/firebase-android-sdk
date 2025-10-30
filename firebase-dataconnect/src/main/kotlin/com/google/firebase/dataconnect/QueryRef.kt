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
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

/**
 * A specialization of [OperationRef] for _query_ operations.
 *
 * ### Safe for concurrent use
 *
 * All methods and properties of [QueryRef] are thread-safe and may be safely called and/or accessed
 * concurrently from multiple threads and/or coroutines.
 *
 * ### Not stable for inheritance
 *
 * The [QueryRef] interface is _not_ stable for inheritance in third-party libraries, as new methods
 * might be added to this interface or contracts of the existing methods can be changed.
 */
public interface QueryRef<Data, Variables> : OperationRef<Data, Variables> {

  public override suspend fun execute(): QueryResult<Data, Variables> =
    execute(FetchPolicy.PreferCache)

  /** Executes this operation with the given fetch policy, and returns the result. */
  public suspend fun execute(
    fetchPolicy: FetchPolicy = FetchPolicy.PreferCache
  ): QueryResult<Data, Variables>

  /** The caching policy to use in [QueryRef.execute]. */
  public enum class FetchPolicy {

    /**
     * If the query has a cached result that has not expired, then return it without any
     * communication with the server, just as [CacheOnly] would do. Otherwise, if there is no cached
     * data for the query or the cached data has expired, then get the latest result from the
     * server, just as [ServerOnly] would do.
     */
    PreferCache,

    /**
     * Return the query result from the cache without any communication with the server. If there is
     * no cached data for the query then return an empty/null result, just as the server would have
     * returned in that case.
     */
    CacheOnly,

    /**
     * Unconditionally get the latest result from the server, even if there is a locally-cached
     * result for the query. The local cache will be updated with the result, if successful.
     */
    ServerOnly,
  }

  /**
   * Subscribes to a query to be notified of updates to the query's data when the query is executed.
   *
   * At this time the notifications are _not_ realtime, and are _not_ pushed from the server.
   * Instead, the notifications are sent whenever the query is explicitly executed by calling
   * [QueryRef.execute].
   *
   * @return an object that can be used to subscribe to query results.
   */
  public fun subscribe(): QuerySubscription<Data, Variables>

  @ExperimentalFirebaseDataConnect
  override fun copy(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
    callerSdkType: FirebaseDataConnect.CallerSdkType,
    dataSerializersModule: SerializersModule?,
    variablesSerializersModule: SerializersModule?,
  ): QueryRef<Data, Variables>

  @ExperimentalFirebaseDataConnect
  override fun <NewVariables> withVariablesSerializer(
    variables: NewVariables,
    variablesSerializer: SerializationStrategy<NewVariables>,
    variablesSerializersModule: SerializersModule?,
  ): QueryRef<Data, NewVariables>

  @ExperimentalFirebaseDataConnect
  override fun <NewData> withDataDeserializer(
    dataDeserializer: DeserializationStrategy<NewData>,
    dataSerializersModule: SerializersModule?,
  ): QueryRef<NewData, Variables>
}

/**
 * A specialization of [OperationResult] for [QueryRef].
 *
 * ### Safe for concurrent use
 *
 * All methods and properties of [QueryResult] are thread-safe and may be safely called and/or
 * accessed concurrently from multiple threads and/or coroutines.
 *
 * ### Not stable for inheritance
 *
 * The [QueryResult] interface is _not_ stable for inheritance in third-party libraries, as new
 * methods might be added to this interface or contracts of the existing methods can be changed.
 */
public interface QueryResult<Data, Variables> : OperationResult<Data, Variables> {
  override val ref: QueryRef<Data, Variables>

  /** The source of the query results provided by this object. */
  public val source: Source

  /** Indicator of the source of a query's results. */
  public sealed interface Source {

    /**
     * The result was served from the local cache.
     *
     * @param isStale The value to use for the [isStale] property.
     *
     * @property isStale Whether the cached data was considered "stale" when it was loaded from the
     * cache. Cached data is considered to be "stale" when its time-to-live ("TTL") that was
     * indicated by the server has lapsed.
     */
    public class Cache(public val isStale: Boolean) : Source {

      /**
       * Compares this object with another object for equality.
       *
       * @param other The object to compare to this for equality.
       * @return true if, and only if, the other object is an instance of [Cache] whose public
       * properties compare equal using the `==` operator to the corresponding properties of this
       * object.
       */
      override fun equals(other: Any?): Boolean = other is Cache && (other.isStale == isStale)

      /**
       * Calculates and returns the hash code for this object.
       *
       * The hash code is _not_ guaranteed to be stable across application restarts.
       *
       * @return the hash code for this object, that incorporates the values of this object's public
       * properties.
       */
      override fun hashCode(): Int = Objects.hash(Cache::class, isStale)

      /**
       * Returns a string representation of this object, useful for debugging.
       *
       * The string representation is _not_ guaranteed to be stable and may change without notice at
       * any time. Therefore, the only recommended usage of the returned string is debugging and/or
       * logging. Namely, parsing the returned string or storing the returned string in non-volatile
       * storage should generally be avoided in order to be robust in case that the string
       * representation changes.
       *
       * @return a string representation of this object, which includes the class name and the
       * values of all public properties.
       */
      override fun toString(): String {
        return "Cache(isStale=$isStale)"
      }
    }

    /** The result was returned by the server. */
    public object Server : Source {

      /**
       * Compares this object with another object for equality.
       *
       * @param other The object to compare to this for equality.
       * @return true if, and only if, the other object is an instance of [Server] whose public
       * properties compare equal using the `==` operator to the corresponding properties of this
       * object.
       */
      override fun equals(other: Any?): Boolean = other is Server

      /**
       * Calculates and returns the hash code for this object.
       *
       * The hash code is _not_ guaranteed to be stable across application restarts.
       *
       * @return the hash code for this object, that incorporates the values of this object's public
       * properties.
       */
      override fun hashCode(): Int = Objects.hash(Server::class)

      /**
       * Returns a string representation of this object, useful for debugging.
       *
       * The string representation is _not_ guaranteed to be stable and may change without notice at
       * any time. Therefore, the only recommended usage of the returned string is debugging and/or
       * logging. Namely, parsing the returned string or storing the returned string in non-volatile
       * storage should generally be avoided in order to be robust in case that the string
       * representation changes.
       *
       * @return a string representation of this object, which includes the class name and the
       * values of all public properties.
       */
      override fun toString(): String {
        return "Server"
      }
    }
  }
}

/** Creates and returns a new [QueryResult.Source.Cache] object with the given property values. */
public fun QueryResult.Source.Cache.copy(
  isStale: Boolean = this.isStale
): QueryResult.Source.Cache = QueryResult.Source.Cache(isStale = isStale)
