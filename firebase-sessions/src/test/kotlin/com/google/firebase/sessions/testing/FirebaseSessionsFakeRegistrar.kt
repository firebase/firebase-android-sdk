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

import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.components.Dependency
import com.google.firebase.components.Qualified.unqualified
import com.google.firebase.platforminfo.LibraryVersionComponent
import com.google.firebase.sessions.BuildConfig
import com.google.firebase.sessions.FirebaseSessions
import com.google.firebase.sessions.FirebaseSessionsComponent

/**
 * [ComponentRegistrar] for setting up Fake components for [FirebaseSessions] and its internal
 * dependencies for unit tests.
 */
internal class FirebaseSessionsFakeRegistrar : ComponentRegistrar {
  override fun getComponents() =
    listOf(
      Component.builder(FirebaseSessionsComponent::class.java)
        .name("fire-sessions-component")
        .add(Dependency.required(firebaseSessionsFakeComponent))
        .factory { container -> container.get(firebaseSessionsFakeComponent) }
        .build(),
      Component.builder(FirebaseSessionsFakeComponent::class.java)
        .name("fire-sessions-fake-component")
        .factory { FirebaseSessionsFakeComponent() }
        .build(),
      LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME),
    )

  private companion object {
    const val LIBRARY_NAME = "fire-sessions"

    val firebaseSessionsFakeComponent = unqualified(FirebaseSessionsFakeComponent::class.java)
  }
}
