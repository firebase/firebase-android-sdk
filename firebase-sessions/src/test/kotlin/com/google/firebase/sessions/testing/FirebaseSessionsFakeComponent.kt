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

package com.google.firebase.sessions.testing

import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.sessions.FirebaseSessions
import com.google.firebase.sessions.FirebaseSessionsComponent
import com.google.firebase.sessions.SessionDatastore
import com.google.firebase.sessions.SessionFirelogPublisher
import com.google.firebase.sessions.SessionGenerator
import com.google.firebase.sessions.SessionLifecycleServiceBinder
import com.google.firebase.sessions.settings.SessionsSettings
import com.google.firebase.sessions.settings.SettingsProvider

/** Fake component to manage [FirebaseSessions] and related, often faked, dependencies. */
@Suppress("MemberVisibilityCanBePrivate") // Keep access to fakes open for convenience
internal class FirebaseSessionsFakeComponent : FirebaseSessionsComponent {
  // TODO(mrober): Move tests that need DI to integration tests, and remove this component.

  // Fakes, access these instances to setup test cases, e.g., add interval to fake time provider.
  val fakeTimeProvider = FakeTimeProvider()
  val fakeUuidGenerator = FakeUuidGenerator()
  val fakeSessionDatastore = FakeSessionDatastore()
  val fakeFirelogPublisher = FakeFirelogPublisher()
  val fakeSessionLifecycleServiceBinder = FakeSessionLifecycleServiceBinder()

  // Settings providers, default to fake, set these to real instances for relevant test cases.
  var localOverrideSettings: SettingsProvider = FakeSettingsProvider()
  var remoteSettings: SettingsProvider = FakeSettingsProvider()

  override val firebaseSessions: FirebaseSessions
    get() = throw NotImplementedError("FirebaseSessions not implemented, use integration tests.")

  override val sessionDatastore: SessionDatastore = fakeSessionDatastore

  override val sessionFirelogPublisher: SessionFirelogPublisher = fakeFirelogPublisher

  override val sessionGenerator: SessionGenerator by lazy {
    SessionGenerator(timeProvider = fakeTimeProvider, uuidGenerator = fakeUuidGenerator)
  }

  override val sessionsSettings: SessionsSettings by lazy {
    SessionsSettings(localOverrideSettings, remoteSettings)
  }

  val sessionLifecycleServiceBinder: SessionLifecycleServiceBinder
    get() = fakeSessionLifecycleServiceBinder

  companion object {
    val instance: FirebaseSessionsFakeComponent
      get() = Firebase.app[FirebaseSessionsFakeComponent::class.java]
  }
}
