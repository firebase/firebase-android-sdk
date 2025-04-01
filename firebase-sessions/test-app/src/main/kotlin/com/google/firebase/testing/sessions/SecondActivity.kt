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

import android.app.ActivityManager
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.firebase.perf.FirebasePerformance
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Second activity from the MainActivity that runs on a different process. */
class SecondActivity : BaseActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_second)
    findViewById<Button>(R.id.prev_activity_button).setOnClickListener {
      val intent = Intent(this, MainActivity::class.java)
      intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
      startActivity(intent)
      finish()
    }
    findViewById<Button>(R.id.second_crash_button).setOnClickListener {
      throw IllegalStateException("SecondActivity has crashed")
    }
    findViewById<Button>(R.id.second_create_trace).setOnClickListener {
      lifecycleScope.launch {
        val performanceTrace = FirebasePerformance.getInstance().newTrace("test_trace")
        performanceTrace.start()
        delay(1000)
        performanceTrace.stop()
      }
    }
    findViewById<Button>(R.id.kill_background_processes).setOnClickListener {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        getSystemService(ActivityManager::class.java)
          .killBackgroundProcesses("com.google.firebase.testing.sessions")
      }
    }
    findViewById<TextView>(R.id.process_name_second).text = getProcessName()
  }

  override fun onResume() {
    super.onResume()
    TestApplication.sessionSubscriber.registerView(findViewById(R.id.session_id_second_text))
  }

  override fun onPause() {
    super.onPause()
    TestApplication.sessionSubscriber.unregisterView(findViewById(R.id.session_id_second_text))
  }
}
