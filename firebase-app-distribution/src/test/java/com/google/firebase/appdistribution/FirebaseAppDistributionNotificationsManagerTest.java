package com.google.firebase.appdistribution;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.content.pm.ApplicationInfoBuilder;
import androidx.test.core.content.pm.PackageInfoBuilder;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowPackageManager;

@RunWith(RobolectricTestRunner.class)
public class FirebaseAppDistributionNotificationsManagerTest {
  private static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  private static final String TEST_PROJECT_ID = "777777777777";

  private FirebaseAppDistributionNotificationsManager notificationsManager;
  private ShadowPackageManager shadowPackageManager;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    FirebaseApp.clearInstancesForTest();

    FirebaseApp firebaseApp =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApplicationId(TEST_APP_ID_1)
                .setProjectId(TEST_PROJECT_ID)
                .setApiKey(TEST_API_KEY)
                .build());

    shadowPackageManager =
        shadowOf(ApplicationProvider.getApplicationContext().getPackageManager());

    notificationsManager = new FirebaseAppDistributionNotificationsManager(firebaseApp);
  }

  @Test
  public void getSmallIcon_whenAppIconSet_usesAppIcon() {
    setupApplicationInfo(R.drawable.test_app_icon);
    int iconId = notificationsManager.getSmallIcon();

    assertEquals(R.drawable.test_app_icon, iconId);
  }

  @Test
  public void getSmallIcon_whenAppIconAdaptive_usesDefaultIcon() {
    setupApplicationInfo(R.drawable.test_adaptive_icon_foreground);
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
    PackageInfo packageInfo =
        PackageInfoBuilder.newBuilder()
            .setPackageName(ApplicationProvider.getApplicationContext().getPackageName())
            .setApplicationInfo(applicationInfo)
            .build();
    shadowPackageManager.installPackage(packageInfo);
  }
}
