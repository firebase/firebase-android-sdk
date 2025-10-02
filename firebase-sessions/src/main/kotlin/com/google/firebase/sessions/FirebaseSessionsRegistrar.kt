/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.sessions

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import androidx.datastore.core.MultiProcessDataStoreFactory
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
import com.google.firebase.sessions.FirebaseSessions.Companion.TAG
import kotlinx.coroutines.CoroutineDispatcher

/**
 * [ComponentRegistrar] for setting up [FirebaseSessions] and its internal dependencies.
 *
 * @hide
 */
@Keep
internal class FirebaseSessionsRegistrar : ComponentRegistrar {
  override fun getComponents() =
    listOf(
      Component.builder(FirebaseSessions::class.java)
        .name(LIBRARY_NAME)
        .add(Dependency.required(firebaseSessionsComponent))
        .factory { container -> container[firebaseSessionsComponent].firebaseSessions }
        .eagerInDefaultApp()
        .build(),
      Component.builder(FirebaseSessionsComponent::class.java)
        .name("fire-sessions-component")
        .add(Dependency.required(appContext))
        .add(Dependency.required(backgroundDispatcher))
        .add(Dependency.required(blockingDispatcher))
        .add(Dependency.required(firebaseApp))
        .add(Dependency.required(firebaseInstallationsApi))
        .add(Dependency.requiredProvider(transportFactory))
        .factory { container ->
          DaggerFirebaseSessionsComponent.builder()
            .appContext(container[appContext])
            .backgroundDispatcher(container[backgroundDispatcher])
            .blockingDispatcher(container[blockingDispatcher])
            .firebaseApp(container[firebaseApp])
            .firebaseInstallationsApi(container[firebaseInstallationsApi])
            .transportFactoryProvider(container.getProvider(transportFactory))
            .build()
        }
        .build(),
      LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME),
    )

  private companion object {
    const val LIBRARY_NAME = "fire-sessions"

    val appContext = unqualified(Context::class.java)
    val firebaseApp = unqualified(FirebaseApp::class.java)
    val firebaseInstallationsApi = unqualified(FirebaseInstallationsApi::class.java)
    val backgroundDispatcher = qualified(Background::class.java, CoroutineDispatcher::class.java)
    val blockingDispatcher = qualified(Blocking::class.java, CoroutineDispatcher::class.java)
    val transportFactory = unqualified(TransportFactory::class.java)
    val firebaseSessionsComponent = unqualified(FirebaseSessionsComponent::class.java)

    init {
      try {
        MultiProcessDataStoreFactory.javaClass
      } catch (ex: NoClassDefFoundError) {
        Log.w(
          TAG,
          """
          Your app is experiencing a known issue in the Android Gradle plugin, see https://issuetracker.google.com/328687152

          It affects Java-only apps using AGP version 8.3.2 and under. To avoid the issue, either:

          1. Upgrade Android Gradle plugin to 8.4.0+
             Follow the guide at https://developer.android.com/build/agp-upgrade-assistant

          2. Or, add the Kotlin plugin to your app
             Follow the guide at https://developer.android.com/kotlin/add-kotlin

          3. Or, do the technical workaround described in https://issuetracker.google.com/issues/328687152#comment3
        """
            .trimIndent(),
        )
      }
    }
  }
}
