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

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CycleDetectorTest {
  private static final ComponentFactory<Object> NULL_FACTORY = container -> null;

  @SuppressWarnings("unchecked")
  private static <T> ComponentFactory<T> nullFactory() {
    return (ComponentFactory<T>) NULL_FACTORY;
  }

  private interface TestInterface1 {}

  private interface TestInterface2 extends TestInterface1 {}

  private interface TestInterface3 {}

  private interface TestInterface4 {}

  private interface TestInterface5 {}

  private interface TestInterface6 {}

  private interface TestInterface7 {}

  /*
   * Tests the following dependency graph.
   *
   *    3 <-------- 4
   *    |\         /|
   *    | \-> 1 <-/ |
   *    ----> 2 <----
   */
  @Test
  public void detect_shouldNotDetectACycle1() {
    List<Component<?>> components =
        Arrays.asList(
            Component.builder(TestInterface4.class)
                .add(Dependency.required(TestInterface1.class))
                .add(Dependency.required(TestInterface3.class))
                .factory(nullFactory())
                .build(),
            Component.builder(TestInterface3.class)
                .add(Dependency.required(TestInterface1.class))
                .add(Dependency.required(TestInterface2.class))
                .factory(nullFactory())
                .build(),
            Component.builder(TestInterface2.class, TestInterface1.class)
                .factory(nullFactory())
                .build());

    twice(() -> detect(components));
  }

  /*
   * Tests the following dependency graph.
   *
   * 1 -> 2 -> 3 -> 4 -> 5
   *
   * 6 -> 7
   */
  @Test
  public void detect_shouldNotDetectACycle2() {
    List<Component<?>> components =
        Arrays.asList(
            Component.builder(TestInterface1.class)
                .add(Dependency.required(TestInterface2.class))
                .factory(nullFactory())
                .build(),
            Component.builder(TestInterface2.class)
                .add(Dependency.required(TestInterface3.class))
                .factory(nullFactory())
                .build(),
            Component.builder(TestInterface3.class)
                .add(Dependency.required(TestInterface4.class))
                .factory(nullFactory())
                .build(),
            Component.builder(TestInterface4.class)
                .add(Dependency.required(TestInterface5.class))
                .factory(nullFactory())
                .build(),
            Component.builder(TestInterface5.class).factory(nullFactory()).build(),
            Component.builder(TestInterface6.class)
                .add(Dependency.required(TestInterface7.class))
                .factory(nullFactory())
                .build(),
            Component.builder(TestInterface7.class).factory(nullFactory()).build());

    twice(() -> detect(components));
  }

  /*
   * Tests the following dependency graph.
   *
   *       1
   *      /|\
   *     v v v
   *     2 3 4
   *    /  |  \
   *   v   v   v
   *   5   6   7
   */
  @Test
  public void detect_shouldNotDetectACycle3() {
    List<Component<?>> components =
        Arrays.asList(
            Component.builder(TestInterface1.class)
                .add(Dependency.required(TestInterface2.class))
                .add(Dependency.required(TestInterface3.class))
                .add(Dependency.required(TestInterface4.class))
                .factory(nullFactory())
                .build(),
            Component.builder(TestInterface2.class)
                .add(Dependency.required(TestInterface5.class))
                .factory(nullFactory())
                .build(),
            Component.builder(TestInterface3.class)
                .add(Dependency.required(TestInterface6.class))
                .factory(nullFactory())
                .build(),
            Component.builder(TestInterface4.class)
                .add(Dependency.required(TestInterface7.class))
                .factory(nullFactory())
                .build(),
            Component.builder(TestInterface5.class).factory(nullFactory()).build(),
            Component.builder(TestInterface6.class).factory(nullFactory()).build(),
            Component.builder(TestInterface7.class).factory(nullFactory()).build());

    twice(() -> detect(components));
  }

  /*
   * Tests the following dependency graph.
   *
   *     1       2
   *    / \     / \    7
   *   v   v   v   v
   *   3   4   5   6
   */
  @Test
  public void detect_shouldNotDetectACycle4() {
    List<Component<?>> components =
        Arrays.asList(
            Component.builder(TestInterface1.class)
                .add(Dependency.required(TestInterface3.class))
                .add(Dependency.required(TestInterface4.class))
                .factory(nullFactory())
                .build(),
            Component.builder(TestInterface2.class)
                .add(Dependency.required(TestInterface5.class))
                .add(Dependency.required(TestInterface6.class))
                .factory(nullFactory())
                .build(),
            Component.builder(TestInterface3.class).factory(nullFactory()).build(),
            Component.builder(TestInterface4.class).factory(nullFactory()).build(),
            Component.builder(TestInterface5.class).factory(nullFactory()).build(),
            Component.builder(TestInterface6.class).factory(nullFactory()).build(),
            Component.builder(TestInterface7.class).factory(nullFactory()).build());

    twice(() -> detect(components));
  }

  /*
   * Tests the following dependency graph.
   *
   *     1 -> 2 -> 3
   *     ^         |
   *     |_________|
   */
  @Test
  public void detect_withDependencyCycle_shouldThrow() {
    List<Component<?>> components =
        Arrays.asList(
            Component.builder(TestInterface1.class)
                .add(Dependency.required(TestInterface2.class))
                .factory(nullFactory())
                .build(),
            Component.builder(TestInterface2.class)
                .add(Dependency.required(TestInterface3.class))
                .factory(nullFactory())
                .build(),
            Component.builder(TestInterface3.class)
                .add(Dependency.required(TestInterface1.class))
                .factory(nullFactory())
                .build());

    try {
      CycleDetector.detect(components);
      fail("Not thrown");
    } catch (DependencyCycleException ex) {
      assertThat(ex.getComponentsInCycle()).containsExactlyElementsIn(components);
      // success.
    }
  }

  /*
  * Tests the following dependency graph.
  *
  *     1 -> 2 ->  3
        ^          |
        |_Provider_|
  */
  @Test
  public void detect_withProviderDependencyCycle_shouldNotThrow() {
    List<Component<?>> components =
        Arrays.asList(
            Component.builder(TestInterface1.class)
                .add(Dependency.required(TestInterface2.class))
                .factory(nullFactory())
                .build(),
            Component.builder(TestInterface2.class)
                .add(Dependency.required(TestInterface3.class))
                .factory(nullFactory())
                .build(),
            Component.builder(TestInterface3.class)
                .add(Dependency.requiredProvider(TestInterface1.class))
                .factory(nullFactory())
                .build());

    CycleDetector.detect(components);
    twice(() -> detect(components));
  }

  @Test
  public void detect_withMultipleComponentsImplementingSameIface_shouldThrow() {
    List<Component<?>> components =
        Arrays.asList(
            Component.builder(TestInterface1.class).factory(nullFactory()).build(),
            Component.builder(TestInterface1.class).factory(nullFactory()).build());

    try {
      CycleDetector.detect(components);
      fail();
    } catch (IllegalArgumentException ex) {
      // success.
    }
  }

  private static void detect(List<Component<?>> components) {
    Collections.shuffle(components);
    try {
      CycleDetector.detect(components);
    } catch (DependencyException ex) {
      fail(String.format("Unexpected exception thrown: %s", ex));
    }
  }

  private static void twice(Runnable runnable) {
    for (int i = 0; i < 2; i++) {
      runnable.run();
    }
  }
}
