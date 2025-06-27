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
package com.google.firebase.dataconnect.minimaldemo

import androidx.annotation.AnyThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class MainViewModel : ViewModel() {

  sealed interface State {
    data object NotStarted : State
    data class Running(val job: Job) : State
    data class Finished(val error: Throwable?) : State
  }

  private val _state = MutableStateFlow<State>(State.NotStarted)

  val state: StateFlow<State> = _state.asStateFlow()

  init {
    startTest()
  }

  @AnyThread
  fun startTest() {
    val newState = _state.updateAndGet { currentState ->
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

  private fun createLazyJob(): Job {
    val job = viewModelScope.launch(Dispatchers.IO, CoroutineStart.LAZY) {
      repeat(5) {
        println("zzyzx $it")
        delay(1.seconds)
      }
    }
    job.invokeOnCompletion { throwable ->
      _state.update { currentState ->
        if (currentState is State.Running && currentState.job === job) {
          State.Finished(throwable)
        } else {
          currentState
        }
      }
    }
    return job
  }

}
