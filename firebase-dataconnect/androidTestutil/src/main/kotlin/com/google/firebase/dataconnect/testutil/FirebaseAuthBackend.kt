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

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

sealed interface FirebaseAuthBackend {

  fun getFirebaseAuth(app: FirebaseApp): FirebaseAuth = FirebaseAuth.getInstance(app)

  object Production : FirebaseAuthBackend {
    override fun toString() = "FirebaseAuthBackend.Production"
  }

  data class Emulator(val host: String? = null, val port: Int? = null) : FirebaseAuthBackend {
    override fun toString() = "FirebaseAuthBackend.Emulator(host=$host, port=$port)"

    override fun getFirebaseAuth(app: FirebaseApp): FirebaseAuth =
      super.getFirebaseAuth(app).apply {
        val emulatorHost = host ?: DEFAULT_HOST
        val emulatorPort = port ?: DEFAULT_PORT
        useEmulator(emulatorHost, emulatorPort)
      }

    companion object {
      const val DEFAULT_HOST = "10.0.2.2"
      const val DEFAULT_PORT = 9099
    }
  }
}
