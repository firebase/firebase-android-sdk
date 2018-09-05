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
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RestrictedComponentContainerTest {

  private final ComponentContainer delegate = mock(ComponentContainer.class);
  private final ComponentContainer container =
      new RestrictedComponentContainer(
          Component.builder(String.class)
              .add(Dependency.required(StringBuilder.class))
              .add(Dependency.requiredProvider(StringBuffer.class))
              .factory(c -> null)
              .build(),
          delegate);

  private final ComponentContainer publishingContainer =
      new RestrictedComponentContainer(
          Component.builder(String.class).publishes(StringBuilder.class).factory(c -> null).build(),
          delegate);

  private final Publisher mockPublisher = mock(Publisher.class);

  @Test
  public void get_withAllowedClass_shouldReturnAnInstanceOfThatClass() {
    StringBuilder sb = new StringBuilder();
    when(delegate.get(StringBuilder.class)).thenReturn(sb);

    assertThat(container.get(StringBuilder.class)).isSameAs(sb);
    verify(delegate).get(StringBuilder.class);
  }

  @Test
  public void get_withNotAllowedClass_shouldThrow() {
    try {
      container.get(List.class);
      fail("Expected exception not thrown.");
    } catch (IllegalArgumentException ex) {
      assertThat(ex.getMessage()).contains("java.util.List");
    }
  }

  @Test
  public void get_withProviderClass_shouldThrow() {
    try {
      container.get(StringBuffer.class);
      fail("Expected exception not thrown.");
    } catch (IllegalArgumentException ex) {
      assertThat(ex.getMessage()).contains("java.lang.StringBuffer");
    }
  }

  @Test
  public void get_withPublisher_shouldThrow() {
    try {
      container.get(Publisher.class);
      fail("Expected exception not thrown.");
    } catch (IllegalArgumentException ex) {
      assertThat(ex.getMessage()).contains("events.Publisher");
    }
  }

  @Test
  public void getProvider_withAllowedClass_shouldReturnAnInstanceOfThatClass() {
    StringBuffer sb = new StringBuffer();
    when(delegate.getProvider(StringBuffer.class)).thenReturn(new Lazy<>(sb));

    assertThat(container.getProvider(StringBuffer.class).get()).isSameAs(sb);
    verify(delegate).getProvider(StringBuffer.class);
  }

  @Test
  public void getProvider_withNotAllowedClass_shouldThrow() {
    try {
      container.getProvider(List.class);
      fail("Expected exception not thrown.");
    } catch (IllegalArgumentException ex) {
      assertThat(ex.getMessage()).contains("java.util.List");
    }
  }

  @Test
  public void getProvider_withDirectClass_shouldThrow() {
    try {
      container.getProvider(StringBuilder.class);
      fail("Expected exception not thrown.");
    } catch (IllegalArgumentException ex) {
      assertThat(ex.getMessage()).contains("java.lang.StringBuilder");
    }
  }

  @Test
  public void publish_withDeclaredEvent_shouldSucceed() {
    when(delegate.get(Publisher.class)).thenReturn(mockPublisher);
    Publisher publisher = publishingContainer.get(Publisher.class);

    Event<StringBuilder> event = new Event<>(StringBuilder.class, new StringBuilder());
    publisher.publish(event);

    verify(mockPublisher).publish(event);
  }

  @Test
  public void publish_withUndeclaredEvent_shouldThrow() {
    when(delegate.get(Publisher.class)).thenReturn(mockPublisher);
    Publisher publisher = publishingContainer.get(Publisher.class);

    Event<StringBuffer> event = new Event<>(StringBuffer.class, new StringBuffer());

    try {
      publisher.publish(event);
      fail("Expected exception not thrown.");
    } catch (IllegalArgumentException ex) {
      assertThat(ex.getMessage()).contains("java.lang.StringBuffer");
    }

    verify(mockPublisher, never()).publish(event);
  }
}
