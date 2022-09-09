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

package com.google.firebase.tracing;

import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.ComponentRegistrarProcessor;
import java.util.ArrayList;
import java.util.List;

/** Wraps components to trace their initialization time. */
public class ComponentMonitor implements ComponentRegistrarProcessor {
  @Override
  public List<Component<?>> processRegistrar(ComponentRegistrar registrar) {
    List<Component<?>> components = new ArrayList<>();
    for (Component comp : registrar.getComponents()) {
      String name = comp.getName();
      if (name != null) {
        @SuppressWarnings("unchecked")
        Component<Object> old = (Component<Object>) comp;
        comp =
            old.withFactory(
                c -> {
                  try {
                    FirebaseTrace.pushTrace(name);
                    return old.getFactory().create(c);
                  } finally {
                    FirebaseTrace.popTrace();
                  }
                });
      }
      components.add(comp);
    }
    return components;
  }
}
