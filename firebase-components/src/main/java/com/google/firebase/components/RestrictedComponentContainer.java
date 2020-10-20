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

import com.google.firebase.events.Event;
import com.google.firebase.events.Publisher;
import com.google.firebase.inject.Deferred;
import com.google.firebase.inject.Provider;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * An implementation of {@link ComponentContainer} that is backed by another delegate {@link
 * ComponentContainer} and restricts access to only declared {@link Dependency dependencies}.
 */
final class RestrictedComponentContainer extends AbstractComponentContainer {
  private final Set<Class<?>> allowedDirectInterfaces;
  private final Set<Class<?>> allowedProviderInterfaces;
  private final Set<Class<?>> allowedDeferredInterfaces;
  private final Set<Class<?>> allowedSetDirectInterfaces;
  private final Set<Class<?>> allowedSetProviderInterfaces;
  private final Set<Class<?>> allowedPublishedEvents;
  private final ComponentContainer delegateContainer;

  RestrictedComponentContainer(Component<?> component, ComponentContainer container) {
    Set<Class<?>> directInterfaces = new HashSet<>();
    Set<Class<?>> providerInterfaces = new HashSet<>();
    Set<Class<?>> deferredInterfaces = new HashSet<>();
    Set<Class<?>> setDirectInterfaces = new HashSet<>();
    Set<Class<?>> setProviderInterfaces = new HashSet<>();
    for (Dependency dependency : component.getDependencies()) {
      if (dependency.isDirectInjection()) {
        if (dependency.isSet()) {
          setDirectInterfaces.add(dependency.getInterface());
        } else {
          directInterfaces.add(dependency.getInterface());
        }
      } else if (dependency.isDeferred()) {
        deferredInterfaces.add(dependency.getInterface());
      } else {
        if (dependency.isSet()) {
          setProviderInterfaces.add(dependency.getInterface());
        } else {
          providerInterfaces.add(dependency.getInterface());
        }
      }
    }
    if (!component.getPublishedEvents().isEmpty()) {
      directInterfaces.add(Publisher.class);
    }
    allowedDirectInterfaces = Collections.unmodifiableSet(directInterfaces);
    allowedProviderInterfaces = Collections.unmodifiableSet(providerInterfaces);
    allowedDeferredInterfaces = Collections.unmodifiableSet(deferredInterfaces);
    allowedSetDirectInterfaces = Collections.unmodifiableSet(setDirectInterfaces);
    allowedSetProviderInterfaces = Collections.unmodifiableSet(setProviderInterfaces);
    allowedPublishedEvents = component.getPublishedEvents();
    delegateContainer = container;
  }

  /**
   * Returns an instance of the requested class if it is allowed.
   *
   * @throws DependencyException otherwise.
   */
  @Override
  public <T> T get(Class<T> anInterface) {
    if (!allowedDirectInterfaces.contains(anInterface)) {
      throw new DependencyException(
          String.format("Attempting to request an undeclared dependency %s.", anInterface));
    }

    // The container is guaranteed to contain a class keyed with Publisher.class. This is what we
    // want to restrict access to, if anyone wants to register their own Component that for whatever
    // reason implements Publisher, we don't want to interfere with it. Hence the equals check and
    // not Publisher.class.isAssignableFrom(anTnterface)
    T value = delegateContainer.get(anInterface);
    if (!anInterface.equals(Publisher.class)) {
      return value;
    }

    @SuppressWarnings("unchecked")
    T publisher = (T) new RestrictedPublisher(allowedPublishedEvents, (Publisher) value);
    return publisher;
  }

  /**
   * Returns an instance of the provider for the requested class if it is allowed.
   *
   * @throws DependencyException otherwise.
   */
  @Override
  public <T> Provider<T> getProvider(Class<T> anInterface) {
    if (!allowedProviderInterfaces.contains(anInterface)) {
      throw new DependencyException(
          String.format(
              "Attempting to request an undeclared dependency Provider<%s>.", anInterface));
    }
    return delegateContainer.getProvider(anInterface);
  }

  @Override
  public <T> Deferred<T> getDeferred(Class<T> anInterface) {
    if (!allowedDeferredInterfaces.contains(anInterface)) {
      throw new DependencyException(
          String.format(
              "Attempting to request an undeclared dependency Deferred<%s>.", anInterface));
    }
    return delegateContainer.getDeferred(anInterface);
  }

  /**
   * Returns an instance of the provider for the set of requested classes if it is allowed.
   *
   * @throws DependencyException otherwise.
   */
  @Override
  public <T> Provider<Set<T>> setOfProvider(Class<T> anInterface) {
    if (!allowedSetProviderInterfaces.contains(anInterface)) {
      throw new DependencyException(
          String.format(
              "Attempting to request an undeclared dependency Provider<Set<%s>>.", anInterface));
    }
    return delegateContainer.setOfProvider(anInterface);
  }

  /**
   * Returns a set of requested classes if it is allowed.
   *
   * @throws DependencyException otherwise.
   */
  @Override
  public <T> Set<T> setOf(Class<T> anInterface) {
    if (!allowedSetDirectInterfaces.contains(anInterface)) {
      throw new DependencyException(
          String.format("Attempting to request an undeclared dependency Set<%s>.", anInterface));
    }
    return delegateContainer.setOf(anInterface);
  }

  /**
   * An implementation of {@link Publisher} that is backed by another delegate {@link Publisher} and
   * restricts publishing to only a set of allowed event types.
   */
  private static class RestrictedPublisher implements Publisher {
    private final Set<Class<?>> allowedPublishedEvents;
    private final Publisher delegate;

    public RestrictedPublisher(Set<Class<?>> allowedPublishedEvents, Publisher delegate) {
      this.allowedPublishedEvents = allowedPublishedEvents;
      this.delegate = delegate;
    }

    @Override
    public void publish(Event<?> event) {
      if (!allowedPublishedEvents.contains(event.getType())) {
        throw new DependencyException(
            String.format("Attempting to publish an undeclared event %s.", event));
      }
      delegate.publish(event);
    }
  }
}
