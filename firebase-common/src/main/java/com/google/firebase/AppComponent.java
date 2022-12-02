package com.google.firebase;

import android.content.Context;
import androidx.core.os.UserManagerCompat;
import com.google.firebase.annotations.AppScope;
import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.components.ComponentDiscovery;
import com.google.firebase.components.ComponentDiscoveryService;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.ComponentRuntime;
import com.google.firebase.components.Qualified;
import com.google.firebase.concurrent.ExecutorsRegistrar;
import com.google.firebase.events.Publisher;
import com.google.firebase.heartbeatinfo.HeartBeatConsumer;
import com.google.firebase.inject.Provider;
import com.google.firebase.platforminfo.GlobalLibraryVersionRegistrar;
import com.google.firebase.platforminfo.LibraryVersionsModule;
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

/** @hide */
@Component(modules = {AppComponent.MainModule.class, LibraryVersionsModule.class})
@AppScope
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
    @AppScope
    static ComponentRuntime provideComponentRuntime(
        Context applicationContext,
        FirebaseOptions options,
        javax.inject.Provider<FirebaseApp> app) {
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
    @AppScope
    static Publisher providesPublisher(ComponentRuntime runtime) {
      return runtime.get(Publisher.class);
    }

    @Provides
    @AppScope
    static Set<HeartBeatConsumer> providesHeartBeatConsumers(ComponentRuntime runtime) {
      return runtime.setOf(HeartBeatConsumer.class);
    }

    @Provides
    @Background
    @AppScope
    static Executor providesBgExecutor(ComponentRuntime runtime) {
      return runtime.get(Qualified.qualified(Background.class, Executor.class));
    }

    @Provides
    static GlobalLibraryVersionRegistrar providesGlobalLibraryRegistrar() {
      return GlobalLibraryVersionRegistrar.getInstance();
    }
  }
}
