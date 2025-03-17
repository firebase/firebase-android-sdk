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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.dataconnect.minimaldemo.connector.GetAllItemsQuery
import com.google.firebase.dataconnect.minimaldemo.databinding.ActivityListItemsBinding
import com.google.firebase.dataconnect.minimaldemo.databinding.ListItemBinding
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
    viewBinding.recyclerView.also {
      val linearLayoutManager = LinearLayoutManager(this)
      it.layoutManager = linearLayoutManager
      val dividerItemDecoration = DividerItemDecoration(this, linearLayoutManager.layoutDirection)
      it.addItemDecoration(dividerItemDecoration)
    }
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
      viewBinding.recyclerView.adapter = null
    } else if (items !== null) {
      viewBinding.statusText.text = null
      viewBinding.statusText.visibility = View.GONE
      viewBinding.recyclerView.visibility = View.VISIBLE
      val oldAdapter = viewBinding.recyclerView.adapter as? RecyclerViewAdapterImpl
      if (oldAdapter === null || oldAdapter.items !== items) {
        viewBinding.recyclerView.adapter = RecyclerViewAdapterImpl(items)
      }
    } else if (exception !== null) {
      viewBinding.statusText.text = "Loading items FAILED: $exception"
      viewBinding.statusText.visibility = View.VISIBLE
      viewBinding.recyclerView.visibility = View.GONE
      viewBinding.recyclerView.adapter = null
    } else {
      viewBinding.statusText.text = null
      viewBinding.statusText.visibility = View.GONE
      viewBinding.recyclerView.visibility = View.GONE
    }
  }

  private class RecyclerViewAdapterImpl(val items: List<GetAllItemsQuery.Data.ItemsItem>) :
    RecyclerView.Adapter<RecyclerViewViewHolderImpl>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerViewViewHolderImpl {
      val binding = ListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
      return RecyclerViewViewHolderImpl(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerViewViewHolderImpl, position: Int) {
      holder.bindTo(items[position])
    }
  }

  private class RecyclerViewViewHolderImpl(private val binding: ListItemBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bindTo(item: GetAllItemsQuery.Data.ItemsItem) {
      binding.id.text = item.id.toString()
      binding.name.text = item.string
    }
  }
}
