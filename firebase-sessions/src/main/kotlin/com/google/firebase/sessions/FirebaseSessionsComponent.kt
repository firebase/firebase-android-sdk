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
