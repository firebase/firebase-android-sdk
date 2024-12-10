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

import kotlinx.coroutines.flow.Flow

/**
 * The logcat logging facilities provided by the Firebase Data Connect SDK.
 *
 * The most common use of this interface is simply setting [level] to [LogLevel.DEBUG] to enable
 * debug logging.
 *
 * ### Obtaining Instances
 *
 * To obtain an instance of [DataConnectLogging] call [FirebaseDataConnect.Companion.logging].
 *
 * ### Safe for Concurrent Use
 *
 * All methods and properties of [DataConnectLogging] are thread-safe and may be safely called
 * and/or accessed concurrently from multiple threads and/or coroutines.
 *
 * ### Not Stable for Inheritance
 *
 * The [DataConnectLogging] interface is _not_ stable for inheritance in third-party libraries, as
 * new methods might be added to this interface or contracts of the existing methods can be changed.
 */
public interface DataConnectLogging<out StateT : DataConnectLogging.State> {

  /**
   * The log level use by all [FirebaseDataConnect] instances.
   *
   * The default log level is [LogLevel.WARN]. Setting this to [LogLevel.DEBUG] will enable debug
   * logging, which is especially useful when reporting issues to Google or investigating problems
   * yourself. Setting it to [LogLevel.NONE] will disable all logging.
   */
  public var level: LogLevel

  /** A [Flow] that can be used to observe changes to [state]. */
  public val flow: Flow<StateT>

  /**
   * The state of the logging facilities.
   *
   * A caller may wish to save the value of this property into a local variable, make some changes
   * to the public properties of [DataConnectLogging] (for example, change [level]), run some code,
   * then restore the original state by calling [DataConnectLogging.State.restore].
   *
   * For an example in Firebase Data Connect's own Android test suite, see https://goo.gle/3ZuRug5.
   */
  public val state: StateT

  /**
   * A snapshot of the state of the logging facilities, that can be later restored.
   *
   * ### Safe for Concurrent Use
   *
   * All methods and properties of [State] are thread-safe and may be safely called and/or accessed
   * concurrently from multiple threads and/or coroutines.
   *
   * ### Not Stable for Inheritance
   *
   * The [State] interface is _not_ stable for inheritance in third-party libraries, as new methods
   * might be added to this interface or contracts of the existing methods can be changed.
   *
   * @see state
   */
  public interface State {

    /** The log level. */
    public val level: LogLevel

    /** Restores the state from this object into the [DataConnectLogging] that created it. */
    public fun restore()

    /**
     * Compares this object with another object for equality.
     *
     * @param other The object to compare to this for equality.
     * @return true if, and only if, the other object is an instance of the same implementation of
     * [State] whose public properties compare equal using the `==` operator to the corresponding
     * properties of this object.
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
     * @return a string representation of this object, which includes the class name and the values
     * of all public properties.
     */
    override fun toString(): String
  }
}

/**
 * Sets the log level to the given level, runs the given block, then restores the log level to its
 * original value.
 *
 * The given block is called exactly once and is called in-place. Using the experimental
 * [kotlin.contracts.contract], this could be expressed as:
 * ```
 * contract {
 *   callsInPlace(block, InvocationKind.EXACTLY_ONCE)
 * }
 * ```
 *
 * @param level the log level to have in effect when the given block is executed.
 * @param block the code to execute with the given log level set; it will be invoked exactly once
 * inline.
 * @return whatever the given block returns.
 */
public inline fun <T> DataConnectLogging<*>.withLevel(level: LogLevel, block: () -> T): T {
  val savedState = state
  this.level = level
  return try {
    block()
  } finally {
    savedState.restore()
  }
}
