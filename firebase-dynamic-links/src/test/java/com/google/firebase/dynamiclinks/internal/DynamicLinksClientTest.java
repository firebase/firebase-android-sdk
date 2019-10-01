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

package com.google.firebase.dynamiclinks.internal;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;

import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.internal.ClientSettings;
import com.google.firebase.dynamiclinks.internal.FirebaseDynamicLinksImpl.DynamicLinkCallbacks;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class DynamicLinksClientTest {

  private static final String REAL_CLIENT_PACKAGE_NAME = "real.client.package.name";
  private static final String DYNAMIC_LINK = "http://appcode.app.goo.gl/?link=http://deeplink.com";

  private static final GoogleApiClient.ConnectionCallbacks CONNECTED_LISTENER =
      new GoogleApiClient.ConnectionCallbacks() {

        @Override
        public void onConnected(@Nullable Bundle connectionHint) {}

        @Override
        public void onConnectionSuspended(int cause) {}
      };
  private static final GoogleApiClient.OnConnectionFailedListener CONNECTION_FAILED_LISTENER =
      new GoogleApiClient.OnConnectionFailedListener() {

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult result) {}
      };

  @Mock DynamicLinksClient client;
  @Mock IDynamicLinksService mockService;
  @Mock DynamicLinkCallbacks mockCallbacks;

  private Bundle parameters;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    parameters = new Bundle();
    ClientSettings clientSettings =
        new ClientSettings(null, null, null, 0, null, REAL_CLIENT_PACKAGE_NAME, null, null);

    client =
        new DynamicLinksClient(
            RuntimeEnvironment.application,
            Looper.getMainLooper(),
            clientSettings,
            CONNECTED_LISTENER,
            CONNECTION_FAILED_LISTENER);
  }

  @Test
  public void testGetStartServiceAction() {
    assertEquals(DynamicLinksClient.ACTION_START_SERVICE, client.getStartServiceAction());
  }

  @Test
  public void testGetServiceDescriptor() {
    assertEquals(DynamicLinksClient.SERVICE_DESCRIPTOR, client.getServiceDescriptor());
  }

  @Test
  public void testCreateServiceInterface() {
    IBinder mockBinder = Mockito.mock(IBinder.class);
    client.createServiceInterface(mockBinder);
  }

  @Test
  public void testGetDynamicLink_NotConnected() {
    try {
      client.getDynamicLink(mockCallbacks, DYNAMIC_LINK);
      fail("Expected IllegalStateException");
    } catch (IllegalStateException expected) {
      // Pass.
    }
  }

  @Test
  public void testCreateShortDynamicLink_NotConnected() {
    try {
      client.createShortDynamicLink(mockCallbacks, parameters);
      fail("Expected IllegalStateException");
    } catch (IllegalStateException expected) {
      // Pass.
    }
  }
}
