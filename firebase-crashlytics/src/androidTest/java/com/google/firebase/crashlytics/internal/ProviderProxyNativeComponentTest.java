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

package com.google.firebase.crashlytics.internal;

import static org.mockito.ArgumentMatchers.eq;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class ProviderProxyNativeComponentTest {
  private static final String TEST_STRING = "abc";
  private static final long TEST_LONG = 123;
  private static final boolean TEST_BOOLEAN = true;
  private static final int TEST_INT = 1234;

  @Mock private CrashlyticsNativeComponent component;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testProviderProxyCallsThroughProvidedValue() {
    ProviderProxyNativeComponent proxy = new ProviderProxyNativeComponent(() -> component);

    proxy.hasCrashDataForSession(TEST_STRING);
    Mockito.verify(component, Mockito.times(1)).hasCrashDataForSession(eq(TEST_STRING));

    proxy.openSession(TEST_STRING);
    Mockito.verify(component, Mockito.times(1)).openSession(eq(TEST_STRING));

    proxy.finalizeSession(TEST_STRING);
    Mockito.verify(component, Mockito.times(1)).finalizeSession(eq(TEST_STRING));

    proxy.getSessionFileProvider(TEST_STRING);
    Mockito.verify(component, Mockito.times(1)).getSessionFileProvider(eq(TEST_STRING));

    proxy.writeBeginSession(TEST_STRING, TEST_STRING, TEST_LONG);
    Mockito.verify(component, Mockito.times(1))
        .writeBeginSession(eq(TEST_STRING), eq(TEST_STRING), eq(TEST_LONG));

    proxy.writeSessionApp(
        TEST_STRING, TEST_STRING, TEST_STRING, TEST_STRING, TEST_STRING, TEST_INT, TEST_STRING);
    Mockito.verify(component, Mockito.times(1))
        .writeSessionApp(
            eq(TEST_STRING),
            eq(TEST_STRING),
            eq(TEST_STRING),
            eq(TEST_STRING),
            eq(TEST_STRING),
            eq(TEST_INT),
            eq(TEST_STRING));

    proxy.writeSessionOs(TEST_STRING, TEST_STRING, TEST_STRING, TEST_BOOLEAN);
    Mockito.verify(component, Mockito.times(1))
        .writeSessionOs(eq(TEST_STRING), eq(TEST_STRING), eq(TEST_STRING), eq(TEST_BOOLEAN));

    proxy.writeSessionDevice(
        TEST_STRING,
        TEST_INT,
        TEST_STRING,
        TEST_INT,
        TEST_LONG,
        TEST_LONG,
        TEST_BOOLEAN,
        TEST_INT,
        TEST_STRING,
        TEST_STRING);
    Mockito.verify(component, Mockito.times(1))
        .writeSessionDevice(
            eq(TEST_STRING),
            eq(TEST_INT),
            eq(TEST_STRING),
            eq(TEST_INT),
            eq(TEST_LONG),
            eq(TEST_LONG),
            eq(TEST_BOOLEAN),
            eq(TEST_INT),
            eq(TEST_STRING),
            eq(TEST_STRING));
  }
}
