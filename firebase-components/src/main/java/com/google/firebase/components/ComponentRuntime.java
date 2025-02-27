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

import android.util.Log;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.annotation.VisibleForTesting;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.firebase.dynamicloading.ComponentLoader;
import com.google.firebase.events.Publisher;
import com.google.firebase.events.Subscriber;
import com.google.firebase.inject.Deferred;
import com.google.firebase.inject.Provider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main entry point to the component system.
 *
 * <p>Does {@link Component} dependency resolution and provides access to resolved {@link
 * Component}s via {@link #get(Class)} method.
 */
public class ComponentRuntime implements ComponentContainer, ComponentLoader {
  private static final Provider<Set<Object>> EMPTY_PROVIDER = Collections::emptySet;
  private final Map<Component<?>, Provider<?>> components = new HashMap<>();
  private final Map<Qualified<?>, Provider<?>> lazyInstanceMap = new HashMap<>();
  private final Map<Qualified<?>, LazySet<?>> lazySetMap = new HashMap<>();
  private final List<Provider<ComponentRegistrar>> unprocessedRegistrarProviders;
  private Set<String> processedCoroutineDispatcherInterfaces = new HashSet<>();
  private final EventBus eventBus;
  private final AtomicReference<Boolean> eagerComponentsInitializedWith = new AtomicReference<>();
  private final ComponentRegistrarProcessor componentRegistrarProcessor;

  /**
   * Creates an instance of {@link ComponentRuntime} for the provided {@link ComponentRegistrar}s
   * and any additional components.
   *
   * @deprecated Use {@link #builder(Executor)} instead.
   */
  @Deprecated
  public ComponentRuntime(
      Executor defaultEventExecutor,
      Iterable<ComponentRegistrar> registrars,
      Component<?>... additionalComponents) {
    this(
        defaultEventExecutor,
        toProviders(registrars),
        Arrays.asList(additionalComponents),
        ComponentRegistrarProcessor.NOOP);
  }

  /** A builder for creating {@link ComponentRuntime} instances. */
  public static Builder builder(Executor defaultEventExecutor) {
    return new Builder(defaultEventExecutor);
  }

  private ComponentRuntime(
      Executor defaultEventExecutor,
      Iterable<Provider<ComponentRegistrar>> registrars,
      Collection<Component<?>> additionalComponents,
      ComponentRegistrarProcessor componentRegistrarProcessor) {
    eventBus = new EventBus(defaultEventExecutor);
    this.componentRegistrarProcessor = componentRegistrarProcessor;

    List<Component<?>> componentsToAdd = new ArrayList<>();

    componentsToAdd.add(Component.of(eventBus, EventBus.class, Subscriber.class, Publisher.class));
    componentsToAdd.add(Component.of(this, ComponentLoader.class));
    for (Component<?> additionalComponent : additionalComponents) {
      if (additionalComponent != null) {
        componentsToAdd.add(additionalComponent);
      }
    }

    unprocessedRegistrarProviders = iterableToList(registrars);

    discoverComponents(componentsToAdd);
  }

  private void discoverComponents(List<Component<?>> componentsToAdd) {
    // During discovery many things need to happen, of which "Deferred resolution" and "Set binding
    // updates" are of most interest. During these phases we execute "client" code(i.e. the code of
    // SDKs participating in Components DI), this code can indirectly end up calling back into the
    // ComponentRuntime which, without proper care, can result in a deadlock. For this reason,
    // instead of executing such code in the synchronized block below, we store it in a list and
    // execute right after the synchronized section.
    List<Runnable> runAfterDiscovery = new ArrayList<>();
    synchronized (this) {
      Iterator<Provider<ComponentRegistrar>> iterator = unprocessedRegistrarProviders.iterator();
      while (iterator.hasNext()) {
        Provider<ComponentRegistrar> provider = iterator.next();
        try {
          ComponentRegistrar registrar = provider.get();
          if (registrar != null) {
            componentsToAdd.addAll(componentRegistrarProcessor.processRegistrar(registrar));
            iterator.remove();
          }
        } catch (InvalidRegistrarException ex) {
          iterator.remove();
          Log.w(ComponentDiscovery.TAG, "Invalid component registrar.", ex);
        }
      }

      // kotlinx.coroutines.CoroutineDispatcher interfaces could be provided by both new version of
      // firebase-common and old version of firebase-common-ktx. In this scenario take the first
      // interface which was provided.

      Iterator<Component<?>> it = componentsToAdd.iterator();
      while (it.hasNext()) {
        Component component = it.next();
        for (Object anInterface : component.getProvidedInterfaces().toArray()) {
          if (anInterface.toString().contains("kotlinx.coroutines.CoroutineDispatcher")) {
            if (processedCoroutineDispatcherInterfaces.contains(anInterface.toString())) {
              it.remove();
              break;
            } else {
              processedCoroutineDispatcherInterfaces.add(anInterface.toString());
            }
          }
        }
      }

      if (components.isEmpty()) {
        CycleDetector.detect(componentsToAdd);
      } else {
        ArrayList<Component<?>> allComponents = new ArrayList<>(this.components.keySet());
        allComponents.addAll(componentsToAdd);
        CycleDetector.detect(allComponents);
      }

      for (Component<?> component : componentsToAdd) {
        Lazy<?> lazy =
            new Lazy<>(
                () ->
                    component
                        .getFactory()
                        .create(new RestrictedComponentContainer(component, this)));

        components.put(component, lazy);
      }

      runAfterDiscovery.addAll(processInstanceComponents(componentsToAdd));
      runAfterDiscovery.addAll(processSetComponents());
      processDependencies();
    }
    for (Runnable runnable : runAfterDiscovery) {
      runnable.run();
    }
    maybeInitializeEagerComponents();
  }

  private void maybeInitializeEagerComponents() {
    Boolean isDefaultApp = eagerComponentsInitializedWith.get();
    if (isDefaultApp != null) {
      doInitializeEagerComponents(components, isDefaultApp);
    }
  }

  private static Iterable<Provider<ComponentRegistrar>> toProviders(
      Iterable<ComponentRegistrar> registrars) {
    List<Provider<ComponentRegistrar>> result = new ArrayList<>();
    for (ComponentRegistrar registrar : registrars) {
      result.add(() -> registrar);
    }
    return result;
  }

  private static <T> List<T> iterableToList(Iterable<T> iterable) {
    ArrayList<T> result = new ArrayList<>();
    for (T item : iterable) {
      result.add(item);
    }
    return result;
  }

  private List<Runnable> processInstanceComponents(List<Component<?>> componentsToProcess) {
    ArrayList<Runnable> runnables = new ArrayList<>();
    for (Component<?> component : componentsToProcess) {
      if (!component.isValue()) {
        continue;
      }

      Provider<?> provider = components.get(component);
      for (Qualified<?> anInterface : component.getProvidedInterfaces()) {
        if (!lazyInstanceMap.containsKey(anInterface)) {
          lazyInstanceMap.put(anInterface, provider);
        } else {
          Provider<?> existingProvider = lazyInstanceMap.get(anInterface);
          @SuppressWarnings("unchecked")
          OptionalProvider<Object> deferred = (OptionalProvider<Object>) existingProvider;
          @SuppressWarnings("unchecked")
          Provider<Object> castedProvider = (Provider<Object>) provider;
          runnables.add(() -> deferred.set(castedProvider));
        }
      }
    }
    return runnables;
  }

  /** Populates lazySetMap to make set components available for consumption via set dependencies. */
  private List<Runnable> processSetComponents() {
    ArrayList<Runnable> runnables = new ArrayList<>();
    Map<Qualified<?>, Set<Provider<?>>> setIndex = new HashMap<>();
    for (Map.Entry<Component<?>, Provider<?>> entry : components.entrySet()) {
      Component<?> component = entry.getKey();

      // only process set components.
      if (component.isValue()) {
        continue;
      }

      Provider<?> provider = entry.getValue();

      for (Qualified<?> anInterface : component.getProvidedInterfaces()) {
        if (!setIndex.containsKey(anInterface)) {
          setIndex.put(anInterface, new HashSet<>());
        }
        setIndex.get(anInterface).add(provider);
      }
    }

    for (Map.Entry<Qualified<?>, Set<Provider<?>>> entry : setIndex.entrySet()) {
      if (!lazySetMap.containsKey(entry.getKey())) {
        lazySetMap.put(entry.getKey(), LazySet.fromCollection(entry.getValue()));
      } else {
        @SuppressWarnings("unchecked")
        LazySet<Object> existingSet = (LazySet<Object>) lazySetMap.get(entry.getKey());
        for (Provider<?> provider : entry.getValue()) {
          @SuppressWarnings("unchecked")
          Provider<Object> castedProvider = (Provider<Object>) provider;
          runnables.add(() -> existingSet.add(castedProvider));
        }
      }
    }
    return runnables;
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized <T> Provider<T> getProvider(Qualified<T> anInterface) {
    Preconditions.checkNotNull(anInterface, "Null interface requested.");
    return (Provider<T>) lazyInstanceMap.get(anInterface);
  }

  @Override
  public <T> Deferred<T> getDeferred(Qualified<T> anInterface) {
    Provider<T> provider = getProvider(anInterface);
    if (provider == null) {
      return OptionalProvider.empty();
    }
    if (provider instanceof OptionalProvider) {
      return (OptionalProvider<T>) provider;
    }
    return OptionalProvider.of(provider);
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized <T> Provider<Set<T>> setOfProvider(Qualified<T> anInterface) {
    LazySet<?> provider = lazySetMap.get(anInterface);
    if (provider != null) {
      return (Provider<Set<T>>) (Provider<?>) provider;
    }
    return (Provider<Set<T>>) (Provider<?>) EMPTY_PROVIDER;
  }

  /**
   * Initializes all eager components.
   *
   * <p>Should be called at an appropriate time in the owner's lifecycle.
   *
   * <p>Note: the method is idempotent.
   *
   * <p>Warning: it's important that this method is not synchronized as it could cause a deadlock if
   * another SDK is initializing in another thread.
   */
  public void initializeEagerComponents(boolean isDefaultApp) {
    if (!eagerComponentsInitializedWith.compareAndSet(null, isDefaultApp)) {
      return;
    }

    // we copy the map under a lock to avoid a race condition.
    // Note that we cannot use a ConcurrentHashMap as it is broken on older Android versions, see:
    // https://issuetracker.google.com/issues/37042460
    HashMap<Component<?>, Provider<?>> componentsCopy;
    synchronized (this) {
      componentsCopy = new HashMap<>(components);
    }
    doInitializeEagerComponents(componentsCopy, isDefaultApp);
  }

  private void doInitializeEagerComponents(
      Map<Component<?>, Provider<?>> componentsToInitialize, boolean isDefaultApp) {
    for (Map.Entry<Component<?>, Provider<?>> entry : componentsToInitialize.entrySet()) {
      Component<?> component = entry.getKey();
      Provider<?> provider = entry.getValue();

      if (component.isAlwaysEager() || (component.isEagerInDefaultApp() && isDefaultApp)) {
        provider.get();
      }
    }

    eventBus.enablePublishingAndFlushPending();
  }

  @Override
  public void discoverComponents() {
    synchronized (this) {
      if (unprocessedRegistrarProviders.isEmpty()) {
        return;
      }
    }
    discoverComponents(new ArrayList<>());
  }

  @VisibleForTesting
  @RestrictTo(Scope.TESTS)
  public void initializeAllComponentsForTests() {
    for (Provider<?> component : components.values()) {
      component.get();
    }
  }

  private void processDependencies() {
    for (Component<?> component : components.keySet()) {
      for (Dependency dependency : component.getDependencies()) {
        if (dependency.isSet() && !lazySetMap.containsKey(dependency.getInterface())) {
          lazySetMap.put(dependency.getInterface(), LazySet.fromCollection(Collections.emptySet()));
        } else if (!lazyInstanceMap.containsKey(dependency.getInterface())) {
          if (dependency.isRequired()) {
            throw new MissingDependencyException(
                String.format(
                    "Unsatisfied dependency for component %s: %s",
                    component, dependency.getInterface()));
          } else if (!dependency.isSet()) {
            lazyInstanceMap.put(dependency.getInterface(), OptionalProvider.empty());
          }
        }
      }
    }
  }

  @VisibleForTesting
  Collection<Component<?>> getAllComponentsForTest() {
    return components.keySet();
  }

  public static final class Builder {
    private final Executor defaultExecutor;
    private final List<Provider<ComponentRegistrar>> lazyRegistrars = new ArrayList<>();
    private final List<Component<?>> additionalComponents = new ArrayList<>();
    private ComponentRegistrarProcessor componentRegistrarProcessor =
        ComponentRegistrarProcessor.NOOP;

    Builder(Executor defaultExecutor) {
      this.defaultExecutor = defaultExecutor;
    }

    @CanIgnoreReturnValue
    public Builder addLazyComponentRegistrars(Collection<Provider<ComponentRegistrar>> registrars) {
      this.lazyRegistrars.addAll(registrars);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addComponentRegistrar(ComponentRegistrar registrar) {
      this.lazyRegistrars.add(() -> registrar);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addComponent(Component<?> component) {
      this.additionalComponents.add(component);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setProcessor(ComponentRegistrarProcessor processor) {
      this.componentRegistrarProcessor = processor;
      return this;
    }

    public ComponentRuntime build() {
      return new ComponentRuntime(
          defaultExecutor, lazyRegistrars, additionalComponents, componentRegistrarProcessor);
    }
  }
}
