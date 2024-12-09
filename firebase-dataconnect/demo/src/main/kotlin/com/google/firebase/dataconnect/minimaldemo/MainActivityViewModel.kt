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
import java.util.Objects
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class MainActivityViewModel(private val app: MyApplication) : ViewModel() {

  // Threading Note: _state may be _read_ by any thread, but _MUST ONLY_ be written to by the
  // main thread. To support writing on other threads, special concurrency controls must be put
  // in place to address the resulting race condition.
  private val _state =
    MutableStateFlow(
      State(
        insertItem = State.OperationState.New,
        getItem = State.OperationState.New,
        deleteItem = State.OperationState.New,
        lastInsertedKey = null,
        nextSequenceNumber = 19999000,
      )
    )
  val state: StateFlow<State> = _state.asStateFlow()

  private val rs = RandomSource.default()

  @OptIn(ExperimentalCoroutinesApi::class)
  @MainThread
  fun insertItem() {
    val arb = Arb.insertItemVariables()
    val variables = if (rs.random.nextFloat() < 0.333f) arb.edgecase(rs)!! else arb.next(rs)

    val originalState = _state.value

    // If there is already an "insert" in progress, then just return and let the in-progress
    // operation finish.
    when (originalState.getItem) {
      is State.OperationState.InProgress -> return
      is State.OperationState.New,
      is State.OperationState.Completed -> Unit
    }

    // Start a new coroutine to perform the "insert" operation.
    Log.i(TAG, "Inserting item: $variables")
    val job: Deferred<Zwda6x9zyyKey> =
      viewModelScope.async { app.getConnector().insertItem.ref(variables).execute().data.key }
    val inProgressOperationState =
      State.OperationState.InProgress(originalState.nextSequenceNumber, variables, job)
    _state.value = originalState.withInsertInProgress(inProgressOperationState)

    // Update the internal state once the "insert" operation has completed.
    job.invokeOnCompletion { exception ->
      // Don't log CancellationException, as document by invokeOnCompletion().
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
        val oldState = _state.value
        if (oldState.insertItem === inProgressOperationState) {
          _state.value =
            oldState.withInsertCompleted(
              State.OperationState.Completed(oldState.nextSequenceNumber, variables, result)
            )
        }
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun getItem() {
    val originalState = _state.value

    // If there is no previous successful "insert" operation, then we don't know any ID's to get,
    // so just do nothing.
    val key: Zwda6x9zyyKey = originalState.lastInsertedKey ?: return

    // If there is already a "get" in progress, then just return and let the in-progress operation
    // finish.
    when (originalState.getItem) {
      is State.OperationState.InProgress -> return
      is State.OperationState.New,
      is State.OperationState.Completed -> Unit
    }

    // Start a new coroutine to perform the "get" operation.
    Log.i(TAG, "Retrieving item with key: $key")
    val job: Deferred<GetItemByKeyQuery.Data.Item?> =
      viewModelScope.async { app.getConnector().getItemByKey.execute(key).data.item }
    val inProgressOperationState =
      State.OperationState.InProgress(originalState.nextSequenceNumber, key, job)
    _state.value = originalState.withGetInProgress(inProgressOperationState)

    // Update the internal state once the "get" operation has completed.
    job.invokeOnCompletion { exception ->
      // Don't log CancellationException, as document by invokeOnCompletion().
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
        val oldState = _state.value
        if (oldState.getItem === inProgressOperationState) {
          _state.value =
            oldState.withGetCompleted(
              State.OperationState.Completed(oldState.nextSequenceNumber, key, result)
            )
        }
      }
    }
  }

  fun deleteItem() {
    val originalState = _state.value

    // If there is no previous successful "insert" operation, then we don't know any ID's to delete,
    // so just do nothing.
    val key: Zwda6x9zyyKey = originalState.lastInsertedKey ?: return

    // If there is already a "delete" in progress, then just return and let the in-progress
    // operation finish.
    when (originalState.getItem) {
      is State.OperationState.InProgress -> return
      is State.OperationState.New,
      is State.OperationState.Completed -> Unit
    }

    // Start a new coroutine to perform the "delete" operation.
    Log.i(TAG, "Deleting item with key: $key")
    val job: Deferred<Unit> =
      viewModelScope.async { app.getConnector().deleteItemByKey.execute(key) }
    val inProgressOperationState =
      State.OperationState.InProgress(originalState.nextSequenceNumber, key, job)
    _state.value = originalState.withDeleteInProgress(inProgressOperationState)

    // Update the internal state once the "delete" operation has completed.
    job.invokeOnCompletion { exception ->
      // Don't log CancellationException, as document by invokeOnCompletion().
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
        val oldState = _state.value
        if (oldState.deleteItem === inProgressOperationState) {
          _state.value =
            oldState.withDeleteCompleted(
              State.OperationState.Completed(oldState.nextSequenceNumber, key, result)
            )
        }
      }
    }
  }

  @Serializable
  class State(
    val insertItem: OperationState<InsertItemMutation.Variables, Zwda6x9zyyKey>,
    val getItem: OperationState<Zwda6x9zyyKey, GetItemByKeyQuery.Data.Item?>,
    val deleteItem: OperationState<Zwda6x9zyyKey, Unit>,
    val lastInsertedKey: Zwda6x9zyyKey?,
    val nextSequenceNumber: Long,
  ) {

    fun withInsertInProgress(
      insertItem: OperationState.InProgress<InsertItemMutation.Variables, Zwda6x9zyyKey>
    ): State =
      State(
        insertItem = insertItem,
        getItem = getItem,
        deleteItem = deleteItem,
        lastInsertedKey = lastInsertedKey,
        nextSequenceNumber = nextSequenceNumber + 1,
      )

    fun withInsertCompleted(
      insertItem: OperationState.Completed<InsertItemMutation.Variables, Zwda6x9zyyKey>
    ): State =
      State(
        insertItem = insertItem,
        getItem = getItem,
        deleteItem = deleteItem,
        lastInsertedKey = insertItem.result.getOrNull() ?: lastInsertedKey,
        nextSequenceNumber = nextSequenceNumber + 1,
      )

    fun withGetInProgress(
      getItem: OperationState.InProgress<Zwda6x9zyyKey, GetItemByKeyQuery.Data.Item?>
    ): State =
      State(
        insertItem = insertItem,
        getItem = getItem,
        deleteItem = deleteItem,
        lastInsertedKey = lastInsertedKey,
        nextSequenceNumber = nextSequenceNumber + 1,
      )

    fun withGetCompleted(
      getItem: OperationState.Completed<Zwda6x9zyyKey, GetItemByKeyQuery.Data.Item?>
    ): State =
      State(
        insertItem = insertItem,
        getItem = getItem,
        deleteItem = deleteItem,
        lastInsertedKey = lastInsertedKey,
        nextSequenceNumber = nextSequenceNumber + 1,
      )

    fun withDeleteInProgress(deleteItem: OperationState.InProgress<Zwda6x9zyyKey, Unit>): State =
      State(
        insertItem = insertItem,
        getItem = getItem,
        deleteItem = deleteItem,
        lastInsertedKey = lastInsertedKey,
        nextSequenceNumber = nextSequenceNumber + 1,
      )

    fun withDeleteCompleted(deleteItem: OperationState.Completed<Zwda6x9zyyKey, Unit>): State =
      State(
        insertItem = insertItem,
        getItem = getItem,
        deleteItem = deleteItem,
        lastInsertedKey = lastInsertedKey,
        nextSequenceNumber = nextSequenceNumber + 1,
      )

    override fun hashCode() = Objects.hash(insertItem, getItem, lastInsertedKey, nextSequenceNumber)

    override fun equals(other: Any?) =
      other is State &&
        insertItem == other.insertItem &&
        getItem == other.getItem &&
        deleteItem == other.deleteItem &&
        lastInsertedKey == other.lastInsertedKey &&
        nextSequenceNumber == other.nextSequenceNumber

    override fun toString() =
      "State(" +
        "insertItem=$insertItem, " +
        "getItem=$getItem, " +
        "deleteItem=$deleteItem, " +
        "lastInsertedKey=$lastInsertedKey, " +
        "sequenceNumber=$nextSequenceNumber)"

    sealed interface OperationState<out Variables, out Data> {
      data object New : OperationState<Nothing, Nothing>

      sealed interface SequencedOperationState<out Variables, out Data> :
        OperationState<Variables, Data> {
        val sequenceNumber: Long
      }

      data class InProgress<out Variables, out Data>(
        override val sequenceNumber: Long,
        val variables: Variables,
        val job: Deferred<Data>,
      ) : SequencedOperationState<Variables, Data>

      data class Completed<out Variables, out Data>(
        override val sequenceNumber: Long,
        val variables: Variables,
        val result: Result<Data>,
      ) : SequencedOperationState<Variables, Data>
    }
  }

  companion object {
    private const val TAG = "MainActivityViewModel"

    val Factory: ViewModelProvider.Factory = viewModelFactory {
      initializer { MainActivityViewModel(this[APPLICATION_KEY] as MyApplication) }
    }
  }
}
