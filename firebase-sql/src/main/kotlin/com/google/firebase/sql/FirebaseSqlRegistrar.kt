/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.sql

import androidx.annotation.Keep
import com.google.firebase.FirebaseApp
import com.google.firebase.annotations.concurrent.Blocking
import com.google.firebase.annotations.concurrent.Lightweight
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.components.Dependency
import com.google.firebase.components.Qualified
import com.google.firebase.platforminfo.LibraryVersionComponent
import java.util.concurrent.Executor
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Registers the [FirebaseSqlComponent] of each [FirebaseApp], and the name and version of the SDK
 * for platform logging.
 *
 * The component gets the blocking and the lightweight executors of Firebase, which run the work of
 * every Firebase SDK in the app.
 *
 * `ComponentDiscovery` creates this class by reflection, from its name in the manifest, so it must
 * be kept.
 */
@Keep
internal class FirebaseSqlRegistrar : ComponentRegistrar {
  @Keep
  override fun getComponents(): List<Component<*>> =
    listOf(
      Component.builder(FirebaseSqlComponent::class.java)
        .name(LIBRARY_NAME)
        .add(Dependency.required(FirebaseApp::class.java))
        .add(Dependency.deferred(InternalAuthProvider::class.java))
        .add(Dependency.deferred(InteropAppCheckTokenProvider::class.java))
        .add(Dependency.required(blockingExecutor))
        .add(Dependency.required(lightweightExecutor))
        .factory { container ->
          FirebaseSqlComponent(
            container.get(FirebaseApp::class.java),
            container.getDeferred(InternalAuthProvider::class.java),
            container.getDeferred(InteropAppCheckTokenProvider::class.java),
            blockingDispatcher = container.get(blockingExecutor).asCoroutineDispatcher(),
            nonBlockingDispatcher = container.get(lightweightExecutor).asCoroutineDispatcher()
          )
        }
        .build(),
      LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME)
    )

  private companion object {
    private const val LIBRARY_NAME = "fire-sql"

    private val blockingExecutor = Qualified.qualified(Blocking::class.java, Executor::class.java)

    private val lightweightExecutor =
      Qualified.qualified(Lightweight::class.java, Executor::class.java)
  }
}
