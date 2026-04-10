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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import org.junit.rules.ExternalResource

/**
 * A JUnit test rule that allows tests to register "cleanups" to be run on test completion. If any
 * one cleanup throws an exception then the exception will be logged and the rest of the cleanups
 * will be executed, finally throwing the exception of the first failed cleanup. Cleanups will be
 * executed in the opposite order in which they are registered. Instances are thread-safe and
 * cleanups may be registered concurrently from multiple threads.
 */
class CleanupsRule : ExternalResource() {

  private sealed interface State {
    data object BeforeNotCalled : State
    class Active(val cleanups: List<Cleanup>) : State
    data object AfterCalled : State
  }

  private val state = MutableStateFlow<State>(State.BeforeNotCalled)

  fun register(autoCloseable: AutoCloseable): Registration = register(name = autoCloseable::class.qualifiedName, autoCloseable::close)

  fun register(cleanup: () -> Unit): Registration = register(name = null, cleanup)

  fun register(name: String?, cleanup: () -> Unit): Registration {
    val registration = Cleanup(name, cleanup)

    state.update { currentState ->
      val activeState: State.Active = when (currentState) {
        is State.Active -> currentState
        State.AfterCalled -> throw IllegalStateException("cleanups can not be registered after after() is called")
        State.BeforeNotCalled -> throw IllegalStateException("cleanups can not be registered until before() is called")
      }

      val newCleanups = buildList(activeState.cleanups.size + 1) {
        addAll(activeState.cleanups)
        add(registration)
      }

      State.Active(newCleanups)
    }

    return registration
  }

  fun registerSuspending(cleanup: suspend () -> Unit): Registration = registerSuspending(name = null, cleanup)

  fun registerSuspending(name: String?, cleanup: suspend () -> Unit): Registration = register(name) { runBlocking { cleanup() } }

  fun unregister(registration: Registration) {
    state.update { currentState ->
      val activeState: State.Active = when (currentState) {
        is State.Active -> currentState
        State.AfterCalled -> return
        State.BeforeNotCalled -> return
      }

      val index = activeState.cleanups.indexOfFirst { it === registration }
      if (index < 0) {
        return
      }

      val newCleanups = activeState.cleanups.toMutableList().let {
        it.removeAt(index)
        it.toList()
      }

      State.Active(newCleanups)
    }
  }

  override fun before() {
    state.update { currentState ->
      when (currentState) {
        State.BeforeNotCalled -> State.Active(emptyList())
        is State.Active -> throw IllegalStateException("before() has already been called")
        State.AfterCalled -> throw IllegalStateException("before() cannot be called after after()")
      }
    }
  }

  override fun after() {
    val oldState = state.getAndUpdate { currentState ->
      when (currentState) {
        State.BeforeNotCalled -> throw IllegalStateException("before() must be called before after()")
        is State.Active -> State.AfterCalled
        State.AfterCalled -> throw IllegalStateException("after() has already been called")
      }
    }

    check(oldState is State.Active)

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
