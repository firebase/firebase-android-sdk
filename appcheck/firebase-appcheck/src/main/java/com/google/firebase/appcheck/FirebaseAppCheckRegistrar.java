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

package com.google.firebase.appcheck;

import com.google.android.gms.common.annotation.KeepForSdk;
import com.google.firebase.FirebaseApp;
import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.annotations.concurrent.UiThread;
import com.google.firebase.appcheck.internal.DefaultFirebaseAppCheck;
import com.google.firebase.appcheck.interop.InternalAppCheckTokenProvider;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.components.Qualified;
import com.google.firebase.heartbeatinfo.HeartBeatConsumerComponent;
import com.google.firebase.heartbeatinfo.HeartBeatController;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

/**
 * {@link ComponentRegistrar} for setting up FirebaseAppCheck's dependency injections in Firebase
 * Android Components.
 *
 * @hide
 */
@KeepForSdk
public class FirebaseAppCheckRegistrar implements ComponentRegistrar {
  private static final String LIBRARY_NAME = "fire-app-check";

  @Override
  public List<Component<?>> getComponents() {
    Qualified<Executor> uiExecutor = Qualified.qualified(UiThread.class, Executor.class);
    Qualified<Executor> liteExecutor = Qualified.qualified(Lightweight.class, Executor.class);
    Qualified<Executor> backgroundExecutor = Qualified.qualified(Background.class, Executor.class);
    Qualified<ScheduledExecutorService> blockingScheduledExecutorService =
        Qualified.qualified(Blocking.class, ScheduledExecutorService.class);

    return Arrays.asList(
        Component.builder(FirebaseAppCheck.class, (InternalAppCheckTokenProvider.class))
            .name(LIBRARY_NAME)
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.required(uiExecutor))
            .add(Dependency.required(liteExecutor))
            .add(Dependency.required(backgroundExecutor))
            .add(Dependency.required(blockingScheduledExecutorService))
            .add(Dependency.optionalProvider(HeartBeatController.class))
            .factory(
                (container) ->
                    new DefaultFirebaseAppCheck(
                        container.get(FirebaseApp.class),
                        container.getProvider(HeartBeatController.class),
                        container.get(uiExecutor),
                        container.get(liteExecutor),
                        container.get(backgroundExecutor),
                        container.get(blockingScheduledExecutorService)))
            .alwaysEager()
            .build(),
        HeartBeatConsumerComponent.create(),
        LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME));
  }
}
