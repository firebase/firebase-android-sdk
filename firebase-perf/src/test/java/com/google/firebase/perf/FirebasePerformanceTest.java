// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.datatransport.TransportFactory;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.config.DeviceCacheManager;
import com.google.firebase.perf.config.RemoteConfigManager;
import com.google.firebase.perf.session.SessionManager;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.ImmutableBundle;
import com.google.firebase.remoteconfig.RemoteConfigComponent;
import com.google.testing.timing.FakeDirectExecutorService;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowPackageManager;

/** Unit tests for {@link FirebasePerformance}. */
@RunWith(RobolectricTestRunner.class)
public class FirebasePerformanceTest {

  // Not using the ones from Constants.java so that this test will fail if we accidentally change
  // them.
  private static final String FIREPERF_FORCE_DEACTIVATED_KEY =
      "firebase_performance_collection_deactivated";
  private static final String FIREPERF_ENABLED_KEY = "firebase_performance_collection_enabled";

  @Nullable private RemoteConfigManager spyRemoteConfigManager = null;
  @Nullable private ConfigResolver spyConfigResolver = null;

  @Nullable private SessionManager spySessionManager = null;

  @Rule public MockitoRule initRule = MockitoJUnit.rule();

  private FakeDirectExecutorService fakeDirectExecutorService;

  @Before
  public void setUp() throws NameNotFoundException {
    FirebaseOptions options =
        new FirebaseOptions.Builder()
            .setApplicationId("1:149208680807:android:0000000000000000")
            .setApiKey("AIzaSyBcE-OOIbhjyR83gm4r2MFCu4MJmprNXsw")
            .setProjectId("fir-perftestapp")
            .build();
    FirebaseApp.clearInstancesForTest();

    Context context = ApplicationProvider.getApplicationContext();
    ShadowPackageManager shadowPackageManager = shadowOf(context.getPackageManager());

    PackageInfo packageInfo =
        shadowPackageManager.getInternalMutablePackageInfo(context.getPackageName());
    packageInfo.versionName = "1.0.0";

    packageInfo.applicationInfo.metaData.clear();

    FirebaseApp.initializeApp(context, options);
    for (int i = 0; i <= Constants.MAX_TRACE_CUSTOM_ATTRIBUTES; i++) {
      FirebasePerformance.getInstance().removeAttribute("dim" + i);
    }
    FirebaseApp.getInstance().setDataCollectionDefaultEnabled(true);
    SharedPreferences sharedPreferences = getSharedPreferences();
    sharedPreferences.edit().clear().commit();
    DeviceCacheManager.clearInstance();

    spyRemoteConfigManager = spy(RemoteConfigManager.getInstance());
    ConfigResolver.clearInstance();
    spyConfigResolver = spy(ConfigResolver.getInstance());

    spySessionManager = spy(SessionManager.getInstance());
    fakeDirectExecutorService = new FakeDirectExecutorService();
  }

  @After
  public void tearDownFirebaseApp() {
    FirebaseApp.clearInstancesForTest();
  }

  @Test
  public void testNoManifestNoSharedPrefs() throws NameNotFoundException {
    FirebasePerformance performance =
        initializeFirebasePerformancePreferences(
            /* metadataFireperfForceDeactivatedKey= */ null,
            /* metadataFireperfEnabledKey= */ null,
            /* sharedPreferencesEnabledDisabledKey= */ null);
    assertThat(performance.getPerformanceCollectionForceEnabledState()).isNull();
  }

  @Test
  public void testPermanentManifestDisabled() throws NameNotFoundException {
    FirebasePerformance performance =
        initializeFirebasePerformancePreferences(
            /* metadataFireperfForceDeactivatedKey= */ true,
            /* metadataFireperfEnabledKey= */ null,
            /* sharedPreferencesEnabledDisabledKey= */ null);
    assertThat(performance.getPerformanceCollectionForceEnabledState()).isFalse();
  }

  @Test
  public void testBothManifestsConflicting() throws NameNotFoundException {
    FirebasePerformance performance =
        initializeFirebasePerformancePreferences(
            /* metadataFireperfForceDeactivatedKey= */ true,
            /* metadataFireperfEnabledKey= */ true,
            /* sharedPreferencesEnabledDisabledKey= */ null);
    assertThat(performance.getPerformanceCollectionForceEnabledState()).isFalse();
  }

  @Test
  public void testBothManifestsAgree() throws NameNotFoundException {
    FirebasePerformance performance =
        initializeFirebasePerformancePreferences(
            /* metadataFireperfForceDeactivatedKey= */ true,
            /* metadataFireperfEnabledKey= */ false,
            /* sharedPreferencesEnabledDisabledKey= */ null);
    assertThat(performance.getPerformanceCollectionForceEnabledState()).isFalse();
  }

  @Test
  public void testTempManifestDisabled() throws NameNotFoundException {
    FirebasePerformance performance =
        initializeFirebasePerformancePreferences(
            /* metadataFireperfForceDeactivatedKey= */ null,
            /* metadataFireperfEnabledKey= */ false,
            /* sharedPreferencesEnabledDisabledKey= */ null);
    assertThat(performance.getPerformanceCollectionForceEnabledState()).isFalse();
  }

  @Test
  public void testTempManifestEnabled() throws NameNotFoundException {
    FirebasePerformance performance =
        initializeFirebasePerformancePreferences(
            /* metadataFireperfForceDeactivatedKey= */ null,
            /* metadataFireperfEnabledKey= */ true,
            /* sharedPreferencesEnabledDisabledKey= */ null);
    assertThat(performance.getPerformanceCollectionForceEnabledState()).isTrue();
  }

  @Test
  public void testTempManifestDisabledSharedPrefsDisabled() throws NameNotFoundException {
    FirebasePerformance performance =
        initializeFirebasePerformancePreferences(
            /* metadataFireperfForceDeactivatedKey= */ null,
            /* metadataFireperfEnabledKey= */ false,
            /* sharedPreferencesEnabledDisabledKey= */ false);
    assertThat(performance.getPerformanceCollectionForceEnabledState()).isFalse();
  }

  @Test
  public void testTempManifestDisabledSharedPrefsEnabled() throws NameNotFoundException {
    FirebasePerformance performance =
        initializeFirebasePerformancePreferences(
            /* metadataFireperfForceDeactivatedKey= */ null,
            /* metadataFireperfEnabledKey= */ false,
            /* sharedPreferencesEnabledDisabledKey= */ true);
    assertThat(performance.getPerformanceCollectionForceEnabledState()).isTrue();
  }

  @Test
  public void testSharedPrefsEnabled() throws NameNotFoundException {
    FirebasePerformance performance =
        initializeFirebasePerformancePreferences(
            /* metadataFireperfForceDeactivatedKey= */ null,
            /* metadataFireperfEnabledKey= */ null,
            /* sharedPreferencesEnabledDisabledKey= */ true);

    assertThat(performance.getPerformanceCollectionForceEnabledState()).isTrue();
  }

  @Test
  public void testSharedPrefsDisabled() throws NameNotFoundException {
    FirebasePerformance performance =
        initializeFirebasePerformancePreferences(
            /* metadataFireperfForceDeactivatedKey= */ null,
            /* metadataFireperfEnabledKey= */ null,
            /* sharedPreferencesEnabledDisabledKey= */ false);

    assertThat(performance.getPerformanceCollectionForceEnabledState()).isFalse();
  }

  @Test
  public void testSharedPrefsDisabledThenCleared() throws NameNotFoundException {
    FirebasePerformance performance =
        initializeFirebasePerformancePreferences(
            /* metadataFireperfForceDeactivatedKey= */ null,
            /* metadataFireperfEnabledKey= */ null,
            /* sharedPreferencesEnabledDisabledKey= */ false);

    assertThat(performance.getPerformanceCollectionForceEnabledState()).isFalse();

    performance.setPerformanceCollectionEnabled(null);
    // The value should be null now. Since there is no metadata
    assertThat(performance.getPerformanceCollectionForceEnabledState()).isNull();
  }

  @Test
  public void testEnableWithPermanentManifestEnabled() throws NameNotFoundException {
    FirebasePerformance performance =
        initializeFirebasePerformancePreferences(
            /* metadataFireperfForceDeactivatedKey= */ true,
            /* metadataFireperfEnabledKey= */ null,
            /* sharedPreferencesEnabledDisabledKey= */ false);

    performance.setPerformanceCollectionEnabled(true);
    assertThat(performance.getPerformanceCollectionForceEnabledState()).isFalse();
  }

  @Test
  public void testEnableWithPermanentManifestDisabled() throws NameNotFoundException {
    FirebasePerformance performance =
        initializeFirebasePerformancePreferences(
            /* metadataFireperfForceDeactivatedKey= */ false,
            /* metadataFireperfEnabledKey= */ null,
            /* sharedPreferencesEnabledDisabledKey= */ true);

    performance.setPerformanceCollectionEnabled(true);
    assertThat(performance.getPerformanceCollectionForceEnabledState()).isTrue();
  }

  @Test
  public void testDisableWithPermanentManifestDisabled() throws NameNotFoundException {
    FirebasePerformance performance =
        initializeFirebasePerformancePreferences(
            /* metadataFireperfForceDeactivatedKey= */ false,
            /* metadataFireperfEnabledKey= */ null,
            /* sharedPreferencesEnabledDisabledKey= */ false);

    performance.setPerformanceCollectionEnabled(false);
    assertThat(performance.getPerformanceCollectionForceEnabledState()).isFalse();
  }

  @Test
  public void testPermanentManifestNotDisabledAndNoSharedPrefs() throws NameNotFoundException {
    FirebasePerformance performance =
        initializeFirebasePerformancePreferences(
            /* metadataFireperfForceDeactivatedKey= */ false,
            /* metadataFireperfEnabledKey= */ null,
            /* sharedPreferencesEnabledDisabledKey= */ null);
    assertThat(performance.getPerformanceCollectionForceEnabledState()).isNull();
  }

  @Test
  public void testPerformanceMonitoringRespectsGlobalDataFlagWhenNotForceEnabledOrDisabled()
      throws NameNotFoundException {
    FirebaseApp.getInstance().setDataCollectionDefaultEnabled(false);
    FirebasePerformance firebasePerformance = FirebasePerformance.getInstance();

    assertThat(firebasePerformance.isPerformanceCollectionEnabled()).isFalse();

    FirebaseApp.getInstance().setDataCollectionDefaultEnabled(true);
    assertThat(firebasePerformance.isPerformanceCollectionEnabled()).isTrue();
  }

  @Test
  public void testPerformanceMonitoringIsEnabledWhenForceEnabled() throws NameNotFoundException {
    FirebaseApp.getInstance().setDataCollectionDefaultEnabled(false);
    FirebasePerformance firebasePerformance = FirebasePerformance.getInstance();
    firebasePerformance.setPerformanceCollectionEnabled(true);

    assertThat(firebasePerformance.isPerformanceCollectionEnabled()).isTrue();

    FirebaseApp.getInstance().setDataCollectionDefaultEnabled(true);
    assertThat(firebasePerformance.isPerformanceCollectionEnabled()).isTrue();
  }

  @Test
  public void setDataCollectionDefaultEnabled_whenForceEnabledThenCleared_respectsGlobalFlag()
      throws NameNotFoundException {
    FirebaseApp.getInstance().setDataCollectionDefaultEnabled(false);
    FirebasePerformance firebasePerformance = FirebasePerformance.getInstance();
    firebasePerformance.setPerformanceCollectionEnabled(true);

    assertThat(firebasePerformance.isPerformanceCollectionEnabled()).isTrue();

    firebasePerformance.setPerformanceCollectionEnabled(null);
    assertThat(firebasePerformance.isPerformanceCollectionEnabled()).isFalse();
  }

  @Test
  public void setDataCollectionDefaultEnabled_whenForceEnabledThenCleared_respectsManifestFalse()
      throws NameNotFoundException {
    FirebasePerformance firebasePerformance =
        initializeFirebasePerformancePreferences(
            /* metadataFireperfForceDeactivatedKey= */ null,
            /* metadataFireperfEnabledKey= */ false,
            /* sharedPreferencesEnabledDisabledKey= */ null);
    firebasePerformance.setPerformanceCollectionEnabled(true);
    assertThat(firebasePerformance.isPerformanceCollectionEnabled()).isTrue();
    firebasePerformance.setPerformanceCollectionEnabled(null);
    assertThat(firebasePerformance.isPerformanceCollectionEnabled()).isFalse();
  }

  @Test
  public void setDataCollectionDefaultEnabled_whenForceDisabledThenCleared_respectsManifestTrue()
      throws NameNotFoundException {
    FirebasePerformance firebasePerformance =
        initializeFirebasePerformancePreferences(
            /* metadataFireperfForceDeactivatedKey= */ null,
            /* metadataFireperfEnabledKey= */ true,
            /* sharedPreferencesEnabledDisabledKey= */ null);
    firebasePerformance.setPerformanceCollectionEnabled(false);
    assertThat(firebasePerformance.isPerformanceCollectionEnabled()).isFalse();
    firebasePerformance.setPerformanceCollectionEnabled(null);
    assertThat(firebasePerformance.isPerformanceCollectionEnabled()).isTrue();
  }

  @Test
  public void setDataCollectionDefaultEnabled_whenForceDisabledThenCleared_respectsGlobalFlag()
      throws NameNotFoundException {
    FirebaseApp.getInstance().setDataCollectionDefaultEnabled(true);
    FirebasePerformance firebasePerformance = FirebasePerformance.getInstance();
    firebasePerformance.setPerformanceCollectionEnabled(false);

    assertThat(firebasePerformance.isPerformanceCollectionEnabled()).isFalse();

    firebasePerformance.setPerformanceCollectionEnabled(null);
    assertThat(firebasePerformance.isPerformanceCollectionEnabled()).isTrue();
  }

  @Test
  public void testPerformanceMonitoringIsDisabledWhenForceDisabled() throws NameNotFoundException {
    FirebaseApp.getInstance().setDataCollectionDefaultEnabled(true);
    FirebasePerformance firebasePerformance = FirebasePerformance.getInstance();
    firebasePerformance.setPerformanceCollectionEnabled(false);

    assertThat(firebasePerformance.isPerformanceCollectionEnabled()).isFalse();

    FirebaseApp.getInstance().setDataCollectionDefaultEnabled(false);
    assertThat(firebasePerformance.isPerformanceCollectionEnabled()).isFalse();
  }

  @Test
  public void testGetAttributesReturnsCopyOfUnderlyingMap() {
    FirebasePerformance.getInstance().putAttribute("dim1", "value1");
    Map<String, String> attributes = FirebasePerformance.getInstance().getAttributes();
    attributes.put("dim2", "values");
    Assert.assertNull(FirebasePerformance.getInstance().getAttribute("dim2"));
    Assert.assertEquals("value1", FirebasePerformance.getInstance().getAttribute("dim1"));
  }

  @Test
  public void testRemovingNonExistingAttributeAttribute() {
    FirebasePerformance.getInstance().putAttribute("dim1", "value1");
    FirebasePerformance.getInstance().removeAttribute("dim2");
    Assert.assertEquals("value1", FirebasePerformance.getInstance().getAttribute("dim1"));
  }

  @Test
  public void testRemovingExistingAndAddingUpdatedValue() {
    FirebasePerformance.getInstance().putAttribute("dim1", "value1");
    FirebasePerformance.getInstance().removeAttribute("dim1");
    FirebasePerformance.getInstance().putAttribute("dim1", "value2");
    Assert.assertEquals("value2", FirebasePerformance.getInstance().getAttribute("dim1"));
  }

  @Test
  public void testAddingMoreThanMaxLocalAttributes() {
    for (int i = 0; i <= Constants.MAX_TRACE_CUSTOM_ATTRIBUTES; i++) {
      FirebasePerformance.getInstance().putAttribute("dim" + i, "value" + i);
    }
    Assert.assertEquals(
        Constants.MAX_TRACE_CUSTOM_ATTRIBUTES,
        FirebasePerformance.getInstance().getAttributes().size());
    for (int i = 0; i < Constants.MAX_TRACE_CUSTOM_ATTRIBUTES; i++) {
      String attributeValue = "value" + i;
      String attributeKey = "dim" + i;
      Assert.assertEquals(
          attributeValue, FirebasePerformance.getInstance().getAttribute(attributeKey));
    }
    FirebasePerformance.getInstance().putAttribute("dim" + 0, "value" + 999);
    Assert.assertEquals("value" + 999, FirebasePerformance.getInstance().getAttribute("dim" + 0));
  }

  @Test
  public void testLongNameGetsIgnored() {
    char[] underscores = new char[Constants.MAX_TRACE_ID_LENGTH];
    for (int i = 0; i < underscores.length; i++) {
      underscores[i] = '_';
    }
    String dimName = "dim" + String.valueOf(underscores);
    FirebasePerformance.getInstance().putAttribute(dimName, "value1");
    Assert.assertNull(FirebasePerformance.getInstance().getAttribute(dimName));
    Assert.assertEquals(0, FirebasePerformance.getInstance().getAttributes().size());
  }

  @Test
  public void testLongValueGetsIgnored() {
    char[] underscores = new char[Constants.MAX_TRACE_ID_LENGTH];
    for (int i = 0; i < underscores.length; i++) {
      underscores[i] = '_';
    }
    String valueString = "value" + String.valueOf(underscores);
    FirebasePerformance.getInstance().putAttribute("dim", valueString);
    Assert.assertNull(FirebasePerformance.getInstance().getAttribute("dim"));
    Assert.assertEquals(0, FirebasePerformance.getInstance().getAttributes().size());
  }

  @Test
  public void testInvalidAttributeKeysAreIgnored() {
    FirebasePerformance.getInstance().putAttribute("_dim", "value1");
    Assert.assertNull(FirebasePerformance.getInstance().getAttribute("_dim"));
    Assert.assertEquals(0, FirebasePerformance.getInstance().getAttributes().size());

    FirebasePerformance.getInstance().putAttribute("0_dim", "value1");
    Assert.assertNull(FirebasePerformance.getInstance().getAttribute("0_dim"));
    Assert.assertEquals(0, FirebasePerformance.getInstance().getAttributes().size());

    FirebasePerformance.getInstance().putAttribute("google_dim", "value1");
    Assert.assertNull(FirebasePerformance.getInstance().getAttribute("google_dim"));
    Assert.assertEquals(0, FirebasePerformance.getInstance().getAttributes().size());

    FirebasePerformance.getInstance().putAttribute("firebase_dim", "value1");
    Assert.assertNull(FirebasePerformance.getInstance().getAttribute("firebase_dim"));
    Assert.assertEquals(0, FirebasePerformance.getInstance().getAttributes().size());

    FirebasePerformance.getInstance().putAttribute("ga_dim", "value1");
    Assert.assertNull(FirebasePerformance.getInstance().getAttribute("ga_dim"));
    Assert.assertEquals(0, FirebasePerformance.getInstance().getAttributes().size());
  }

  @Test
  public void firebasePerformanceInitialization_providesRcProvider_remoteConfigManagerIsSet()
      throws NameNotFoundException {
    Provider<RemoteConfigComponent> firebaseRemoteConfigProvider =
        () -> FirebaseApp.getInstance().get(RemoteConfigComponent.class);

    FirebasePerformance unusedPerformance =
        initializeFirebasePerformancePreferences(
            /* metadataFireperfForceDeactivatedKey= */ null,
            /* metadataFireperfEnabledKey= */ null,
            /* sharedPreferencesEnabledDisabledKey= */ null,
            firebaseRemoteConfigProvider,
            () -> FirebaseApp.getInstance().get(TransportFactory.class));

    verify(spyRemoteConfigManager).setFirebaseRemoteConfigProvider(firebaseRemoteConfigProvider);
  }

  @Test
  public void initializeFirebasePerformance_emptyMetadataAndCache_metadataAndContextInjected()
      throws NameNotFoundException {
    FirebasePerformance unusedPerformance =
        initializeFirebasePerformancePreferences(
            /* metadataFireperfForceDeactivatedKey= */ null,
            /* metadataFireperfEnabledKey= */ null,
            /* sharedPreferencesEnabledDisabledKey= */ null);

    verify(spyConfigResolver).setMetadataBundle(nullable(ImmutableBundle.class));
    verify(spyConfigResolver).setApplicationContext(nullable(Context.class));
  }

  @Test
  public void initFirebasePerformance_injectsMetadataIntoConfigResolver()
      throws NameNotFoundException {
    FirebasePerformance unusedPerformance =
        initializeFirebasePerformancePreferences(
            /* metadataFireperfForceDeactivatedKey= */ null,
            /* metadataFireperfEnabledKey= */ null,
            /* sharedPreferencesEnabledDisabledKey= */ null);

    verify(spyConfigResolver).setMetadataBundle(nullable(ImmutableBundle.class));
  }

  @Test
  public void testFirebasePerformanceInitializationInjectsContextIntoSessionManager()
      throws NameNotFoundException {
    FirebasePerformance unusedPerformance =
        initializeFirebasePerformancePreferences(
            /* metadataFireperfForceDeactivatedKey= */ null,
            /* metadataFireperfEnabledKey= */ null,
            /* sharedPreferencesEnabledDisabledKey= */ null);

    verify(spySessionManager).setApplicationContext(nullable(Context.class));
  }

  private static SharedPreferences getSharedPreferences() {
    return ApplicationProvider.getApplicationContext()
        .getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
  }

  private FirebasePerformance initializeFirebasePerformancePreferences(
      Boolean metadataFireperfForceDeactivatedKey,
      Boolean metadataFireperfEnabledKey,
      Boolean sharedPreferencesEnabledDisabledKey) {

    return initializeFirebasePerformancePreferences(
        metadataFireperfForceDeactivatedKey,
        metadataFireperfEnabledKey,
        sharedPreferencesEnabledDisabledKey,
        () -> FirebaseApp.getInstance().get(RemoteConfigComponent.class),
        () -> FirebaseApp.getInstance().get(TransportFactory.class));
  }

  /**
   * Creates a FirebasePerformance instance in a way that imitates the given manifest metadata or
   * sharedPreferences are present.
   *
   * @param metadataFireperfForceDeactivatedKey The value for the metadata key {@link
   *     #FIREPERF_FORCE_DEACTIVATED_KEY}. If null is passed, this key value pair will not be added
   *     to the bundle.
   * @param metadataFireperfEnabledKey The value for the metadata key {@link #FIREPERF_ENABLED_KEY}.
   *     If null is passed, this key value pair will not be added to the bundle.
   * @param sharedPreferencesEnabledDisabledKey The value for the sharedPreferences key {@link
   *     Constants#ENABLE_DISABLE}. If null is passed, this key value pair will not be added to the
   *     sharedPreferences.
   * @param firebaseRemoteConfigProvider The {@link Provider} for FirebaseRemoteConfig instance.
   * @return The FirebasePerformance instance initialized as if the given manifest metadata or
   *     sharedPreferences are present.
   */
  private FirebasePerformance initializeFirebasePerformancePreferences(
      Boolean metadataFireperfForceDeactivatedKey,
      Boolean metadataFireperfEnabledKey,
      Boolean sharedPreferencesEnabledDisabledKey,
      Provider<RemoteConfigComponent> firebaseRemoteConfigProvider,
      Provider<TransportFactory> transportFactoryProvider) {
    Context context = ApplicationProvider.getApplicationContext();
    DeviceCacheManager deviceCacheManager = new DeviceCacheManager(fakeDirectExecutorService);
    deviceCacheManager.setContext(context);
    if (sharedPreferencesEnabledDisabledKey != null) {
      deviceCacheManager.setValue(Constants.ENABLE_DISABLE, sharedPreferencesEnabledDisabledKey);
    }
    spyConfigResolver.setDeviceCacheManager(deviceCacheManager);

    Bundle bundle = new Bundle();
    if (metadataFireperfEnabledKey != null) {
      bundle.putBoolean(FIREPERF_ENABLED_KEY, metadataFireperfEnabledKey);
    }

    if (metadataFireperfForceDeactivatedKey != null) {
      bundle.putBoolean(FIREPERF_FORCE_DEACTIVATED_KEY, metadataFireperfForceDeactivatedKey);
    }

    shadowOf(context.getPackageManager())
        .getInternalMutablePackageInfo(context.getPackageName())
        .applicationInfo
        .metaData
        .putAll(bundle);

    return new FirebasePerformance(
        FirebaseApp.getInstance(),
        firebaseRemoteConfigProvider,
        mock(FirebaseInstallationsApi.class),
        transportFactoryProvider,
        spyRemoteConfigManager,
        spyConfigResolver,
        spySessionManager);
  }
}
