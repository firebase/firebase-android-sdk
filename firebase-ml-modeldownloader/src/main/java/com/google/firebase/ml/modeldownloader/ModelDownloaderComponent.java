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

package com.google.firebase.ml.modeldownloader;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import com.google.android.datatransport.TransportFactory;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import dagger.BindsInstance;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import java.util.concurrent.Executor;
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
    Builder setFis(Provider<FirebaseInstallationsApi> fis);

    @BindsInstance
    Builder setTransportFactory(Provider<TransportFactory> transportFactory);

    @BindsInstance
    Builder setBlockingExecutor(@Blocking Executor blockingExecutor);

    @BindsInstance
    Builder setBgExecutor(@Background Executor bgExecutor);

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
