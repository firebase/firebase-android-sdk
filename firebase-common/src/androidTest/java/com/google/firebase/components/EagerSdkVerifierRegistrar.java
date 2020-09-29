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

package com.google.firebase.components;

import java.util.Arrays;
import java.util.List;

public class EagerSdkVerifierRegistrar implements ComponentRegistrar {
  @Override
  public List<Component<?>> getComponents() {
    return Arrays.asList(
        Component.builder(InitializationTracker.class)
            .factory(container -> new InitializationTracker())
            .build(),
        Component.builder(EagerComponent.class)
            .add(Dependency.required(InitializationTracker.class))
            .factory(container -> new EagerComponent(container.get(InitializationTracker.class)))
            .alwaysEager()
            .build(),
        Component.builder(EagerInDefaultAppComponent.class)
            .add(Dependency.required(InitializationTracker.class))
            .factory(
                container ->
                    new EagerInDefaultAppComponent(container.get(InitializationTracker.class)))
            .eagerInDefaultApp()
            .build(),
        Component.builder(LazyComponent.class)
            .add(Dependency.required(InitializationTracker.class))
            .factory(container -> new LazyComponent(container.get(InitializationTracker.class)))
            .build(),
        Component.builder(EagerSdkVerifier.class)
            .add(Dependency.required(InitializationTracker.class))
            .factory(container -> new EagerSdkVerifier(container.get(InitializationTracker.class)))
            .build());
  }
}
