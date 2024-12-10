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
public interface DataConnectLogging {

  /**
   * The log level use by all [FirebaseDataConnect] instances.
   *
   * The default log level is [LogLevel.WARN]. Setting this to [LogLevel.DEBUG] will enable debug
   * logging, which is especially useful when reporting issues to Google or investigating problems
   * yourself. Setting it to [LogLevel.NONE] will disable all logging.
   */
  public var level: LogLevel

  /** A [Flow] that can be used to observe the changes to [level]. */
  public val flow: Flow<LogLevel>

  /**
   * Sets the log level to the given level, as if by setting [level], and returns an object that,
   * when closed, will restore the log level to the value it was at the time that this method was
   * invoked.
   *
   * This method can be particularly useful to enable debug logging only for a small section of
   * code, without having to manually keep track of the original state. Unit tests and small chunks
   * of code that require debugging are the intended use cases for this function.
   *
   * For an example in Firebase Data Connect's own Android test suite, see https://goo.gle/3ZuRug5.
   *
   * Note that when a log level is "popped" it does not interact with any other pushed log levels.
   * In particular, if there are nested calls to [push] then they are expected to be popped in
   * reverse order; otherwise, the "original" log level may not be properly restored.
   */
  public fun push(level: LogLevel): LogLevelStackFrame

  /**
   * A "stack frame" that saves the state of the logging facilities in response to a call to [push].
   *
   * ### Safe for Concurrent Use
   *
   * All methods and properties of [LogLevelStackFrame] are thread-safe and may be safely called
   * and/or accessed concurrently from multiple threads and/or coroutines.
   *
   * ### Not Stable for Inheritance
   *
   * The [LogLevelStackFrame] interface is _not_ stable for inheritance in third-party libraries, as
   * new methods might be added to this interface or contracts of the existing methods can be
   * changed.
   */
  public interface LogLevelStackFrame : AutoCloseable {

    /** The log level that was in place when this frame was pushed. */
    public val originalLevel: LogLevel

    /** The log level that was set when this frame was pushed. */
    public val newLevel: LogLevel

    /**
     * Pops this frame off of the log level stack, restoring the log level to the state it was in at
     * the time that this frame was pushed onto the stack.
     *
     * This method may be safely called multiple times. Subsequent invocations will do nothing and
     * return as if successful. Subsequent invocations will _block_ until the state is restored by
     * the thread that is actually doing the restoring.
     */
    override fun close()

    /**
     * Does the exact same thing as [close], except that it _suspends_ rather than _blocks_ if
     * another thread is in the process of restoring the original log level.
     */
    public suspend fun suspendingClose()
  }
}
