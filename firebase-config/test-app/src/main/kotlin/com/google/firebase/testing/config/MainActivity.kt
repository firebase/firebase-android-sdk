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
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Task
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.recordFatalException
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException


class MainActivity : AppCompatActivity() {

  private lateinit var remoteConfig: FirebaseRemoteConfig

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    remoteConfig = FirebaseRemoteConfig.getInstance()

    findViewById<TextView>(R.id.greeting_text).text = getText(R.string.firebase_greetings)

    // Remote Config functionality

    findViewById<Button>(R.id.get_config_button).setOnClickListener {
      val configMap = remoteConfig.all
      var configString = "";
      configString += "--------- config start ---------\n"
      configMap.forEach { entry ->
        val keyValueString = entry.key + ": " + entry.value.asString() + "\n"
        configString += keyValueString
      }
      configString += "--------- config end ---------\n"
      logToConsole(configString)
    }

    findViewById<Button>(R.id.clear_button).setOnClickListener {
      findViewById<TextView>(R.id.log_window).text = ""
    }

    findViewById<Button>(R.id.fetch_and_activate_button).setOnClickListener {
      remoteConfig.fetchAndActivate().addOnCompleteListener {didActivate: Task<Boolean> ->
          Log.d("RolloutsTestApp", "FetchAndActivate completed. Did activate? : " + didActivate.result)
          logToConsole("Config fetched and activated!\nDid config change: " + didActivate.result)
      }
    }

    findViewById<Button>(R.id.activate_button).setOnClickListener {
      remoteConfig.activate().addOnCompleteListener { didActivate: Task<Boolean> ->
        Log.d("RolloutsTestApp", "Activate completed. Did activate? : " + didActivate.result)
        logToConsole("Config activated!\nDid config change: " + didActivate.result)
      }
    }

    findViewById<Button>(R.id.fetch_button).setOnClickListener {
      remoteConfig.fetch(0).addOnCompleteListener {
        Log.d("RolloutsTestApp", "Fetched config!")
        logToConsole("Config fetched!")
      }
    }

    remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
      override fun onUpdate(configUpdate: ConfigUpdate) {
        remoteConfig.activate().addOnCompleteListener { didActivate: Task<Boolean> ->
          val logString = "Real-time update!\nUpdated keys: " + configUpdate.updatedKeys.toString()
          logToConsole(logString)
        }
      }

      override fun onError(error: FirebaseRemoteConfigException) {
        Log.w("RolloutsTestApp", "Config update error with code: " + error.code, error)
      }
    })

    // Crashlytics integration

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

  fun logToConsole(message: String) {
    val console = findViewById<TextView>(R.id.log_window)
    console.append(message + "\n")
    // scroll to bottom
    val scrollView = findViewById<ScrollView>(R.id.scroll_view)
    scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
  }
}
