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

import android.app.Application
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
import androidx.lifecycle.lifecycleScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.trace
import com.google.firebase.testing.sessions.databinding.FragmentFirstBinding
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A simple [Fragment] subclass as the default destination in the navigation. */
class FirstFragment : Fragment() {
  val crashlytics = FirebaseCrashlytics.getInstance()
  val performance = FirebasePerformance.getInstance()

  private var _binding: FragmentFirstBinding? = null

  // This property is only valid between onCreateView and
  // onDestroyView.
  private val binding
    get() = _binding!!

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
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
    binding.createTrace.setOnClickListener {
      lifecycleScope.launch(Dispatchers.IO) {
        val performanceTrace = performance.newTrace("test_trace")
        performanceTrace.start()
        delay(1000)
        performanceTrace.stop()
      }
    }
    binding.createTrace2.setOnClickListener {
      lifecycleScope.launch(Dispatchers.IO) {
        val performanceTrace = performance.newTrace("test_trace_2")
        performanceTrace.start()
        delay(1200)
        performanceTrace.stop()
      }
    }
    binding.createNetworkTrace.setOnClickListener {
      lifecycleScope.launch(Dispatchers.IO) {
        val url = URL("https://www.google.com")
        val metric =
          performance.newHttpMetric("https://www.google.com", FirebasePerformance.HttpMethod.GET)
        metric.trace {
          val conn = url.openConnection() as HttpURLConnection
          val content = conn.inputStream.bufferedReader().use { it.readText() }
          setHttpResponseCode(conn.responseCode)
          setResponsePayloadSize(content.length.toLong())
          conn.disconnect()
        }
      }
    }
    binding.buttonForegroundProcess.setOnClickListener {
      if (binding.buttonForegroundProcess.getText().startsWith("Start")) {
        ForegroundService.startService(requireContext(), "Starting service at ${getDateText()}")
        binding.buttonForegroundProcess.setText("Stop foreground service")
      } else {
        ForegroundService.stopService(requireContext())
        binding.buttonForegroundProcess.setText("Start foreground service")
      }
    }
    binding.startSplitscreen.setOnClickListener {
      val intent = Intent(requireContext(), SecondActivity::class.java)
      intent.addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_LAUNCH_ADJACENT)
      startActivity(intent)
    }
    binding.startSplitscreenSame.setOnClickListener {
      val intent = Intent(requireContext(), MainActivity::class.java)
      intent.addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_LAUNCH_ADJACENT)
      startActivity(intent)
    }
    binding.nextActivityButton.setOnClickListener {
      val intent = Intent(requireContext(), SecondActivity::class.java)
      intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
      startActivity(intent)
    }
    binding.processName.text = getProcessName()
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

    fun getProcessName(): String =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName()
      else "unknown"
  }
}
