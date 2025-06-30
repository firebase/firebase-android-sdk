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

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.firebase.dataconnect.minimaldemo.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {

  private lateinit var viewBinding: ActivityMainBinding
  private val viewModel: MainViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    viewBinding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(viewBinding.root)

    viewBinding.configText.text = "build type: ${if (BuildConfig.DEBUG) "debug" else "release"}"
    viewBinding.startTestButton.setOnClickListener { viewModel.startTest() }

    lifecycleScope.launch {
      viewModel.state.flowWithLifecycle(lifecycle).collectLatest { onViewModelStateChange(it) }
    }
  }

  private fun onViewModelStateChange(newState: MainViewModel.State) {
    viewBinding.startTestButton.isEnabled =
      when (newState) {
        is MainViewModel.State.NotStarted -> true
        is MainViewModel.State.Running -> false
        is MainViewModel.State.Finished -> true
      }

    viewBinding.progressText.text =
      when (newState) {
        is MainViewModel.State.NotStarted -> "not started"
        is MainViewModel.State.Running -> "running"
        is MainViewModel.State.Finished ->
          buildString {
            append("Test completed in ${newState.elapsedTime.inWholeSeconds} seconds:\n")
            when (newState) {
              is MainViewModel.State.Finished.Error -> append(newState.error)
              is MainViewModel.State.Finished.Success -> {
                newState.result.apply {
                  append("original: ${original.logString}\n")
                  append("slow: ${slow.logString}\n")
                  append("new: ${new.logString}\n")
                  append("denver: ${denver.logString}")
                }
              }
            }
          }
      }
  }
}
