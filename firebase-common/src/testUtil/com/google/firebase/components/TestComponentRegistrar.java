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

import android.content.Context;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import java.util.Arrays;
import java.util.List;

class TestComponentRegistrar implements ComponentRegistrar {
  @Override
  public List<Component<?>> getComponents() {
    return Arrays.asList(
        Component.builder(TestComponentOne.class)
            .add(Dependency.required(Context.class))
            .factory(container -> new TestComponentOne(container.get(Context.class)))
            .build(),
        Component.builder(TestComponentTwo.class)
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.required(FirebaseOptions.class))
            .add(Dependency.required(TestComponentOne.class))
            .factory(
                container ->
                    new TestComponentTwo(
                        container.get(FirebaseApp.class),
                        container.get(FirebaseOptions.class),
                        container.get(TestComponentOne.class)))
            .build());
  }
}
