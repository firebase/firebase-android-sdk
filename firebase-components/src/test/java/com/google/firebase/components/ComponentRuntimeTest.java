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
import static org.junit.Assert.fail;

import com.google.firebase.inject.Provider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ComponentRuntimeTest {
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
        new ComponentRuntime(
            EXECUTOR,
            Collections.singletonList(new ComponentRegistrarImpl(Eagerness.ALWAYS)),
            Component.of(initTracker, InitTracker.class));

    assertThat(initTracker.isInitialized()).isFalse();

    runtime.initializeEagerComponents(true);

    assertThat(initTracker.isInitialized()).isTrue();
  }

  @Test
  public void
      container_withValidDependencyGraph_withDefaultApp_shouldInitializeDefaultAppEagerComponentsAndTheirDependencies() {
    InitTracker initTracker = new InitTracker();

    ComponentRuntime runtime =
        new ComponentRuntime(
            EXECUTOR,
            Collections.singletonList(new ComponentRegistrarImpl(Eagerness.DEFAULT_ONLY)),
            Component.of(initTracker, InitTracker.class));

    assertThat(initTracker.isInitialized()).isFalse();

    runtime.initializeEagerComponents(true);

    assertThat(initTracker.isInitialized()).isTrue();
  }

  @Test
  public void container_withValidDependencyGraph_shouldNotInitializeNonDefaultAppEagerComponents() {
    InitTracker initTracker = new InitTracker();

    ComponentRuntime runtime =
        new ComponentRuntime(
            EXECUTOR,
            Collections.singletonList(new ComponentRegistrarImpl(Eagerness.DEFAULT_ONLY)),
            Component.of(initTracker, InitTracker.class));

    assertThat(initTracker.isInitialized()).isFalse();

    runtime.initializeEagerComponents(false);

    assertThat(initTracker.isInitialized()).isFalse();
  }

  @Test
  public void container_withValidDependencyGraph_shouldProperlyInjectComponents() {
    InitTracker initTracker = new InitTracker();

    ComponentRuntime runtime =
        new ComponentRuntime(
            EXECUTOR,
            Collections.singletonList(new ComponentRegistrarImpl(Eagerness.ALWAYS)),
            Component.of(initTracker, InitTracker.class));

    assertThat(initTracker.isInitialized()).isFalse();

    ComponentTwo componentTwo = runtime.get(ComponentTwo.class);
    assertThat(componentTwo.getOne()).isNotNull();
    assertThat(componentTwo.getOne().getTracker()).isSameInstanceAs(initTracker);

    assertThat(initTracker.isInitialized()).isTrue();
  }

  @Test
  public void container_withCyclicDependencyGraph_shouldThrow() {
    try {
      new ComponentRuntime(
          EXECUTOR,
          Collections.singletonList(new ComponentRegistrarImpl(Eagerness.ALWAYS)),
          Component.builder(InitTracker.class)
              .add(Dependency.required(ComponentTwo.class))
              .factory(container -> null)
              .build());
      fail("Expected exception not thrown.");
    } catch (DependencyCycleException ex) {
      // success.
    }
  }

  @Test
  public void container_withMultipleComponentsRegisteredForSameInterface_shouldThrow() {
    try {
      new ComponentRuntime(
          EXECUTOR,
          Collections.singletonList(new ComponentRegistrarImpl(Eagerness.ALWAYS)),
          Component.builder(ComponentOne.class).factory(container -> null).build());
      fail("Expected exception not thrown.");
    } catch (IllegalArgumentException ex) {
      // success.
    }
  }

  @Test
  public void container_withMissingDependencies_shouldThrow() {
    try {
      new ComponentRuntime(
          EXECUTOR, Collections.singletonList(new ComponentRegistrarImpl(Eagerness.ALWAYS)));
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
        new ComponentRuntime(
            EXECUTOR,
            Collections.emptyList(),
            Component.builder(CyclicOne.class)
                .add(Dependency.required(CyclicTwo.class))
                .factory(container -> new CyclicOne(container.get(CyclicTwo.class)))
                .build(),
            Component.builder(CyclicTwo.class)
                .add(Dependency.requiredProvider(CyclicOne.class))
                .factory(container -> new CyclicTwo(container.getProvider(CyclicOne.class)))
                .build());
    CyclicOne one = runtime.get(CyclicOne.class);

    assertThat(one.cyclicTwo).isNotNull();
    Provider<CyclicOne> oneProvider = one.cyclicTwo.cyclicOne;
    assertThat(oneProvider).isNotNull();
    assertThat(oneProvider.get()).isSameInstanceAs(one);
  }

  @Test
  public void get_withNullInterface_shouldThrow() {
    ComponentRuntime runtime = new ComponentRuntime(EXECUTOR, new ArrayList<>());
    try {
      runtime.get(null);
      fail("Expected exception not thrown.");
    } catch (NullPointerException ex) {
      // success.
    }
  }

  @Test
  public void get_withMissingInterface_shouldReturnNull() {
    ComponentRuntime runtime = new ComponentRuntime(EXECUTOR, new ArrayList<>());
    assertThat(runtime.get(List.class)).isNull();
  }

  private interface Parent {}

  private static class Child implements Parent {}

  @Test
  public void container_shouldExposeAllProvidedInterfacesOfAComponent() {
    ComponentRuntime runtime =
        new ComponentRuntime(
            EXECUTOR,
            Collections.emptyList(),
            Component.builder(Child.class, Parent.class).factory(c -> new Child()).build());

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
        new ComponentRuntime(
            EXECUTOR,
            Collections.emptyList(),
            Component.intoSet(1, Integer.class),
            Component.intoSet(2, Integer.class));

    assertThat(runtime.setOf(Integer.class)).containsExactly(1, 2);
  }

  @Test
  public void setComponents_shouldParticipateInCycleDetection() {
    try {
      new ComponentRuntime(
          EXECUTOR,
          Collections.emptyList(),
          Component.builder(ComponentOne.class)
              .add(Dependency.setOf(Integer.class))
              .factory(c -> null)
              .build(),
          Component.intoSet(1, Integer.class),
          Component.intoSetBuilder(Integer.class)
              .add(Dependency.required(ComponentOne.class))
              .factory(c -> 2)
              .build());
      fail("Expected exception not thrown.");
    } catch (DependencyCycleException ex) {
      // success.
    }
  }

  @Test
  public void setComponents_shouldNotPreventValueComponentsFromBeingRegistered() {
    ComponentRuntime runtime =
        new ComponentRuntime(
            EXECUTOR,
            Collections.emptySet(),
            Component.intoSet(1, Integer.class),
            Component.intoSet(2, Integer.class),
            Component.of(2f, Float.class),
            Component.intoSet(3, Integer.class),
            Component.intoSet(4, Integer.class),
            Component.of(4d, Double.class));

    assertThat(runtime.setOf(Integer.class)).containsExactly(1, 2, 3, 4);
    assertThat(runtime.get(Float.class)).isEqualTo(2f);
    assertThat(runtime.get(Double.class)).isEqualTo(4d);
  }
}
