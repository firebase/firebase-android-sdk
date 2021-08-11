package com.google.firebase.appdistribution;

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
