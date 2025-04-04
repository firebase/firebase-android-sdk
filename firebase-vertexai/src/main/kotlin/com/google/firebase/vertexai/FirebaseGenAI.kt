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

package com.google.firebase.vertexai

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app

/** Entry point for all Firebase Generative AI functionality. */
public class FirebaseGenAI {

  /**
   * Returns the [FirebaseVertexAI] instance for the provided [FirebaseApp] and [location].
   *
   * @param location location identifier, defaults to `us-central1`; see available
   * [Vertex AI regions](https://firebase.google.com/docs/vertex-ai/locations?platform=android#available-locations)
   * .
   */
  @JvmOverloads
  public fun vertexAI(
    app: FirebaseApp = Firebase.app,
    location: String = "us-central1",
  ): FirebaseVertexAI = FirebaseVertexAI.getInstance(app, location)

  /** Returns the [FirebaseGoogleAI] instance for the provided [FirebaseApp]. */
  @JvmOverloads
  public fun googleAI(app: FirebaseApp = Firebase.app): FirebaseGoogleAI =
    FirebaseGoogleAI.getInstance(app)

  /** Returns the [FirebaseGoogleAI] instance of the default [FirebaseApp]. */
  public val googleAI: FirebaseGoogleAI
    get() = FirebaseGoogleAI.instance

  /** Returns the [FirebaseVertexAI] instance of the default [FirebaseApp]. */
  public val vertexAI: FirebaseVertexAI
    get() = FirebaseVertexAI.instance

  internal companion object {
    internal val INSTANCE = FirebaseGenAI()
  }
}

/** Returns the [FirebaseGenAI] instance. */
public val Firebase.genAI: FirebaseGenAI
  get() = FirebaseGenAI()

/** Returns the [FirebaseGenAI] instance. */
public fun Firebase.genAI(): FirebaseGenAI = FirebaseGenAI.INSTANCE
