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
import com.google.firebase.dataconnect.minimaldemo.connector.GetItemByKeyQuery
import com.google.firebase.dataconnect.minimaldemo.connector.InsertItemMutation
import com.google.firebase.dataconnect.minimaldemo.connector.Zwda6x9zyyKey
import com.google.firebase.dataconnect.minimaldemo.connector.execute
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val app: MyApplication) : ViewModel() {

  private val rs = RandomSource.default()

  // Threading Note: _state and the variables below it may ONLY be accessed (read from and/or
  // written to) by the main thread; otherwise a race condition and undefined behavior will result.
  private val _stateSequenceNumber = MutableStateFlow(111999L)
  val stateSequenceNumber: StateFlow<Long> = _stateSequenceNumber.asStateFlow()

  var insertState: OperationState<InsertItemMutation.Variables, Zwda6x9zyyKey>? = null
    private set

  var getState: OperationState<Zwda6x9zyyKey, GetItemByKeyQuery.Data.Item?>? = null
    private set

  var deleteState: OperationState<Zwda6x9zyyKey, Unit>? = null
    private set

  var lastInsertedKey: Zwda6x9zyyKey? = null
    private set

  @OptIn(ExperimentalCoroutinesApi::class)
  @MainThread
  fun insertItem() {
    val arb = Arb.insertItemVariables()
    val variables = if (rs.random.nextFloat() < 0.333f) arb.edgecase(rs)!! else arb.next(rs)

    // If there is already an "insert" in progress, then just return and let the in-progress
    // operation finish.
    if (insertState is OperationState.InProgress) {
      return
    }

    // Start a new coroutine to perform the "insert" operation.
    Log.i(TAG, "Inserting item: $variables")
    val job: Deferred<Zwda6x9zyyKey> =
      viewModelScope.async { app.getConnector().insertItem.ref(variables).execute().data.key }
    val inProgressOperationState =
      OperationState.InProgress(_stateSequenceNumber.value, variables, job)
    insertState = inProgressOperationState
    _stateSequenceNumber.value++

    // Update the internal state once the "insert" operation has completed.
    job.invokeOnCompletion { exception ->
      // Don't log CancellationException, as documented by invokeOnCompletion().
      if (exception is CancellationException) {
        return@invokeOnCompletion
      }

      val result =
        if (exception !== null) {
          Log.w(TAG, "WARNING: Inserting item FAILED: $exception (variables=$variables)", exception)
          Result.failure(exception)
        } else {
          val key = job.getCompleted()
          Log.i(TAG, "Inserted item with key: $key (variables=${variables})")
          Result.success(key)
        }

      viewModelScope.launch {
        if (insertState === inProgressOperationState) {
          insertState = OperationState.Completed(_stateSequenceNumber.value, variables, result)
          result.onSuccess { lastInsertedKey = it }
          _stateSequenceNumber.value++
        }
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun getItem() {
    // If there is no previous successful "insert" operation, then we don't know any ID's to get,
    // so just do nothing.
    val key: Zwda6x9zyyKey = lastInsertedKey ?: return

    // If there is already a "get" in progress, then just return and let the in-progress operation
    // finish.
    if (getState is OperationState.InProgress) {
      return
    }

    // Start a new coroutine to perform the "get" operation.
    Log.i(TAG, "Retrieving item with key: $key")
    val job: Deferred<GetItemByKeyQuery.Data.Item?> =
      viewModelScope.async { app.getConnector().getItemByKey.execute(key).data.item }
    val inProgressOperationState = OperationState.InProgress(_stateSequenceNumber.value, key, job)
    getState = inProgressOperationState
    _stateSequenceNumber.value++

    // Update the internal state once the "get" operation has completed.
    job.invokeOnCompletion { exception ->
      // Don't log CancellationException, as documented by invokeOnCompletion().
      if (exception is CancellationException) {
        return@invokeOnCompletion
      }

      val result =
        if (exception !== null) {
          Log.w(TAG, "WARNING: Retrieving item with key=$key FAILED: $exception", exception)
          Result.failure(exception)
        } else {
          val item = job.getCompleted()
          Log.i(TAG, "Retrieved item with key: $key (item=${item})")
          Result.success(item)
        }

      viewModelScope.launch {
        if (getState === inProgressOperationState) {
          getState = OperationState.Completed(_stateSequenceNumber.value, key, result)
          _stateSequenceNumber.value++
        }
      }
    }
  }

  fun deleteItem() {
    // If there is no previous successful "insert" operation, then we don't know any ID's to delete,
    // so just do nothing.
    val key: Zwda6x9zyyKey = lastInsertedKey ?: return

    // If there is already a "delete" in progress, then just return and let the in-progress
    // operation finish.
    if (deleteState is OperationState.InProgress) {
      return
    }

    // Start a new coroutine to perform the "delete" operation.
    Log.i(TAG, "Deleting item with key: $key")
    val job: Deferred<Unit> =
      viewModelScope.async { app.getConnector().deleteItemByKey.execute(key) }
    val inProgressOperationState = OperationState.InProgress(_stateSequenceNumber.value, key, job)
    deleteState = inProgressOperationState
    _stateSequenceNumber.value++

    // Update the internal state once the "delete" operation has completed.
    job.invokeOnCompletion { exception ->
      // Don't log CancellationException, as documented by invokeOnCompletion().
      if (exception is CancellationException) {
        return@invokeOnCompletion
      }

      val result =
        if (exception !== null) {
          Log.w(TAG, "WARNING: Deleting item with key=$key FAILED: $exception", exception)
          Result.failure(exception)
        } else {
          Log.i(TAG, "Deleted item with key: $key")
          Result.success(Unit)
        }

      viewModelScope.launch {
        if (deleteState === inProgressOperationState) {
          deleteState = OperationState.Completed(_stateSequenceNumber.value, key, result)
          _stateSequenceNumber.value++
        }
      }
    }
  }

  sealed interface OperationState<out Variables, out Data> {
    val sequenceNumber: Long

    data class InProgress<out Variables, out Data>(
      override val sequenceNumber: Long,
      val variables: Variables,
      val job: Deferred<Data>,
    ) : OperationState<Variables, Data>

    data class Completed<out Variables, out Data>(
      override val sequenceNumber: Long,
      val variables: Variables,
      val result: Result<Data>,
    ) : OperationState<Variables, Data>
  }

  companion object {
    private const val TAG = "MainViewModel"

    val Factory: ViewModelProvider.Factory = viewModelFactory {
      initializer { MainViewModel(this[APPLICATION_KEY] as MyApplication) }
    }
  }
}
