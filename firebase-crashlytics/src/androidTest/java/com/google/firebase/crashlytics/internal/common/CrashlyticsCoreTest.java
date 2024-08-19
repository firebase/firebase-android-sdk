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

package com.google.firebase.crashlytics.internal.common;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.SuccessContinuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.crashlytics.BuildConfig;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponentDeferredProxy;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.DevelopmentPlatformProvider;
import com.google.firebase.crashlytics.internal.RemoteConfigDeferredProxy;
import com.google.firebase.crashlytics.internal.analytics.UnavailableAnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbHandler;
import com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbSource;
import com.google.firebase.crashlytics.internal.breadcrumbs.DisabledBreadcrumbSource;
import com.google.firebase.crashlytics.internal.metadata.UserMetadata;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.firebase.crashlytics.internal.settings.Settings;
import com.google.firebase.crashlytics.internal.settings.SettingsController;
import com.google.firebase.crashlytics.internal.settings.TestSettings;
import com.google.firebase.inject.Deferred;
import com.google.firebase.installations.FirebaseInstallationsApi;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.mockito.Mockito;

public class CrashlyticsCoreTest extends CrashlyticsTestCase {

  private static final String GOOGLE_APP_ID = "google:app:id";

  private static final CrashlyticsNativeComponent MISSING_NATIVE_COMPONENT =
      new CrashlyticsNativeComponentDeferredProxy(
          new Deferred<CrashlyticsNativeComponent>() {
            @Override
            public void whenAvailable(
                @NonNull Deferred.DeferredHandler<CrashlyticsNativeComponent> handler) {
              // no-op
            }
          });

  private CrashlyticsCore crashlyticsCore;
  private BreadcrumbSource mockBreadcrumbSource;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mockBreadcrumbSource = mock(BreadcrumbSource.class);

    crashlyticsCore = appRestart();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testCustomAttributes() throws Exception {
    UserMetadata metadata = crashlyticsCore.getController().getUserMetadata();

    assertNull(metadata.getUserId());
    assertTrue(metadata.getCustomKeys().isEmpty());

    final String id = "id012345";
    crashlyticsCore.setUserId(id);

    assertEquals(id, metadata.getUserId());

    final StringBuffer idBuffer = new StringBuffer(id);
    while (idBuffer.length() < UserMetadata.MAX_ATTRIBUTE_SIZE) {
      idBuffer.append("0");
    }
    final String longId = idBuffer.toString();
    final String superLongId = longId + "more chars";

    crashlyticsCore.setUserId(superLongId);
    assertEquals(longId, metadata.getUserId());

    final String key1 = "key1";
    final String value1 = "value1";
    crashlyticsCore.setCustomKey(key1, value1);
    assertEquals(value1, metadata.getCustomKeys().get(key1));

    // Adding an existing key with the same value should return false
    assertFalse(metadata.setCustomKey(key1, value1));
    assertTrue(metadata.setCustomKey(key1, "someOtherValue"));
    assertTrue(metadata.setCustomKey(key1, value1));
    assertFalse(metadata.setCustomKey(key1, value1));

    final String longValue = longId.replaceAll("0", "x");
    final String superLongValue = longValue + "some more chars";

    // test truncation of custom keys and attributes
    crashlyticsCore.setCustomKey(superLongId, superLongValue);
    assertNull(metadata.getCustomKeys().get(superLongId));
    assertEquals(longValue, metadata.getCustomKeys().get(longId));

    // test the max number of attributes. We've already set 2.
    for (int i = 2; i < UserMetadata.MAX_ATTRIBUTES; ++i) {
      final String key = "key" + i;
      final String value = "value" + i;
      crashlyticsCore.setCustomKey(key, value);
      assertEquals(value, metadata.getCustomKeys().get(key));
    }
    // should be full now, extra key, value pairs will be dropped.
    final String key = "new key";
    crashlyticsCore.setCustomKey(key, "some value");
    assertFalse(metadata.getCustomKeys().containsKey(key));

    // should be able to update existing keys
    crashlyticsCore.setCustomKey(key1, longValue);
    assertEquals(longValue, metadata.getCustomKeys().get(key1));

    // when we set a key to null, it should still exist with an empty value
    crashlyticsCore.setCustomKey(key1, null);
    assertEquals("", metadata.getCustomKeys().get(key1));

    // keys and values are trimmed.
    crashlyticsCore.setCustomKey(" " + key1 + " ", " " + longValue + " ");
    assertTrue(metadata.getCustomKeys().containsKey(key1));
    assertEquals(longValue, metadata.getCustomKeys().get(key1));
  }

  public void testBulkCustomKeys() throws Exception {
    final double DELTA = 1e-15;

    UserMetadata metadata = crashlyticsCore.getController().getUserMetadata();

    final String stringKey = "string key";
    final String stringValue = "value1";
    final String trimmedKey = "trimmed key";
    final String trimmedValue = "trimmed value";

    final StringBuffer idBuffer = new StringBuffer("id012345");
    while (idBuffer.length() < UserMetadata.MAX_ATTRIBUTE_SIZE) {
      idBuffer.append("0");
    }
    final String longId = idBuffer.toString();
    final String superLongId = longId + "more chars";
    final String longStringValue = longId.replaceAll("0", "x");
    final String superLongValue = longStringValue + "some more chars";

    final String booleanKey = "boolean key";
    final Boolean booleanValue = true;

    final String doubleKey = "double key";
    final double doubleValue = 1.000000000000001;

    final String floatKey = "float key";
    final float floatValue = 2.000002f;

    final String longKey = "long key";
    final long longValue = 3;

    final String intKey = "int key";
    final int intValue = 4;

    Map<String, String> keysAndValues = new HashMap<>();
    keysAndValues.put(stringKey, stringValue);
    keysAndValues.put(" " + trimmedKey + " ", " " + trimmedValue + " ");
    keysAndValues.put(longId, longStringValue);
    keysAndValues.put(superLongId, superLongValue);
    keysAndValues.put(booleanKey, booleanValue.toString());
    keysAndValues.put(doubleKey, String.valueOf(doubleValue));
    keysAndValues.put(floatKey, String.valueOf(floatValue));
    keysAndValues.put(longKey, String.valueOf(longValue));
    keysAndValues.put(intKey, String.valueOf(intValue));

    crashlyticsCore.setCustomKeys(keysAndValues);

    assertEquals(stringValue, metadata.getCustomKeys().get(stringKey));
    assertEquals(trimmedValue, metadata.getCustomKeys().get(trimmedKey));
    assertEquals(longStringValue, metadata.getCustomKeys().get(longId));
    // Test truncation of custom keys and attributes
    assertNull(metadata.getCustomKeys().get(superLongId));
    assertTrue(Boolean.parseBoolean(metadata.getCustomKeys().get(booleanKey)));
    assertEquals(doubleValue, Double.parseDouble(metadata.getCustomKeys().get(doubleKey)), DELTA);
    assertEquals(floatValue, Float.parseFloat(metadata.getCustomKeys().get(floatKey)), DELTA);
    assertEquals(longValue, Long.parseLong(metadata.getCustomKeys().get(longKey)), DELTA);
    assertEquals(intValue, Integer.parseInt(metadata.getCustomKeys().get(intKey)), DELTA);

    // Add the max number of attributes (already set 8)
    Map<String, String> addlKeysAndValues = new HashMap<>();
    for (int i = 8; i < UserMetadata.MAX_ATTRIBUTES; ++i) {
      final String key = "key" + i;
      final String value = "value" + i;
      addlKeysAndValues.put(key, value);
    }
    crashlyticsCore.setCustomKeys(addlKeysAndValues);

    // Ensure all keys have been set
    assertEquals(UserMetadata.MAX_ATTRIBUTES, metadata.getCustomKeys().size(), DELTA);

    // Make sure the first MAX_ATTRIBUTES - 8 keys were set
    for (int i = 8; i < UserMetadata.MAX_ATTRIBUTES + 1; ++i) {
      final String key = "key" + i;
      final String value = "value" + i;
    }

    Map<String, String> extraKeysAndValues = new HashMap<>();
    for (int i = UserMetadata.MAX_ATTRIBUTES; i < UserMetadata.MAX_ATTRIBUTES + 10; ++i) {
      final String key = "key" + i;
      final String value = "value" + i;
      extraKeysAndValues.put(key, value);
    }
    crashlyticsCore.setCustomKeys(extraKeysAndValues);

    // Make sure the extra keys were not added
    for (int i = UserMetadata.MAX_ATTRIBUTES; i < UserMetadata.MAX_ATTRIBUTES + 10; ++i) {
      final String key = "key" + i;
      assertFalse(metadata.getCustomKeys().containsKey(key));
    }

    // Check updating existing keys and setting to null
    final String updatedStringValue = "string value 1";
    final boolean updatedBooleanValue = false;
    final double updatedDoubleValue = -1.000000000000001;
    final float updatedFloatValue = -2.000002f;
    final long updatedLongValue = -3;
    final int updatedIntValue = -4;

    Map<String, String> updatedKeysAndValues = new HashMap<>();
    updatedKeysAndValues.put(stringKey, updatedStringValue);
    updatedKeysAndValues.put(longId, null);
    updatedKeysAndValues.put(booleanKey, String.valueOf(updatedBooleanValue));
    updatedKeysAndValues.put(doubleKey, String.valueOf(updatedDoubleValue));
    updatedKeysAndValues.put(floatKey, String.valueOf(updatedFloatValue));
    updatedKeysAndValues.put(longKey, String.valueOf(updatedLongValue));
    updatedKeysAndValues.put(intKey, String.valueOf(updatedIntValue));

    crashlyticsCore.setCustomKeys(updatedKeysAndValues);

    assertEquals(updatedStringValue, metadata.getCustomKeys().get(stringKey));
    assertFalse(Boolean.parseBoolean(metadata.getCustomKeys().get(booleanKey)));
    assertEquals(
        updatedDoubleValue, Double.parseDouble(metadata.getCustomKeys().get(doubleKey)), DELTA);
    assertEquals(
        updatedFloatValue, Float.parseFloat(metadata.getCustomKeys().get(floatKey)), DELTA);
    assertEquals(updatedLongValue, Long.parseLong(metadata.getCustomKeys().get(longKey)), DELTA);
    assertEquals(updatedIntValue, Integer.parseInt(metadata.getCustomKeys().get(intKey)), DELTA);
    assertEquals("", metadata.getCustomKeys().get(longId));
  }

  public void testGetVersion() {
    assertFalse(TextUtils.isEmpty(CrashlyticsCore.getVersion()));
    assertFalse(CrashlyticsCore.getVersion().equalsIgnoreCase("version"));
    assertEquals(BuildConfig.VERSION_NAME, CrashlyticsCore.getVersion());
  }

  public void testNullBuildIdRequiredTrue() {
    assertFalse(CrashlyticsCore.isBuildIdValid(null, true));
  }

  public void testEmptyBuildIdRequiredTrue() {
    assertFalse(CrashlyticsCore.isBuildIdValid("", true));
  }

  public void testValidBuildIdRequiredTrue() {
    assertTrue(CrashlyticsCore.isBuildIdValid("buildId", true));
  }

  public void testNullBuildIdRequiredFalse() {
    assertTrue(CrashlyticsCore.isBuildIdValid(null, false));
  }

  public void testEmptyBuildIdRequiredFalse() {
    assertTrue(CrashlyticsCore.isBuildIdValid("", false));
  }

  public void testBreadcrumbSourceIsRegistered() {
    Mockito.verify(mockBreadcrumbSource).registerBreadcrumbHandler(any(BreadcrumbHandler.class));
  }

  public void testOnPreExecute_nativeDidCrashOnPreviousExecution() throws Exception {
    appRestart(); // Create a previous execution
    final CrashlyticsNativeComponent mockNativeComponent = mock(CrashlyticsNativeComponent.class);
    when(mockNativeComponent.hasCrashDataForSession(anyString())).thenReturn(true);
    final CrashlyticsCore crashlyticsCore = appRestart(mockNativeComponent);
    assertTrue(crashlyticsCore.didCrashOnPreviousExecution());
  }

  public void testOnPreExecute_nativeDidNotCrashOnPreviousExecution() throws Exception {
    appRestart(); // Create a previous execution
    final CrashlyticsNativeComponent mockNativeComponent = mock(CrashlyticsNativeComponent.class);
    when(mockNativeComponent.hasCrashDataForSession(anyString())).thenReturn(false);
    final CrashlyticsCore crashlyticsCore = appRestart(mockNativeComponent);
    assertFalse(crashlyticsCore.didCrashOnPreviousExecution());
  }

  // Convenience method that recreates the CrashlyticsCore and starts it up.
  private CrashlyticsCore appRestart() throws Exception {
    return appRestart(MISSING_NATIVE_COMPONENT);
  }

  // Convenience method because so many tests was to replace the NDK data provider.
  private CrashlyticsCore appRestart(CrashlyticsNativeComponent mocknativeComponent)
      throws Exception {
    CrashlyticsCore core =
        CoreBuilder.newBuilder()
            .setCrashlyticsnativeComponent(mocknativeComponent)
            .setBreadcrumbSource(mockBreadcrumbSource)
            .build(getContext());
    return await(startCoreAsync(core));
  }

  // Wraps Tasks.await with a default timeout, so tests fail gracefully.
  private <T> T await(Task<T> task) throws Exception {
    return Tasks.await(task, 5, TimeUnit.SECONDS);
  }

  // Starts the given CrashlyticsCore.
  private Task<CrashlyticsCore> startCoreAsync(CrashlyticsCore crashlyticsCore) {
    // Swallow exceptions so tests don't crash.
    Thread.setDefaultUncaughtExceptionHandler(NOOP_HANDLER);

    SettingsController mockSettingsController = mock(SettingsController.class);
    final Settings settings = new TestSettings(3);
    when(mockSettingsController.getSettingsSync()).thenReturn(settings);
    when(mockSettingsController.getSettingsAsync()).thenReturn(Tasks.forResult(settings));

    List<BuildIdInfo> buildIdInfoList = new ArrayList<>();
    buildIdInfoList.add(new BuildIdInfo("lib.so", "x86", "aabb"));
    AppData appData =
        new AppData(
            GOOGLE_APP_ID,
            "buildId",
            buildIdInfoList,
            "installerPackageName",
            "packageName",
            "versionCode",
            "versionName",
            mock(DevelopmentPlatformProvider.class));

    crashlyticsCore.onPreExecute(appData, mockSettingsController);

    return crashlyticsCore
        .doBackgroundInitializationAsync(mockSettingsController)
        .onSuccessTask(
            new SuccessContinuation<Void, CrashlyticsCore>() {
              @NonNull
              @Override
              public Task<CrashlyticsCore> then(@Nullable Void aVoid) throws Exception {
                return Tasks.forResult(crashlyticsCore);
              }
            });
  }

  /** Helper class for building CrashlyticsCore instances. */
  private static class CoreBuilder {
    private DataCollectionArbiter arbiter;
    private CrashlyticsNativeComponent nativeComponent;
    private BreadcrumbSource breadcrumbSource;

    CoreBuilder() {
      setDataCollectionEnabled(true);
    }

    static CoreBuilder newBuilder() {
      return new CoreBuilder();
    }

    CoreBuilder setDataCollectionEnabled(boolean enabled) {
      arbiter = mock(DataCollectionArbiter.class);
      when(arbiter.isAutomaticDataCollectionEnabled()).thenReturn(enabled);
      return this;
    }

    CoreBuilder setCrashlyticsnativeComponent(CrashlyticsNativeComponent nativeComponent) {
      this.nativeComponent = nativeComponent;
      return this;
    }

    CoreBuilder setBreadcrumbSource(BreadcrumbSource breadcrumbSource) {
      this.breadcrumbSource = breadcrumbSource;
      return this;
    }

    CrashlyticsCore build(Context context) {
      FirebaseOptions testFirebaseOptions;
      testFirebaseOptions = new FirebaseOptions.Builder().setApplicationId(GOOGLE_APP_ID).build();

      FirebaseApp app = mock(FirebaseApp.class);
      when(app.getApplicationContext()).thenReturn(context);
      when(app.getOptions()).thenReturn(testFirebaseOptions);
      FirebaseInstallationsApi installationsApiMock = mock(FirebaseInstallationsApi.class);
      when(installationsApiMock.getId()).thenReturn(Tasks.forResult("instanceId"));
      BreadcrumbSource breadcrumbSource =
          this.breadcrumbSource == null ? new DisabledBreadcrumbSource() : this.breadcrumbSource;
      final CrashlyticsCore crashlyticsCore =
          new CrashlyticsCore(
              app,
              new IdManager(
                  context,
                  "unused",
                  installationsApiMock,
                  DataCollectionArbiterTest.MOCK_ARBITER_ENABLED),
              nativeComponent,
              arbiter,
              breadcrumbSource,
              new UnavailableAnalyticsEventLogger(),
              new FileStore(context),
              new SameThreadExecutorService(),
              mock(CrashlyticsAppQualitySessionsSubscriber.class),
              mock(RemoteConfigDeferredProxy.class));
      return crashlyticsCore;
    }
  }

  private static final Thread.UncaughtExceptionHandler NOOP_HANDLER =
      (Thread thread, Throwable ex) -> {};
}
