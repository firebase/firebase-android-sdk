package com.google.firebase.dataconnect.testutil

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.firebase.dataconnect.nextAlphanumericString
import com.google.firebase.initialize
import kotlin.random.Random

/**
 * A JUnit test rule that creates instances of [FirebaseApp] for use during testing, and closes them
 * upon test completion.
 */
class TestFirebaseAppFactory : FactoryTestRule<FirebaseApp, Nothing>() {

  override fun createInstance(params: Nothing?) =
    Firebase.initialize(
      Firebase.app.applicationContext,
      Firebase.app.options,
      "test-app-${Random.nextAlphanumericString()}"
    )

  override fun destroyInstance(instance: FirebaseApp) {
    instance.delete()
  }
}
