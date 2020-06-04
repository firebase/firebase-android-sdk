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

package com.google.firebase.crashlytics.ndk;

import android.content.Context;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentContainer;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.Arrays;
import java.util.List;

public class CrashlyticsNdkRegistrar implements ComponentRegistrar {
  @Override
  public List<Component<?>> getComponents() {
    return Arrays.asList(
        Component.builder(CrashlyticsNativeComponent.class)
            .add(Dependency.required(Context.class))
            .factory(this::buildCrashlyticsNdk)
            .eagerInDefaultApp()
            .build(),
        LibraryVersionComponent.create("fire-cls-ndk", BuildConfig.VERSION_NAME));
  }

  private CrashlyticsNativeComponent buildCrashlyticsNdk(ComponentContainer container) {
    Context context = container.get(Context.class);
    return FirebaseCrashlyticsNdk.create(context);
  }
}
