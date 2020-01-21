// Copyright 2019 Google LLC
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

package com.google.firebase.crashlytics.internal.settings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.common.CurrentTimeProvider;
import com.google.firebase.crashlytics.internal.common.DataCollectionArbiter;
import com.google.firebase.crashlytics.internal.common.DeliveryMechanism;
import com.google.firebase.crashlytics.internal.common.ExecutorUtils;
import com.google.firebase.crashlytics.internal.common.InstallIdProvider;
import com.google.firebase.crashlytics.internal.settings.model.SettingsData;
import com.google.firebase.crashlytics.internal.settings.model.SettingsRequest;
import com.google.firebase.crashlytics.internal.settings.network.SettingsSpiCall;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

public class DefaultSettingsControllerTest extends CrashlyticsTestCase {

  private static final String googleAppId = "1:12345678901:android:1234567890abcdefg";
  private static final String instanceId = "dev_version_6";
  private static final String buildVersion = "1";
  private static final String displayVersion = "1.0";
  private static final String deviceModel = "Samsung/SM-G920";
  private static final String osBuildVersion = "123abc";
  private static final String osDisplayVersion = "4.2.2";
  private static final String installationId = "d1dc3e52e16cbfe632902aeb112830491690504e";
  private static final int source = DeliveryMechanism.APP_STORE.getId();

  private static final long UNEXPIRED_CURRENT_TIME_MILLIS = -10;
  private static final long EXPIRED_CURRENT_TIME_MILLIS = 10;

  private CurrentTimeProvider mockCurrentTimeProvider;
  private CachedSettingsIo mockCachedSettingsIo;
  private SettingsJsonParser mockSettingsJsonParser;
  private SettingsSpiCall mockSettingsSpiCall;
  private DataCollectionArbiter mockDataCollectionArbiter;

  private Executor networkExecutor = ExecutorUtils.buildSingleThreadExecutorService("network");

  public DefaultSettingsControllerTest() {}

  SettingsController newSettingsController(
      SettingsRequest settingsRequest,
      CurrentTimeProvider currentTimeProvider,
      SettingsJsonParser settingsJsonParser,
      CachedSettingsIo cachedSettingsIo,
      SettingsSpiCall settingsSpiCall,
      DataCollectionArbiter dataCollectionArbiter,
      final boolean buildInstanceIdentifierChanged) {
    return new SettingsController(
        getContext(),
        settingsRequest,
        currentTimeProvider,
        settingsJsonParser,
        cachedSettingsIo,
        settingsSpiCall,
        dataCollectionArbiter) {
      @Override
      boolean buildInstanceIdentifierChanged() {
        return buildInstanceIdentifierChanged;
      }
    };
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mockCurrentTimeProvider = mock(CurrentTimeProvider.class);
    mockCachedSettingsIo = mock(CachedSettingsIo.class);
    mockSettingsJsonParser = mock(SettingsJsonParser.class);
    mockSettingsSpiCall = mock(SettingsSpiCall.class);
    mockDataCollectionArbiter = mock(DataCollectionArbiter.class);
  }

  private <T> T await(Task<T> task) throws Exception {
    return Tasks.await(task, 10, TimeUnit.SECONDS);
  }

  public void testCachedSettingsLoad() throws Exception {
    final JSONObject cachedJson = new JSONObject();
    when(mockCachedSettingsIo.readCachedSettings()).thenReturn(cachedJson);

    when(mockCurrentTimeProvider.getCurrentTimeMillis())
        .thenReturn(Long.valueOf(UNEXPIRED_CURRENT_TIME_MILLIS));

    final SettingsData cachedSettings = new TestSettingsData();
    when(mockSettingsJsonParser.parseSettingsJson(cachedJson)).thenReturn(cachedSettings);

    final SettingsRequest requestData = buildSettingsRequest();

    final SettingsController controller =
        newSettingsController(
            requestData,
            mockCurrentTimeProvider,
            mockSettingsJsonParser,
            mockCachedSettingsIo,
            mockSettingsSpiCall,
            mockDataCollectionArbiter,
            false);

    await(controller.loadSettingsData(networkExecutor));
    assertEquals(cachedSettings, controller.getSettings());
    assertEquals(cachedSettings.appData, await(controller.getAppSettings()));

    verifyZeroInteractions(mockSettingsSpiCall);
    verify(mockCachedSettingsIo).readCachedSettings();
    verify(mockSettingsJsonParser).parseSettingsJson(cachedJson);
    verify(mockCurrentTimeProvider, times(2)).getCurrentTimeMillis();
  }

  public void testCachedSettingsLoad_newInstanceIdentifier() throws Exception {
    final SettingsData fetchedSettings = new TestSettingsData();

    final JSONObject fetchedJson = new JSONObject();
    when(mockSettingsSpiCall.invoke(any(SettingsRequest.class), eq(true))).thenReturn(fetchedJson);

    when(mockSettingsJsonParser.parseSettingsJson(fetchedJson)).thenReturn(fetchedSettings);

    TaskCompletionSource<Void> dataCollectionPermission = new TaskCompletionSource<>();
    when(mockDataCollectionArbiter.waitForDataCollectionPermission())
        .thenReturn(dataCollectionPermission.getTask());

    final SettingsRequest requestData = buildSettingsRequest();
    final SettingsController controller =
        newSettingsController(
            requestData,
            mockCurrentTimeProvider,
            mockSettingsJsonParser,
            mockCachedSettingsIo,
            mockSettingsSpiCall,
            mockDataCollectionArbiter,
            true);

    controller.loadSettingsData(SettingsCacheBehavior.SKIP_CACHE_LOOKUP, networkExecutor);
    assertNotNull(controller.getSettings());

    dataCollectionPermission.trySetResult(null);
    assertEquals(fetchedSettings.appData, await(controller.getAppSettings()));
    assertEquals(fetchedSettings, controller.getSettings());

    verify(mockSettingsSpiCall).invoke(any(SettingsRequest.class), eq(true));
    verify(mockCachedSettingsIo).writeCachedSettings(fetchedSettings.expiresAtMillis, fetchedJson);
    verify(mockSettingsJsonParser).parseSettingsJson(fetchedJson);
    verify(mockCurrentTimeProvider).getCurrentTimeMillis();
  }

  public void testExpiredCachedSettingsLoad() throws Exception {

    final SettingsData cachedSettings = new TestSettingsData();
    final SettingsData fetchedSettings = new TestSettingsData();

    final JSONObject fetchedJson = new JSONObject();
    when(mockSettingsSpiCall.invoke(any(SettingsRequest.class), eq(true))).thenReturn(fetchedJson);

    final JSONObject cachedJson = new JSONObject();
    when(mockCachedSettingsIo.readCachedSettings()).thenReturn(cachedJson);

    when(mockCurrentTimeProvider.getCurrentTimeMillis())
        .thenReturn(Long.valueOf(EXPIRED_CURRENT_TIME_MILLIS));

    when(mockSettingsJsonParser.parseSettingsJson(cachedJson)).thenReturn(cachedSettings);
    when(mockSettingsJsonParser.parseSettingsJson(fetchedJson)).thenReturn(fetchedSettings);

    TaskCompletionSource<Void> dataCollectionPermission = new TaskCompletionSource<>();
    when(mockDataCollectionArbiter.waitForDataCollectionPermission())
        .thenReturn(dataCollectionPermission.getTask());

    final SettingsRequest requestData = buildSettingsRequest();
    final SettingsController controller =
        newSettingsController(
            requestData,
            mockCurrentTimeProvider,
            mockSettingsJsonParser,
            mockCachedSettingsIo,
            mockSettingsSpiCall,
            mockDataCollectionArbiter,
            false);

    Task<Void> loadFinished = controller.loadSettingsData(networkExecutor);

    assertEquals(cachedSettings, controller.getSettings());
    assertEquals(cachedSettings.appData, await(controller.getAppSettings()));

    dataCollectionPermission.trySetResult(null);
    await(loadFinished);

    assertEquals(fetchedSettings.appData, await(controller.getAppSettings()));
    assertEquals(fetchedSettings, controller.getSettings());

    verify(mockSettingsSpiCall).invoke(any(SettingsRequest.class), eq(true));
    verify(mockCachedSettingsIo, times(2)).readCachedSettings();
    verify(mockCachedSettingsIo).writeCachedSettings(fetchedSettings.expiresAtMillis, fetchedJson);
    verify(mockSettingsJsonParser, times(2)).parseSettingsJson(cachedJson);
    verify(mockSettingsJsonParser).parseSettingsJson(fetchedJson);
    verify(mockCurrentTimeProvider, times(3)).getCurrentTimeMillis();
  }

  public void testIgnoreExpiredCachedSettingsLoad() throws Exception {
    final JSONObject cachedJson = new JSONObject();
    when(mockCachedSettingsIo.readCachedSettings()).thenReturn(cachedJson);

    when(mockCurrentTimeProvider.getCurrentTimeMillis())
        .thenReturn(Long.valueOf(EXPIRED_CURRENT_TIME_MILLIS));

    final SettingsData cachedSettings = new TestSettingsData();
    when(mockSettingsJsonParser.parseSettingsJson(cachedJson)).thenReturn(cachedSettings);

    final SettingsRequest requestData = buildSettingsRequest();
    final SettingsController controller =
        newSettingsController(
            requestData,
            mockCurrentTimeProvider,
            mockSettingsJsonParser,
            mockCachedSettingsIo,
            mockSettingsSpiCall,
            mockDataCollectionArbiter,
            false);
    controller.loadSettingsData(SettingsCacheBehavior.IGNORE_CACHE_EXPIRATION, networkExecutor);
    assertEquals(cachedSettings, controller.getSettings());
    assertEquals(cachedSettings.appData, await(controller.getAppSettings()));

    verifyZeroInteractions(mockSettingsSpiCall);
    verify(mockCachedSettingsIo).readCachedSettings();
    verify(mockSettingsJsonParser).parseSettingsJson(cachedJson);
    verify(mockCurrentTimeProvider, times(2)).getCurrentTimeMillis();
  }

  public void testSkipCachedSettingsLoad() throws Exception {

    final SettingsData fetchedSettings = new TestSettingsData();

    final JSONObject fetchedJson = new JSONObject();
    when(mockSettingsSpiCall.invoke(any(SettingsRequest.class), eq(true))).thenReturn(fetchedJson);

    when(mockSettingsJsonParser.parseSettingsJson(fetchedJson)).thenReturn(fetchedSettings);

    final JSONObject expiredCachedSettingsJson = new JSONObject();
    when(mockCachedSettingsIo.readCachedSettings()).thenReturn(expiredCachedSettingsJson);

    when(mockCurrentTimeProvider.getCurrentTimeMillis())
        .thenReturn(Long.valueOf(EXPIRED_CURRENT_TIME_MILLIS));

    final SettingsData expiredCachedSettings = new TestSettingsData();
    when(mockSettingsJsonParser.parseSettingsJson(expiredCachedSettingsJson))
        .thenReturn(expiredCachedSettings);

    TaskCompletionSource<Void> dataCollectionPermission = new TaskCompletionSource<>();
    when(mockDataCollectionArbiter.waitForDataCollectionPermission())
        .thenReturn(dataCollectionPermission.getTask());

    final SettingsRequest requestData = buildSettingsRequest();
    final SettingsController controller =
        newSettingsController(
            requestData,
            mockCurrentTimeProvider,
            mockSettingsJsonParser,
            mockCachedSettingsIo,
            mockSettingsSpiCall,
            mockDataCollectionArbiter,
            false);

    Task<Void> loadFinished =
        controller.loadSettingsData(SettingsCacheBehavior.SKIP_CACHE_LOOKUP, networkExecutor);
    assertEquals(expiredCachedSettings.appData, await(controller.getAppSettings()));
    assertEquals(expiredCachedSettings, controller.getSettings());

    dataCollectionPermission.trySetResult(null);
    await(loadFinished);

    assertEquals(fetchedSettings.appData, await(controller.getAppSettings()));
    assertEquals(fetchedSettings, controller.getSettings());

    verify(mockSettingsSpiCall).invoke(any(SettingsRequest.class), eq(true));
    verify(mockCachedSettingsIo).readCachedSettings();
    verify(mockCachedSettingsIo).writeCachedSettings(fetchedSettings.expiresAtMillis, fetchedJson);
    verify(mockSettingsJsonParser).parseSettingsJson(fetchedJson);
    verify(mockCurrentTimeProvider, times(2)).getCurrentTimeMillis();
  }

  /**
   * Test loading settings in the scenario that initial cache lookup is skipped and the remote call
   * returns null. Should attempt another cache lookup, this time forcing use of an expired cache
   * result.
   *
   * @throws Exception
   */
  public void testLastDitchSettingsLoad() throws Exception {
    when(mockSettingsSpiCall.invoke(any(SettingsRequest.class), eq(true))).thenReturn(null);

    final JSONObject expiredCachedSettingsJson = new JSONObject();
    when(mockCachedSettingsIo.readCachedSettings()).thenReturn(expiredCachedSettingsJson);

    when(mockCurrentTimeProvider.getCurrentTimeMillis())
        .thenReturn(Long.valueOf(EXPIRED_CURRENT_TIME_MILLIS));

    final SettingsData expiredCachedSettings = new TestSettingsData();
    when(mockSettingsJsonParser.parseSettingsJson(expiredCachedSettingsJson))
        .thenReturn(expiredCachedSettings);

    TaskCompletionSource<Void> dataCollectionPermission = new TaskCompletionSource<>();
    when(mockDataCollectionArbiter.waitForDataCollectionPermission())
        .thenReturn(dataCollectionPermission.getTask());

    final SettingsRequest requestData = buildSettingsRequest();
    final SettingsController controller =
        newSettingsController(
            requestData,
            mockCurrentTimeProvider,
            mockSettingsJsonParser,
            mockCachedSettingsIo,
            mockSettingsSpiCall,
            mockDataCollectionArbiter,
            false);

    Task<Void> loadFinished =
        controller.loadSettingsData(SettingsCacheBehavior.SKIP_CACHE_LOOKUP, networkExecutor);
    assertEquals(expiredCachedSettings, controller.getSettings());
    assertEquals(expiredCachedSettings.appData, await(controller.getAppSettings()));

    dataCollectionPermission.trySetResult(null);
    await(loadFinished);

    assertEquals(expiredCachedSettings.appData, await(controller.getAppSettings()));
    assertEquals(expiredCachedSettings, controller.getSettings());

    verify(mockSettingsSpiCall).invoke(any(SettingsRequest.class), eq(true));
    verify(mockCachedSettingsIo).readCachedSettings();
    verify(mockSettingsJsonParser).parseSettingsJson(expiredCachedSettingsJson);
    verify(mockCurrentTimeProvider, times(2)).getCurrentTimeMillis();
  }

  public void testNoAvailableSettingsLoad() throws Exception {
    when(mockSettingsSpiCall.invoke(any(SettingsRequest.class), eq(true))).thenReturn(null);

    when(mockCachedSettingsIo.readCachedSettings()).thenReturn(null);

    TaskCompletionSource<Void> dataCollectionPermission = new TaskCompletionSource<>();
    when(mockDataCollectionArbiter.waitForDataCollectionPermission())
        .thenReturn(dataCollectionPermission.getTask());

    final SettingsRequest requestData = buildSettingsRequest();
    final SettingsController controller =
        newSettingsController(
            requestData,
            mockCurrentTimeProvider,
            mockSettingsJsonParser,
            mockCachedSettingsIo,
            mockSettingsSpiCall,
            mockDataCollectionArbiter,
            false);

    Task<Void> loadFinished = controller.loadSettingsData(networkExecutor);
    assertNotNull(controller.getSettings());
    assertFalse(controller.getAppSettings().isComplete());

    dataCollectionPermission.trySetResult(null);
    await(loadFinished);

    assertNotNull(controller.getSettings());
    assertFalse(controller.getAppSettings().isComplete());

    verify(mockSettingsSpiCall).invoke(any(SettingsRequest.class), eq(true));
    verify(mockCachedSettingsIo, times(2)).readCachedSettings();
    verifyZeroInteractions(mockSettingsJsonParser);
    verify(mockCurrentTimeProvider).getCurrentTimeMillis();
  }

  private SettingsRequest buildSettingsRequest() {
    final InstallIdProvider installIdProvider =
        new InstallIdProvider() {
          @Override
          public String getCrashlyticsInstallId() {
            return installationId;
          }
        };

    return new SettingsRequest(
        googleAppId,
        deviceModel,
        osBuildVersion,
        osDisplayVersion,
        installIdProvider,
        instanceId,
        displayVersion,
        buildVersion,
        source);
  }
}
