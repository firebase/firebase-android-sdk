// Copyright 2020 Google LLC
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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.testing.FirebaseIidRoboTestHelper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.robolectric.RobolectricTestRunner;

/** Robolectric test for the FirebaseInstanceIdService. */
@RunWith(RobolectricTestRunner.class)
public class SyncTaskConnectivityChangeReceveiverRoboTest {
  private Context context;
  private SyncTask syncTask;
  private SyncTask.ConnectivityChangeReceiver receiver;

  @Before
  public void setUp() {
    context = spy(ApplicationProvider.getApplicationContext());
    doReturn(context).when(context).getApplicationContext();

    // Clear static singleton instances
    FirebaseApp.clearInstancesForTest();

    syncTask = mock(SyncTask.class);
    doReturn(context).when(syncTask).getContext();
    receiver = new SyncTask.ConnectivityChangeReceiver(syncTask);

    FirebaseIidRoboTestHelper.addGmsCorePackageInfo();
  }

  @Test
  public void testReceviverRun_registerReceiver() throws Exception {
    receiver.registerReceiver();

    verify(context)
        .registerReceiver(
            eq(receiver),
            argThat(
                new ArgumentMatcher<IntentFilter>() {
                  @Override
                  public boolean matches(IntentFilter argument) {
                    IntentFilter filter = (IntentFilter) argument;
                    return filter.hasAction(ConnectivityManager.CONNECTIVITY_ACTION);
                  }
                }));
  }
}
