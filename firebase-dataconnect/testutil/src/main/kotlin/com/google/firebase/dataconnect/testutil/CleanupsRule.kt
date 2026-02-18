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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.rules.ExternalResource

/**
 * A JUnit test rule that allows tests to register "cleanups" to be run on test completion. If any
 * one cleanup throws an exception then the exception will be logged and the rest of the cleanups
 * will be executed, finally throwing the exception of the first failed cleanup. Cleanups will be
 * executed in the opposite order in which they are registered. Instances are thread-safe and
 * cleanups may be registered concurrently from multiple threads.
 */
class CleanupsRule : ExternalResource() {

  private val mutex = Mutex()
  private var beforeCalled = false
  private var afterCalled = false
  private val cleanups = mutableListOf<Cleanup>()

  /**
   * Registers a cleanup. This function potentially blocks briefly while acquiring the lock on the
   * list of cleanups. Throws an exception if called before [before] or after [after] has started.
   */
  fun register(name: String? = null, cleanup: suspend () -> Unit) = runBlocking {
    registerSuspending(name, cleanup)
  }

  /**
   * Registers a cleanup. This function potentially suspends while acquiring the lock on the list of
   * cleanups. Throws an exception if called before [before] or after [after] has started.
   */
  suspend fun registerSuspending(name: String? = null, cleanup: suspend () -> Unit) {
    mutex.withLock {
      check(beforeCalled) {
        "cleanups can not be registered until before() is called " + "(error code 3yrwbehmvk)"
      }

      check(!afterCalled) {
        "cleanups can not be registered after after() has started " + "(error code dw92ms797f)"
      }

      cleanups.add(Cleanup(name, cleanup))
    }
  }

  override fun before(): Unit = runBlocking {
    mutex.withLock {
      check(!beforeCalled) { "before() has already been called (error code hg4a8ab5ve)" }
      beforeCalled = true
    }
  }

  override fun after(): Unit = runBlocking {
    mutex.withLock {
      check(!afterCalled) { "after() has already been called (error code brewwkxs6g)" }
      afterCalled = true
    }

    var firstException: Throwable? = null

    while (true) {
      val cleanup = mutex.withLock { cleanups.removeLastOrNull() } ?: break

      val result = cleanup.runCatching { action() }
      result.onFailure {
        Log.e("CleanupsRule", "cleanup ${cleanup.name} failed: $it", it)
        if (firstException === null) {
          firstException = it
        }
      }
    }

    firstException?.let { throw it }
  }

  private data class Cleanup(val name: String?, val action: suspend () -> Unit)
}
