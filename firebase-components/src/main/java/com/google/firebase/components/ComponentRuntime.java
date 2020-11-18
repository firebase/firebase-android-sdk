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

package com.google.firebase.components;

import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.events.Publisher;
import com.google.firebase.events.Subscriber;
import com.google.firebase.inject.Provider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Main entry point to the component system.
 *
 * <p>Does {@link Component} dependency resolution and provides access to resolved {@link
 * Component}s via {@link #get(Class)} method.
 */
public class ComponentRuntime extends AbstractComponentContainer {
  private static final Provider<Set<Object>> EMPTY_PROVIDER = Collections::emptySet;
  private final Map<Component<?>, Lazy<?>> components = new HashMap<>();
  private final Map<Class<?>, Lazy<?>> lazyInstanceMap = new HashMap<>();
  private final Map<Class<?>, Lazy<Set<?>>> lazySetMap = new HashMap<>();
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
    for (Component<?> additionalComponent : additionalComponents) {
      if (additionalComponent != null) {
        componentsToAdd.add(additionalComponent);
      }
    }

    CycleDetector.detect(componentsToAdd);

    for (Component<?> component : componentsToAdd) {
      Lazy<?> lazy =
          new Lazy<>(
              () ->
                  component.getFactory().create(new RestrictedComponentContainer(component, this)));

      components.put(component, lazy);
    }
    processInstanceComponents();
    processSetComponents();
  }

  private void processInstanceComponents() {
    for (Map.Entry<Component<?>, Lazy<?>> entry : components.entrySet()) {
      Component<?> component = entry.getKey();
      if (!component.isValue()) {
        continue;
      }

      Lazy<?> lazy = entry.getValue();
      for (Class<?> anInterface : component.getProvidedInterfaces()) {
        lazyInstanceMap.put(anInterface, lazy);
      }
    }
    validateDependencies();
  }

  /** Populates lazySetMap to make set components available for consumption via set dependencies. */
  private void processSetComponents() {
    Map<Class<?>, Set<Lazy<?>>> setIndex = new HashMap<>();
    for (Map.Entry<Component<?>, Lazy<?>> entry : components.entrySet()) {
      Component<?> component = entry.getKey();

      // only process set components.
      if (component.isValue()) {
        continue;
      }

      Lazy<?> lazy = entry.getValue();

      for (Class<?> anInterface : component.getProvidedInterfaces()) {
        if (!setIndex.containsKey(anInterface)) {
          setIndex.put(anInterface, new HashSet<>());
        }
        setIndex.get(anInterface).add(lazy);
      }
    }

    for (Map.Entry<Class<?>, Set<Lazy<?>>> entry : setIndex.entrySet()) {
      Set<Lazy<?>> lazies = entry.getValue();
      lazySetMap.put(
          entry.getKey(),
          new Lazy<>(
              () -> {
                Set<Object> set = new HashSet<>();
                for (Lazy<?> lazy : lazies) {
                  set.add(lazy.get());
                }
                return Collections.unmodifiableSet(set);
              }));
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> Provider<T> getProvider(Class<T> anInterface) {
    Preconditions.checkNotNull(anInterface, "Null interface requested.");
    return (Provider<T>) lazyInstanceMap.get(anInterface);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> Provider<Set<T>> setOfProvider(Class<T> anInterface) {
    Lazy<Set<?>> lazy = lazySetMap.get(anInterface);
    if (lazy != null) {
      return (Provider<Set<T>>) (Provider<?>) lazy;
    }
    return (Provider<Set<T>>) (Provider<?>) EMPTY_PROVIDER;
  }

  /**
   * Initializes all eager components.
   *
   * <p>Should be called at an appropriate time in the owner's lifecycle.
   *
   * <p>Note: the method is idempotent.
   */
  public void initializeEagerComponents(boolean isDefaultApp) {
    for (Map.Entry<Component<?>, Lazy<?>> entry : components.entrySet()) {
      Component<?> component = entry.getKey();
      Lazy<?> lazy = entry.getValue();

      if (component.isAlwaysEager() || (component.isEagerInDefaultApp() && isDefaultApp)) {
        lazy.get();
      }
    }

    eventBus.enablePublishingAndFlushPending();
  }

  @VisibleForTesting
  @RestrictTo(Scope.TESTS)
  public void initializeAllComponentsForTests() {
    for (Lazy<?> component : components.values()) {
      component.get();
    }
  }

  private void validateDependencies() {
    for (Component<?> component : components.keySet()) {
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
