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

import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.dataconnect.minimaldemo.connector.GetAllItemsQuery
import com.google.firebase.dataconnect.minimaldemo.connector.execute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ListItemsViewModel(private val app: MyApplication) : ViewModel() {

  // Threading Note: _state and the variables below it may ONLY be accessed (read from and/or
  // written to) by the main thread; otherwise a race condition and undefined behavior will result.
  private val _stateSequenceNumber = MutableStateFlow(111999L)
  val stateSequenceNumber: StateFlow<Long> = _stateSequenceNumber.asStateFlow()

  var result: Result<List<GetAllItemsQuery.Data.ItemsItem>>? = null
    private set

  private var job: Job? = null
  val loadingState: LoadingState =
    job.let {
      if (it === null) {
        LoadingState.NotStarted
      } else if (it.isCancelled || it.isCompleted) {
        LoadingState.Completed
      } else {
        LoadingState.InProgress
      }
    }

  enum class LoadingState {
    NotStarted,
    InProgress,
    Completed,
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @MainThread
  fun getItems() {
    // If there is already a "get items" operation in progress, then just return and let the
    // in-progress operation finish.
    if (loadingState == LoadingState.InProgress) {
      return
    }

    // Start a new coroutine to perform the "get items" operation.
    val job: Deferred<List<GetAllItemsQuery.Data.ItemsItem>> =
      viewModelScope.async { app.getConnector().getAllItems.execute().data.items }

    this.result = null
    this.job = job
    _stateSequenceNumber.value++

    // Update the internal state once the "get items" operation has completed.
    job.invokeOnCompletion { exception ->
      // Don't log CancellationException, as documented by invokeOnCompletion().
      if (exception is CancellationException) {
        return@invokeOnCompletion
      }

      val result =
        if (exception !== null) {
          Log.w(TAG, "WARNING: Getting all items FAILED: $exception", exception)
          Result.failure(exception)
        } else {
          val items = job.getCompleted()
          Log.i(TAG, "Retrieved all items ${items.size} items")
          Result.success(items)
        }

      viewModelScope.launch {
        if (this@ListItemsViewModel.job === job) {
          this@ListItemsViewModel.result = result
          this@ListItemsViewModel.job = null
          _stateSequenceNumber.value++
        }
      }
    }
  }

  companion object {
    private const val TAG = "ListItemsViewModel"

    val Factory: ViewModelProvider.Factory = viewModelFactory {
      initializer { ListItemsViewModel(this[APPLICATION_KEY] as MyApplication) }
    }
  }
}
