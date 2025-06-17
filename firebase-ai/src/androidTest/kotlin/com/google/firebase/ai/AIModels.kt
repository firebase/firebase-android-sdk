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
      val context = InstrumentationRegistry.getInstrumentation().context;
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
