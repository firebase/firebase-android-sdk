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
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.google.android.datatransport.TransportFactory
import com.google.firebase.FirebaseApp
import com.google.firebase.annotations.concurrent.Background
import com.google.firebase.annotations.concurrent.Blocking
import com.google.firebase.inject.Provider
import com.google.firebase.installations.FirebaseInstallationsApi
import com.google.firebase.sessions.FirebaseSessions.Companion.TAG
import com.google.firebase.sessions.settings.CrashlyticsSettingsFetcher
import com.google.firebase.sessions.settings.LocalOverrideSettings
import com.google.firebase.sessions.settings.RemoteSettings
import com.google.firebase.sessions.settings.RemoteSettingsFetcher
import com.google.firebase.sessions.settings.SessionConfigs
import com.google.firebase.sessions.settings.SessionConfigsSerializer
import com.google.firebase.sessions.settings.SessionsSettings
import com.google.firebase.sessions.settings.SettingsCache
import com.google.firebase.sessions.settings.SettingsCacheImpl
import com.google.firebase.sessions.settings.SettingsProvider
import dagger.Binds
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import dagger.Provides
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope

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

    @Binds
    @Singleton
    fun sessionFirelogPublisher(impl: SessionFirelogPublisherImpl): SessionFirelogPublisher

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

    @Binds @Singleton fun settingsCache(impl: SettingsCacheImpl): SettingsCache

    @Binds
    @Singleton
    fun sharedSessionRepository(impl: SharedSessionRepositoryImpl): SharedSessionRepository

    @Binds @Singleton fun processDataManager(impl: ProcessDataManagerImpl): ProcessDataManager

    companion object {
      @Provides @Singleton fun timeProvider(): TimeProvider = TimeProviderImpl

      @Provides @Singleton fun uuidGenerator(): UuidGenerator = UuidGeneratorImpl

      @Provides
      @Singleton
      fun applicationInfo(firebaseApp: FirebaseApp): ApplicationInfo =
        SessionEvents.getApplicationInfo(firebaseApp)

      @Provides
      @Singleton
      fun sessionConfigsDataStore(
        appContext: Context,
        @Blocking blockingDispatcher: CoroutineContext,
      ): DataStore<SessionConfigs> =
        createDataStore(
          serializer = SessionConfigsSerializer,
          corruptionHandler =
            ReplaceFileCorruptionHandler { ex ->
              Log.w(TAG, "CorruptionException in session configs DataStore", ex)
              SessionConfigsSerializer.defaultValue
            },
          scope = CoroutineScope(blockingDispatcher),
          produceFile = { appContext.dataStoreFile("aqs/sessionConfigsDataStore.data") },
        )

      @Provides
      @Singleton
      fun sessionDataStore(
        appContext: Context,
        @Blocking blockingDispatcher: CoroutineContext,
        sessionDataSerializer: SessionDataSerializer,
      ): DataStore<SessionData> =
        createDataStore(
          serializer = sessionDataSerializer,
          corruptionHandler =
            ReplaceFileCorruptionHandler { ex ->
              Log.w(TAG, "CorruptionException in session data DataStore", ex)
              sessionDataSerializer.defaultValue
            },
          scope = CoroutineScope(blockingDispatcher),
          produceFile = { appContext.dataStoreFile("aqs/sessionDataStore.data") },
        )

      private fun <T> createDataStore(
        serializer: Serializer<T>,
        corruptionHandler: ReplaceFileCorruptionHandler<T>,
        migrations: List<DataMigration<T>> = listOf(),
        scope: CoroutineScope,
        produceFile: () -> File,
      ): DataStore<T> =
        if (loadDataStoreSharedCounter()) {
          MultiProcessDataStoreFactory.create(
            serializer,
            corruptionHandler,
            migrations,
            scope,
            produceFile,
          )
        } else {
          DataStoreFactory.create(serializer, corruptionHandler, migrations, scope, produceFile)
        }

      /** This native library in unavailable in some conditions, for example, Robolectric tests */
      // TODO(mrober): Remove this when b/392626815 is resolved
      private fun loadDataStoreSharedCounter(): Boolean =
        try {
          System.loadLibrary("datastore_shared_counter")
          true
        } catch (_: UnsatisfiedLinkError) {
          false
        } catch (_: SecurityException) {
          false
        }
    }
  }
}
