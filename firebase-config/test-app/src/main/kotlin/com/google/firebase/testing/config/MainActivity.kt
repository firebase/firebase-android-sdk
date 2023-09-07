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
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig

class MainActivity : AppCompatActivity() {

  private lateinit var remoteConfig: FirebaseRemoteConfig

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    findViewById<TextView>(R.id.greeting_text).text = getText(R.string.firebase_greetings)
    findViewById<Button>(R.id.crash_button).setOnClickListener {
      throw RuntimeException("JVM Crash")
    }

    findViewById<Button>(R.id.fetch_button).setOnClickListener {
      remoteConfig = FirebaseRemoteConfig.getInstance()
      remoteConfig.fetch(0).addOnCompleteListener {
        Log.d("RolloutsTestApp", "Fetched config!")
        remoteConfig.activate()
      }
    }
  }
}
