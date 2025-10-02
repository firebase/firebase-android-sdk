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

package com.google.firebase.testing.config

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Task
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.recordFatalException
import com.google.firebase.remoteconfig.FirebaseRemoteConfig

class MainActivity : AppCompatActivity() {

  private lateinit var remoteConfig: FirebaseRemoteConfig

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    findViewById<TextView>(R.id.greeting_text).text = getText(R.string.firebase_greetings)

    findViewById<Button>(R.id.fetch_button).setOnClickListener {
      remoteConfig = FirebaseRemoteConfig.getInstance()
      remoteConfig.fetch(0).addOnCompleteListener {
        Log.d("RolloutsTestApp", "Fetched config!")
        remoteConfig.activate().addOnCompleteListener { didActivate: Task<Boolean> ->
          Log.d("RolloutsTestApp", "Activate completed. Did activate? : " + didActivate.result)
        }
      }
    }

    findViewById<Button>(R.id.jvm_crash_button).setOnClickListener {
      throw RuntimeException("JVM Crash")
    }

    findViewById<Button>(R.id.anr_button).setOnClickListener {
      // Cause an ANR
      while (true) {
        Thread.sleep(1000)
      }
    }

    findViewById<Button>(R.id.on_demand_fatal_button).setOnClickListener {
      FirebaseCrashlytics.getInstance()
        .recordFatalException(RuntimeException("This is an on demand fatal"))
    }

    findViewById<Button>(R.id.non_fatal_button).setOnClickListener {
      FirebaseCrashlytics.getInstance().recordException(RuntimeException("This is an non-fatal"))
    }
  }
}
