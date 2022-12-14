// Copyright 2022 Google LLC
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

package com.google.firebase;

import android.content.Context;
import androidx.core.os.UserManagerCompat;
import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.components.ComponentDiscovery;
import com.google.firebase.components.ComponentDiscoveryService;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.ComponentRuntime;
import com.google.firebase.components.Qualified;
import com.google.firebase.concurrent.ExecutorsRegistrar;
import com.google.firebase.events.Publisher;
import com.google.firebase.heartbeatinfo.DefaultHeartBeatController;
import com.google.firebase.heartbeatinfo.HeartBeatConsumer;
import com.google.firebase.heartbeatinfo.HeartBeatController;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.inject.Provider;
import com.google.firebase.platforminfo.DefaultUserAgentPublisher;
import com.google.firebase.platforminfo.GlobalLibraryVersionRegistrar;
import com.google.firebase.platforminfo.LibraryVersionsModule;
import com.google.firebase.platforminfo.UserAgentPublisher;
import com.google.firebase.provider.FirebaseInitProvider;
import com.google.firebase.tracing.ComponentMonitor;
import com.google.firebase.tracing.FirebaseTrace;
import dagger.BindsInstance;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Main Dagger entry point, used to initialize {@link FirebaseApp}s.
 *
 * <p>Use by {@link FirebaseApp#initializeApp(Context, FirebaseOptions, String)}.
 *
 * @hide
 */
@Component(modules = {AppComponent.MainModule.class, LibraryVersionsModule.class})
@Singleton
interface AppComponent {

  FirebaseApp getApp();

  @Component.Builder
  interface Builder {
    @BindsInstance
    Builder setApplicationContext(Context ctx);

    @BindsInstance
    Builder setName(@Named("appName") String name);

    @BindsInstance
    Builder setOptions(FirebaseOptions options);

    AppComponent build();
  }

  @Module
  interface MainModule {
    @Provides
    @Named("persistenceKey")
    static String providesPersistenceKey(@Named("appName") String name, FirebaseOptions options) {
      return FirebaseApp.getPersistenceKey(name, options);
    }

    @Provides
    @Singleton
    static ComponentRuntime provideComponentRuntime(
        Context applicationContext,
        FirebaseOptions options,
        javax.inject.Provider<FirebaseApp> app,
        DefaultUserAgentPublisher userAgentPublisher,
        DefaultHeartBeatController heartBeatController) {
      FirebaseTrace.pushTrace("ComponentDiscovery");
      List<Provider<ComponentRegistrar>> registrars =
          ComponentDiscovery.forContext(applicationContext, ComponentDiscoveryService.class)
              .discoverLazy();
      FirebaseTrace.popTrace(); // ComponentDiscovery

      FirebaseTrace.pushTrace("Runtime");
      ComponentRuntime.Builder builder =
          ComponentRuntime.builder(com.google.firebase.concurrent.UiExecutor.INSTANCE)
              .addLazyComponentRegistrars(registrars)
              .addComponentRegistrar(new FirebaseCommonRegistrar())
              .addComponentRegistrar(new ExecutorsRegistrar())
              .addComponent(
                  com.google.firebase.components.Component.of(
                      userAgentPublisher, UserAgentPublisher.class))
              .addComponent(
                  com.google.firebase.components.Component.of(
                      heartBeatController,
                      DefaultHeartBeatController.class,
                      HeartBeatController.class,
                      HeartBeatInfo.class))
              .addComponent(
                  com.google.firebase.components.Component.of(applicationContext, Context.class))
              .addComponent(
                  com.google.firebase.components.Component.builder(FirebaseApp.class)
                      .factory(c -> app.get())
                      .build())
              .addComponent(
                  com.google.firebase.components.Component.of(options, FirebaseOptions.class))
              .setProcessor(new ComponentMonitor());

      StartupTime startupTime = FirebaseInitProvider.getStartupTime();
      // Don't provide StartupTime in direct boot mode or if Firebase was manually started
      if (UserManagerCompat.isUserUnlocked(applicationContext)
          && FirebaseInitProvider.isCurrentlyInitializing()) {
        builder.addComponent(
            com.google.firebase.components.Component.of(startupTime, StartupTime.class));
      }

      ComponentRuntime componentRuntime = builder.build();
      FirebaseTrace.popTrace(); // Runtime
      return componentRuntime;
    }

    @Provides
    @Singleton
    static Publisher providesPublisher(ComponentRuntime runtime) {
      return runtime.get(Publisher.class);
    }

    @Provides
    @Singleton
    static Set<HeartBeatConsumer> providesHeartBeatConsumers(ComponentRuntime runtime) {
      return runtime.setOf(HeartBeatConsumer.class);
    }

    @Provides
    @Background
    @Singleton
    static Executor providesBgExecutor(ComponentRuntime runtime) {
      return runtime.get(Qualified.qualified(Background.class, Executor.class));
    }

    @Provides
    static GlobalLibraryVersionRegistrar providesGlobalLibraryRegistrar() {
      return GlobalLibraryVersionRegistrar.getInstance();
    }
  }
}
