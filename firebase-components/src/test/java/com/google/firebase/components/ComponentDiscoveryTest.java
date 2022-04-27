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
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.firebase.components.ComponentDiscovery.RegistrarNameRetriever;
import com.google.firebase.inject.Provider;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ComponentDiscoveryTest {
  static class TestRegistrar1 implements ComponentRegistrar {
    @Override
    public List<Component<?>> getComponents() {
      return Collections.emptyList();
    }
  }

  static class TestRegistrar2 implements ComponentRegistrar {
    @Override
    public List<Component<?>> getComponents() {
      return Collections.emptyList();
    }
  }

  static class InvalidRegistrar {}

  private static class PrivateTestRegistrar implements ComponentRegistrar {
    @Override
    public List<Component<?>> getComponents() {
      return Collections.emptyList();
    }
  }

  static class NoDefaultCtorRegistrar implements ComponentRegistrar {
    NoDefaultCtorRegistrar(String unused) {}

    @Override
    public List<Component<?>> getComponents() {
      return Collections.emptyList();
    }
  }

  private static class MockContext {}

  private final MockContext context = new MockContext();

  @SuppressWarnings("unchecked")
  private final RegistrarNameRetriever<MockContext> retriever = mock(RegistrarNameRetriever.class);

  private final ComponentDiscovery<MockContext> discovery =
      new ComponentDiscovery<>(context, retriever);

  @Test
  public void discover_shouldCorrectlyInstantiateValidComponentRegistrars() {
    when(retriever.retrieve(context))
        .thenReturn(Arrays.asList(TestRegistrar1.class.getName(), TestRegistrar2.class.getName()));

    List<ComponentRegistrar> result = discovery.discover();

    assertThat(result).hasSize(2);
    ComponentRegistrar registrar;

    registrar = result.get(0);
    assertThat(registrar).isInstanceOf(TestRegistrar1.class);

    registrar = result.get(1);
    assertThat(registrar).isInstanceOf(TestRegistrar2.class);
  }

  @Test
  public void discover_shouldSkipClassesThatDontImplementComponentRegistrarInterface() {
    when(retriever.retrieve(context))
        .thenReturn(Collections.singletonList(InvalidRegistrar.class.getName()));

    assertThat(discovery.discover()).isEmpty();
  }

  @Test
  public void discover_shouldSkipNonExistentClasses() {
    when(retriever.retrieve(context))
        .thenReturn(Collections.singletonList("com.example.DoesNotExistClass"));

    assertThat(discovery.discover()).isEmpty();
  }

  @Test
  public void discover_shouldSkipPrivateClasses() {
    when(retriever.retrieve(context))
        .thenReturn(Collections.singletonList(PrivateTestRegistrar.class.getName()));

    assertThat(discovery.discover()).isEmpty();
  }

  @Test
  public void discover_shouldSkipClassesWithNoDefaultConstructors() {
    when(retriever.retrieve(context))
        .thenReturn(Collections.singletonList(NoDefaultCtorRegistrar.class.getName()));

    assertThat(discovery.discover()).isEmpty();
  }

  @Test
  public void discoverLazy_whenRegistrarClassDoesNotExist_shouldReturnProviderThatReturnsNull() {
    when(retriever.retrieve(context))
        .thenReturn(Collections.singletonList("com.example.DoesNotExistClass"));

    List<Provider<ComponentRegistrar>> result = discovery.discoverLazy();
    assertThat(result).hasSize(1);
    Provider<ComponentRegistrar> provider = result.get(0);
    assertThat(provider.get()).isNull();
  }

  @Test
  public void discoverLazy_whenRegistrarClassesAreInvalid_shouldReturnThrowingProviders() {
    when(retriever.retrieve(context))
        .thenReturn(
            Arrays.asList(
                InvalidRegistrar.class.getName(),
                PrivateTestRegistrar.class.getName(),
                NoDefaultCtorRegistrar.class.getName()));

    List<Provider<ComponentRegistrar>> result = discovery.discoverLazy();
    assertThat(result).hasSize(3);
    for (Provider<ComponentRegistrar> provider : result) {
      assertThrows(InvalidRegistrarException.class, provider::get);
    }
  }
}
