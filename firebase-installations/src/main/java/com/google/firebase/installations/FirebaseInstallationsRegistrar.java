// Copyright 2019 Google LLC
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

package com.google.firebase.installations;

import androidx.annotation.Keep;
import com.google.firebase.FirebaseApp;
import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.components.Qualified;
import com.google.firebase.concurrent.FirebaseExecutors;
import com.google.firebase.heartbeatinfo.HeartBeatConsumerComponent;
import com.google.firebase.heartbeatinfo.HeartBeatController;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/** @hide */
@Keep
public class FirebaseInstallationsRegistrar implements ComponentRegistrar {
  private static final String LIBRARY_NAME = "fire-installations";

  @Override
  public List<Component<?>> getComponents() {
    return Arrays.asList(
        Component.builder(FirebaseInstallationsApi.class)
            .name(LIBRARY_NAME)
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.optionalProvider(HeartBeatController.class))
            .add(Dependency.required(Qualified.qualified(Background.class, ExecutorService.class)))
            .add(Dependency.required(Qualified.qualified(Blocking.class, Executor.class)))
            .factory(
                c ->
                    new FirebaseInstallations(
                        c.get(FirebaseApp.class),
                        c.getProvider(HeartBeatController.class),
                        c.get(Qualified.qualified(Background.class, ExecutorService.class)),
                        FirebaseExecutors.newSequentialExecutor(
                            c.get(Qualified.qualified(Blocking.class, Executor.class)))))
            .build(),
        HeartBeatConsumerComponent.create(),
        LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME));
  }
}
