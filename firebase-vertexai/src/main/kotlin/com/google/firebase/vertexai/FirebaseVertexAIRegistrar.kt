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

import androidx.annotation.Keep
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.components.Dependency
import com.google.firebase.components.Qualified.unqualified
import com.google.firebase.platforminfo.LibraryVersionComponent

/**
 * [ComponentRegistrar] for setting up [FirebaseVertexAI] and its internal dependencies.
 *
 * @hide
 */
@Keep
internal class FirebaseVertexAIRegistrar : ComponentRegistrar {
  override fun getComponents() =
    listOf(
      Component.builder(FirebaseVertexAIMultiResourceComponent::class.java)
        .name(LIBRARY_NAME)
        .add(Dependency.required(firebaseApp))
        .add(Dependency.optionalProvider(appCheckInterop))
        .add(Dependency.optionalProvider(internalAuthProvider))
        .factory { container ->
          FirebaseVertexAIMultiResourceComponent(
            container[firebaseApp],
            container.getProvider(appCheckInterop),
            container.getProvider(internalAuthProvider)
          )
        }
        .build(),
      LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME),
    )

  private companion object {
    private const val LIBRARY_NAME = "fire-vertex"

    private val firebaseApp = unqualified(FirebaseApp::class.java)
    private val appCheckInterop = unqualified(InteropAppCheckTokenProvider::class.java)
    private val internalAuthProvider = unqualified(InternalAuthProvider::class.java)
  }
}
