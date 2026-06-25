/*
 * Copyright 2026 Google LLC
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

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking

class Cleanups : AutoCloseable {

  private val state = AtomicReference<State>(State.Open())

  fun register(autoCloseable: AutoCloseable) {
    register(name = autoCloseable::class.qualifiedName, autoCloseable::close)
  }

  fun register(cleanup: () -> Unit) {
    register(name = null, cleanup)
  }

  fun register(name: String?, cleanup: () -> Unit) {
    register(SynchronousCleanup(name, cleanup))
  }

  fun registerSuspending(cleanup: suspend () -> Unit) {
    registerSuspending(name = null, cleanup)
  }

  fun registerSuspending(name: String?, cleanup: suspend () -> Unit) {
    register(SuspendingCleanup(name, cleanup))
  }

  private fun register(cleanup: Cleanup) {
    while (true) {
      when (val currentState = state.get()) {
        is State.Open ->
          if (state.compareAndSet(currentState, currentState.withAppended(cleanup))) {
            return
          }
        State.Closed,
        State.Closing ->
          error(
            "failed to register cleanup with name=${cleanup.name}: " +
              "close() has been called [n3jsqd4d2f]"
          )
      }
    }
  }

  override fun close() {
    val cleanups = transitionToClosing()

    var firstException: Throwable? = null

    cleanups.asReversed().forEach { cleanup ->
      val result = runCatching { cleanup.cleanup() }
      val exception = result.exceptionOrNull()
      if (exception != null) {
        println("WARNING [e2cf4gwrrx]: cleanup with name=${cleanup.name} failed: $exception")
        if (firstException == null) {
          firstException = exception
        } else {
          firstException.addSuppressed(exception)
        }
      }
    }

    if (!state.compareAndSet(State.Closing, State.Closed)) {
      error("internal error pnf3zhderx: transition to closed state failed")
    }

    if (firstException != null) {
      throw firstException
    }
  }

  private fun transitionToClosing(): List<Cleanup> {
    while (true) {
      when (val currentState = state.get()) {
        State.Closed,
        State.Closing -> error("close() has already been called [hfteew3829]")
        is State.Open ->
          if (state.compareAndSet(currentState, State.Closing)) {
            return currentState.cleanups
          }
      }
    }
  }

  interface Cleanup {
    val name: String?
    fun cleanup()
  }

  private class SynchronousCleanup(override val name: String?, val action: () -> Unit) : Cleanup {
    override fun cleanup() {
      action()
    }
  }

  private class SuspendingCleanup(override val name: String?, val action: suspend () -> Unit) :
    Cleanup {
    override fun cleanup() {
      runBlocking { action() }
    }
  }

  private sealed interface State {
    class Open private constructor(val cleanups: List<Cleanup>) : State {
      constructor() : this(emptyList())

      fun withAppended(cleanup: Cleanup) = Open(cleanups.plus(cleanup))

      override fun toString() = "Open"
    }

    object Closing : State {
      override fun toString() = "Closing"
    }

    object Closed : State {
      override fun toString() = "Closed"
    }
  }
}
