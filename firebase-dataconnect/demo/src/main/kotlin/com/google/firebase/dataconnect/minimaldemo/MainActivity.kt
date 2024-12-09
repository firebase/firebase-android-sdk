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

import android.os.Bundle
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.firebase.dataconnect.minimaldemo.MainActivityViewModel.State.OperationState
import com.google.firebase.dataconnect.minimaldemo.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

  private lateinit var myApplication: MyApplication
  private lateinit var viewBinding: ActivityMainBinding
  private val viewModel: MainActivityViewModel by viewModels { MainActivityViewModel.Factory }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    myApplication = application as MyApplication

    viewBinding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(viewBinding.root)

    viewBinding.insertItemButton.setOnClickListener { viewModel.insertItem() }
    viewBinding.getItemButton.setOnClickListener { viewModel.getItem() }
    viewBinding.deleteItemButton.setOnClickListener { viewModel.deleteItem() }
    viewBinding.useEmulatorCheckBox.setOnCheckedChangeListener(useEmulatorOnCheckedChangeListener)
    viewBinding.debugLoggingCheckBox.setOnCheckedChangeListener(debugLoggingOnCheckedChangeListener)

    lifecycleScope.launch {
      viewModel.state.flowWithLifecycle(lifecycle).collectLatest(::collectViewModelState)
    }
  }

  override fun onResume() {
    super.onResume()
    lifecycleScope.launch {
      viewBinding.useEmulatorCheckBox.isChecked = myApplication.getUseDataConnectEmulator()
      viewBinding.debugLoggingCheckBox.isChecked = myApplication.getDataConnectDebugLoggingEnabled()
    }
  }

  private fun collectViewModelState(state: MainActivityViewModel.State) {
    viewBinding.progressText.text = state.progressText
    viewBinding.insertItemButton.isEnabled = !state.isInsertOperationInProgress
    viewBinding.getItemButton.isEnabled =
      state.isGetOperationRunnable && !state.isGetOperationInProgress
    viewBinding.deleteItemButton.isEnabled =
      state.isDeleteOperationRunnable && !state.isDeleteOperationInProgress
  }

  private val debugLoggingOnCheckedChangeListener = OnCheckedChangeListener { _, isChecked ->
    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
      return@OnCheckedChangeListener
    }
    myApplication.coroutineScope.launch {
      myApplication.setDataConnectDebugLoggingEnabled(isChecked)
    }
  }

  private val useEmulatorOnCheckedChangeListener = OnCheckedChangeListener { _, isChecked ->
    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
      return@OnCheckedChangeListener
    }
    myApplication.coroutineScope.launch { myApplication.setUseDataConnectEmulator(isChecked) }
  }

  companion object {

    private val MainActivityViewModel.State.isInsertOperationInProgress: Boolean
      get() = insertItem is OperationState.InProgress

    private val MainActivityViewModel.State.isGetOperationInProgress: Boolean
      get() = getItem is OperationState.InProgress

    private val MainActivityViewModel.State.isGetOperationRunnable: Boolean
      get() = lastInsertedKey !== null

    private val MainActivityViewModel.State.isDeleteOperationInProgress: Boolean
      get() = deleteItem is OperationState.InProgress

    private val MainActivityViewModel.State.isDeleteOperationRunnable: Boolean
      get() = lastInsertedKey !== null

    private val MainActivityViewModel.State.progressText: String?
      get() {
        val insertState = insertItem as? OperationState.SequencedOperationState
        val getState = getItem as? OperationState.SequencedOperationState
        val deleteState = deleteItem as? OperationState.SequencedOperationState

        val state =
          listOfNotNull(insertState, getState, deleteState).maxByOrNull { it.sequenceNumber }

        return if (state === null) {
          null
        } else if (state === insertState) {
          when (insertState) {
            is OperationState.InProgress ->
              "Inserting item: ${insertState.variables.toDisplayString()}"
            is OperationState.Completed ->
              insertState.result.fold(
                onSuccess = {
                  "Inserted item with id=${it.id}:\n${insertState.variables.toDisplayString()}"
                },
                onFailure = { "Inserting item ${insertState.variables} FAILED: $it" },
              )
          }
        } else if (state === getState) {
          when (getState) {
            is OperationState.InProgress -> "Retrieving item with ID ${getState.variables.id}..."
            is OperationState.Completed ->
              getState.result.fold(
                onSuccess = {
                  "Retrieved item with ID ${getState.variables.id}:\n${it?.toDisplayString()}"
                },
                onFailure = { "Retrieving item with ID ${getState.variables.id} FAILED: $it" },
              )
          }
        } else if (state === deleteState) {
          when (deleteState) {
            is OperationState.InProgress -> "Deleting item with ID ${deleteState.variables.id}..."
            is OperationState.Completed ->
              deleteState.result.fold(
                onSuccess = { "Deleted item with ID ${deleteState.variables.id}" },
                onFailure = { "Deleting item with ID ${deleteState.variables.id} FAILED: $it" },
              )
          }
        } else {
          throw RuntimeException("internal error: unknown state: $state (error code vp4rjptx6r)")
        }
      }
  }
}
