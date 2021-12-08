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
import static org.mockito.Mockito.mock;

import androidx.annotation.NonNull;
import androidx.test.runner.AndroidJUnit4;
import com.google.firebase.crashlytics.internal.model.StaticSessionData;
import com.google.firebase.inject.Deferred;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class CrashlyticsNativeComponentDeferredProxyTest {
  private static final String TEST_SESSION_ID = "abc";
  private static final String TEST_GENERATOR = "abc";
  private static final long TEST_START_TIME = 123;

  @Mock private CrashlyticsNativeComponent component;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testProviderProxyCallsThroughProvidedValue() {
    CrashlyticsNativeComponentDeferredProxy proxy =
        new CrashlyticsNativeComponentDeferredProxy(
            new Deferred<CrashlyticsNativeComponent>() {
              @Override
              public void whenAvailable(
                  @NonNull Deferred.DeferredHandler<CrashlyticsNativeComponent> handler) {
                handler.handle(() -> component);
              }
            });

    proxy.hasCrashDataForSession(TEST_SESSION_ID);
    Mockito.verify(component, Mockito.times(1)).hasCrashDataForSession(eq(TEST_SESSION_ID));

    StaticSessionData.AppData appData =
        StaticSessionData.AppData.create(
            "appId", "123", "1.2.3", "install_id", 0, mock(DevelopmentPlatformProvider.class));
    StaticSessionData.OsData osData = StaticSessionData.OsData.create("release", "codeName", false);
    StaticSessionData.DeviceData deviceData =
        StaticSessionData.DeviceData.create(
            0, "model", 1, 1000, 2000, false, 0, "manufacturer", "modelClass");
    StaticSessionData sessionData = StaticSessionData.create(appData, osData, deviceData);

    proxy.prepareNativeSession(TEST_SESSION_ID, TEST_GENERATOR, TEST_START_TIME, sessionData);
    Mockito.verify(component, Mockito.times(1))
        .prepareNativeSession(
            eq(TEST_SESSION_ID), eq(TEST_GENERATOR), eq(TEST_START_TIME), eq(sessionData));

    proxy.getSessionFileProvider(TEST_SESSION_ID);
    Mockito.verify(component, Mockito.times(1)).getSessionFileProvider(eq(TEST_SESSION_ID));
  }
}
