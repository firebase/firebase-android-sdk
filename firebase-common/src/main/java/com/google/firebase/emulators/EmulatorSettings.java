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

package com.google.firebase.emulators;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.FirebaseApp;
import com.google.firebase.components.Component;
import com.google.firebase.components.Dependency;
import java.util.HashMap;
import java.util.Map;

/** @hide */
public class EmulatorSettings {

  @NonNull
  public static Component<EmulatorSettings> component() {
    return Component.builder(EmulatorSettings.class)
        .add(Dependency.required(FirebaseApp.class))
        .factory(c -> new EmulatorSettings(c.get(FirebaseApp.class)))
        .build();
  }

  private final Map<String, EmulatedServiceSettings> settings = new HashMap<>();
  private final FirebaseApp app;

  EmulatorSettings(@NonNull FirebaseApp app) {
    this.app = app;
  }

  @Nullable
  public EmulatedServiceSettings get(@NonNull String name) {
    return settings.get(getKeyFor(name));
  }

  public void set(@NonNull String name, @Nullable EmulatedServiceSettings serviceSettings) {
    settings.put(getKeyFor(name), serviceSettings);
  }

  private String getKeyFor(@NonNull String name) {
    return this.app.getName() + '-' + name;
  }
}
