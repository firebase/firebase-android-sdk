// Copyright 2020 Google LLC
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
import android.os.Build.VERSION_CODES;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.google.android.datatransport.TransportFactory;
import com.google.firebase.FirebaseApp;
import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.components.Qualified;
import com.google.firebase.datatransport.TransportBackend;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Registrar for setting up Firebase ML Model Downloader's dependency injections in Firebase Android
 * Components.
 *
 * @hide
 */
public class FirebaseModelDownloaderRegistrar implements ComponentRegistrar {
  private static final String LIBRARY_NAME = "firebase-ml-modeldownloader";

  @Override
  @NonNull
  @RequiresApi(api = VERSION_CODES.KITKAT)
  public List<Component<?>> getComponents() {
    Qualified<Executor> bgExecutor = Qualified.qualified(Background.class, Executor.class);
    Qualified<Executor> blockingExecutor = Qualified.qualified(Blocking.class, Executor.class);
    Qualified<TransportFactory> transportFactory =
        Qualified.qualified(TransportBackend.class, TransportFactory.class);
    return Arrays.asList(
        Component.builder(FirebaseModelDownloader.class)
            .name(LIBRARY_NAME)
            .add(Dependency.required(Context.class))
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.requiredProvider(FirebaseInstallationsApi.class))
            .add(Dependency.requiredProvider(transportFactory))
            .add(Dependency.required(bgExecutor))
            .add(Dependency.required(blockingExecutor))
            .factory(
                c ->
                    DaggerModelDownloaderComponent.builder()
                        .setApplicationContext(c.get(Context.class))
                        .setFirebaseApp(c.get(FirebaseApp.class))
                        .setFis(c.getProvider(FirebaseInstallationsApi.class))
                        .setBlockingExecutor(c.get(blockingExecutor))
                        .setBgExecutor(c.get(bgExecutor))
                        .setTransportFactory(c.getProvider(transportFactory))
                        .build()
                        .getModelDownloader())
            .build(),
        LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME));
  }
}
