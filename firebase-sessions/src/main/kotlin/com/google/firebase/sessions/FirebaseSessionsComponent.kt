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

package com.google.firebase.sessions

import android.content.Context
import com.google.android.datatransport.TransportFactory
import com.google.firebase.FirebaseApp
import com.google.firebase.annotations.concurrent.Background
import com.google.firebase.annotations.concurrent.Blocking
import com.google.firebase.inject.Provider
import com.google.firebase.installations.FirebaseInstallationsApi
import com.google.firebase.sessions.settings.SessionsSettings
import dagger.Binds
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import dagger.Provides
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

/** Dagger component to provide [FirebaseSessions] and its dependencies. */
@Singleton
@Component(modules = [FirebaseSessionsComponent.MainModule::class])
internal interface FirebaseSessionsComponent {
  val firebaseSessions: FirebaseSessions

  val sessionDatastore: SessionDatastore
  val sessionFirelogPublisher: SessionFirelogPublisher
  val sessionGenerator: SessionGenerator
  val sessionsSettings: SessionsSettings

  @Component.Builder
  interface Builder {
    @BindsInstance fun appContext(appContext: Context): Builder

    @BindsInstance
    fun backgroundDispatcher(@Background backgroundDispatcher: CoroutineContext): Builder

    @BindsInstance fun blockingDispatcher(@Blocking blockingDispatcher: CoroutineContext): Builder

    @BindsInstance fun firebaseApp(firebaseApp: FirebaseApp): Builder

    @BindsInstance
    fun firebaseInstallationsApi(firebaseInstallationsApi: FirebaseInstallationsApi): Builder

    @BindsInstance
    fun transportFactoryProvider(transportFactoryProvider: Provider<TransportFactory>): Builder

    fun build(): FirebaseSessionsComponent
  }

  @Module
  interface MainModule {
    @Binds @Singleton fun eventGDTLoggerInterface(impl: EventGDTLogger): EventGDTLoggerInterface

    @Binds @Singleton fun sessionDatastore(impl: SessionDatastoreImpl): SessionDatastore

    @Binds
    @Singleton
    fun sessionFirelogPublisher(impl: SessionFirelogPublisherImpl): SessionFirelogPublisher

    @Binds
    @Singleton
    fun sessionLifecycleServiceBinder(
      impl: SessionLifecycleServiceBinderImpl
    ): SessionLifecycleServiceBinder

    companion object {
      @Provides @Singleton fun sessionGenerator() = SessionGenerator(timeProvider = WallClock)
    }
  }
}
