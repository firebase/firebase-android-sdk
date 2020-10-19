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

package com.google.firebase.dynamicloading;

import android.content.Context;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import java.util.Collections;
import java.util.List;

public class DynamicLoadingRegistrar implements ComponentRegistrar {

  @Override
  public List<Component<?>> getComponents() {
    return Collections.singletonList(
        Component.builder(DynamicLoadingSupport.class)
            .add(Dependency.required(Context.class))
            .add(Dependency.required(ComponentLoader.class))
            .alwaysEager()
            .factory(
                container ->
                    new DynamicLoadingSupport(
                        container.get(Context.class), container.get(ComponentLoader.class)))
            .build());
  }
}
