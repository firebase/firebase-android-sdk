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

package com.google.firebase.appcheck.playintegrity;

import com.google.android.gms.common.annotation.KeepForSdk;
import com.google.firebase.FirebaseApp;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.appcheck.playintegrity.internal.PlayIntegrityAppCheckProvider;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.components.Qualified;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * {@link ComponentRegistrar} for setting up FirebaseAppCheck play integrity's dependency injections
 * in Firebase Android Components.
 *
 * @hide
 */
@KeepForSdk
public class FirebaseAppCheckPlayIntegrityRegistrar implements ComponentRegistrar {
  private static final String LIBRARY_NAME = "fire-app-check-play-integrity";

  @Override
  public List<Component<?>> getComponents() {
    Qualified<Executor> liteExecutor = Qualified.qualified(Lightweight.class, Executor.class);
    Qualified<Executor> blockingExecutor = Qualified.qualified(Blocking.class, Executor.class);

    return Arrays.asList(
        Component.builder(PlayIntegrityAppCheckProvider.class)
            .name(LIBRARY_NAME)
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.required(liteExecutor))
            .add(Dependency.required(blockingExecutor))
            .factory(
                (container) ->
                    new PlayIntegrityAppCheckProvider(
                        container.get(FirebaseApp.class),
                        container.get(liteExecutor),
                        container.get(blockingExecutor)))
            .build(),
        LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME));
  }
}
