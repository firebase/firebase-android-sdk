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

package com.google.firebase.app.distribution;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.content.pm.ApplicationInfoBuilder;
import com.google.firebase.FirebaseApp;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowPackageManager;

@RunWith(RobolectricTestRunner.class)
public class FirebaseAppDistributionNotificationsManagerTest {

  @Mock private Context mockContext;
  @Mock private FirebaseApp mockFirebaseApp;

  private FirebaseAppDistributionNotificationsManager notificationsManager;
  private ShadowPackageManager shadowPackageManager;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    FirebaseApp.clearInstancesForTest();

    when(mockFirebaseApp.getApplicationContext()).thenReturn(mockContext);
    shadowPackageManager =
        shadowOf(ApplicationProvider.getApplicationContext().getPackageManager());

    notificationsManager = new FirebaseAppDistributionNotificationsManager(mockFirebaseApp);
  }

  @Test
  public void getSmallIcon_whenAppIconSet_usesAppIcon() {
    setupApplicationInfo(R.drawable.test_app_icon);
    int iconId = notificationsManager.getSmallIcon();

    assertEquals(R.drawable.test_app_icon, iconId);
  }

  @Test
  public void getSmallIcon_whenAppIconAdaptive_usesDefaultIcon() {
    setupApplicationInfo(R.mipmap.test_adaptive_icon);
    ApplicationInfo info = mockContext.getApplicationInfo();
    // get correct drawable for ContextCompat.getDrawable in isAdaptiveIcon()
    when(mockContext.getDrawable(R.mipmap.test_adaptive_icon))
        .thenReturn(
            ApplicationProvider.getApplicationContext().getDrawable(R.mipmap.test_adaptive_icon));
    int iconId = notificationsManager.getSmallIcon();

    assertEquals(android.R.drawable.sym_def_app_icon, iconId);
  }

  @Test
  public void getSmallIcon_whenAppIconNotPresent_usesDefaultIcon() {
    setupApplicationInfo();
    int iconId = notificationsManager.getSmallIcon();

    assertEquals(android.R.drawable.sym_def_app_icon, iconId);
  }

  private void setupApplicationInfo() {
    setupApplicationInfo(0);
  }

  private void setupApplicationInfo(int iconId) {
    ApplicationInfo applicationInfo =
        ApplicationInfoBuilder.newBuilder()
            .setPackageName(ApplicationProvider.getApplicationContext().getPackageName())
            .build();
    applicationInfo.metaData = new Bundle();
    applicationInfo.icon = iconId;
    when(mockContext.getApplicationInfo()).thenReturn(applicationInfo);
  }
}
