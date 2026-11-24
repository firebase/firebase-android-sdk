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

package com.google.firebase.crashlytics.internal.common;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.ConnectivityManager;
import android.os.Looper;
import android.os.ProfilingTrigger;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.concurrent.TestOnlyExecutors;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponentDeferredProxy;
import com.google.firebase.crashlytics.internal.DevelopmentPlatformProvider;
import com.google.firebase.crashlytics.internal.analytics.AnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.concurrency.CrashlyticsWorker;
import com.google.firebase.crashlytics.internal.concurrency.CrashlyticsWorkers;
import com.google.firebase.crashlytics.internal.metadata.LogFileManager;
import com.google.firebase.crashlytics.internal.metadata.UserMetadata;
import com.google.firebase.crashlytics.internal.network.ScottyUploader;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.firebase.crashlytics.internal.persistence.ResumableUploadMetadataStore;
import com.google.firebase.crashlytics.internal.persistence.ResumableUploadMetadataStore.InProgressUploadMetadata;
import com.google.firebase.crashlytics.internal.settings.Settings;
import com.google.firebase.crashlytics.internal.settings.Settings.FeatureFlagData;
import com.google.firebase.crashlytics.internal.settings.Settings.Profiling;
import com.google.firebase.crashlytics.internal.settings.Settings.SessionData;
import com.google.firebase.crashlytics.internal.settings.SettingsProvider;
import com.google.firebase.inject.Deferred;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowActivityManager;

@RunWith(RobolectricTestRunner.class)
public class CrashlyticsControllerRobolectricTest {
  private static final String GOOGLE_APP_ID = "google:app:id";
  private static final String SESSION_ID = "session_id";
  private static final String HEAPDUMP_PATH = "profile_trigger-type-7.perfetto-java-heap-dump";

  private Context testContext;
  @Mock private IdManager idManager;
  @Mock private SettingsProvider mockSettingsProvider;
  @Mock private FileStore testFileStore;
  @Mock private SessionReportingCoordinator mockSessionReportingCoordinator;
  @Mock private DataCollectionArbiter mockDataCollectionArbiter;
  @Mock private ResumableUploadMetadataStore mockUploadMetadataStore;
  @Mock private ScottyUploader mockScottyUploader;
  @Mock private CrashlyticsController.ProfilingManagerRegistrar mockProfilingManagerRegistrar;

  @Mock private FirebaseApp firebaseApp;

  private CrashlyticsWorkers crashlyticsWorkers;

  private static final CrashlyticsNativeComponent MISSING_NATIVE_COMPONENT =
      new CrashlyticsNativeComponentDeferredProxy(
          new Deferred<CrashlyticsNativeComponent>() {
            @Override
            public void whenAvailable(
                @NonNull Deferred.DeferredHandler<CrashlyticsNativeComponent> handler) {
              // no-op
            }
          });

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    testContext = getApplicationContext();
    testFileStore = new FileStore(testContext);
    crashlyticsWorkers =
        new CrashlyticsWorkers(TestOnlyExecutors.background(), TestOnlyExecutors.blocking());

    FirebaseOptions options =
        new FirebaseOptions.Builder()
            .setApiKey("api-key")
            .setApplicationId("applicationId")
            .setProjectId("projectId")
            .build();

    when(firebaseApp.getOptions()).thenReturn(options);
    when(mockUploadMetadataStore.hasPendingUploads()).thenReturn(false);
    when(mockUploadMetadataStore.getUpload()).thenReturn(Optional.empty());
  }

  @Test
  public void testDoCloseSession_enabledAnrs_doesNotPersistsAppExitInfoIfItDoesntExist()
      throws Exception {
    final String sessionId = "sessionId";
    final CrashlyticsController controller = createController();

    when(mockSessionReportingCoordinator.listSortedOpenSessionIds())
        .thenReturn(new TreeSet<>(Collections.singletonList(sessionId)));
    mockSettingsProvider(true, false);

    crashlyticsWorkers.common.submit(() -> controller.doCloseSessions(mockSettingsProvider));
    // cannot use await since it check preconditions if blocking main thread
    Thread.sleep(10);

    // Since we haven't added any app exit info to the shadow activity manager, there won't exist a
    // single app exit info, and so this method won't be called.
    verify(mockSessionReportingCoordinator, never())
        .persistRelevantAppExitInfoEvent(
            eq(sessionId), any(), any(LogFileManager.class), any(UserMetadata.class));
  }

  @Test
  public void testDoCloseSession_enabledAnrs_persistsAppExitInfoIfItExists() throws Exception {
    final String sessionIdPrevious = "sessionIdPrevious";
    final String sessionId = "sessionId";
    final CrashlyticsController controller = createController();
    // Adds multiple AppExitInfos to confirm that Crashlytics loops through
    // them rather than just looking at the first.
    addAppExitInfo(ApplicationExitInfo.REASON_ANR);
    addAppExitInfo(ApplicationExitInfo.REASON_EXIT_SELF);
    List<ApplicationExitInfo> testApplicationExitInfo = getApplicationExitInfoList();

    when(mockSessionReportingCoordinator.listSortedOpenSessionIds())
        .thenReturn(new TreeSet<>(Arrays.asList(sessionId, sessionIdPrevious)));
    mockSettingsProvider(true, false);
    crashlyticsWorkers.common.submit(() -> controller.finalizeSessions(mockSettingsProvider));
    // cannot use await since it check preconditions if blocking main thread
    Thread.sleep(100);
    verify(mockSessionReportingCoordinator)
        .persistRelevantAppExitInfoEvent(
            eq(sessionIdPrevious),
            eq(testApplicationExitInfo),
            any(LogFileManager.class),
            any(UserMetadata.class));
  }

  @Test
  public void testDoCloseSession_disabledAnrs_doesNotPersistsAppExitInfo() throws Exception {
    final String sessionId = "sessionId";
    final CrashlyticsController controller = createController();

    when(mockSessionReportingCoordinator.listSortedOpenSessionIds())
        .thenReturn(new TreeSet<>(Collections.singletonList(sessionId)));
    mockSettingsProvider(false, false);
    crashlyticsWorkers.common.submit(() -> controller.doCloseSessions(mockSettingsProvider));
    // cannot use await since it check preconditions if blocking main thread
    Thread.sleep(10);
    verify(mockSessionReportingCoordinator, never())
        .persistRelevantAppExitInfoEvent(
            eq(sessionId), any(), any(LogFileManager.class), any(UserMetadata.class));
  }

  @Test
  public void testFinalizeSessions_doesNotUploadHeapDumpWithoutSubmitAllReports() throws Exception {
    Context unmeteredContext = createContextWithMeteredNetwork(/* isMetered= */ false);
    InProgressUploadMetadata pending =
        new InProgressUploadMetadata(SESSION_ID, ProfilingTrigger.TRIGGER_TYPE_OOM, HEAPDUMP_PATH);
    when(mockUploadMetadataStore.hasPendingUploads()).thenReturn(true);
    when(mockUploadMetadataStore.getUpload()).thenReturn(Optional.of(pending));
    when(mockDataCollectionArbiter.isAutomaticDataCollectionEnabled()).thenReturn(false);
    when(mockSessionReportingCoordinator.listSortedOpenSessionIds())
        .thenReturn(new TreeSet<>(Collections.singletonList(SESSION_ID)));
    mockSettingsProvider(false, false, /* heapDumpCollectionEnabled= */ true);

    CrashlyticsController controller =
        createController(
            unmeteredContext,
            mockDataCollectionArbiter,
            mockUploadMetadataStore,
            mockScottyUploader);

    crashlyticsWorkers.common.submit(() -> controller.finalizeSessions(mockSettingsProvider));
    awaitWorker(crashlyticsWorkers.common);

    verify(mockScottyUploader, never())
        .triggerUpload(anyString(), anyString(), anyInt(), anyString(), any());
    verify(mockScottyUploader, never()).resumeUpload(anyString(), anyString(), any());
  }

  @Test
  public void testSubmitAllReports_dataCollectionDisabled_doesNotUploadPendingHeapDump()
      throws Exception {
    Context unmeteredContext = createContextWithMeteredNetwork(/* isMetered= */ false);
    InProgressUploadMetadata pending =
        new InProgressUploadMetadata(SESSION_ID, ProfilingTrigger.TRIGGER_TYPE_OOM, HEAPDUMP_PATH);
    when(mockUploadMetadataStore.hasPendingUploads()).thenReturn(true);
    when(mockUploadMetadataStore.getUpload()).thenReturn(Optional.of(pending));
    when(mockSessionReportingCoordinator.hasReportsToSend()).thenReturn(true);
    when(mockDataCollectionArbiter.isAutomaticDataCollectionEnabled()).thenReturn(false);
    when(mockDataCollectionArbiter.waitForAutomaticDataCollectionEnabled())
        .thenReturn(new TaskCompletionSource<Void>().getTask());
    Settings settings = createSettings(false, false, /* heapDumpCollectionEnabled= */ true);

    CrashlyticsController controller =
        createController(
            unmeteredContext,
            mockDataCollectionArbiter,
            mockUploadMetadataStore,
            mockScottyUploader);

    controller.submitAllReports(Tasks.forResult(settings));
    awaitWorker(crashlyticsWorkers.common);

    assertThat(await(controller.checkForUnsentReports())).isTrue();
    verify(mockScottyUploader, never())
        .triggerUpload(anyString(), anyString(), anyInt(), anyString(), any());
    verify(mockScottyUploader, never()).resumeUpload(anyString(), anyString(), any());
    verify(mockSessionReportingCoordinator, never()).sendReports(any(Executor.class));
  }

  @Test
  public void testSubmitAllReports_dataCollectionDisabled_deleteUnsentReportsClearsHeapDumpQueue()
      throws Exception {
    Context unmeteredContext = createContextWithMeteredNetwork(/* isMetered= */ false);
    InProgressUploadMetadata pending =
        new InProgressUploadMetadata(SESSION_ID, ProfilingTrigger.TRIGGER_TYPE_OOM, HEAPDUMP_PATH);
    when(mockUploadMetadataStore.hasPendingUploads()).thenReturn(true);
    when(mockUploadMetadataStore.getUpload()).thenReturn(Optional.of(pending));
    when(mockSessionReportingCoordinator.hasReportsToSend()).thenReturn(true);
    when(mockDataCollectionArbiter.isAutomaticDataCollectionEnabled()).thenReturn(false);
    when(mockDataCollectionArbiter.waitForAutomaticDataCollectionEnabled())
        .thenReturn(new TaskCompletionSource<Void>().getTask());
    Settings settings = createSettings(false, false, /* heapDumpCollectionEnabled= */ true);

    CrashlyticsController controller =
        createController(
            unmeteredContext,
            mockDataCollectionArbiter,
            mockUploadMetadataStore,
            mockScottyUploader);

    controller.submitAllReports(Tasks.forResult(settings));
    await(controller.deleteUnsentReports());
    awaitWorker(crashlyticsWorkers.common);

    verify(mockSessionReportingCoordinator).removeAllReports();
    verify(mockUploadMetadataStore).clearInProgressUploads();
    verify(mockScottyUploader, never())
        .triggerUpload(anyString(), anyString(), anyInt(), anyString(), any());
    verify(mockScottyUploader, never()).resumeUpload(anyString(), anyString(), any());
  }

  @Test
  public void testSubmitAllReports_onlyHeapDumpPending_disabled_deleteUnsentReportsClearsQueue()
      throws Exception {
    Context unmeteredContext = createContextWithMeteredNetwork(/* isMetered= */ false);
    InProgressUploadMetadata pending =
        new InProgressUploadMetadata(SESSION_ID, ProfilingTrigger.TRIGGER_TYPE_OOM, HEAPDUMP_PATH);
    when(mockUploadMetadataStore.hasPendingUploads()).thenReturn(true);
    when(mockUploadMetadataStore.getUpload()).thenReturn(Optional.of(pending));
    when(mockSessionReportingCoordinator.hasReportsToSend()).thenReturn(false);
    when(mockDataCollectionArbiter.isAutomaticDataCollectionEnabled()).thenReturn(false);
    when(mockDataCollectionArbiter.waitForAutomaticDataCollectionEnabled())
        .thenReturn(new TaskCompletionSource<Void>().getTask());
    Settings settings = createSettings(false, false, /* heapDumpCollectionEnabled= */ true);

    CrashlyticsController controller =
        createController(
            unmeteredContext,
            mockDataCollectionArbiter,
            mockUploadMetadataStore,
            mockScottyUploader);

    controller.submitAllReports(Tasks.forResult(settings));
    assertThat(await(controller.checkForUnsentReports())).isTrue();

    await(controller.deleteUnsentReports());
    awaitWorker(crashlyticsWorkers.common);

    verify(mockUploadMetadataStore).clearInProgressUploads();
    verify(mockScottyUploader, never())
        .triggerUpload(anyString(), anyString(), anyInt(), anyString(), any());
  }

  @Test
  public void testSubmitAllReports_dataCollectionDisabled_sendUnsentReportsUploadsHeapDump()
      throws Exception {
    Context unmeteredContext = createContextWithMeteredNetwork(/* isMetered= */ false);
    InProgressUploadMetadata pending =
        new InProgressUploadMetadata(SESSION_ID, ProfilingTrigger.TRIGGER_TYPE_OOM, HEAPDUMP_PATH);
    when(mockUploadMetadataStore.hasPendingUploads()).thenReturn(true);
    when(mockUploadMetadataStore.getUpload()).thenReturn(Optional.of(pending));
    when(mockSessionReportingCoordinator.hasReportsToSend()).thenReturn(true);
    when(mockSessionReportingCoordinator.sendReports(any(Executor.class)))
        .thenReturn(Tasks.forResult(null));
    when(mockDataCollectionArbiter.isAutomaticDataCollectionEnabled()).thenReturn(false);
    when(mockDataCollectionArbiter.waitForAutomaticDataCollectionEnabled())
        .thenReturn(new TaskCompletionSource<Void>().getTask());
    Settings settings = createSettings(false, false, /* heapDumpCollectionEnabled= */ true);

    CrashlyticsController controller =
        createController(
            unmeteredContext,
            mockDataCollectionArbiter,
            mockUploadMetadataStore,
            mockScottyUploader);

    controller.submitAllReports(Tasks.forResult(settings));
    verify(mockScottyUploader, never())
        .triggerUpload(anyString(), anyString(), anyInt(), anyString(), any());

    await(controller.sendUnsentReports());
    awaitWorker(crashlyticsWorkers.common);

    verify(mockDataCollectionArbiter).grantDataCollectionPermission(true);
    verify(mockSessionReportingCoordinator).sendReports(any(Executor.class));
    verify(mockScottyUploader)
        .triggerUpload(
            eq(GOOGLE_APP_ID),
            eq(SESSION_ID),
            eq(ProfilingTrigger.TRIGGER_TYPE_OOM),
            eq(HEAPDUMP_PATH),
            any());
  }

  @Test
  public void testSubmitAllReports_dataCollectionDisabledThenEnabled_uploadsPendingHeapDump()
      throws Exception {
    Context unmeteredContext = createContextWithMeteredNetwork(/* isMetered= */ false);
    when(firebaseApp.getApplicationContext()).thenReturn(testContext);
    DataCollectionArbiter arbiter = new DataCollectionArbiter(firebaseApp);
    arbiter.setCrashlyticsDataCollectionEnabled(false);

    InProgressUploadMetadata pending =
        new InProgressUploadMetadata(SESSION_ID, ProfilingTrigger.TRIGGER_TYPE_OOM, HEAPDUMP_PATH);
    when(mockUploadMetadataStore.hasPendingUploads()).thenReturn(true);
    when(mockUploadMetadataStore.getUpload()).thenReturn(Optional.of(pending));
    when(mockSessionReportingCoordinator.hasReportsToSend()).thenReturn(false);
    Settings settings = createSettings(false, false, /* heapDumpCollectionEnabled= */ true);

    CrashlyticsController controller =
        createController(unmeteredContext, arbiter, mockUploadMetadataStore, mockScottyUploader);

    controller.submitAllReports(Tasks.forResult(settings));
    awaitWorker(crashlyticsWorkers.common);
    verify(mockScottyUploader, never())
        .triggerUpload(anyString(), anyString(), anyInt(), anyString(), any());

    arbiter.setCrashlyticsDataCollectionEnabled(true);
    await(controller.unsentReportsHandled.getTask());
    awaitWorker(crashlyticsWorkers.common);

    verify(mockScottyUploader)
        .triggerUpload(
            eq(GOOGLE_APP_ID),
            eq(SESSION_ID),
            eq(ProfilingTrigger.TRIGGER_TYPE_OOM),
            eq(HEAPDUMP_PATH),
            any());
  }

  @Test
  public void testSubmitAllReports_cellularToWifi_onlyHeapDumpPending_uploadsWhenEnabled()
      throws Exception {
    Context unmeteredContext = createContextWithMeteredNetwork(/* isMetered= */ false);
    InProgressUploadMetadata pending =
        new InProgressUploadMetadata(
            SESSION_ID, ProfilingTrigger.TRIGGER_TYPE_ANOMALY, HEAPDUMP_PATH);
    when(mockUploadMetadataStore.hasPendingUploads()).thenReturn(true);
    when(mockUploadMetadataStore.getUpload()).thenReturn(Optional.of(pending));
    when(mockSessionReportingCoordinator.hasReportsToSend()).thenReturn(false);
    when(mockDataCollectionArbiter.isAutomaticDataCollectionEnabled()).thenReturn(true);
    Settings settings = createSettings(false, false, /* heapDumpCollectionEnabled= */ true);

    CrashlyticsController controller =
        createController(
            unmeteredContext,
            mockDataCollectionArbiter,
            mockUploadMetadataStore,
            mockScottyUploader);

    controller.submitAllReports(Tasks.forResult(settings));
    await(controller.unsentReportsHandled.getTask());
    awaitWorker(crashlyticsWorkers.common);

    verify(mockSessionReportingCoordinator, never()).sendReports(any(Executor.class));
    verify(mockScottyUploader)
        .triggerUpload(
            eq(GOOGLE_APP_ID),
            eq(SESSION_ID),
            eq(ProfilingTrigger.TRIGGER_TYPE_ANOMALY),
            eq(HEAPDUMP_PATH),
            any());
  }

  @Test
  public void testSubmitAllReports_inProgressHeapDump_resumesOnlyAfterOptIn() throws Exception {
    Context unmeteredContext = createContextWithMeteredNetwork(/* isMetered= */ false);
    String handle = "https://upload.example/scotty-handle";
    InProgressUploadMetadata inProgress =
        new InProgressUploadMetadata(
            SESSION_ID, ProfilingTrigger.TRIGGER_TYPE_OOM, handle, HEAPDUMP_PATH);
    when(mockUploadMetadataStore.hasPendingUploads()).thenReturn(true);
    when(mockUploadMetadataStore.getUpload()).thenReturn(Optional.of(inProgress));
    when(mockSessionReportingCoordinator.hasReportsToSend()).thenReturn(false);
    when(mockDataCollectionArbiter.isAutomaticDataCollectionEnabled()).thenReturn(false);
    when(mockDataCollectionArbiter.waitForAutomaticDataCollectionEnabled())
        .thenReturn(new TaskCompletionSource<Void>().getTask());
    Settings settings = createSettings(false, false, /* heapDumpCollectionEnabled= */ true);

    CrashlyticsController controller =
        createController(
            unmeteredContext,
            mockDataCollectionArbiter,
            mockUploadMetadataStore,
            mockScottyUploader);

    controller.submitAllReports(Tasks.forResult(settings));
    awaitWorker(crashlyticsWorkers.common);
    verify(mockScottyUploader, never()).resumeUpload(anyString(), anyString(), any());

    await(controller.sendUnsentReports());
    awaitWorker(crashlyticsWorkers.common);

    verify(mockScottyUploader).resumeUpload(eq(HEAPDUMP_PATH), eq(handle), any());
  }

  @Test
  public void testSubmitAllReports_heapDumpSettingDisabled_doesNotUploadHeapDump()
      throws Exception {
    Context unmeteredContext = createContextWithMeteredNetwork(/* isMetered= */ false);
    InProgressUploadMetadata pending =
        new InProgressUploadMetadata(SESSION_ID, ProfilingTrigger.TRIGGER_TYPE_OOM, HEAPDUMP_PATH);
    when(mockUploadMetadataStore.hasPendingUploads()).thenReturn(true);
    when(mockUploadMetadataStore.getUpload()).thenReturn(Optional.of(pending));
    when(mockSessionReportingCoordinator.hasReportsToSend()).thenReturn(false);
    when(mockDataCollectionArbiter.isAutomaticDataCollectionEnabled()).thenReturn(true);
    Settings settings = createSettings(false, false, /* heapDumpCollectionEnabled= */ false);

    CrashlyticsController controller =
        createController(
            unmeteredContext,
            mockDataCollectionArbiter,
            mockUploadMetadataStore,
            mockScottyUploader);

    controller.submitAllReports(Tasks.forResult(settings));
    await(controller.unsentReportsHandled.getTask());
    awaitWorker(crashlyticsWorkers.common);

    verify(mockScottyUploader, never())
        .triggerUpload(anyString(), anyString(), anyInt(), anyString(), any());
    verify(mockScottyUploader, never()).resumeUpload(anyString(), anyString(), any());
  }

  @Test
  public void testWriteProfilingManagerInfo_dataCollectionDisabled_disablesHeapDumpQueueing() {
    when(mockDataCollectionArbiter.isAutomaticDataCollectionEnabled()).thenReturn(false);
    mockSettingsProvider(false, false, /* heapDumpCollectionEnabled= */ true);
    CrashlyticsController controller = createController();

    controller.writeProfilingManagerInfo(SESSION_ID, mockSettingsProvider);

    verify(mockSessionReportingCoordinator)
        .persistProfilingManagerInfo(
            eq(testContext.getFilesDir()),
            eq(SESSION_ID),
            any(LogFileManager.class),
            any(UserMetadata.class),
            any(),
            eq(false));
  }

  @Test
  public void testWriteProfilingManagerInfo_dataCollectionEnabled_forwardsSettingsFlag() {
    when(mockDataCollectionArbiter.isAutomaticDataCollectionEnabled()).thenReturn(true);
    mockSettingsProvider(false, false, /* heapDumpCollectionEnabled= */ true);
    CrashlyticsController controller = createController();

    controller.writeProfilingManagerInfo(SESSION_ID, mockSettingsProvider);

    verify(mockSessionReportingCoordinator)
        .persistProfilingManagerInfo(
            eq(testContext.getFilesDir()),
            eq(SESSION_ID),
            any(LogFileManager.class),
            any(UserMetadata.class),
            any(),
            eq(true));
  }

  @Test
  public void testUpdateProfilingManagerRegistration_disabled_unregistersAndDoesNotRegister() {
    when(mockDataCollectionArbiter.isAutomaticDataCollectionEnabled()).thenReturn(false);

    CrashlyticsController controller =
        createController(
            testContext,
            mockDataCollectionArbiter,
            mockUploadMetadataStore,
            mockScottyUploader,
            mockProfilingManagerRegistrar);

    controller.updateProfilingManagerRegistration(Runnable::run);

    verify(mockProfilingManagerRegistrar).unregister();
    verify(mockProfilingManagerRegistrar, never()).register(any());
  }

  @Test
  public void testUpdateProfilingManagerRegistration_toggleConsent_registersAndUnregistersCleanly()
      throws Exception {
    when(firebaseApp.getApplicationContext()).thenReturn(testContext);
    DataCollectionArbiter arbiter = new DataCollectionArbiter(firebaseApp);
    arbiter.setCrashlyticsDataCollectionEnabled(true);

    CrashlyticsController controller =
        createController(
            testContext,
            arbiter,
            mockUploadMetadataStore,
            mockScottyUploader,
            mockProfilingManagerRegistrar);

    // Initial startup with collection enabled registers triggers.
    controller.enableProfilingManagerListener();
    awaitWorker(crashlyticsWorkers.diskWrite);
    verify(mockProfilingManagerRegistrar, times(1)).register(any(Executor.class));
    verify(mockProfilingManagerRegistrar, never()).unregister();

    // Opt out at runtime removes persisted triggers.
    arbiter.setCrashlyticsDataCollectionEnabled(false);
    controller.updateProfilingManagerRegistration();
    awaitWorker(crashlyticsWorkers.diskWrite);
    verify(mockProfilingManagerRegistrar, times(1)).unregister();

    // Opt back in at runtime re-registers triggers.
    arbiter.setCrashlyticsDataCollectionEnabled(true);
    controller.updateProfilingManagerRegistration();
    awaitWorker(crashlyticsWorkers.diskWrite);
    verify(mockProfilingManagerRegistrar, times(2)).register(any(Executor.class));
  }

  private void mockSettingsProvider(boolean collectAnrs, boolean collectBuildIds) {
    mockSettingsProvider(collectAnrs, collectBuildIds, /* heapDumpCollectionEnabled= */ false);
  }

  private void mockSettingsProvider(
      boolean collectAnrs, boolean collectBuildIds, boolean heapDumpCollectionEnabled) {
    Settings settings = createSettings(collectAnrs, collectBuildIds, heapDumpCollectionEnabled);
    when(mockSettingsProvider.getSettingsSync()).thenReturn(settings);
    when(mockSettingsProvider.getSettingsAsync()).thenReturn(Tasks.forResult(settings));
  }

  private static Settings createSettings(
      boolean collectAnrs, boolean collectBuildIds, boolean heapDumpCollectionEnabled) {
    return new Settings(
        0,
        new SessionData(4, 4),
        new FeatureFlagData(true, collectAnrs, collectBuildIds),
        3,
        0,
        1.0,
        1.0,
        1,
        new Profiling(heapDumpCollectionEnabled));
  }

  private Context createContextWithMeteredNetwork(boolean isMetered) {
    ConnectivityManager mockConnectivityManager = mock(ConnectivityManager.class);
    when(mockConnectivityManager.isActiveNetworkMetered()).thenReturn(isMetered);
    return new ContextWrapper(testContext) {
      @Override
      public Object getSystemService(String name) {
        if (Context.CONNECTIVITY_SERVICE.equals(name)) {
          return mockConnectivityManager;
        }
        return super.getSystemService(name);
      }
    };
  }

  /** Creates a new CrashlyticsController with default options and opens a session. */
  private CrashlyticsController createController() {
    ResumableUploadMetadataStore store =
        new ResumableUploadMetadataStore(testContext, testFileStore);
    return createController(
        testContext,
        mockDataCollectionArbiter,
        store,
        mockScottyUploader,
        mockProfilingManagerRegistrar);
  }

  private CrashlyticsController createController(
      Context context,
      DataCollectionArbiter arbiter,
      ResumableUploadMetadataStore store,
      ScottyUploader scottyUploader) {
    return createController(context, arbiter, store, scottyUploader, mockProfilingManagerRegistrar);
  }

  private CrashlyticsController createController(
      Context context,
      DataCollectionArbiter arbiter,
      ResumableUploadMetadataStore store,
      ScottyUploader scottyUploader,
      CrashlyticsController.ProfilingManagerRegistrar profilingManagerRegistrar) {
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

    final CrashlyticsController controller =
        new CrashlyticsController(
            context,
            idManager,
            arbiter,
            testFileStore,
            new CrashlyticsFileMarker(CrashlyticsCore.CRASH_MARKER_FILE_NAME, testFileStore),
            appData,
            null,
            null,
            mockSessionReportingCoordinator,
            MISSING_NATIVE_COMPONENT,
            mock(AnalyticsEventLogger.class),
            mock(CrashlyticsAppQualitySessionsSubscriber.class),
            crashlyticsWorkers,
            store,
            firebaseApp,
            scottyUploader,
            profilingManagerRegistrar);
    controller.openSession(SESSION_ID);
    return controller;
  }

  private static <T> T await(Task<T> task) throws Exception {
    long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (!task.isComplete()) {
      shadowOf(Looper.getMainLooper()).idle();
      if (System.nanoTime() >= deadlineNanos) {
        throw new AssertionError("Timed out waiting for task completion");
      }
      Thread.sleep(10);
    }
    if (!task.isSuccessful()) {
      Exception ex = task.getException();
      if (ex != null) {
        throw ex;
      }
      throw new AssertionError("Task failed without exception");
    }
    return task.getResult();
  }

  private static void awaitWorker(CrashlyticsWorker worker) throws Exception {
    await(worker.submit(() -> {}));
  }

  private void addAppExitInfo(int reason) {
    ActivityManager activityManager =
        (ActivityManager)
            ApplicationProvider.getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
    ActivityManager.RunningAppProcessInfo runningAppProcessInfo =
        activityManager.getRunningAppProcesses().get(0);
    shadowOf(activityManager)
        .addApplicationExitInfo(
            ShadowActivityManager.ApplicationExitInfoBuilder.newBuilder()
                .setRealUid(runningAppProcessInfo.pid)
                .setProcessName(runningAppProcessInfo.processName)
                .setReason(reason)
                .setStatus(1)
                .build());
  }

  private List<ApplicationExitInfo> getApplicationExitInfoList() {
    ActivityManager activityManager =
        (ActivityManager)
            ApplicationProvider.getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
    return activityManager.getHistoricalProcessExitReasons(null, 0, 0);
  }
}
