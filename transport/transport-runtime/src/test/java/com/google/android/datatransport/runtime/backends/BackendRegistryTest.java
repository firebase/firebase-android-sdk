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

package com.google.android.datatransport.runtime.backends;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.datatransport.runtime.time.TestClock;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class BackendRegistryTest {

  private final CreationContextFactory creationContextFactory =
      new CreationContextFactory(
          ApplicationProvider.getApplicationContext(), new TestClock(1), new TestClock(2));
  private final BackendRegistry registry =
      new MetadataBackendRegistry(
          ApplicationProvider.getApplicationContext(), creationContextFactory);

  private final BackendFactory mockBackendFactory = mock(BackendFactory.class);
  private final BackendRegistry fakeRegistry =
      new MetadataBackendRegistry(
          new MetadataBackendRegistry.BackendFactoryProvider(
              ApplicationProvider.getApplicationContext()) {
            @Nullable
            @Override
            BackendFactory get(String name) {
              return mockBackendFactory;
            }
          },
          creationContextFactory);

  @Test
  public void get_withRegisteredBackendName_shouldReturnCorrectBackend() {
    assertThat(registry.get("testBackend")).isInstanceOf(TestBackendFactory.TestBackend.class);
  }

  @Test
  public void get_withUnknownBackend_name_shouldReturnNull() {
    assertThat(registry.get("unknown")).isNull();
  }

  @Test
  public void get_calls_transportFactory_withCorrectCreationContext() {
    fakeRegistry.get("testBackend");

    verify(mockBackendFactory).create(creationContextFactory.create("testBackend"));

    fakeRegistry.get("unknown");
    verify(mockBackendFactory).create(creationContextFactory.create("unknown"));
  }
}
