// Copyright 2023 Google LLC
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

import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import androidx.annotation.NonNull;
import com.google.firebase.crashlytics.internal.metadata.UserMetadata;
import com.google.firebase.inject.Deferred;
import com.google.firebase.remoteconfig.interop.FirebaseRemoteConfigInterop;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class RemoteConfigDeferredProxyTest {

  @Mock private FirebaseRemoteConfigInterop interop;

  @Mock private UserMetadata userMetadata;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  public void testProviderProxyCallsThroughProvidedValue() {
    RemoteConfigDeferredProxy proxy =
        new RemoteConfigDeferredProxy(
            new Deferred<FirebaseRemoteConfigInterop>() {
              @Override
              public void whenAvailable(
                  @NonNull Deferred.DeferredHandler<FirebaseRemoteConfigInterop> handler) {
                handler.handle(() -> interop);
              }
            });
    proxy.setupListener(userMetadata);
    verify(interop).registerRolloutsStateSubscriber(eq("firebase"), anyObject());
  }
}
