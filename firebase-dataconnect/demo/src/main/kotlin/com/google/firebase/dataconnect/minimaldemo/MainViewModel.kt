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
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.google.firebase.dataconnect.minimaldemo

import android.util.Log
import androidx.annotation.AnyThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.dataconnect.minimaldemo.test.TestResult
import com.google.firebase.dataconnect.minimaldemo.test.utf8PerformanceIntegrationTest
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

class MainViewModel : ViewModel() {

  sealed interface State {
    data object NotStarted : State

    data class Running(val job: Job) : State

    sealed interface Finished : State {
      data class Success(val result: TestResult) : State

      data class Error(val error: Throwable) : State
    }
  }

  private val _state = MutableStateFlow<State>(State.NotStarted)

  val state: StateFlow<State> = _state.asStateFlow()

  init {
    startTest()
  }

  @AnyThread
  fun startTest() {
    val newState =
      _state.updateAndGet { currentState ->
        when (currentState) {
          is State.Running -> currentState
          else -> {
            State.Running(createLazyJob())
          }
        }
      }

    if (newState is State.Running) {
      newState.job.start()
    }
  }

  private fun createLazyJob(): Deferred<TestResult> {
    val job =
      viewModelScope.async(Dispatchers.IO, CoroutineStart.LAZY) { utf8PerformanceIntegrationTest() }
    job.invokeOnCompletion { throwable ->
      if (throwable !== null) {
        Log.e("Utf8PerfTestResult", "utf8PerformanceIntegrationTest failed", throwable)
      } else
        job.getCompleted().apply {
          Log.i("Utf8PerfTestResult", "utf8PerformanceIntegrationTest completed successfully:")
          Log.i("Utf8PerfTestResult", "  original: " + original.logString)
          Log.i("Utf8PerfTestResult", "  slow: " + slow.logString)
          Log.i("Utf8PerfTestResult", "  new: " + new.logString)
          Log.i("Utf8PerfTestResult", "  denver: " + denver.logString)
        }
    }
    job.invokeOnCompletion { throwable ->
      _state.update { currentState ->
        if (currentState !is State.Running || currentState.job !== job) {
          currentState
        } else if (throwable !== null) {
          State.Finished.Error(throwable)
        } else {
          State.Finished.Success(job.getCompleted())
        }
      }
    }
    return job
  }
}

val TestResult.Result.logString: String
  get() = "${averageMs}ms (n=$n)"
