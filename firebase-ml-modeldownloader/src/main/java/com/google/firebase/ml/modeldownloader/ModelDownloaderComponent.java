package com.google.firebase.ml.modeldownloader;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import com.google.android.datatransport.TransportFactory;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.installations.FirebaseInstallationsApi;
import dagger.BindsInstance;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import javax.inject.Named;
import javax.inject.Singleton;

/** @hide */
@Component(modules = ModelDownloaderComponent.MainModule.class)
@Singleton
interface ModelDownloaderComponent {
  FirebaseModelDownloader getModelDownloader();

  @Component.Builder
  interface Builder {
    @BindsInstance
    Builder setApplicationContext(Context context);

    @BindsInstance
    Builder setFirebaseApp(FirebaseApp app);

    @BindsInstance
    Builder setFis(FirebaseInstallationsApi fis);

    @BindsInstance
    Builder setTransportFactory(TransportFactory transportFactory);

    ModelDownloaderComponent build();
  }

  @Module
  interface MainModule {
    @Provides
    @Named("persistenceKey")
    static String persistenceKey(FirebaseApp app) {
      return app.getPersistenceKey();
    }

    @Provides
    @Named("appPackageName")
    static String appPackageName(Context applicationContext) {
      return applicationContext.getPackageName();
    }

    @Provides
    static FirebaseOptions firebaseOptions(FirebaseApp app) {
      return app.getOptions();
    }

    @Provides
    @Singleton
    @Named("appVersionCode")
    static String appVersionCode(
        Context applicationContext, @Named("appPackageName") String appPackageName) {
      try {
        return String.valueOf(
            applicationContext.getPackageManager().getPackageInfo(appPackageName, 0).versionCode);
      } catch (PackageManager.NameNotFoundException e) {
        Log.e("ModelDownloader", "Exception thrown when trying to get app version " + e);
      }
      return "";
    }
  }
}
