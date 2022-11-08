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

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.components.Qualified.qualified;
import static com.google.firebase.components.Qualified.unqualified;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import com.google.firebase.dynamicloading.ComponentLoader;
import com.google.firebase.inject.Deferred;
import com.google.firebase.inject.Provider;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ComponentRuntimeTest {
  private @interface Qualifier1 {}

  private @interface Qualifier2 {}

  private static final Executor EXECUTOR = Runnable::run;

  interface ComponentOne {
    InitTracker getTracker();
  }

  interface ComponentTwo {
    ComponentOne getOne();
  }

  private static class ComponentOneImpl implements ComponentOne {
    private final InitTracker tracker;

    ComponentOneImpl(InitTracker tracker) {
      this.tracker = tracker;
      tracker.initialize();
    }

    @Override
    public InitTracker getTracker() {
      return tracker;
    }
  }

  private static class ComponentTwoImpl implements ComponentTwo {
    private final ComponentOne one;

    ComponentTwoImpl(ComponentOne one) {
      this.one = one;
    }

    @Override
    public ComponentOne getOne() {
      return one;
    }
  }

  enum Eagerness {
    ALWAYS,
    DEFAULT_ONLY
  }

  private static class ComponentRegistrarImpl implements ComponentRegistrar {

    private final Eagerness eagerness;

    private ComponentRegistrarImpl(Eagerness eagerness) {
      this.eagerness = eagerness;
    }

    @Override
    public List<Component<?>> getComponents() {
      Component.Builder<ComponentOne> cmpOne =
          Component.builder(ComponentOne.class)
              .add(Dependency.required(InitTracker.class))
              .factory(container -> new ComponentOneImpl(container.get(InitTracker.class)));
      switch (eagerness) {
        case ALWAYS:
          cmpOne.alwaysEager();
          break;
        case DEFAULT_ONLY:
          cmpOne.eagerInDefaultApp();
          break;
        default:
          throw new IllegalStateException("Unsupported eagerness specified " + eagerness);
      }
      return Arrays.asList(
          Component.builder(ComponentTwo.class)
              .add(Dependency.required(ComponentOne.class))
              .factory(container -> new ComponentTwoImpl(container.get(ComponentOne.class)))
              .build(),
          cmpOne.build());
    }
  }

  @Test
  public void
      container_withValidDependencyGraph_withDefaultApp_shouldInitializeEagerComponentsAndTheirDependencies() {
    InitTracker initTracker = new InitTracker();

    ComponentRuntime runtime =
        ComponentRuntime.builder(EXECUTOR)
            .addComponentRegistrar(new ComponentRegistrarImpl(Eagerness.ALWAYS))
            .addComponent(Component.of(initTracker, InitTracker.class))
            .build();

    assertThat(initTracker.isInitialized()).isFalse();

    runtime.initializeEagerComponents(true);

    assertThat(initTracker.isInitialized()).isTrue();
  }

  @Test
  public void
      container_withValidDependencyGraph_withDefaultApp_shouldInitializeDefaultAppEagerComponentsAndTheirDependencies() {
    InitTracker initTracker = new InitTracker();

    ComponentRuntime runtime =
        ComponentRuntime.builder(EXECUTOR)
            .addComponentRegistrar(new ComponentRegistrarImpl(Eagerness.DEFAULT_ONLY))
            .addComponent(Component.of(initTracker, InitTracker.class))
            .build();

    assertThat(initTracker.isInitialized()).isFalse();

    runtime.initializeEagerComponents(true);

    assertThat(initTracker.isInitialized()).isTrue();
  }

  @Test
  public void container_withValidDependencyGraph_shouldNotInitializeNonDefaultAppEagerComponents() {
    InitTracker initTracker = new InitTracker();

    ComponentRuntime runtime =
        ComponentRuntime.builder(EXECUTOR)
            .addComponentRegistrar(new ComponentRegistrarImpl(Eagerness.DEFAULT_ONLY))
            .addComponent(Component.of(initTracker, InitTracker.class))
            .build();

    assertThat(initTracker.isInitialized()).isFalse();

    runtime.initializeEagerComponents(false);

    assertThat(initTracker.isInitialized()).isFalse();
  }

  @Test
  public void container_withValidDependencyGraph_shouldProperlyInjectComponents() {
    InitTracker initTracker = new InitTracker();

    ComponentRuntime runtime =
        ComponentRuntime.builder(EXECUTOR)
            .addComponentRegistrar(new ComponentRegistrarImpl(Eagerness.ALWAYS))
            .addComponent(Component.of(initTracker, InitTracker.class))
            .build();

    assertThat(initTracker.isInitialized()).isFalse();

    ComponentTwo componentTwo = runtime.get(ComponentTwo.class);
    assertThat(componentTwo.getOne()).isNotNull();
    assertThat(componentTwo.getOne().getTracker()).isSameInstanceAs(initTracker);

    assertThat(initTracker.isInitialized()).isTrue();
  }

  @Test
  public void container_withCyclicDependencyGraph_shouldThrow() {
    try {
      ComponentRuntime.builder(EXECUTOR)
          .addComponentRegistrar(new ComponentRegistrarImpl(Eagerness.ALWAYS))
          .addComponent(
              Component.builder(InitTracker.class)
                  .add(Dependency.required(ComponentTwo.class))
                  .factory(container -> null)
                  .build())
          .build();
      fail("Expected exception not thrown.");
    } catch (DependencyCycleException ex) {
      // success.
    }
  }

  @Test
  public void container_withMultipleComponentsRegisteredForSameInterface_shouldThrow() {
    try {
      ComponentRuntime.builder(EXECUTOR)
          .addComponentRegistrar(new ComponentRegistrarImpl(Eagerness.ALWAYS))
          .addComponent(Component.builder(ComponentOne.class).factory(container -> null).build())
          .build();
      fail("Expected exception not thrown.");
    } catch (IllegalArgumentException ex) {
      // success.
    }
  }

  @Test
  public void
      container_withMultipleComponentsRegisteredForSameInterfaceButQualified_shouldNotThrow() {
    ComponentRuntime runtime =
        ComponentRuntime.builder(EXECUTOR)
            .addComponent(Component.of(1, Integer.class))
            .addComponent(Component.of(2, qualified(Qualifier1.class, Integer.class)))
            .addComponent(Component.of(3, qualified(Qualifier2.class, Integer.class)))
            .build();

    assertThat(runtime.get(Integer.class)).isEqualTo(1);
    assertThat(runtime.get(qualified(Qualifier1.class, Integer.class))).isEqualTo(2);
    assertThat(runtime.get(qualified(Qualifier2.class, Integer.class))).isEqualTo(3);
  }

  @Test
  public void container_withMissingDependencies_shouldThrow() {
    try {
      ComponentRuntime.builder(EXECUTOR)
          .addComponentRegistrar(new ComponentRegistrarImpl(Eagerness.ALWAYS))
          .build();
      fail("Expected exception not thrown.");
    } catch (MissingDependencyException ex) {
      // success.
    }
  }

  private static class CyclicOne {
    final CyclicTwo cyclicTwo;

    CyclicOne(CyclicTwo two) {
      cyclicTwo = two;
    }
  }

  private static class CyclicTwo {
    final Provider<CyclicOne> cyclicOne;

    CyclicTwo(Provider<CyclicOne> one) {
      cyclicOne = one;
    }
  }

  @Test
  public void container_withCyclicProviderDependency_shouldProperlyInitialize() {
    ComponentRuntime runtime =
        ComponentRuntime.builder(EXECUTOR)
            .addComponent(
                Component.builder(CyclicOne.class)
                    .add(Dependency.required(CyclicTwo.class))
                    .factory(container -> new CyclicOne(container.get(CyclicTwo.class)))
                    .build())
            .addComponent(
                Component.builder(CyclicTwo.class)
                    .add(Dependency.requiredProvider(CyclicOne.class))
                    .factory(container -> new CyclicTwo(container.getProvider(CyclicOne.class)))
                    .build())
            .build();
    CyclicOne one = runtime.get(CyclicOne.class);

    assertThat(one.cyclicTwo).isNotNull();
    Provider<CyclicOne> oneProvider = one.cyclicTwo.cyclicOne;
    assertThat(oneProvider).isNotNull();
    assertThat(oneProvider.get()).isSameInstanceAs(one);
  }

  @Test
  public void get_withNullInterface_shouldThrow() {
    ComponentRuntime runtime = ComponentRuntime.builder(EXECUTOR).build();
    try {
      runtime.get((Qualified<?>) null);
      fail("Expected exception not thrown.");
    } catch (NullPointerException ex) {
      // success.
    }
  }

  @Test
  public void get_withMissingInterface_shouldReturnNull() {
    ComponentRuntime runtime = ComponentRuntime.builder(EXECUTOR).build();
    assertThat(runtime.get(List.class)).isNull();
  }

  private interface Parent {}

  private static class Child implements Parent {}

  @Test
  public void container_shouldExposeAllProvidedInterfacesOfAComponent() {
    ComponentRuntime runtime =
        ComponentRuntime.builder(EXECUTOR)
            .addComponent(
                Component.builder(Child.class, Parent.class).factory(c -> new Child()).build())
            .build();

    Provider<Child> child = runtime.getProvider(Child.class);
    assertThat(child).isNotNull();
    Provider<Parent> parent = runtime.getProvider(Parent.class);
    assertThat(parent).isNotNull();

    assertThat(child).isSameInstanceAs(parent);
    assertThat(child.get()).isSameInstanceAs(parent.get());
  }

  @Test
  public void container_shouldExposeAllRegisteredSetValues() {
    ComponentRuntime runtime =
        ComponentRuntime.builder(EXECUTOR)
            .addComponent(Component.intoSet(1, Integer.class))
            .addComponent(Component.intoSet(2, Integer.class))
            .build();

    Set<Integer> integers = runtime.setOf(Integer.class);
    assertThat(integers).containsExactly(1, 2);
  }

  @Test
  public void setComponents_shouldParticipateInCycleDetection() {
    try {
      ComponentRuntime.builder(EXECUTOR)
          .addComponent(
              Component.builder(ComponentOne.class)
                  .add(Dependency.setOf(Integer.class))
                  .factory(c -> null)
                  .build())
          .addComponent(Component.intoSet(1, Integer.class))
          .addComponent(
              Component.intoSetBuilder(Integer.class)
                  .add(Dependency.required(ComponentOne.class))
                  .factory(c -> 2)
                  .build())
          .build();
      fail("Expected exception not thrown.");
    } catch (DependencyCycleException ex) {
      // success.
    }
  }

  @Test
  public void setComponents_shouldNotPreventValueComponentsFromBeingRegistered() {
    ComponentRuntime runtime =
        ComponentRuntime.builder(EXECUTOR)
            .addComponent(Component.intoSet(1, Integer.class))
            .addComponent(Component.intoSet(2, Integer.class))
            .addComponent(Component.of(2f, Float.class))
            .addComponent(Component.intoSet(3, Integer.class))
            .addComponent(Component.intoSet(4, Integer.class))
            .addComponent(Component.of(4d, Double.class))
            .build();

    assertThat(runtime.setOf(Integer.class)).containsExactly(1, 2, 3, 4);
    assertThat(runtime.get(Float.class)).isEqualTo(2f);
    assertThat(runtime.get(Double.class)).isEqualTo(4d);
  }

  @Test
  public void setComponents_withQualifiers_shouldContributeToAppropriateSets() {
    ComponentRuntime runtime =
        ComponentRuntime.builder(EXECUTOR)
            .addComponent(Component.of(5, Integer.class))
            .addComponent(Component.intoSet(1, Integer.class))
            .addComponent(Component.intoSet(3, Integer.class))
            .addComponent(Component.intoSet(1, qualified(Qualifier1.class, Integer.class)))
            .addComponent(Component.intoSet(2, qualified(Qualifier1.class, Integer.class)))
            .addComponent(Component.intoSet(3, qualified(Qualifier2.class, Integer.class)))
            .addComponent(Component.intoSet(4, qualified(Qualifier2.class, Integer.class)))
            .build();

    assertThat(runtime.get(Integer.class)).isEqualTo(5);
    assertThat(runtime.setOf(Integer.class)).containsExactly(1, 3);
    assertThat(runtime.setOf(qualified(Qualifier1.class, Integer.class))).containsExactly(1, 2);
    assertThat(runtime.setOf(qualified(Qualifier2.class, Integer.class))).containsExactly(3, 4);
  }

  private static class DependsOnString {

    private final Provider<String> dep;

    DependsOnString(Provider<String> dep) {
      this.dep = dep;
    }
  }

  @Test
  public void newlyDiscoveredComponent_shouldBecomeAvailableThroughItsProvider() {
    OptionalProvider<ComponentRegistrar> missingRegistrar = OptionalProvider.empty();
    ComponentRuntime runtime =
        ComponentRuntime.builder(EXECUTOR)
            .addLazyComponentRegistrars(Collections.singleton(missingRegistrar))
            .addComponent(
                Component.builder(DependsOnString.class)
                    .add(Dependency.optionalProvider(String.class))
                    .factory(c -> new DependsOnString(c.getProvider(String.class)))
                    .build())
            .build();
    ComponentLoader componentLoader = runtime.get(ComponentLoader.class);

    DependsOnString dependsOnString = runtime.get(DependsOnString.class);
    assertThat(dependsOnString.dep.get()).isNull();
    missingRegistrar.set(
        () -> () -> Collections.singletonList(Component.of("hello", String.class)));
    assertThat(dependsOnString.dep.get()).isNull();
    componentLoader.discoverComponents();
    assertThat(dependsOnString.dep.get()).isEqualTo("hello");
  }

  private static class DependsOnSet {

    private final Set<String> dep;

    private DependsOnSet(Set<String> dep) {
      this.dep = dep;
    }
  }

  @Test
  public void newlyDiscoveredSetComponent_shouldBecomeAvailableThroughItsSet() {
    OptionalProvider<ComponentRegistrar> missingRegistrar = OptionalProvider.empty();
    ComponentRuntime runtime =
        ComponentRuntime.builder(EXECUTOR)
            .addLazyComponentRegistrars(Collections.singleton(missingRegistrar))
            .addComponent(
                Component.builder(DependsOnSet.class)
                    .add(Dependency.setOf(String.class))
                    .factory(c -> new DependsOnSet(c.setOf(String.class)))
                    .build())
            .build();
    ComponentLoader componentLoader = runtime.get(ComponentLoader.class);

    DependsOnSet component = runtime.get(DependsOnSet.class);
    assertThat(component.dep).isEmpty();
    missingRegistrar.set(
        () -> () -> Collections.singletonList(Component.intoSet("hello", String.class)));
    assertThat(component.dep).isEmpty();
    componentLoader.discoverComponents();
    assertThat(component.dep).containsExactly("hello");
  }

  @Test
  public void newlyDiscoveredComponents_whenNewComponentConflictsWithExisting_shouldThrow() {
    OptionalProvider<ComponentRegistrar> missingRegistrar = OptionalProvider.empty();
    ComponentRuntime runtime =
        ComponentRuntime.builder(EXECUTOR)
            .addLazyComponentRegistrars(Collections.singleton(missingRegistrar))
            .addComponent(Component.of("string", String.class))
            .build();
    missingRegistrar.set(
        () -> () -> Collections.singletonList(Component.of("hello", String.class)));
    assertThrows(IllegalArgumentException.class, runtime::discoverComponents);
  }

  @Test
  public void
      newlyDiscoveredEagerComponents_whenExistingEagerComponentsAreInitialized_shouldInitializeUponDiscovery() {
    OptionalProvider<ComponentRegistrar> missingRegistrar = OptionalProvider.empty();
    InitTracker initTracker = new InitTracker();
    ComponentRuntime runtime =
        ComponentRuntime.builder(EXECUTOR)
            .addLazyComponentRegistrars(Collections.singleton(missingRegistrar))
            .addComponent(Component.of(initTracker, InitTracker.class))
            .build();
    runtime.initializeEagerComponents(true);
    assertThat(initTracker.isInitialized()).isFalse();
    missingRegistrar.set(
        () ->
            () ->
                Collections.singletonList(
                    Component.builder(ComponentOneImpl.class, ComponentOne.class)
                        .add(Dependency.required(InitTracker.class))
                        .alwaysEager()
                        .factory(c -> new ComponentOneImpl(c.get(InitTracker.class)))
                        .build()));
    runtime.discoverComponents();
    assertThat(initTracker.isInitialized()).isTrue();
  }

  @Test
  public void
      newlyDiscoveredEagerComponents_whenExistingEagerComponentsAreInitializedInNonDefaultApp_shouldNotInitializeUponDiscovery() {
    OptionalProvider<ComponentRegistrar> missingRegistrar = OptionalProvider.empty();
    InitTracker initTracker = new InitTracker();
    ComponentRuntime runtime =
        ComponentRuntime.builder(EXECUTOR)
            .addLazyComponentRegistrars(Collections.singleton(missingRegistrar))
            .addComponent(Component.of(initTracker, InitTracker.class))
            .build();
    runtime.initializeEagerComponents(false);
    assertThat(initTracker.isInitialized()).isFalse();
    missingRegistrar.set(
        () ->
            () ->
                Collections.singletonList(
                    Component.builder(ComponentOneImpl.class, ComponentOne.class)
                        .add(Dependency.required(InitTracker.class))
                        .eagerInDefaultApp()
                        .factory(c -> new ComponentOneImpl(c.get(InitTracker.class)))
                        .build()));
    runtime.discoverComponents();
    assertThat(initTracker.isInitialized()).isFalse();
  }

  @Test
  public void
      newlyDiscoveredEagerDefaultComponents_whenExistingEagerComponentsAreInitialized_shouldNotInitializeUponDiscovery() {
    OptionalProvider<ComponentRegistrar> missingRegistrar = OptionalProvider.empty();
    InitTracker initTracker = new InitTracker();
    ComponentRuntime runtime =
        ComponentRuntime.builder(EXECUTOR)
            .addLazyComponentRegistrars(Collections.singleton(missingRegistrar))
            .addComponent(Component.of(initTracker, InitTracker.class))
            .build();
    runtime.initializeEagerComponents(false);
    assertThat(initTracker.isInitialized()).isFalse();
    missingRegistrar.set(
        () ->
            () ->
                Collections.singletonList(
                    Component.builder(ComponentOneImpl.class, ComponentOne.class)
                        .add(Dependency.required(InitTracker.class))
                        .eagerInDefaultApp()
                        .factory(c -> new ComponentOneImpl(c.get(InitTracker.class)))
                        .build()));
    runtime.discoverComponents();
    assertThat(initTracker.isInitialized()).isFalse();
  }

  @Test
  public void
      newlyDiscoveredEagerComponents_whenExistingEagerComponentsAreNotInitialized_shouldNotInitializeUponDiscovery() {
    OptionalProvider<ComponentRegistrar> missingRegistrar = OptionalProvider.empty();
    InitTracker initTracker = new InitTracker();
    ComponentRuntime runtime =
        ComponentRuntime.builder(EXECUTOR)
            .addLazyComponentRegistrars(Collections.singleton(missingRegistrar))
            .addComponent(Component.of(initTracker, InitTracker.class))
            .build();
    assertThat(initTracker.isInitialized()).isFalse();
    missingRegistrar.set(
        () ->
            () ->
                Collections.singletonList(
                    Component.builder(ComponentOneImpl.class, ComponentOne.class)
                        .add(Dependency.required(InitTracker.class))
                        .alwaysEager()
                        .factory(c -> new ComponentOneImpl(c.get(InitTracker.class)))
                        .build()));
    runtime.discoverComponents();

    assertThat(initTracker.isInitialized()).isFalse();
    runtime.initializeEagerComponents(true);

    assertThat(initTracker.isInitialized()).isTrue();
  }

  @Test
  public void undeclaredDep_withDeferredLoading_shouldThrow() {
    OptionalProvider<ComponentRegistrar> missingRegistrar = OptionalProvider.empty();
    InitTracker initTracker = new InitTracker();
    ComponentRuntime runtime =
        ComponentRuntime.builder(EXECUTOR)
            .addLazyComponentRegistrars(Collections.singleton(missingRegistrar))
            .addComponent(Component.of(initTracker, InitTracker.class))
            .build();
    runtime.initializeEagerComponents(true);
    missingRegistrar.set(
        () ->
            () ->
                Collections.singletonList(
                    Component.builder(ComponentOneImpl.class, ComponentOne.class)
                        .alwaysEager()
                        .factory(c -> new ComponentOneImpl(c.get(InitTracker.class)))
                        .build()));
    DependencyException thrown =
        assertThrows(DependencyException.class, runtime::discoverComponents);
    assertThat(thrown).hasMessageThat().contains("undeclared dependency");
  }

  private static class DependsOnDeferredString {

    private String value;

    DependsOnDeferredString(Deferred<String> dep) {
      dep.whenAvailable(p -> value = p.get());
    }
  }

  @Test
  public void newlyDiscoveredComponent_shouldBecomeAvailableThroughItsDeferred() {
    OptionalProvider<ComponentRegistrar> missingRegistrar = OptionalProvider.empty();
    ComponentRuntime runtime =
        ComponentRuntime.builder(EXECUTOR)
            .addLazyComponentRegistrars(Collections.singleton(missingRegistrar))
            .addComponent(
                Component.builder(DependsOnDeferredString.class)
                    .add(Dependency.deferred(String.class))
                    .factory(c -> new DependsOnDeferredString(c.getDeferred(String.class)))
                    .build())
            .build();
    ComponentLoader componentLoader = runtime.get(ComponentLoader.class);

    DependsOnDeferredString dependsOnDeferredString = runtime.get(DependsOnDeferredString.class);
    assertThat(dependsOnDeferredString.value).isNull();
    missingRegistrar.set(
        () -> () -> Collections.singletonList(Component.of("hello", String.class)));
    assertThat(dependsOnDeferredString.value).isNull();
    componentLoader.discoverComponents();
    assertThat(dependsOnDeferredString.value).isEqualTo("hello");
  }

  @Test
  public void container_withComponentProcessor_shouldDelegateToItForEachComponentRegistrar() {
    InitTracker initTracker = new InitTracker();

    ComponentFactory<Object> replacedFactory = c -> null;
    ComponentRegistrarProcessor processor =
        r ->
            r.getComponents().stream()
                .map(c -> ((Component<Object>) c).withFactory(replacedFactory))
                .collect(Collectors.toList());

    ComponentRuntime runtime =
        ComponentRuntime.builder(EXECUTOR)
            .addComponentRegistrar(new ComponentRegistrarImpl(Eagerness.ALWAYS))
            .addComponent(Component.of(initTracker, InitTracker.class))
            .setProcessor(processor)
            .build();

    assertThat(
            runtime.getAllComponentsForTest().stream()
                .filter(
                    c ->
                        (c.getProvidedInterfaces().contains(unqualified(ComponentOne.class))
                            || c.getProvidedInterfaces().contains(unqualified(ComponentTwo.class))))
                .allMatch(c -> c.getFactory() == replacedFactory))
        .isTrue();
  }
}
