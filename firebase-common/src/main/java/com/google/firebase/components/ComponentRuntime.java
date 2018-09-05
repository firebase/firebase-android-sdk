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

import com.google.android.gms.common.internal.Preconditions;
import com.google.firebase.events.Publisher;
import com.google.firebase.events.Subscriber;
import com.google.firebase.inject.Provider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Main entry point to the component system.
 *
 * <p>Does {@link Component} dependency resolution and provides access to resolved {@link
 * Component}s via {@link #get(Class)} method.
 */
public class ComponentRuntime extends AbstractComponentContainer {
  private final List<Component<?>> components;
  private final Map<Class<?>, Lazy<?>> lazyInstanceMap = new HashMap<>();
  private final EventBus eventBus;

  /**
   * Creates an instance of {@link ComponentRuntime} for the provided {@link ComponentRegistrar}s
   * and any additional components.
   */
  public ComponentRuntime(
      Executor defaultEventExecutor,
      Iterable<ComponentRegistrar> registrars,
      Component<?>... additionalComponents) {
    eventBus = new EventBus(defaultEventExecutor);
    List<Component<?>> componentsToAdd = new ArrayList<>();
    componentsToAdd.add(Component.of(eventBus, EventBus.class, Subscriber.class, Publisher.class));

    for (ComponentRegistrar registrar : registrars) {
      componentsToAdd.addAll(registrar.getComponents());
    }
    Collections.addAll(componentsToAdd, additionalComponents);

    components = Collections.unmodifiableList(ComponentSorter.sorted(componentsToAdd));

    for (Component<?> component : components) {
      register(component);
    }
    validateDependencies();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> Provider<T> getProvider(Class<T> anInterface) {
    Preconditions.checkNotNull(anInterface, "Null interface requested.");
    return (Provider<T>) lazyInstanceMap.get(anInterface);
  }

  /**
   * Initializes all eager components.
   *
   * <p>Should be called at an appropriate time in the owner's lifecycle.
   *
   * <p>Note: the method is idempotent.
   */
  public void initializeEagerComponents(boolean isDefaultApp) {
    for (Component<?> component : components) {
      if (component.isAlwaysEager() || (component.isEagerInDefaultApp() && isDefaultApp)) {
        // at least one interface is guarenteed to be provided by a component.
        get(component.getProvidedInterfaces().iterator().next());
      }
    }

    eventBus.enablePublishingAndFlushPending();
  }

  private <T> void register(Component<T> component) {
    Lazy<T> lazy =
        new Lazy<>(component.getFactory(), new RestrictedComponentContainer(component, this));

    for (Class<? super T> anInterface : component.getProvidedInterfaces()) {
      lazyInstanceMap.put(anInterface, lazy);
    }
  }

  private void validateDependencies() {
    for (Component<?> component : components) {
      for (Dependency dependency : component.getDependencies()) {
        if (dependency.isRequired() && !lazyInstanceMap.containsKey(dependency.getInterface())) {
          throw new MissingDependencyException(
              String.format(
                  "Unsatisfied dependency for component %s: %s",
                  component, dependency.getInterface()));
        }
      }
    }
  }
}
