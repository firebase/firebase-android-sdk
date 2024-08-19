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
package com.google.firebase.dataconnect.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.dataconnect.demo.connector.Ctrgqyawcfbm4Connector
import com.google.firebase.dataconnect.demo.connector.execute
import com.google.firebase.dataconnect.demo.connector.instance
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    lifecycleScope.launch {
      println("zzyzx Getting Ctrgqyawcfbm4Connector")
      val connector = Ctrgqyawcfbm4Connector.instance
      connector.dataConnect.useEmulator()

      println("zzyzx Inserting person")
      val key = connector.insertPerson.execute(name = "William") { age = 42 }.data.key
      println("zzyzx Inserted person with key $key")

      println("zzyzx Getting person with key $key")
      val queryResult1 = connector.getPersonByKey.execute(key)
      println("zzyzx Got person: ${queryResult1.data.person}")

      println("zzyzx Logging out of FirebaseAuth")
      val auth = FirebaseAuth.getInstance(connector.dataConnect.app)
      auth.useEmulator("10.0.2.2", 9099)
      auth.signOut()

      connector
          .runCatching { getPersonByKeyAuth.execute(key) }
          .fold(
              onSuccess = { println("zzyzx ERROR: getPersonByKeyAuth unexpectedly succeeded") },
              onFailure = { println("zzyzx getPersonByKeyAuth failed as expected: $it") })

      println("zzyzx Logging into FirebaseAuth")
      auth.signInAnonymously().await()

      connector
          .runCatching { getPersonByKeyAuth.execute(key) }
          .fold(
              onSuccess = { println("zzyzx getPersonByKeyAuth succeeded: $it") },
              onFailure = { println("zzyzx getPersonByKeyAuth FAILED: $it") })
    }
  }
}