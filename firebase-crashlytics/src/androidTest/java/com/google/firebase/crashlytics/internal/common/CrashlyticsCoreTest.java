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

import static org.mockito.Mockito.*;

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
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.MissingNativeComponent;
import com.google.firebase.crashlytics.internal.analytics.UnavailableAnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbHandler;
import com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbSource;
import com.google.firebase.crashlytics.internal.breadcrumbs.DisabledBreadcrumbSource;
import com.google.firebase.crashlytics.internal.settings.SettingsController;
import com.google.firebase.crashlytics.internal.settings.TestSettingsData;
import com.google.firebase.crashlytics.internal.settings.model.SettingsData;
import com.google.firebase.installations.FirebaseInstallationsApi;
import java.util.concurrent.TimeUnit;
import org.mockito.Mockito;

public class CrashlyticsCoreTest extends CrashlyticsTestCase {

  private static final String GOOGLE_APP_ID = "google:app:id";

  private static final CrashlyticsNativeComponent MISSING_NATIVE_COMPONENT =
      new MissingNativeComponent();

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
    final SettingsData settings = new TestSettingsData(3);
    when(mockSettingsController.getSettings()).thenReturn(settings);
    when(mockSettingsController.getAppSettings()).thenReturn(Tasks.forResult(settings.appData));

    crashlyticsCore.onPreExecute(mockSettingsController);

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
              new IdManager(context, "unused", installationsApiMock),
              nativeComponent,
              arbiter,
              breadcrumbSource,
              new UnavailableAnalyticsEventLogger(),
              new SameThreadExecutorService());
      return crashlyticsCore;
    }
  }

  private static final Thread.UncaughtExceptionHandler NOOP_HANDLER =
      (Thread thread, Throwable ex) -> {};
}
