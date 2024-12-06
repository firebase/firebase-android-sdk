/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.testing.sessions

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.testing.sessions.databinding.FragmentFirstBinding
import java.util.Date
import java.util.Locale

/** A simple [Fragment] subclass as the default destination in the navigation. */
class FirstFragment : Fragment() {
  val crashlytics = FirebaseCrashlytics.getInstance()

  private var _binding: FragmentFirstBinding? = null

  // This property is only valid between onCreateView and
  // onDestroyView.
  private val binding
    get() = _binding!!

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {

    _binding = FragmentFirstBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.buttonCrash.setOnClickListener { throw RuntimeException("CRASHED") }
    binding.buttonNonFatal.setOnClickListener {
      crashlytics.recordException(IllegalStateException())
    }
    binding.buttonAnr.setOnClickListener {
      while (true) {
        Thread.sleep(1_000)
      }
    }
    binding.buttonForegroundProcess.setOnClickListener {
      if (binding.buttonForegroundProcess.getText().startsWith("Start")) {
        ForegroundService.startService(getContext()!!, "Starting service at ${getDateText()}")
        binding.buttonForegroundProcess.setText("Stop foreground service")
      } else {
        ForegroundService.stopService(getContext()!!)
        binding.buttonForegroundProcess.setText("Start foreground service")
      }
    }
    binding.startSplitscreen.setOnClickListener {
      val intent = Intent(getContext()!!, SecondActivity::class.java)
      intent.addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_LAUNCH_ADJACENT)
      startActivity(intent)
      activity?.finish()
    }
    binding.startSplitscreenSame.setOnClickListener {
      val intent = Intent(getContext()!!, MainActivity::class.java)
      intent.addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_LAUNCH_ADJACENT)
      startActivity(intent)
    }
    binding.nextActivityButton.setOnClickListener {
      val intent = Intent(getContext()!!, SecondActivity::class.java)
      intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
      startActivity(intent)
    }
  }

  override fun onResume() {
    super.onResume()
    TestApplication.sessionSubscriber.registerView(binding.sessionIdFragmentText)
  }

  override fun onPause() {
    super.onPause()
    TestApplication.sessionSubscriber.unregisterView(binding.sessionIdFragmentText)
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  companion object {
    fun getDateText(): String =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
      else "unknown"
  }
}
