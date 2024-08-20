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

package com.google.firebase.dataconnect.core

import android.content.Context
import androidx.annotation.Keep
import androidx.annotation.RestrictTo
import com.google.firebase.FirebaseApp
import com.google.firebase.annotations.concurrent.Blocking
import com.google.firebase.annotations.concurrent.Lightweight
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.components.Dependency
import com.google.firebase.components.Qualified
import com.google.firebase.dataconnect.*
import com.google.firebase.platforminfo.LibraryVersionComponent
import java.util.concurrent.Executor

/**
 * [ComponentRegistrar] for setting up [FirebaseDataConnect].
 *
 * @hide
 */
@Keep
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class FirebaseDataConnectRegistrar : ComponentRegistrar {

  @Keep
  override fun getComponents() =
    listOf(
      Component.builder(FirebaseDataConnectFactory::class.java)
        .name(LIBRARY_NAME)
        .add(Dependency.required(firebaseApp))
        .add(Dependency.required(context))
        .add(Dependency.required(blockingExecutor))
        .add(Dependency.required(nonBlockingExecutor))
        .add(Dependency.deferred(internalAuthProvider))
        .add(Dependency.deferred(interopAppCheckTokenProvider))
        .factory { container ->
          FirebaseDataConnectFactory(
            context = container.get(context),
            firebaseApp = container.get(firebaseApp),
            blockingExecutor = container.get(blockingExecutor),
            nonBlockingExecutor = container.get(nonBlockingExecutor),
            deferredAuthProvider = container.getDeferred(internalAuthProvider),
            deferredAppCheckProvider = container.getDeferred(interopAppCheckTokenProvider),
          )
        }
        .build(),
      LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME)
    )

  companion object {
    private const val LIBRARY_NAME = "fire-data-connect"

    private val firebaseApp = Qualified.unqualified(FirebaseApp::class.java)
    private val context = Qualified.unqualified(Context::class.java)
    private val blockingExecutor = Qualified.qualified(Blocking::class.java, Executor::class.java)
    private val nonBlockingExecutor =
      Qualified.qualified(Lightweight::class.java, Executor::class.java)
    private val internalAuthProvider = Qualified.unqualified(InternalAuthProvider::class.java)
    private val interopAppCheckTokenProvider =
      Qualified.unqualified(InteropAppCheckTokenProvider::class.java)
  }
}
