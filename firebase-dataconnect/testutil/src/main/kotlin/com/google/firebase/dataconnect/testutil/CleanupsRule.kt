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
package com.google.firebase.dataconnect.testutil

import android.util.Log
import io.kotest.common.runBlocking
import java.util.concurrent.atomic.AtomicReference
import org.junit.rules.ExternalResource

/**
 * A JUnit test rule that allows tests to register "cleanups" to be run on test completion.
 *
 * If any cleanup throws an exception, the exception will be logged and the remaining cleanups will
 * still be executed. After all cleanups have been run, the exception from the first failed cleanup
 * will be thrown. Cleanups are executed in the reverse order of their registration.
 *
 * All methods and properties of [CleanupsRule] are thread-safe and may be safely called and/or
 * accessed concurrently from multiple threads and/or coroutines.
 */
class CleanupsRule : ExternalResource() {

  private sealed interface State {
    data object BeforeNotCalled : State
    class Active(val cleanups: List<Cleanup>) : State
    data object AfterCalled : State
  }

  private val state = AtomicReference<State>(State.BeforeNotCalled)

  /**
   * Registers an [AutoCloseable] to be closed on test completion.
   *
   * @param autoCloseable The resource to register for closing upon test completion.
   * @return A [Registration] that can be used to unregister this cleanup.
   * @throws IllegalStateException if called before [before] or after [after].
   */
  fun register(autoCloseable: AutoCloseable): Registration =
    register(name = autoCloseable::class.qualifiedName, autoCloseable::close)

  /**
   * Registers a cleanup to be run on test completion.
   *
   * @param cleanup The block of code to register for execution upon test completion.
   * @return A [Registration] that can be used to unregister this cleanup.
   * @throws IllegalStateException if called before [before] or after [after].
   */
  fun register(cleanup: () -> Unit): Registration = register(name = null, cleanup)

  /**
   * Registers a cleanup to be run on test completion.
   *
   * @param name An optional name for the cleanup, used in logging messages for debugging purposes.
   * @param cleanup The block of code to register for execution upon test completion.
   * @return A [Registration] that can be used to unregister this cleanup.
   * @throws IllegalStateException if called before [before] or after [after].
   */
  fun register(name: String?, cleanup: () -> Unit): Registration {
    val registration = Cleanup(name, cleanup)

    while (true) {
      val currentState = state.get()
      val activeState: State.Active =
        when (currentState) {
          is State.Active -> currentState
          State.AfterCalled -> error("cleanups can not be registered after after() is called")
          State.BeforeNotCalled -> error("cleanups can not be registered until before() is called")
        }

      val newCleanups =
        buildList(activeState.cleanups.size + 1) {
          addAll(activeState.cleanups)
          add(registration)
        }

      val newState = State.Active(newCleanups)
      if (state.compareAndSet(currentState, newState)) {
        break
      }
    }

    return registration
  }

  /**
   * Registers a suspending cleanup to be run on test completion.
   *
   * The suspending cleanup is wrapped in `runBlocking` when executed.
   *
   * @param cleanup The block of code to register for execution upon test completion.
   * @return A [Registration] that can be used to unregister this cleanup.
   * @throws IllegalStateException if called before [before] or after [after].
   */
  fun registerSuspending(cleanup: suspend () -> Unit): Registration =
    registerSuspending(name = null, cleanup)

  /**
   * Registers a suspending cleanup to be run on test completion.
   *
   * The suspending cleanup is wrapped in `runBlocking` when executed.
   *
   * @param name An optional name for the cleanup, used in logging messages for debugging purposes.
   * @param cleanup The block of code to register for execution upon test completion.
   * @return A [Registration] that can be used to unregister this cleanup.
   * @throws IllegalStateException if called before [before] or after [after].
   */
  fun registerSuspending(name: String?, cleanup: suspend () -> Unit): Registration =
    register(name) { runBlocking { cleanup() } }

  /**
   * Unregisters a previously registered cleanup.
   *
   * If the cleanup has already been unregistered or [after] has been called then this method does
   * nothing and returns as if successful.
   *
   * @param registration The registration object returned by one of the `register` methods to
   * unregister.
   */
  fun unregister(registration: Registration) {
    while (true) {
      val currentState = state.get()
      val activeState: State.Active =
        when (currentState) {
          is State.Active -> currentState
          State.AfterCalled -> return
          State.BeforeNotCalled -> return
        }

      val index = activeState.cleanups.indexOfFirst { it === registration }
      if (index < 0) {
        return
      }

      val newCleanups =
        activeState.cleanups.toMutableList().let {
          it.removeAt(index)
          it.toList()
        }

      val newState = State.Active(newCleanups)
      if (state.compareAndSet(currentState, newState)) {
        break
      }
    }
  }

  override fun before() {
    while (true) {
      val currentState = state.get()
      val newState =
        when (currentState) {
          State.BeforeNotCalled -> State.Active(emptyList())
          is State.Active -> error("before() has already been called")
          State.AfterCalled -> error("before() cannot be called after after()")
        }
      if (state.compareAndSet(currentState, newState)) {
        break
      }
    }
  }

  override fun after() {
    val oldState: State
    while (true) {
      val currentState = state.get()
      val newState =
        when (currentState) {
          is State.Active -> State.AfterCalled
          State.BeforeNotCalled -> error("before() must be called before after()")
          State.AfterCalled -> error("after() has already been called")
        }
      if (state.compareAndSet(currentState, newState)) {
        oldState = currentState
        break
      }
    }

    var firstException: Throwable? = null

    oldState.cleanups.reversed().forEach { cleanup ->
      val result = cleanup.runCatching { this.action() }
      result.onFailure {
        Log.e("CleanupsRule", "cleanup ${cleanup.name} failed: $it", it)
        if (firstException === null) {
          firstException = it
        }
      }
    }

    firstException?.let { throw it }
  }

  interface Registration

  private data class Cleanup(val name: String?, val action: () -> Unit) : Registration
}
