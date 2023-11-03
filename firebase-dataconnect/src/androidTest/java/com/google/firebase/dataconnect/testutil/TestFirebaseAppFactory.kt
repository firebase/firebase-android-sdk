package com.google.firebase.dataconnect.testutil

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.firebase.initialize

/**
 * A JUnit test rule that creates instances of [FirebaseApp] for use during testing, and closes them
 * upon test completion.
 */
class TestFirebaseAppFactory : FactoryTestRule<FirebaseApp, Nothing>(startId = 0xaabb0000000L) {

  override fun createInstance(instanceId: String, params: Nothing?) =
    Firebase.initialize(
      Firebase.app.applicationContext,
      Firebase.app.options,
      "test-app-$instanceId"
    )

  override fun destroyInstance(instance: FirebaseApp) {
    instance.delete()
  }
}
