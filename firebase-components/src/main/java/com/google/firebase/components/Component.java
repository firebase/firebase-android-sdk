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

import androidx.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Class that represents Firebase Components.
 *
 * <p>It essentially is a descriptor for an implementation of interface {@code T}, including its
 * dependencies, instantiation requirements and a factory that is able to instantiate instances of
 * {@code T}.
 *
 * @param <T> Interface that a component provides.
 */
public final class Component<T> {

  /** Specifies instantiation behavior of a {@link Component}. */
  @IntDef({Instantiation.LAZY, Instantiation.ALWAYS_EAGER, Instantiation.EAGER_IN_DEFAULT_APP})
  @Retention(RetentionPolicy.SOURCE)
  private @interface Instantiation {
    /** Component is not instantiated until requested by developer or a dependent component. */
    int LAZY = 0;

    /**
     * Component is unconditionally instantiated upon startup of the {@link ComponentRuntime}.
     *
     * <p>Namely when {@link ComponentRuntime#initializeEagerComponents(boolean)} is called.
     */
    int ALWAYS_EAGER = 1;

    /**
     * Component is instantiated upon startup of the {@link ComponentRuntime} if the runtime is
     * initialized for the default app.
     */
    int EAGER_IN_DEFAULT_APP = 2;
  }

  /** Specifies the type of a {@link Component}. */
  @IntDef({ComponentType.VALUE, ComponentType.SET})
  @Retention(RetentionPolicy.SOURCE)
  private @interface ComponentType {
    /**
     * Value components provide scalar values to the {@link ComponentRuntime}.
     *
     * <p>Such components can be requested by dependents via {@link ComponentContainer#get(Class)}
     * or {@link ComponentContainer#getProvider(Class)}. e.g. {@code FirebaseInstanceId}.
     */
    int VALUE = 0;

    /**
     * Set components collectively contribute values of type {@code T} to a {@link Set
     * Set&lt;T&gt;}.
     *
     * <p>Such components can be requested by dependents via {@link ComponentContainer#setOf(Class)}
     * or {@link ComponentContainer#setOfProvider(Class)}.
     */
    int SET = 1;
  }

  private final Set<Class<? super T>> providedInterfaces;
  private final Set<Dependency> dependencies;
  private final @Instantiation int instantiation;
  private final @ComponentType int type;
  private final ComponentFactory<T> factory;
  private final Set<Class<?>> publishedEvents;

  private Component(
      Set<Class<? super T>> providedInterfaces,
      Set<Dependency> dependencies,
      @Instantiation int instantiation,
      @ComponentType int type,
      ComponentFactory<T> factory,
      Set<Class<?>> publishedEvents) {
    this.providedInterfaces = Collections.unmodifiableSet(providedInterfaces);
    this.dependencies = Collections.unmodifiableSet(dependencies);
    this.instantiation = instantiation;
    this.type = type;
    this.factory = factory;
    this.publishedEvents = Collections.unmodifiableSet(publishedEvents);
  }

  /**
   * Returns all interfaces this component provides.
   *
   * <p>Note: T conforms to all of these interfaces.
   */
  public Set<Class<? super T>> getProvidedInterfaces() {
    return providedInterfaces;
  }

  /** Returns a list of this component's dependencies. */
  public Set<Dependency> getDependencies() {
    return dependencies;
  }

  /** Returns a component factory. */
  public ComponentFactory<T> getFactory() {
    return factory;
  }

  public Set<Class<?>> getPublishedEvents() {
    return publishedEvents;
  }

  /**
   * Returns whether a component is lazy.
   *
   * <p>Meaning that it will be instantiated only when it is requested.
   */
  public boolean isLazy() {
    return instantiation == Instantiation.LAZY;
  }

  /**
   * Returns whether a component is always eager.
   *
   * <p>Meaning that it will be instantiated upon application startup.
   */
  public boolean isAlwaysEager() {
    return instantiation == Instantiation.ALWAYS_EAGER;
  }

  /**
   * Returns whether a component is eager in default app.
   *
   * <p>Meaning that it will be instantiated upon startup of the default application.
   */
  public boolean isEagerInDefaultApp() {
    return instantiation == Instantiation.EAGER_IN_DEFAULT_APP;
  }

  /** Returns whether a component is a Value Component or a Set Component. */
  public boolean isValue() {
    return type == ComponentType.VALUE;
  }

  @Override
  public String toString() {
    StringBuilder sb =
        new StringBuilder("Component<")
            .append(Arrays.toString(providedInterfaces.toArray()))
            .append(">{")
            .append(instantiation)
            .append(", type=")
            .append(type)
            .append(", deps=")
            .append(Arrays.toString(dependencies.toArray()))
            .append("}");
    return sb.toString();
  }

  /** Returns a Component<T> builder. */
  public static <T> Component.Builder<T> builder(Class<T> anInterface) {
    return new Builder<>(anInterface);
  }

  /** Returns a Component<T> builder. */
  @SafeVarargs
  public static <T> Component.Builder<T> builder(
      Class<T> anInterface, Class<? super T>... additionalInterfaces) {
    return new Builder<>(anInterface, additionalInterfaces);
  }

  /**
   * Wraps a value in a {@link Component} with no dependencies.
   *
   * @deprecated Use {@link #of(Object, Class, Class[])} instead.
   */
  @Deprecated
  public static <T> Component<T> of(Class<T> anInterface, T value) {
    return builder(anInterface).factory((args) -> value).build();
  }

  /** Wraps a value in a {@link Component} with no dependencies. */
  @SafeVarargs
  public static <T> Component<T> of(
      T value, Class<T> anInterface, Class<? super T>... additionalInterfaces) {
    return builder(anInterface, additionalInterfaces).factory((args) -> value).build();
  }

  /**
   * Provides a builder for a {@link Set}-multibinding {@link Component}.
   *
   * <p>Such components can be requested by dependents via {@link ComponentContainer#setOf(Class)} *
   * or {@link ComponentContainer#setOfProvider(Class)}.
   */
  public static <T> Component.Builder<T> intoSetBuilder(Class<T> anInterface) {
    return builder(anInterface).intoSet();
  }

  /**
   * Wraps a value in a {@link Set}-multibinding {@link Component} with no dependencies. *
   *
   * <p>Such components can be requested by dependents via {@link ComponentContainer#setOf(Class)} *
   * or {@link ComponentContainer#setOfProvider(Class)}.
   */
  public static <T> Component<T> intoSet(T value, Class<T> anInterface) {
    return intoSetBuilder(anInterface).factory(c -> value).build();
  }

  /** FirebaseComponent builder. */
  public static class Builder<T> {
    private final Set<Class<? super T>> providedInterfaces = new HashSet<>();
    private final Set<Dependency> dependencies = new HashSet<>();
    private @Instantiation int instantiation = Instantiation.LAZY;
    private @ComponentType int type = ComponentType.VALUE;
    private ComponentFactory<T> factory;
    private Set<Class<?>> publishedEvents = new HashSet<>();

    @SafeVarargs
    private Builder(Class<T> anInterface, Class<? super T>... additionalInterfaces) {
      Preconditions.checkNotNull(anInterface, "Null interface");
      providedInterfaces.add(anInterface);
      for (Class<? super T> iface : additionalInterfaces) {
        Preconditions.checkNotNull(iface, "Null interface");
      }
      Collections.addAll(providedInterfaces, additionalInterfaces);
    }

    /** Add a {@link Dependency} to the {@link Component} being built. */
    public Builder<T> add(Dependency dependency) {
      Preconditions.checkNotNull(dependency, "Null dependency");
      validateInterface(dependency.getInterface());
      dependencies.add(dependency);
      return this;
    }

    /** Make the {@link Component} initialize upon startup. */
    public Builder<T> alwaysEager() {
      return setInstantiation(Instantiation.ALWAYS_EAGER);
    }

    /** Make the component initialize upon startup in default app. */
    public Builder<T> eagerInDefaultApp() {
      return setInstantiation(Instantiation.EAGER_IN_DEFAULT_APP);
    }

    /** Make the {@link Component} eligible to publish events of provided eventType. */
    public Builder<T> publishes(Class<?> eventType) {
      publishedEvents.add(eventType);
      return this;
    }

    private Builder<T> setInstantiation(@Instantiation int instantiation) {
      Preconditions.checkState(
          this.instantiation == Instantiation.LAZY, "Instantiation type has already been set.");
      this.instantiation = instantiation;
      return this;
    }

    private void validateInterface(Class<?> anInterface) {
      Preconditions.checkArgument(
          !providedInterfaces.contains(anInterface),
          "Components are not allowed to depend on interfaces they themselves provide.");
    }

    /** Set the factory that will be used to initialize the {@link Component}. */
    public Builder<T> factory(ComponentFactory<T> value) {
      factory = Preconditions.checkNotNull(value, "Null factory");
      return this;
    }

    private Builder<T> intoSet() {
      type = ComponentType.SET;
      return this;
    }

    /** Return the built {@link Component} definition. */
    public Component<T> build() {
      Preconditions.checkState(factory != null, "Missing required property: factory.");
      return new Component<>(
          new HashSet<>(providedInterfaces),
          new HashSet<>(dependencies),
          instantiation,
          type,
          factory,
          publishedEvents);
    }
  }
}
