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
import com.google.firebase.components.ComponentFactory;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.ComponentRegistrarProcessor;
import java.util.ArrayList;
import java.util.List;

/** Wraps components to trace their initialization time. */
public class ComponentMonitor implements ComponentRegistrarProcessor {
  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  public List<Component<?>> processRegistrar(ComponentRegistrar registrar) {
    List<Component<?>> components = new ArrayList<>();
    for (Component comp : registrar.getComponents()) {
      final String name = comp.getName();
      if (name != null) {
        ComponentFactory<?> old = comp.getFactory();
        comp =
            comp.withFactory(
                c -> {
                  try {
                    FirebaseTrace.pushTrace(name);
                    return old.create(c);
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
