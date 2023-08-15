// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.sessions

import androidx.annotation.Keep
import com.google.android.datatransport.TransportFactory
import com.google.firebase.FirebaseApp
import com.google.firebase.annotations.concurrent.Background
import com.google.firebase.annotations.concurrent.Blocking
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.components.Dependency
import com.google.firebase.components.Qualified.qualified
import com.google.firebase.components.Qualified.unqualified
import com.google.firebase.installations.FirebaseInstallationsApi
import com.google.firebase.platforminfo.LibraryVersionComponent
import kotlinx.coroutines.CoroutineDispatcher

/**
 * [ComponentRegistrar] for setting up [FirebaseSessions].
 *
 * @hide
 */
@Keep
internal class FirebaseSessionsRegistrar : ComponentRegistrar {
  override fun getComponents() =
    listOf(
      Component.builder(FirebaseSessions::class.java)
        .name(LIBRARY_NAME)
        .add(Dependency.required(firebaseApp))
        .add(Dependency.required(firebaseInstallationsApi))
        .add(Dependency.required(backgroundDispatcher))
        .add(Dependency.required(blockingDispatcher))
        .add(Dependency.requiredProvider(transportFactory))
        .factory { container ->
          FirebaseSessions(
            container.get(firebaseApp),
            container.get(firebaseInstallationsApi),
            container.get(backgroundDispatcher),
            container.get(blockingDispatcher),
            container.getProvider(transportFactory),
          )
        }
        .build(),
      LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME)
    )

  companion object {
    private const val LIBRARY_NAME = "fire-sessions"

    private val firebaseApp = unqualified(FirebaseApp::class.java)
    private val firebaseInstallationsApi = unqualified(FirebaseInstallationsApi::class.java)
    private val backgroundDispatcher =
      qualified(Background::class.java, CoroutineDispatcher::class.java)
    private val blockingDispatcher =
      qualified(Blocking::class.java, CoroutineDispatcher::class.java)
    private val transportFactory = unqualified(TransportFactory::class.java)
  }
}
