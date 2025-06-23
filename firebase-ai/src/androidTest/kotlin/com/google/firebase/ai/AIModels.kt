/*
 * Copyright 2025 Google LLC
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
package com.google.firebase.ai

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.ai.type.GenerativeBackend

class AIModels {

  companion object {
    private val API_KEY: String = ""
    private val APP_ID: String = ""
    private val PROJECT_ID: String = "fireescape-integ-tests"
    // General purpose models
    var app: FirebaseApp? = null
    var flash2Model: GenerativeModel? = null
    var flash2LiteModel: GenerativeModel? = null

    /** Returns a list of general purpose models to test */
    fun getModels(): List<GenerativeModel> {
      if (flash2Model == null) {
        setup()
      }
      return listOf(flash2Model!!, flash2LiteModel!!)
    }

    fun app(): FirebaseApp {
      if (app == null) {
        setup()
      }
      return app!!
    }

    fun setup() {
      val context = InstrumentationRegistry.getInstrumentation().context
      app =
        FirebaseApp.initializeApp(
          context,
          FirebaseOptions.Builder()
            .setApiKey(API_KEY)
            .setApplicationId(APP_ID)
            .setProjectId(PROJECT_ID)
            .build()
        )
      flash2Model =
        FirebaseAI.getInstance(app!!, GenerativeBackend.vertexAI())
          .generativeModel(
            modelName = "gemini-2.0-flash",
          )
      flash2LiteModel =
        FirebaseAI.getInstance(app!!, GenerativeBackend.vertexAI())
          .generativeModel(
            modelName = "gemini-2.0-flash-lite",
          )
    }
  }
}
