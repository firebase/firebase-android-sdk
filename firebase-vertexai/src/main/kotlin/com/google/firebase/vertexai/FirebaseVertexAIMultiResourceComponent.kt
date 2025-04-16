/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.vertexai

import androidx.annotation.GuardedBy
import com.google.firebase.FirebaseApp
import com.google.firebase.annotations.concurrent.Background
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.inject.Provider
import com.google.firebase.vertexai.type.GenerativeBackend
import kotlin.coroutines.CoroutineContext

/**
 * Multi-resource container for Firebase Vertex AI.
 *
 * @hide
 */
internal class FirebaseVertexAIMultiResourceComponent(
  private val app: FirebaseApp,
  @Background val backgroundDispatcher: CoroutineContext,
  private val appCheckProvider: Provider<InteropAppCheckTokenProvider>,
  private val internalAuthProvider: Provider<InternalAuthProvider>,
) {

  @GuardedBy("this")
  private val regularVertexInstances: MutableMap<String, FirebaseVertexAI> = mutableMapOf()


    @GuardedBy("this")
    private val vertexInstances: MutableMap<String, FirebaseAI> = mutableMapOf()

  @GuardedBy("this") private val firebaseAiInstance: MutableList<FirebaseAI> = mutableListOf()

    fun getRegularVertexAI(location: String): FirebaseVertexAI =
        synchronized(this) {
            regularVertexInstances[location]
                ?: FirebaseVertexAI(
                    app,
                    backgroundDispatcher,
                    location,
                    appCheckProvider,
                    internalAuthProvider,
                )
                    .also { regularVertexInstances[location] = it }
        }

    fun getVertexAI(location: String): FirebaseAI =
    synchronized(this) {
        vertexInstances[location]
        ?: FirebaseAI(
            app,
            GenerativeBackend.VERTEX_AI,
            backgroundDispatcher,
            location,
            appCheckProvider,
            internalAuthProvider,
          )
          .also { vertexInstances[location] = it }
    }

    // THIS CAN BE DONE BETTER
  fun getFirebaseAI(location: String?): FirebaseAI {
      if (location != null) {
          return synchronized(this) {
              firebaseAiInstance.getOrNull(0)
                  ?: FirebaseAI(
                      app,
                      GenerativeBackend.VERTEX_AI,
                      backgroundDispatcher,
                      location,
                      appCheckProvider,
                      internalAuthProvider,
                  )
                      .also { firebaseAiInstance.add(it) }
          }
      }
      return synchronized(this) {
          firebaseAiInstance.getOrNull(0)
              ?: FirebaseAI(
                  app,
                  GenerativeBackend.GOOGLE_AI,
                  backgroundDispatcher,
                  "UNUSED",
                  appCheckProvider,
                  internalAuthProvider,
              )
                  .also { firebaseAiInstance.add(it) }
      }
  }
}
