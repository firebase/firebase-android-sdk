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
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.android.datatransport.TransportFactory
import com.google.firebase.FirebaseApp
import com.google.firebase.annotations.concurrent.Background
import com.google.firebase.annotations.concurrent.Blocking
import com.google.firebase.inject.Provider
import com.google.firebase.installations.FirebaseInstallationsApi
import com.google.firebase.sessions.ProcessDetailsProvider.getProcessName
import com.google.firebase.sessions.settings.CrashlyticsSettingsFetcher
import com.google.firebase.sessions.settings.LocalOverrideSettings
import com.google.firebase.sessions.settings.RemoteSettings
import com.google.firebase.sessions.settings.RemoteSettingsFetcher
import com.google.firebase.sessions.settings.SessionsSettings
import com.google.firebase.sessions.settings.SettingsProvider
import dagger.Binds
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import dagger.Provides
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Qualifier internal annotation class SessionConfigsDataStore

@Qualifier internal annotation class SessionDetailsDataStore

@Qualifier internal annotation class LocalOverrideSettingsProvider

@Qualifier internal annotation class RemoteSettingsProvider

/**
 * Dagger component to provide [FirebaseSessions] and its dependencies.
 *
 * This gets configured and built in [FirebaseSessionsRegistrar.getComponents].
 */
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

    @Binds
    @Singleton
    fun crashlyticsSettingsFetcher(impl: RemoteSettingsFetcher): CrashlyticsSettingsFetcher

    @Binds
    @Singleton
    @LocalOverrideSettingsProvider
    fun localOverrideSettings(impl: LocalOverrideSettings): SettingsProvider

    @Binds
    @Singleton
    @RemoteSettingsProvider
    fun remoteSettings(impl: RemoteSettings): SettingsProvider

    companion object {
      private const val TAG = "FirebaseSessions"

      @Provides @Singleton fun timeProvider(): TimeProvider = TimeProviderImpl

      @Provides @Singleton fun uuidGenerator(): UuidGenerator = UuidGeneratorImpl

      @Provides
      @Singleton
      fun applicationInfo(firebaseApp: FirebaseApp): ApplicationInfo =
        SessionEvents.getApplicationInfo(firebaseApp)

      @Provides
      @Singleton
      @SessionConfigsDataStore
      fun sessionConfigsDataStore(appContext: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
          corruptionHandler =
            ReplaceFileCorruptionHandler { ex ->
              Log.w(TAG, "CorruptionException in settings DataStore in ${getProcessName()}.", ex)
              emptyPreferences()
            }
        ) {
          appContext.preferencesDataStoreFile(SessionDataStoreConfigs.SETTINGS_CONFIG_NAME)
        }

      @Provides
      @Singleton
      @SessionDetailsDataStore
      fun sessionDetailsDataStore(appContext: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
          corruptionHandler =
            ReplaceFileCorruptionHandler { ex ->
              Log.w(TAG, "CorruptionException in sessions DataStore in ${getProcessName()}.", ex)
              emptyPreferences()
            }
        ) {
          appContext.preferencesDataStoreFile(SessionDataStoreConfigs.SESSIONS_CONFIG_NAME)
        }
    }
  }
}
