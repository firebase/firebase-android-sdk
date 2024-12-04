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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

class MainActivityViewModel(private val app: MyApplication) : ViewModel() {

  private val _state =
    MutableStateFlow(
      State(
        insertItem = State.OperationState.New,
        getItem = State.OperationState.New,
        lastInsertedKey = null,
        nextSequenceNumber = 19999000,
      )
    )
  val state: StateFlow<State> = _state.asStateFlow()

  private val rs = RandomSource.default()

  fun insertItem() {
    while (true) {
      if (tryInsertItem()) {
        break
      }
    }
  }

  private fun tryInsertItem(): Boolean {
    val arb = Arb.insertItemVariables()
    val variables = if (rs.random.nextFloat() < 0.333f) arb.edgecase(rs)!! else arb.next(rs)

    val oldState = _state.value

    // If there is already an "insert" in progress, then just return and let the in-progress
    // operation finish.
    when (oldState.getItem) {
      is State.OperationState.InProgress -> return true
      is State.OperationState.New,
      is State.OperationState.Completed -> Unit
    }

    // Create a new coroutine to perform the "insert" operation, but don't start it yet by
    // specifying start=CoroutineStart.LAZY because we won't start it until the state is
    // successfully set.
    val newInsertJob: Deferred<Zwda6x9zyyKey> =
      viewModelScope.async(start = CoroutineStart.LAZY) {
        app.getConnector().insertItem.ref(variables).execute().data.key
      }

    // Update the state and start the coroutine if it is successfully set.
    val insertItemOperationInProgressState =
      State.OperationState.InProgress(oldState.nextSequenceNumber, variables, newInsertJob)
    val newState = oldState.withInsertInProgress(insertItemOperationInProgressState)
    if (!_state.compareAndSet(oldState, newState)) {
      return false
    }

    // Actually start the coroutine now that the state has been set.
    Log.i(TAG, "Inserting item: $variables")
    newState.startInsert(insertItemOperationInProgressState)
    return true
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun State.startInsert(
    insertItemOperationInProgressState:
      State.OperationState.InProgress<InsertItemMutation.Variables, Zwda6x9zyyKey>
  ) {
    require(insertItemOperationInProgressState === insertItem)
    val job: Deferred<Zwda6x9zyyKey> = insertItemOperationInProgressState.job
    val variables: InsertItemMutation.Variables = insertItemOperationInProgressState.variables

    job.start()

    job.invokeOnCompletion { exception ->
      val result =
        if (exception !== null) {
          Log.w(TAG, "WARNING: Inserting item FAILED: $exception (variables=$variables)", exception)
          Result.failure(exception)
        } else {
          val key = job.getCompleted()
          Log.i(TAG, "Inserted item with key: $key (variables=${variables})")
          Result.success(key)
        }

      while (true) {
        val oldState = _state.value
        if (oldState.insertItem !== insertItemOperationInProgressState) {
          break
        }

        val insertItemOperationCompletedState =
          State.OperationState.Completed(oldState.nextSequenceNumber, variables, result)
        val newState = oldState.withInsertCompleted(insertItemOperationCompletedState)
        if (_state.compareAndSet(oldState, newState)) {
          break
        }
      }
    }
  }

  fun getItem() {
    while (true) {
      if (tryGetItem()) {
        break
      }
    }
  }

  private fun tryGetItem(): Boolean {
    val oldState = _state.value

    // If there is no previous successful "insert" operation, then we don't know any ID's to get,
    // so just do nothing.
    val key: Zwda6x9zyyKey = oldState.lastInsertedKey ?: return true

    // If there is already a "get" in progress, then just return and let the in-progress operation
    // finish.
    when (oldState.getItem) {
      is State.OperationState.InProgress -> return true
      is State.OperationState.New,
      is State.OperationState.Completed -> Unit
    }

    // Create a new coroutine to perform the "get" operation, but don't start it yet by specifying
    // start=CoroutineStart.LAZY because we won't start it until the state is successfully set.
    val newGetJob: Deferred<GetItemByKeyQuery.Data.Item?> =
      viewModelScope.async(start = CoroutineStart.LAZY) {
        app.getConnector().getItemByKey.execute(key).data.item
      }

    // Update the state and start the coroutine if it is successfully set.
    val getItemOperationInProgressState =
      State.OperationState.InProgress(oldState.nextSequenceNumber, key, newGetJob)
    val newState = oldState.withGetInProgress(getItemOperationInProgressState)
    if (!_state.compareAndSet(oldState, newState)) {
      return false
    }

    // Actually start the coroutine now that the state has been set.
    Log.i(TAG, "Getting item with key: $key")
    newState.startGet(getItemOperationInProgressState)
    return true
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun State.startGet(
    getItemOperationInProgressState:
      State.OperationState.InProgress<Zwda6x9zyyKey, GetItemByKeyQuery.Data.Item?>
  ) {
    require(getItemOperationInProgressState === getItem)
    val job: Deferred<GetItemByKeyQuery.Data.Item?> = getItemOperationInProgressState.job
    val key: Zwda6x9zyyKey = getItemOperationInProgressState.variables

    job.start()

    job.invokeOnCompletion { exception ->
      val result =
        if (exception !== null) {
          Log.w(TAG, "WARNING: Getting item with key $key FAILED: $exception", exception)
          Result.failure(exception)
        } else {
          val item = job.getCompleted()
          Log.i(TAG, "Got item with key $key: $item")
          Result.success(item)
        }

      while (true) {
        val oldState = _state.value
        if (oldState.getItem !== getItemOperationInProgressState) {
          break
        }

        val getItemOperationCompletedState =
          State.OperationState.Completed(
            oldState.nextSequenceNumber,
            getItemOperationInProgressState.variables,
            result,
          )
        val newState = oldState.withGetCompleted(getItemOperationCompletedState)
        if (_state.compareAndSet(oldState, newState)) {
          break
        }
      }
    }
  }

  @Serializable
  class State(
    val insertItem: OperationState<InsertItemMutation.Variables, Zwda6x9zyyKey>,
    val getItem: OperationState<Zwda6x9zyyKey, GetItemByKeyQuery.Data.Item?>,
    val lastInsertedKey: Zwda6x9zyyKey?,
    val nextSequenceNumber: Long,
  ) {

    fun withInsertInProgress(
      insertItem: OperationState.InProgress<InsertItemMutation.Variables, Zwda6x9zyyKey>
    ): State =
      State(
        insertItem = insertItem,
        getItem = getItem,
        lastInsertedKey = lastInsertedKey,
        nextSequenceNumber = nextSequenceNumber + 1,
      )

    fun withInsertCompleted(
      insertItem: OperationState.Completed<InsertItemMutation.Variables, Zwda6x9zyyKey>
    ): State =
      State(
        insertItem = insertItem,
        getItem = getItem,
        lastInsertedKey = insertItem.result.getOrNull() ?: lastInsertedKey,
        nextSequenceNumber = nextSequenceNumber + 1,
      )

    fun withGetInProgress(
      getItem: OperationState.InProgress<Zwda6x9zyyKey, GetItemByKeyQuery.Data.Item?>
    ): State =
      State(
        insertItem = insertItem,
        getItem = getItem,
        lastInsertedKey = lastInsertedKey,
        nextSequenceNumber = nextSequenceNumber + 1,
      )

    fun withGetCompleted(
      getItem: OperationState.Completed<Zwda6x9zyyKey, GetItemByKeyQuery.Data.Item?>
    ): State =
      State(
        insertItem = insertItem,
        getItem = getItem,
        lastInsertedKey = lastInsertedKey,
        nextSequenceNumber = nextSequenceNumber + 1,
      )

    override fun hashCode() = Objects.hash(insertItem, getItem, lastInsertedKey, nextSequenceNumber)

    override fun equals(other: Any?) =
      other is State &&
        insertItem == other.insertItem &&
        getItem == other.getItem &&
        lastInsertedKey == other.lastInsertedKey &&
        nextSequenceNumber == other.nextSequenceNumber

    override fun toString() =
      "State(" +
        "insertItem=$insertItem, " +
        "getItem=$getItem, " +
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
