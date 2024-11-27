/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.testutil

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.firebase.initialize
import com.google.firebase.util.nextAlphanumericString
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A JUnit test rule that creates instances of [FirebaseApp] for use during testing, and closes them
 * upon test completion.
 */
class TestFirebaseAppFactory : FactoryTestRule<FirebaseApp, Nothing>() {

  override fun createInstance(params: Nothing?) =
    Firebase.initialize(
      Firebase.app.applicationContext,
      Firebase.app.options,
      "test-app-${Random.nextAlphanumericString(length=10)}"
    )

  override fun destroyInstance(instance: FirebaseApp) {
    // Work around app crash due to IllegalStateException from FirebaseAuth if `delete()` is called
    // very quickly after `FirebaseApp.getInstance()`. See b/378116261 for details.
    @OptIn(DelicateCoroutinesApi::class)
    GlobalScope.launch(Dispatchers.IO) {
      delay(1.seconds)
      instance.delete()
    }
  }
}
