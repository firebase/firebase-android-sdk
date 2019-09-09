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

package com.google.firebase;

import android.content.Context;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.ContainerInfo;
import com.google.firebase.components.Dependency;
import com.google.firebase.events.Publisher;
import com.google.firebase.heartbeatinfo.DefaultHeartBeatInfo;
import com.google.firebase.platforminfo.DefaultUserAgentPublisher;
import com.google.firebase.platforminfo.KotlinDetector;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** @hide */
public class FirebaseCommonRegistrar implements ComponentRegistrar {
  private static final String FIREBASE_ANDROID = "fire-android";
  private static final String FIREBASE_COMMON = "fire-core";
  private static final String KOTLIN = "kotlin";

  @Override
  public List<Component<?>> getComponents() {
    List<Component<?>> components =
        new ArrayList<>(
            Arrays.asList(
                Component.builder(FirebaseApp.class)
                    .add(Dependency.required(Context.class))
                    .add(Dependency.required(ContainerInfo.class))
                    .add(Dependency.required(FirebaseOptions.class))
                    .publishes(DataCollectionDefaultChange.class)
                    .factory(
                        container ->
                            new FirebaseApp(
                                container.get(Context.class),
                                container.get(ContainerInfo.class).getName(),
                                container.get(FirebaseOptions.class),
                                container.get(Publisher.class)))
                    .build(),
                LibraryVersionComponent.create(FIREBASE_ANDROID, ""),
                LibraryVersionComponent.create(FIREBASE_COMMON, BuildConfig.VERSION_NAME),
                DefaultUserAgentPublisher.component(),
                DefaultHeartBeatInfo.component()));

    String kotlinVersion = KotlinDetector.detectVersion();
    if (kotlinVersion != null) {
      components.add(LibraryVersionComponent.create(KOTLIN, kotlinVersion));
    }
    return components;
  }
}
