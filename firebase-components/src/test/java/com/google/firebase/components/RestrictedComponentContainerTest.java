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
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.events.Event;
import com.google.firebase.events.Publisher;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RestrictedComponentContainerTest {

  private final ComponentContainer delegate = mock(ComponentContainer.class);
  private final ComponentContainer container =
      new RestrictedComponentContainer(
          Component.builder(String.class)
              .add(Dependency.required(Float.class))
              .add(Dependency.requiredProvider(Double.class))
              .add(Dependency.deferred(Integer.class))
              .add(Dependency.setOf(Long.class))
              .add(Dependency.setOfProvider(Boolean.class))
              .factory(c -> null)
              .build(),
          delegate);

  private final ComponentContainer publishingContainer =
      new RestrictedComponentContainer(
          Component.builder(String.class).publishes(Float.class).factory(c -> null).build(),
          delegate);

  private final Publisher mockPublisher = mock(Publisher.class);

  @Test
  public void get_withAllowedClass_shouldReturnAnInstanceOfThatClass() {
    Float value = 1.0f;
    when(delegate.get(Float.class)).thenReturn(value);

    assertThat(container.get(Float.class)).isSameInstanceAs(value);
    verify(delegate).get(Float.class);
  }

  @Test
  public void get_withNotAllowedClass_shouldThrow() {
    try {
      container.get(List.class);
      fail("Expected exception not thrown.");
    } catch (DependencyException ex) {
      assertThat(ex.getMessage()).contains("java.util.List");
    }
  }

  @Test
  public void get_withProviderClass_shouldThrow() {
    try {
      container.get(Double.class);
      fail("Expected exception not thrown.");
    } catch (DependencyException ex) {
      assertThat(ex.getMessage()).contains("java.lang.Double");
    }
  }

  @Test
  public void get_withPublisher_shouldThrow() {
    try {
      container.get(Publisher.class);
      fail("Expected exception not thrown.");
    } catch (DependencyException ex) {
      assertThat(ex.getMessage()).contains("events.Publisher");
    }
  }

  @Test
  public void getProvider_withAllowedClass_shouldReturnAnInstanceOfThatClass() {
    Double value = 3.0d;
    when(delegate.getProvider(Double.class)).thenReturn(new Lazy<>(value));

    assertThat(container.getProvider(Double.class).get()).isSameInstanceAs(value);
    verify(delegate).getProvider(Double.class);
  }

  @Test
  public void getProvider_withNotAllowedClass_shouldThrow() {
    try {
      container.getProvider(List.class);
      fail("Expected exception not thrown.");
    } catch (DependencyException ex) {
      assertThat(ex.getMessage()).contains("java.util.List");
    }
  }

  @Test
  public void getProvider_withDirectClass_shouldThrow() {
    try {
      container.getProvider(Float.class);
      fail("Expected exception not thrown.");
    } catch (DependencyException ex) {
      assertThat(ex.getMessage()).contains("java.lang.Float");
    }
  }

  @Test
  public void getDeferred_withAllowedClass_shouldReturnAnInstanceOfThatClass() {
    Integer value = 3;
    when(delegate.getDeferred(Integer.class)).thenReturn(OptionalProvider.of(() -> value));

    AtomicReference<Integer> returned = new AtomicReference<>();
    container.getDeferred(Integer.class).whenAvailable(d -> returned.set(d.get()));
    assertThat(returned.get()).isSameInstanceAs(value);
    verify(delegate).getDeferred(Integer.class);
  }

  @Test
  public void getDeferred_withNotAllowedClass_shouldThrow() {
    try {
      container.getDeferred(List.class);
      fail("Expected exception not thrown.");
    } catch (DependencyException ex) {
      assertThat(ex.getMessage()).contains("java.util.List");
    }
  }

  @Test
  public void getDeferred_withDirectClass_shouldThrow() {
    try {
      container.getDeferred(Float.class);
      fail("Expected exception not thrown.");
    } catch (DependencyException ex) {
      assertThat(ex.getMessage()).contains("java.lang.Float");
    }
  }

  @Test
  public void getDeferred_withProviderClass_shouldThrow() {
    try {
      container.getDeferred(Double.class);
      fail("Expected exception not thrown.");
    } catch (DependencyException ex) {
      assertThat(ex.getMessage()).contains("java.lang.Double");
    }
  }

  @Test
  public void setOf_withAllowedClass_shouldReturnExpectedSet() {
    Set<Long> set = Collections.emptySet();
    when(delegate.setOf(Long.class)).thenReturn(set);

    assertThat(container.setOf(Long.class)).isSameInstanceAs(set);
    verify(delegate).setOf(Long.class);
  }

  @Test
  public void setOf_withNotAllowedClass_shouldThrow() {
    try {
      container.setOf(List.class);
      fail("Expected exception not thrown.");
    } catch (DependencyException ex) {
      assertThat(ex.getMessage()).contains("java.util.List");
    }
  }

  @Test
  public void setOfProvider_withAllowedClass_shouldReturnExpectedSet() {
    Set<Boolean> set = Collections.emptySet();
    when(delegate.setOfProvider(Boolean.class)).thenReturn(new Lazy<>(set));

    assertThat(container.setOfProvider(Boolean.class).get()).isSameInstanceAs(set);
    verify(delegate).setOfProvider(Boolean.class);
  }

  @Test
  public void setOfProvider_withNotAllowedClass_shouldThrow() {
    try {
      container.setOf(List.class);
      fail("Expected exception not thrown.");
    } catch (DependencyException ex) {
      assertThat(ex.getMessage()).contains("java.util.List");
    }
  }

  @Test
  public void publish_withDeclaredEvent_shouldSucceed() {
    when(delegate.get(Publisher.class)).thenReturn(mockPublisher);
    Publisher publisher = publishingContainer.get(Publisher.class);

    Event<Float> event = new Event<>(Float.class, 1f);
    publisher.publish(event);

    verify(mockPublisher).publish(event);
  }

  @Test
  public void publish_withUndeclaredEvent_shouldThrow() {
    when(delegate.get(Publisher.class)).thenReturn(mockPublisher);
    Publisher publisher = publishingContainer.get(Publisher.class);

    Event<Double> event = new Event<>(Double.class, 1d);

    try {
      publisher.publish(event);
      fail("Expected exception not thrown.");
    } catch (DependencyException ex) {
      assertThat(ex.getMessage()).contains("java.lang.Double");
    }

    verify(mockPublisher, never()).publish(event);
  }
}
