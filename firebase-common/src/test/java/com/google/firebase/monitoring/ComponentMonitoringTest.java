// Copyright 2021 Google LLC
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

package com.google.firebase.monitoring;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentContainer;
import com.google.firebase.inject.Deferred;
import com.google.firebase.inject.Provider;
import com.google.firebase.internal.ObfuscationUtils;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ComponentMonitoringTest {
  private static final String STRING_COMPONENT_VALUE = "string";
  public static final String SDK_NAME = "test";
  public static final String SDK_VERSION = "version";
  private static final Component<?> VERSION_COMPONENT =
      LibraryVersionComponent.create(SDK_NAME, SDK_VERSION);

  private final Tracer mockTracer = mock(Tracer.class);
  private final TraceHandle mockTraceHandle = mock(TraceHandle.class);
  private final ComponentMonitoring componentMonitoring = new ComponentMonitoring(mockTracer);

  @Before
  public void setUp() {
    when(mockTracer.startTrace(anyString())).thenReturn(mockTraceHandle);
  }

  @Test
  public void registrar_withOnlyOneComponent_shouldNotGetInstrumented() {
    Component<String> component =
        Component.builder(String.class).factory(c -> STRING_COMPONENT_VALUE).build();
    List<Component<?>> processedComponents =
        componentMonitoring.processRegistrar(() -> Collections.singletonList(component));

    assertThat(processedComponents).containsExactly(component);
  }

  @Test
  public void registrar_withTwoNamedComponents_shouldNotGetInstrumented() {
    Component<String> component1 =
        Component.builder(String.class).factory(c -> STRING_COMPONENT_VALUE).build();
    Component<Integer> component2 = Component.builder(Integer.class).factory(c -> 42).build();
    List<Component<?>> processedComponents =
        componentMonitoring.processRegistrar(() -> ImmutableList.of(component1, component2));

    assertThat(processedComponents).containsExactly(component1, component2);
  }

  @Test
  public void registrar_whenVersionComponentIsNonTrivial_shouldNotGetInstrumented() {
    Component<String> component =
        Component.builder(String.class).factory(c -> STRING_COMPONENT_VALUE).build();
    Component<?> nonTrivialVersionComponent =
        LibraryVersionComponent.fromContext(SDK_NAME, c -> SDK_VERSION);
    List<Component<?>> processedComponents =
        componentMonitoring.processRegistrar(
            () -> ImmutableList.of(component, nonTrivialVersionComponent));

    assertThat(processedComponents).containsExactly(component, nonTrivialVersionComponent);
  }

  @Test
  public void registrar_withOneComponentAndAVersionComponent_shouldGetInstrumented() {
    Component<String> component =
        Component.builder(String.class).factory(c -> STRING_COMPONENT_VALUE).build();
    List<Component<?>> processedComponents =
        componentMonitoring.processRegistrar(() -> ImmutableList.of(component, VERSION_COMPONENT));

    assertThat(processedComponents).hasSize(2);
    assertThat(processedComponents).doesNotContain(component);
    assertThat(processedComponents).contains(VERSION_COMPONENT);

    Component<?> instrumentedComponent = processedComponents.get(0);
    assertThat(instrumentedComponent.getProvidedInterfaces()).containsExactly(String.class);

    Object result = instrumentedComponent.getFactory().create(EMPTY_CONTAINER);
    assertThat(result).isEqualTo(STRING_COMPONENT_VALUE);
    verify(mockTracer).startTrace(SDK_NAME);
    verify(mockTraceHandle).addAttribute("version", SDK_VERSION);
    verify(mockTraceHandle)
        .addAttribute("optimized", String.valueOf(ObfuscationUtils.isAppObfuscated()));
  }

  @Test
  public void registrar_withTwoComponentsAndVersionComponent_shouldInstrumentTheFirst() {
    Component<String> component1 =
        Component.builder(String.class).factory(c -> STRING_COMPONENT_VALUE).build();
    Component<Integer> component2 = Component.builder(Integer.class).factory(c -> 42).build();
    List<Component<?>> processedComponents =
        componentMonitoring.processRegistrar(
            () -> ImmutableList.of(component1, component2, VERSION_COMPONENT));

    assertThat(processedComponents).hasSize(3);
    assertThat(processedComponents).doesNotContain(component1);
    assertThat(processedComponents).contains(component2);
    assertThat(processedComponents).contains(VERSION_COMPONENT);
  }

  @Test
  public void
      registrar_withTwoComponentsAndSecondNamedAndVersionComponent_shouldInstrumentTheSecond() {
    Component<String> component1 =
        Component.builder(String.class).factory(c -> STRING_COMPONENT_VALUE).build();

    String componentName = "cmpName";
    Component<Integer> component2 =
        Component.builder(Integer.class).name(componentName).factory(c -> 42).build();
    List<Component<?>> processedComponents =
        componentMonitoring.processRegistrar(
            () -> ImmutableList.of(component1, component2, VERSION_COMPONENT));

    assertThat(processedComponents).hasSize(3);
    assertThat(processedComponents).contains(component1);
    assertThat(processedComponents).doesNotContain(component2);
    assertThat(processedComponents).contains(VERSION_COMPONENT);

    Component<?> instrumentedComponent = processedComponents.get(1);
    assertThat(instrumentedComponent.getProvidedInterfaces()).containsExactly(Integer.class);

    Object result = instrumentedComponent.getFactory().create(EMPTY_CONTAINER);
    assertThat(result).isEqualTo(42);
    verify(mockTracer).startTrace(componentName);
    verify(mockTraceHandle).addAttribute("version", SDK_VERSION);
    verify(mockTraceHandle)
        .addAttribute("optimized", String.valueOf(ObfuscationUtils.isAppObfuscated()));
  }

  private static final ComponentContainer EMPTY_CONTAINER =
      new ComponentContainer() {
        @Override
        public <T> T get(Class<T> anInterface) {
          return null;
        }

        @Override
        public <T> Provider<T> getProvider(Class<T> anInterface) {
          return null;
        }

        @Override
        public <T> Deferred<T> getDeferred(Class<T> anInterface) {
          return null;
        }

        @Override
        public <T> Set<T> setOf(Class<T> anInterface) {
          return null;
        }

        @Override
        public <T> Provider<Set<T>> setOfProvider(Class<T> anInterface) {
          return null;
        }
      };
}
