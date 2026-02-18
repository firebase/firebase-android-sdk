// Copyright 2024 Google LLC
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
package com.google.firebase.messaging;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Build.VERSION_CODES;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Tasks;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Robolectric tests for ProxyNotificationPreferences. */
@RunWith(RobolectricTestRunner.class)
public class ProxyNotificationPreferencesRoboTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  private Context context;
  @Mock private GmsRpc mockGmsRpc;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    when(mockGmsRpc.setRetainProxiedNotifications(anyBoolean())).thenReturn(Tasks.forResult(null));
  }

  @Test
  @Config(sdk = VERSION_CODES.Q)
  public void setProxyRetention() {
    ProxyNotificationPreferences.setProxyRetention(context, mockGmsRpc, true);

    verify(mockGmsRpc).setRetainProxiedNotifications(true);
  }

  @Test
  @Config(sdk = VERSION_CODES.Q)
  public void setProxyRetention_sameTwiceSetOnce() {
    ProxyNotificationPreferences.setProxyRetention(context, mockGmsRpc, true);
    ProxyNotificationPreferences.setProxyRetention(context, mockGmsRpc, true);

    verify(mockGmsRpc).setRetainProxiedNotifications(true);
  }

  @Test
  @Config(sdk = VERSION_CODES.Q)
  public void setProxyRetention_differentChanges() {
    ProxyNotificationPreferences.setProxyRetention(context, mockGmsRpc, true);
    ProxyNotificationPreferences.setProxyRetention(context, mockGmsRpc, false);

    verify(mockGmsRpc).setRetainProxiedNotifications(true);
    verify(mockGmsRpc).setRetainProxiedNotifications(false);
  }

  @Test
  @Config(sdk = VERSION_CODES.P)
  public void setProxyRetention_preQ() {
    ProxyNotificationPreferences.setProxyRetention(context, mockGmsRpc, true);

    verify(mockGmsRpc, never()).setRetainProxiedNotifications(anyBoolean());
  }
}
