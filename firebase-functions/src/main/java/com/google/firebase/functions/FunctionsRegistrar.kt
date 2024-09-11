// Copyright 2018 Google LLC
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
package com.google.firebase.functions

import android.content.Context
import androidx.annotation.Keep
import com.google.firebase.FirebaseOptions
import com.google.firebase.annotations.concurrent.Lightweight
import com.google.firebase.annotations.concurrent.UiThread
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentContainer
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.components.Dependency
import com.google.firebase.components.Qualified
import com.google.firebase.iid.internal.FirebaseInstanceIdInternal
import com.google.firebase.platforminfo.LibraryVersionComponent
import java.util.Arrays
import java.util.concurrent.Executor

/**
 * Registers [FunctionsMultiResourceComponent].
 *
 * @hide
 */
@Keep
class FunctionsRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> {
    val liteExecutor = Qualified.qualified(Lightweight::class.java, Executor::class.java)
    val uiExecutor = Qualified.qualified(UiThread::class.java, Executor::class.java)
    return Arrays.asList(
      Component.builder(FunctionsMultiResourceComponent::class.java)
        .name(LIBRARY_NAME)
        .add(Dependency.required(Context::class.java))
        .add(Dependency.required(FirebaseOptions::class.java))
        .add(Dependency.optionalProvider(InternalAuthProvider::class.java))
        .add(Dependency.requiredProvider(FirebaseInstanceIdInternal::class.java))
        .add(Dependency.deferred(InteropAppCheckTokenProvider::class.java))
        .add(Dependency.required(liteExecutor))
        .add(Dependency.required(uiExecutor))
        .factory { c: ComponentContainer ->
          DaggerFunctionsComponent.builder()
            .setApplicationContext(c.get(Context::class.java))
            .setFirebaseOptions(c.get(FirebaseOptions::class.java))
            .setLiteExecutor(c.get(liteExecutor))
            .setUiExecutor(c.get(uiExecutor))
            .setAuth(c.getProvider(InternalAuthProvider::class.java))
            .setIid(c.getProvider(FirebaseInstanceIdInternal::class.java))
            .setAppCheck(c.getDeferred(InteropAppCheckTokenProvider::class.java))
            .build()
            ?.multiResourceComponent
        }
        .build(),
      LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME)
    )
  }

  companion object {
    private const val LIBRARY_NAME = "fire-fn"
  }
}
