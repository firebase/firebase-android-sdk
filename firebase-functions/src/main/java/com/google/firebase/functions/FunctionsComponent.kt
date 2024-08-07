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
import com.google.firebase.FirebaseOptions
import com.google.firebase.annotations.concurrent.Lightweight
import com.google.firebase.annotations.concurrent.UiThread
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.iid.internal.FirebaseInstanceIdInternal
import com.google.firebase.inject.Deferred
import com.google.firebase.inject.Provider
import dagger.Binds
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import dagger.Provides
import java.util.concurrent.Executor
import javax.inject.Named
import javax.inject.Singleton

/** @hide */
@Component(modules = [FunctionsComponent.MainModule::class])
@Singleton
internal interface FunctionsComponent {
  val multiResourceComponent: FunctionsMultiResourceComponent?

  @Component.Builder
  interface Builder {
    @BindsInstance fun setApplicationContext(applicationContext: Context): Builder

    @BindsInstance fun setFirebaseOptions(options: FirebaseOptions): Builder

    @BindsInstance fun setLiteExecutor(@Lightweight executor: Executor): Builder

    @BindsInstance fun setUiExecutor(@UiThread executor: Executor): Builder

    @BindsInstance fun setAuth(auth: Provider<InternalAuthProvider>): Builder

    @BindsInstance fun setIid(iid: Provider<FirebaseInstanceIdInternal>): Builder

    @BindsInstance fun setAppCheck(appCheck: Deferred<InteropAppCheckTokenProvider>): Builder
    fun build(): FunctionsComponent
  }

  @Module
  interface MainModule {
    @Binds fun contextProvider(provider: FirebaseContextProvider): ContextProvider

    companion object {
      @Provides
      @Named("projectId")
      fun bindProjectId(options: FirebaseOptions): String? {
        return options.projectId
      }
    }
  }
}
