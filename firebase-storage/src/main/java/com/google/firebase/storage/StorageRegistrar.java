// Copyright 2018 Google LLC
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

package com.google.firebase.storage;

import androidx.annotation.Keep;
import androidx.annotation.RestrictTo;
import com.google.firebase.FirebaseApp;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.UiThread;
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.components.Qualified;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/** @hide */
@Keep
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class StorageRegistrar implements ComponentRegistrar {
  private static final String LIBRARY_NAME = "fire-gcs";
  Qualified<Executor> blockingExecutor = Qualified.qualified(Blocking.class, Executor.class);
  Qualified<Executor> uiExecutor = Qualified.qualified(UiThread.class, Executor.class);

  @Override
  public List<Component<?>> getComponents() {
    return Arrays.asList(
        Component.builder(FirebaseStorageComponent.class)
            .name(LIBRARY_NAME)
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.required(blockingExecutor))
            .add(Dependency.required(uiExecutor))
            .add(Dependency.optionalProvider(InternalAuthProvider.class))
            .add(Dependency.optionalProvider(InteropAppCheckTokenProvider.class))
            .factory(
                c ->
                    new FirebaseStorageComponent(
                        c.get(FirebaseApp.class),
                        c.getProvider(InternalAuthProvider.class),
                        c.getProvider(InteropAppCheckTokenProvider.class),
                        c.get(blockingExecutor),
                        c.get(uiExecutor)))
            .build(),
        LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME));
  }
}
