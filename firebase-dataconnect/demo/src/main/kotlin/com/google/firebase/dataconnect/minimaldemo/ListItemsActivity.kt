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
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.firebase.dataconnect.minimaldemo.databinding.ActivityListItemsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ListItemsActivity : AppCompatActivity() {

  private lateinit var myApplication: MyApplication
  private lateinit var viewBinding: ActivityListItemsBinding
  private val viewModel: ListItemsViewModel by viewModels { ListItemsViewModel.Factory }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    myApplication = application as MyApplication

    viewBinding = ActivityListItemsBinding.inflate(layoutInflater)
    setContentView(viewBinding.root)

    lifecycleScope.launch {
      if (viewModel.loadingState == ListItemsViewModel.LoadingState.NotStarted) {
        viewModel.getItems()
      }
      viewModel.stateSequenceNumber.flowWithLifecycle(lifecycle).collectLatest {
        onViewModelStateChange()
      }
    }
  }

  private fun onViewModelStateChange() {
    val items = viewModel.result?.getOrNull()
    val exception = viewModel.result?.exceptionOrNull()
    val loadingState = viewModel.loadingState

    if (loadingState == ListItemsViewModel.LoadingState.InProgress) {
      viewBinding.statusText.text = "Loading Items..."
      viewBinding.statusText.visibility = View.VISIBLE
      viewBinding.recyclerView.visibility = View.GONE
    } else if (items !== null) {
      viewBinding.statusText.text = "Items: $items"
      viewBinding.statusText.visibility = View.VISIBLE
      viewBinding.recyclerView.visibility = View.GONE
    } else if (exception !== null) {
      viewBinding.statusText.text = "Loading items FAILED: $exception"
      viewBinding.statusText.visibility = View.VISIBLE
      viewBinding.recyclerView.visibility = View.GONE
    } else {
      viewBinding.statusText.text = null
      viewBinding.statusText.visibility = View.GONE
      viewBinding.recyclerView.visibility = View.GONE
    }
  }
}
