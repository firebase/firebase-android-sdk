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

public class TestEagerComponentRegistrar implements ComponentRegistrar {

  @Override
  public List<Component<?>> getComponents() {
    return Arrays.asList(
        Component.builder(InitTracker.class).factory(container -> new InitTracker()).build(),
        Component.builder(TestTrackedComponent.class)
            .add(Dependency.required(InitTracker.class))
            .alwaysEager()
            .factory(container -> new TestTrackedComponent(container.get(InitTracker.class)))
            .build());
  }
}
