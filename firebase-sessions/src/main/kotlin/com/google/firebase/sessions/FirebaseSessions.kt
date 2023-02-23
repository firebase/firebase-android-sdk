package com.google.firebase.sessions

import androidx.annotation.Discouraged
import com.google.firebase.FirebaseApp
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app

class FirebaseSessions internal constructor() {
  @Discouraged(message = "This will be replaced with a real API.")
  fun greeting(): String = "Matt says hi!"

  companion object {
    @JvmStatic
    val instance: FirebaseSessions
      get() = getInstance(Firebase.app)

    @JvmStatic
    fun getInstance(app: FirebaseApp): FirebaseSessions = app.get(FirebaseSessions::class.java)
  }
}
