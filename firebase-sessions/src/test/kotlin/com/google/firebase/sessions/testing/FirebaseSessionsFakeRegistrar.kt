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

package com.google.firebase.sessions.testing

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
import com.google.firebase.sessions.BuildConfig
import com.google.firebase.sessions.FirebaseSessions
import com.google.firebase.sessions.SessionDatastore
import com.google.firebase.sessions.SessionFirelogPublisher
import com.google.firebase.sessions.SessionGenerator
import com.google.firebase.sessions.SessionLifecycleServiceBinder
import com.google.firebase.sessions.WallClock
import com.google.firebase.sessions.settings.SessionsSettings
import kotlinx.coroutines.CoroutineDispatcher

/**
 * [ComponentRegistrar] for setting up Fake components for [FirebaseSessions] and its internal
 * dependencies for unit tests.
 *
 * @hide
 */
@Keep
internal class FirebaseSessionsFakeRegistrar : ComponentRegistrar {
  override fun getComponents() =
    listOf(
      Component.builder(SessionGenerator::class.java)
        .name("session-generator")
        .factory { SessionGenerator(timeProvider = WallClock) }
        .build(),
      Component.builder(FakeFirelogPublisher::class.java)
        .name("fake-session-publisher")
        .factory { FakeFirelogPublisher() }
        .build(),
      Component.builder(SessionFirelogPublisher::class.java)
        .name("session-publisher")
        .add(Dependency.required(fakeFirelogPublisher))
        .factory { container -> container.get(fakeFirelogPublisher) }
        .build(),
      Component.builder(SessionsSettings::class.java)
        .name("sessions-settings")
        .add(Dependency.required(firebaseApp))
        .add(Dependency.required(blockingDispatcher))
        .add(Dependency.required(backgroundDispatcher))
        .factory { container ->
          SessionsSettings(
            container.get(firebaseApp),
            container.get(blockingDispatcher),
            container.get(backgroundDispatcher),
            fakeFirebaseInstallations,
          )
        }
        .build(),
      Component.builder(FakeSessionDatastore::class.java)
        .name("fake-sessions-datastore")
        .factory { FakeSessionDatastore() }
        .build(),
      Component.builder(SessionDatastore::class.java)
        .name("sessions-datastore")
        .add(Dependency.required(fakeDatastore))
        .factory { container -> container.get(fakeDatastore) }
        .build(),
      Component.builder(FakeSessionLifecycleServiceBinder::class.java)
        .name("fake-sessions-service-binder")
        .factory { FakeSessionLifecycleServiceBinder() }
        .build(),
      Component.builder(SessionLifecycleServiceBinder::class.java)
        .name("sessions-service-binder")
        .add(Dependency.required(fakeServiceBinder))
        .factory { container -> container.get(fakeServiceBinder) }
        .build(),
      LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME),
    )

  private companion object {
    private const val LIBRARY_NAME = "fire-sessions"

    private val firebaseApp = unqualified(FirebaseApp::class.java)
    private val firebaseInstallationsApi = unqualified(FirebaseInstallationsApi::class.java)
    private val backgroundDispatcher =
      qualified(Background::class.java, CoroutineDispatcher::class.java)
    private val blockingDispatcher =
      qualified(Blocking::class.java, CoroutineDispatcher::class.java)
    private val transportFactory = unqualified(TransportFactory::class.java)
    private val fakeFirelogPublisher = unqualified(FakeFirelogPublisher::class.java)
    private val fakeDatastore = unqualified(FakeSessionDatastore::class.java)
    private val fakeServiceBinder = unqualified(FakeSessionLifecycleServiceBinder::class.java)
    private val sessionGenerator = unqualified(SessionGenerator::class.java)
    private val sessionsSettings = unqualified(SessionsSettings::class.java)

    private val fakeFirebaseInstallations = FakeFirebaseInstallations("FaKeFiD")
  }
}
