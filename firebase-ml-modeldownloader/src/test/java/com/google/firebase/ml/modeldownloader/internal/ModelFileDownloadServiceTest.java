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

package com.google.firebase.ml.modeldownloader.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.content.Intent;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.FirebaseOptions.Builder;
import com.google.firebase.ml.modeldownloader.CustomModel;
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions;
import com.google.firebase.ml.modeldownloader.FirebaseMlException;
import com.google.firebase.ml.modeldownloader.TestOnCompleteListener;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.DownloadStatus;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.ErrorCode;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.LEGACY)
public class ModelFileDownloadServiceTest {

  private static final String TEST_PROJECT_ID = "777777777777";
  private static final FirebaseOptions FIREBASE_OPTIONS =
      new Builder()
          .setApplicationId("1:123456789:android:abcdef")
          .setProjectId(TEST_PROJECT_ID)
          .build();

  private static final String MODEL_NAME = "MODEL_NAME_1";
  private static final String MODEL_HASH = "dsf324";
  public static final String MODEL_URL = "https://project.firebase.com/modelName/23424.jpg";

  private static final long URL_EXPIRATION_OLD = 1608063572000L;

  private static final long URL_EXPIRATION_FUTURE = (new Date()).getTime() + 600000;
  private static final Long DOWNLOAD_ID = 987923L;

  private CustomModel CUSTOM_MODEL_PREVIOUS_LOADED;
  private CustomModel CUSTOM_MODEL_NO_URL;
  private CustomModel CUSTOM_MODEL_URL;
  private CustomModel CUSTOM_MODEL_EXPIRED_URL;
  private CustomModel CUSTOM_MODEL_DOWNLOADING;
  CustomModel customModelDownloadComplete;

  private static final CustomModelDownloadConditions DOWNLOAD_CONDITIONS_CHARGING_IDLE =
      new CustomModelDownloadConditions.Builder().requireCharging().requireDeviceIdle().build();

  File testTempModelFile;
  File testAppModelFile;

  private final DownloadManager mockDownloadManager = mock(DownloadManager.class);
  private final ModelFileManager mockFileManager = mock(ModelFileManager.class);
  private final FirebaseMlLogger mockStatsLogger = mock(FirebaseMlLogger.class);

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  private ModelFileDownloadService modelFileDownloadService;

  private ModelFileDownloadService modelFileDownloadServiceInitialLoad;
  private SharedPreferencesUtil sharedPreferencesUtil;
  private CustomModel.Factory modelFactory;

  private MatrixCursor matrixCursor;
  FirebaseApp app;

  @Before
  public void setUp() throws IOException {
    FirebaseApp.clearInstancesForTest();
    app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), FIREBASE_OPTIONS);

    AtomicReference<ModelFileDownloadService> serviceRef = new AtomicReference<>();
    modelFactory =
        (name, modelHash, fileSize, downloadId, localFilePath, downloadUrl, downloadUrlExpiry) ->
            new CustomModel(
                serviceRef.get(),
                name,
                modelHash,
                fileSize,
                downloadId,
                localFilePath,
                downloadUrl,
                downloadUrlExpiry);
    sharedPreferencesUtil = new SharedPreferencesUtil(app, modelFactory);
    sharedPreferencesUtil.clearModelDetails(MODEL_NAME);

    modelFileDownloadService =
        new ModelFileDownloadService(
            ApplicationProvider.getApplicationContext(),
            mockDownloadManager,
            mockFileManager,
            sharedPreferencesUtil,
            mockStatsLogger,
            false,
            modelFactory);
    serviceRef.set(modelFileDownloadService);

    modelFileDownloadServiceInitialLoad =
        new ModelFileDownloadService(
            ApplicationProvider.getApplicationContext(),
            mockDownloadManager,
            mockFileManager,
            sharedPreferencesUtil,
            mockStatsLogger,
            true,
            modelFactory);

    CUSTOM_MODEL_PREVIOUS_LOADED =
        modelFactory.create(MODEL_NAME, MODEL_HASH + "2", 105, 0, "FakeFile/path.tflite");
    CUSTOM_MODEL_NO_URL = modelFactory.create(MODEL_NAME, MODEL_HASH, 100, 0);
    CUSTOM_MODEL_URL =
        modelFactory.create(MODEL_NAME, MODEL_HASH, 100, MODEL_URL, URL_EXPIRATION_FUTURE);
    CUSTOM_MODEL_EXPIRED_URL =
        modelFactory.create(MODEL_NAME, MODEL_HASH, 100, MODEL_URL, URL_EXPIRATION_OLD);
    CUSTOM_MODEL_DOWNLOADING = modelFactory.create(MODEL_NAME, MODEL_HASH, 100, DOWNLOAD_ID);

    matrixCursor = new MatrixCursor(new String[] {DownloadManager.COLUMN_STATUS});
    testTempModelFile = File.createTempFile("fakeTempFile", ".tflite");

    testAppModelFile = File.createTempFile("fakeAppFile", ".tflite");
    customModelDownloadComplete =
        modelFactory.create(MODEL_NAME, MODEL_HASH, 100, 0, testAppModelFile.getPath());
  }

  @After
  public void teardown() {
    if (testAppModelFile.isFile()) {
      testAppModelFile.delete();
    }
    if (testTempModelFile.isFile()) {
      testTempModelFile.delete();
    }
  }

  @Test
  public void downloaded_success_chargingAndIdle() throws Exception {
    Request downloadRequest = new Request(Uri.parse(CUSTOM_MODEL_URL.getDownloadUrl()));
    downloadRequest.setRequiresCharging(true);
    downloadRequest.setRequiresDeviceIdle(true);

    when(mockDownloadManager.enqueue(any())).thenReturn(DOWNLOAD_ID);
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_SUCCESSFUL});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);

    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    Task<Void> task =
        modelFileDownloadService.download(CUSTOM_MODEL_URL, DOWNLOAD_CONDITIONS_CHARGING_IDLE);

    // Complete the download
    Intent downloadCompleteIntent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
    downloadCompleteIntent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, DOWNLOAD_ID);
    app.getApplicationContext().sendBroadcast(downloadCompleteIntent);

    task.addOnCompleteListener(executor, onCompleteListener);
    onCompleteListener.await();

    assertTrue(task.isComplete());
    assertTrue(task.isSuccessful());
    assertNull(task.getResult());
    assertEquals(
        sharedPreferencesUtil.getDownloadingCustomModelDetails(MODEL_NAME),
        CUSTOM_MODEL_DOWNLOADING);

    verify(mockStatsLogger, times(1))
        .logDownloadEventWithExactDownloadTime(
            eq(CUSTOM_MODEL_DOWNLOADING), eq(ErrorCode.NO_ERROR), eq(DownloadStatus.SUCCEEDED));
    verify(mockDownloadManager, times(1)).enqueue(any());
    verify(mockDownloadManager, atLeastOnce()).query(any());
  }

  @Test
  public void downloaded_success_wifi() throws Exception {
    Request downloadRequest = new Request(Uri.parse(CUSTOM_MODEL_URL.getDownloadUrl()));
    downloadRequest.setRequiresCharging(true);
    downloadRequest.setAllowedNetworkTypes(Request.NETWORK_WIFI);

    when(mockDownloadManager.enqueue(any())).thenReturn(DOWNLOAD_ID);
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_SUCCESSFUL});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);

    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    Task<Void> task =
        modelFileDownloadService.download(
            CUSTOM_MODEL_URL, new CustomModelDownloadConditions.Builder().requireWifi().build());
    when(mockDownloadManager.remove(anyLong())).thenReturn(1);

    // Complete the download
    Intent downloadCompleteIntent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
    downloadCompleteIntent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, DOWNLOAD_ID);
    app.getApplicationContext().sendBroadcast(downloadCompleteIntent);

    task.addOnCompleteListener(executor, onCompleteListener);
    onCompleteListener.await();

    assertTrue(task.isComplete());
    assertTrue(task.isSuccessful());
    assertNull(task.getResult());

    assertEquals(
        sharedPreferencesUtil.getDownloadingCustomModelDetails(MODEL_NAME),
        CUSTOM_MODEL_DOWNLOADING);
    verify(mockStatsLogger, times(1))
        .logDownloadEventWithExactDownloadTime(
            eq(CUSTOM_MODEL_DOWNLOADING), eq(ErrorCode.NO_ERROR), eq(DownloadStatus.SUCCEEDED));
    verify(mockDownloadManager, times(1)).enqueue(any());
    verify(mockDownloadManager, atLeastOnce()).query(any());
  }

  @Test
  public void getExistingDownloadTask_matchingTask() {
    Task<Void> downloadTask =
        modelFileDownloadService.getTaskCompletionSourceInstance(99).getTask();
    assertEquals(modelFileDownloadService.getExistingDownloadTask(99), downloadTask);
  }

  @Test
  public void getExistingDownloadTask_noMatchingTask() {
    assertNull(modelFileDownloadService.getExistingDownloadTask(77));
  }

  @Test
  public void getExistingDownloadTask_noDownloadId() {
    assertNull(modelFileDownloadService.getExistingDownloadTask(0));
  }

  @Test
  public void ensureModelDownloaded_noUrl() {
    when(mockDownloadManager.enqueue(any())).thenReturn(DOWNLOAD_ID);
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_SUCCESSFUL});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);

    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    Task<Void> task = modelFileDownloadService.ensureModelDownloaded(CUSTOM_MODEL_NO_URL);

    assertTrue(task.isComplete());

    // Complete the download
    Intent downloadCompleteIntent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
    downloadCompleteIntent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, DOWNLOAD_ID);
    app.getApplicationContext().sendBroadcast(downloadCompleteIntent);

    task.addOnCompleteListener(executor, onCompleteListener);
    assertThrows(Exception.class, () -> onCompleteListener.await());

    assertTrue(task.isComplete());
    assertFalse(task.isSuccessful());

    assertNull(sharedPreferencesUtil.getDownloadingCustomModelDetails(MODEL_NAME));
    verify(mockDownloadManager, never()).enqueue(any());
    verify(mockDownloadManager, never()).query(any());
    verify(mockStatsLogger, times(1))
        .logDownloadEventWithErrorCode(
            eq(CUSTOM_MODEL_NO_URL),
            eq(false),
            eq(DownloadStatus.EXPLICITLY_REQUESTED),
            eq(ErrorCode.NO_ERROR));
  }

  @Test
  public void ensureModelDownloaded_success() throws Exception {
    when(mockDownloadManager.enqueue(any())).thenReturn(DOWNLOAD_ID);
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_SUCCESSFUL});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);

    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    Task<Void> task = modelFileDownloadService.ensureModelDownloaded(CUSTOM_MODEL_URL);

    // Complete the download
    Intent downloadCompleteIntent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
    downloadCompleteIntent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, DOWNLOAD_ID);
    app.getApplicationContext().sendBroadcast(downloadCompleteIntent);

    task.addOnCompleteListener(executor, onCompleteListener);
    onCompleteListener.await();

    assertTrue(task.isComplete());
    assertTrue(task.isSuccessful());
    assertNull(task.getResult());
    assertEquals(
        sharedPreferencesUtil.getDownloadingCustomModelDetails(MODEL_NAME),
        CUSTOM_MODEL_DOWNLOADING);
    verify(mockStatsLogger, times(1))
        .logDownloadEventWithExactDownloadTime(
            eq(CUSTOM_MODEL_DOWNLOADING), eq(ErrorCode.NO_ERROR), eq(DownloadStatus.SUCCEEDED));
    verify(mockDownloadManager, times(1)).enqueue(any());
    verify(mockDownloadManager, atLeastOnce()).query(any());

    verify(mockStatsLogger, times(1))
        .logDownloadEventWithErrorCode(
            eq(CUSTOM_MODEL_URL),
            eq(false),
            eq(DownloadStatus.EXPLICITLY_REQUESTED),
            eq(ErrorCode.NO_ERROR));
  }

  @Test
  public void ensureModelDownloaded_downloadCompletes_missingModel() throws Exception {
    when(mockDownloadManager.enqueue(any())).thenReturn(DOWNLOAD_ID);
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_SUCCESSFUL});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);

    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    Task<Void> task = modelFileDownloadService.ensureModelDownloaded(CUSTOM_MODEL_URL);

    // clear the model before completing the download
    sharedPreferencesUtil.clearModelDetails(CUSTOM_MODEL_URL.getName());

    // Complete the download
    Intent downloadCompleteIntent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
    downloadCompleteIntent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, DOWNLOAD_ID);
    app.getApplicationContext().sendBroadcast(downloadCompleteIntent);

    try {
      task.addOnCompleteListener(executor, onCompleteListener);
      onCompleteListener.await();
    } catch (FirebaseMlException ex) {
      assertEquals(ex.getCode(), FirebaseMlException.INTERNAL);
    }
    assertTrue(task.isComplete());
    assertFalse(task.isSuccessful());
    assertTrue(task.getException() instanceof FirebaseMlException);

    verify(mockDownloadManager, times(1)).enqueue(any());
    verify(mockDownloadManager, atLeastOnce()).query(any());

    verify(mockStatsLogger, times(1))
        .logDownloadEventWithErrorCode(
            eq(CUSTOM_MODEL_URL),
            eq(false),
            eq(DownloadStatus.EXPLICITLY_REQUESTED),
            eq(ErrorCode.NO_ERROR));
  }

  @Test
  public void ensureModelDownloaded_downloadFailed() {
    when(mockDownloadManager.enqueue(any())).thenReturn(DOWNLOAD_ID);
    matrixCursor =
        new MatrixCursor(
            new String[] {DownloadManager.COLUMN_STATUS, DownloadManager.COLUMN_REASON});
    matrixCursor.addRow(
        new Integer[] {DownloadManager.STATUS_FAILED, DownloadManager.ERROR_INSUFFICIENT_SPACE});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);
    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    Task<Void> task = modelFileDownloadService.ensureModelDownloaded(CUSTOM_MODEL_URL);

    try {
      // Complete the download
      Intent downloadCompleteIntent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
      downloadCompleteIntent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, DOWNLOAD_ID);
      app.getApplicationContext().sendBroadcast(downloadCompleteIntent);

      task.addOnCompleteListener(executor, onCompleteListener);
      onCompleteListener.await();
    } catch (FirebaseMlException ex) {
      assertEquals(ex.getCode(), FirebaseMlException.NOT_ENOUGH_SPACE);
    } catch (Exception ex) {
      fail("Unexpected error: " + ex.getMessage());
    }

    assertTrue(task.isComplete());
    assertFalse(task.isSuccessful());
    assertTrue(task.getException() instanceof FirebaseMlException);
    assertEquals(
        sharedPreferencesUtil.getDownloadingCustomModelDetails(MODEL_NAME),
        CUSTOM_MODEL_DOWNLOADING);

    verify(mockDownloadManager, times(1)).enqueue(any());
    verify(mockDownloadManager, atLeastOnce()).query(any());

    verify(mockStatsLogger, times(1))
        .logDownloadEventWithErrorCode(
            eq(CUSTOM_MODEL_URL),
            eq(false),
            eq(DownloadStatus.EXPLICITLY_REQUESTED),
            eq(ErrorCode.NO_ERROR));
  }

  @Test
  public void ensureModelDownloaded_downloadFailed_urlExpiry() {
    when(mockDownloadManager.enqueue(any())).thenReturn(DOWNLOAD_ID);
    matrixCursor =
        new MatrixCursor(
            new String[] {DownloadManager.COLUMN_STATUS, DownloadManager.COLUMN_REASON});
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_FAILED, 400});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);

    CustomModel justAboutToExpireModel =
        modelFactory.create(MODEL_NAME, MODEL_HASH, 100, MODEL_URL, (new Date()).getTime() + 3);

    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    Task<Void> task = modelFileDownloadService.ensureModelDownloaded(justAboutToExpireModel);

    try {
      // Complete the download
      // sleep long enough for the url to expire
      Thread.sleep(4);
      Intent downloadCompleteIntent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
      downloadCompleteIntent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, DOWNLOAD_ID);
      app.getApplicationContext().sendBroadcast(downloadCompleteIntent);

      task.addOnCompleteListener(executor, onCompleteListener);
      onCompleteListener.await();
    } catch (FirebaseMlException ex) {
      assertEquals(ex.getCode(), FirebaseMlException.DOWNLOAD_URL_EXPIRED);
    } catch (Exception ex) {
      fail("Unexpected error message: " + ex.getMessage());
    }

    assertTrue(task.isComplete());
    assertFalse(task.isSuccessful());
    assertTrue(task.getException().getMessage().contains("Retry: Expired URL"));
    assertEquals(
        sharedPreferencesUtil.getDownloadingCustomModelDetails(MODEL_NAME),
        CUSTOM_MODEL_DOWNLOADING);

    verify(mockDownloadManager, times(1)).enqueue(any());
    verify(mockDownloadManager, atLeastOnce()).query(any());

    verify(mockStatsLogger, times(1))
        .logDownloadEventWithErrorCode(
            eq(justAboutToExpireModel),
            eq(false),
            eq(DownloadStatus.EXPLICITLY_REQUESTED),
            eq(ErrorCode.NO_ERROR));
  }

  @Test
  public void ensureModelDownloaded_downloadFailed_goodUrlExpiry() {
    when(mockDownloadManager.enqueue(any())).thenReturn(DOWNLOAD_ID);
    matrixCursor =
        new MatrixCursor(
            new String[] {DownloadManager.COLUMN_STATUS, DownloadManager.COLUMN_REASON});
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_FAILED, 304});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);

    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    Task<Void> task = modelFileDownloadService.ensureModelDownloaded(CUSTOM_MODEL_URL);

    try {
      // Complete the download
      Intent downloadCompleteIntent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
      downloadCompleteIntent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, DOWNLOAD_ID);
      app.getApplicationContext().sendBroadcast(downloadCompleteIntent);

      task.addOnCompleteListener(executor, onCompleteListener);
      onCompleteListener.await();
    } catch (FirebaseMlException ex) {
      assertEquals(ex.getCode(), FirebaseMlException.INTERNAL);
    } catch (Exception ex) {
      fail("Unexpected error message: " + ex.getMessage());
    }

    assertTrue(task.isComplete());
    assertFalse(task.isSuccessful());
    assertTrue(task.getException().getMessage().contains("304"));
    assertEquals(
        sharedPreferencesUtil.getDownloadingCustomModelDetails(MODEL_NAME),
        CUSTOM_MODEL_DOWNLOADING);

    verify(mockDownloadManager, times(1)).enqueue(any());
    verify(mockDownloadManager, atLeastOnce()).query(any());

    verify(mockStatsLogger, times(1))
        .logDownloadEventWithErrorCode(
            eq(CUSTOM_MODEL_URL),
            eq(false),
            eq(DownloadStatus.EXPLICITLY_REQUESTED),
            eq(ErrorCode.NO_ERROR));
  }

  @Test
  public void ensureModelDownloaded_downloadFailed_noUrl() {
    when(mockDownloadManager.enqueue(any())).thenReturn(DOWNLOAD_ID);
    matrixCursor =
        new MatrixCursor(
            new String[] {DownloadManager.COLUMN_STATUS, DownloadManager.COLUMN_REASON});
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_FAILED, 400});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);

    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    Task<Void> task = modelFileDownloadService.ensureModelDownloaded(CUSTOM_MODEL_NO_URL);

    try {
      // Complete the download
      Intent downloadCompleteIntent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
      downloadCompleteIntent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, DOWNLOAD_ID);
      app.getApplicationContext().sendBroadcast(downloadCompleteIntent);

      task.addOnCompleteListener(executor, onCompleteListener);
      onCompleteListener.await();
    } catch (FirebaseMlException ex) {
      assertEquals(ex.getCode(), FirebaseMlException.INTERNAL);
    } catch (Exception ex) {
      fail("Unexpected error message: " + ex.getMessage());
    }

    assertTrue(task.isComplete());
    assertFalse(task.isSuccessful());
    assertTrue(task.getException().getMessage().contains("Failed to schedule"));
    assertEquals(sharedPreferencesUtil.getDownloadingCustomModelDetails(MODEL_NAME), null);

    verify(mockDownloadManager, never()).enqueue(any());

    verify(mockStatsLogger, times(1))
        .logDownloadEventWithErrorCode(
            eq(CUSTOM_MODEL_NO_URL),
            eq(false),
            eq(DownloadStatus.EXPLICITLY_REQUESTED),
            eq(ErrorCode.NO_ERROR));
  }

  @Test
  public void ensureModelDownloaded_alreadyInProgess_completed() throws Exception {
    when(mockDownloadManager.enqueue(any())).thenReturn(DOWNLOAD_ID);
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_SUCCESSFUL});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);

    // set up first request
    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    Task<Void> task = modelFileDownloadService.ensureModelDownloaded(CUSTOM_MODEL_URL);
    task.addOnCompleteListener(executor, onCompleteListener);
    assertEquals(
        sharedPreferencesUtil.getDownloadingCustomModelDetails(MODEL_NAME),
        CUSTOM_MODEL_DOWNLOADING);

    // now retry before completing
    TestOnCompleteListener<Void> onCompleteListener2 = new TestOnCompleteListener<>();
    Task<Void> task2 = modelFileDownloadService.ensureModelDownloaded(CUSTOM_MODEL_URL);
    task2.addOnCompleteListener(executor, onCompleteListener2);

    // Complete the download
    Intent downloadCompleteIntent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
    downloadCompleteIntent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, DOWNLOAD_ID);
    app.getApplicationContext().sendBroadcast(downloadCompleteIntent);

    onCompleteListener.await();
    onCompleteListener2.await();

    assertTrue(task.isComplete());
    assertTrue(task.isSuccessful());
    assertNull(task.getResult());
    assertTrue(task2.isComplete());
    assertTrue(task2.isSuccessful());
    assertNull(task2.getResult());
    assertEquals(
        sharedPreferencesUtil.getDownloadingCustomModelDetails(MODEL_NAME),
        CUSTOM_MODEL_DOWNLOADING);

    verify(mockDownloadManager, times(1)).enqueue(any());
    verify(mockDownloadManager, atLeastOnce()).query(any());
    verify(mockStatsLogger, atLeastOnce())
        .logDownloadEventWithErrorCode(
            eq(CUSTOM_MODEL_DOWNLOADING),
            eq(false),
            eq(DownloadStatus.DOWNLOADING),
            eq(ErrorCode.NO_ERROR));

    verify(mockStatsLogger, times(2))
        .logDownloadEventWithErrorCode(
            eq(CUSTOM_MODEL_URL),
            eq(false),
            eq(DownloadStatus.EXPLICITLY_REQUESTED),
            eq(ErrorCode.NO_ERROR));
  }

  @Test
  public void ensureModelDownloaded_alreadyInProgess_UrlExpired() throws Exception {
    when(mockDownloadManager.enqueue(any())).thenReturn(DOWNLOAD_ID);
    matrixCursor.addRow(new Integer[] {DownloadManager.PAUSED_WAITING_FOR_NETWORK});
    MatrixCursor matrixCursor2 =
        new MatrixCursor(
            new String[] {DownloadManager.COLUMN_STATUS, DownloadManager.COLUMN_REASON});
    matrixCursor2.addRow(new Integer[] {DownloadManager.STATUS_FAILED, 400});

    MatrixCursor matrixCursorRetry = new MatrixCursor(new String[] {DownloadManager.COLUMN_STATUS});
    matrixCursorRetry.addRow(new Integer[] {DownloadManager.STATUS_SUCCESSFUL});
    when(mockDownloadManager.query(any()))
        .thenReturn(matrixCursor) // first download query in progress - get status
        // .thenReturn(matrixCursor2) // first download failure triggered - get status
        // .thenReturn(matrixCursor2) // first download failed - check error cause
        .thenReturn(matrixCursorRetry); // second download query

    when(mockDownloadManager.remove(eq(DOWNLOAD_ID))).thenReturn(1);

    // set up the first request
    CustomModel justAboutToExpireModel =
        modelFactory.create(MODEL_NAME, MODEL_HASH, 100, MODEL_URL, (new Date()).getTime() + 30);
    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    Task<Void> task = modelFileDownloadService.ensureModelDownloaded(justAboutToExpireModel);
    task.addOnCompleteListener(executor, onCompleteListener);

    assertEquals(
        sharedPreferencesUtil.getDownloadingCustomModelDetails(MODEL_NAME),
        CUSTOM_MODEL_DOWNLOADING);

    // now retry before completing
    Task<Void> task2 = modelFileDownloadService.ensureModelDownloaded(CUSTOM_MODEL_EXPIRED_URL);
    try {
      TestOnCompleteListener<Void> onCompleteListener2 = new TestOnCompleteListener<>();
      task2.addOnCompleteListener(executor, onCompleteListener2);
      Intent downloadCompleteIntent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
      downloadCompleteIntent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, DOWNLOAD_ID);
      app.getApplicationContext().sendBroadcast(downloadCompleteIntent);
      onCompleteListener2.await();
    } catch (FirebaseMlException ex) {
      assertEquals(ex.getCode(), FirebaseMlException.DOWNLOAD_URL_EXPIRED);
    }

    verify(mockDownloadManager, times(1)).enqueue(any());
    verify(mockDownloadManager, times(1)).query(any());
    verify(mockDownloadManager, times(1)).remove(anyLong());
    verify(mockStatsLogger, times(1))
        .logDownloadEventWithErrorCode(
            eq(CUSTOM_MODEL_DOWNLOADING),
            eq(false),
            eq(DownloadStatus.SCHEDULED),
            eq(ErrorCode.NO_ERROR));
    verify(mockStatsLogger, times(1))
        .logDownloadEventWithErrorCode(
            eq(CUSTOM_MODEL_DOWNLOADING),
            eq(false),
            eq(DownloadStatus.SCHEDULED),
            eq(ErrorCode.NO_ERROR));
    verify(mockStatsLogger, times(1))
        .logDownloadFailureWithReason(
            eq(CUSTOM_MODEL_EXPIRED_URL), eq(false), eq(ErrorCode.URI_EXPIRED.getValue()));
    verify(mockStatsLogger, times(1))
        .logDownloadEventWithErrorCode(
            eq(CUSTOM_MODEL_EXPIRED_URL),
            eq(false),
            eq(DownloadStatus.EXPLICITLY_REQUESTED),
            eq(ErrorCode.NO_ERROR));
    verify(mockStatsLogger, times(1))
        .logDownloadEventWithErrorCode(
            eq(justAboutToExpireModel),
            eq(false),
            eq(DownloadStatus.EXPLICITLY_REQUESTED),
            eq(ErrorCode.NO_ERROR));
  }

  @Test
  public void scheduleModelDownload_success() throws FirebaseMlException {
    when(mockDownloadManager.enqueue(any())).thenReturn(DOWNLOAD_ID);
    Long id = modelFileDownloadService.scheduleModelDownload(CUSTOM_MODEL_URL);
    assertEquals(DOWNLOAD_ID, id);
    assertEquals(
        sharedPreferencesUtil.getDownloadingCustomModelDetails(MODEL_NAME),
        CUSTOM_MODEL_DOWNLOADING);
    verify(mockDownloadManager, times(1)).enqueue(any());
    verify(mockStatsLogger, times(1))
        .logDownloadEventWithErrorCode(
            eq(CUSTOM_MODEL_DOWNLOADING),
            eq(false),
            eq(DownloadStatus.SCHEDULED),
            eq(ErrorCode.NO_ERROR));
  }

  @Test
  public void scheduleModelDownload_noUri() throws FirebaseMlException {
    assertNull(modelFileDownloadService.scheduleModelDownload(CUSTOM_MODEL_NO_URL));
    verify(mockDownloadManager, never()).enqueue(any());
  }

  @Test
  public void scheduleModelDownload_failed() {
    when(mockDownloadManager.enqueue(any())).thenThrow(new IllegalArgumentException("bad enqueue"));
    assertThrows(
        IllegalArgumentException.class,
        () -> modelFileDownloadService.scheduleModelDownload(CUSTOM_MODEL_URL));
    assertNull(sharedPreferencesUtil.getDownloadingCustomModelDetails(MODEL_NAME));
    verify(mockDownloadManager, times(1)).enqueue(any());
  }

  @Test
  public void scheduleModelDownload_failed_expiredUrl() {
    assertThrows(
        FirebaseMlException.class,
        () -> modelFileDownloadService.scheduleModelDownload(CUSTOM_MODEL_EXPIRED_URL));
    assertNull(sharedPreferencesUtil.getDownloadingCustomModelDetails(MODEL_NAME));
    verify(mockDownloadManager, never()).enqueue(any());
  }

  @Test
  public void getDownloadStatus_NullCursor() {
    // Not found
    assertNull(modelFileDownloadService.getDownloadingModelStatusCode(0L));
    when(mockDownloadManager.query(any())).thenReturn(null);
    assertNull(modelFileDownloadService.getDownloadingModelStatusCode(DOWNLOAD_ID));
  }

  @Test
  public void getDownloadStatus_Success() {
    // Not found
    assertNull(modelFileDownloadService.getDownloadingModelStatusCode(0L));
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_SUCCESSFUL});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);
    assertTrue(
        modelFileDownloadService.getDownloadingModelStatusCode(DOWNLOAD_ID)
            == DownloadManager.STATUS_SUCCESSFUL);
  }

  @Test
  public void maybeCheckDownloadingComplete_downloadComplete() throws Exception {
    sharedPreferencesUtil.setDownloadingCustomModelDetails(CUSTOM_MODEL_DOWNLOADING);
    assertNull(modelFileDownloadService.getDownloadingModelStatusCode(0L));
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_SUCCESSFUL});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);
    when(mockDownloadManager.openDownloadedFile(anyLong()))
        .thenReturn(
            ParcelFileDescriptor.open(testTempModelFile, ParcelFileDescriptor.MODE_READ_ONLY));
    when(mockDownloadManager.remove(eq(DOWNLOAD_ID))).thenReturn(1);

    when(mockFileManager.moveModelToDestinationFolder(any(), any())).thenReturn(testAppModelFile);

    modelFileDownloadService.maybeCheckDownloadingComplete();

    assertEquals(
        sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME), customModelDownloadComplete);
    verify(mockDownloadManager, times(3)).query(any());
    verify(mockDownloadManager, times(1)).remove(anyLong());
  }

  @Test
  public void maybeCheckDownloadingComplete_downloadFailed() throws Exception {
    sharedPreferencesUtil.setDownloadingCustomModelDetails(CUSTOM_MODEL_DOWNLOADING);
    assertNull(modelFileDownloadService.getDownloadingModelStatusCode(0L));
    matrixCursor =
        new MatrixCursor(
            new String[] {DownloadManager.COLUMN_STATUS, DownloadManager.COLUMN_REASON});
    matrixCursor.addRow(
        new Integer[] {DownloadManager.STATUS_FAILED, DownloadManager.ERROR_INSUFFICIENT_SPACE});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);
    when(mockDownloadManager.openDownloadedFile(anyLong()))
        .thenReturn(
            ParcelFileDescriptor.open(testTempModelFile, ParcelFileDescriptor.MODE_READ_ONLY));
    when(mockDownloadManager.remove(eq(DOWNLOAD_ID))).thenReturn(1);

    when(mockFileManager.moveModelToDestinationFolder(any(), any())).thenReturn(testAppModelFile);

    modelFileDownloadService.maybeCheckDownloadingComplete();

    assertNull(sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME));
    verify(mockDownloadManager, times(4)).query(any());
    verify(mockDownloadManager, times(1)).remove(anyLong());
    verify(mockStatsLogger, times(1))
        .logDownloadFailureWithReason(
            eq(CUSTOM_MODEL_DOWNLOADING), eq(false), eq(DownloadManager.ERROR_INSUFFICIENT_SPACE));
  }

  @Test
  public void maybeCheckDownloadingComplete_secondDownloadFailed() throws Exception {
    sharedPreferencesUtil.setLoadedCustomModelDetails(CUSTOM_MODEL_PREVIOUS_LOADED);
    sharedPreferencesUtil.setDownloadingCustomModelDetails(CUSTOM_MODEL_DOWNLOADING);
    assertNull(modelFileDownloadService.getDownloadingModelStatusCode(0L));
    matrixCursor =
        new MatrixCursor(
            new String[] {DownloadManager.COLUMN_STATUS, DownloadManager.COLUMN_REASON});
    matrixCursor.addRow(
        new Integer[] {DownloadManager.STATUS_FAILED, DownloadManager.ERROR_FILE_ERROR});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);
    when(mockDownloadManager.openDownloadedFile(anyLong()))
        .thenReturn(
            ParcelFileDescriptor.open(testTempModelFile, ParcelFileDescriptor.MODE_READ_ONLY));
    when(mockDownloadManager.remove(eq(DOWNLOAD_ID))).thenReturn(1);

    when(mockFileManager.moveModelToDestinationFolder(any(), any())).thenReturn(testAppModelFile);

    modelFileDownloadService.maybeCheckDownloadingComplete();

    assertEquals(
        sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME), CUSTOM_MODEL_PREVIOUS_LOADED);
    verify(mockDownloadManager, times(4)).query(any());
    verify(mockDownloadManager, times(1)).remove(anyLong());
    verify(mockStatsLogger, times(1))
        .logDownloadFailureWithReason(any(), eq(false), eq(DownloadManager.ERROR_FILE_ERROR));
  }

  @Test
  public void maybeCheckDownloadingComplete_downloadInprogress() {
    sharedPreferencesUtil.setDownloadingCustomModelDetails(CUSTOM_MODEL_DOWNLOADING);
    assertNull(modelFileDownloadService.getDownloadingModelStatusCode(0L));
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_RUNNING});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);

    modelFileDownloadService.maybeCheckDownloadingComplete();
    assertEquals(
        sharedPreferencesUtil.getDownloadingCustomModelDetails(MODEL_NAME),
        CUSTOM_MODEL_DOWNLOADING);
    verify(mockDownloadManager, times(2)).query(any());
  }

  @Test
  public void maybeCheckDownloadingComplete_multipleDownloads() throws Exception {
    sharedPreferencesUtil.setDownloadingCustomModelDetails(CUSTOM_MODEL_DOWNLOADING);
    String secondModelName = "secondModelName";
    CustomModel downloading2 =
        modelFactory.create(secondModelName, MODEL_HASH, 100, DOWNLOAD_ID + 1);
    sharedPreferencesUtil.setDownloadingCustomModelDetails(downloading2);

    assertNull(modelFileDownloadService.getDownloadingModelStatusCode(0L));
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_SUCCESSFUL});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);
    when(mockDownloadManager.openDownloadedFile(anyLong()))
        .thenReturn(
            ParcelFileDescriptor.open(testTempModelFile, ParcelFileDescriptor.MODE_READ_ONLY));
    when(mockDownloadManager.remove(anyLong())).thenReturn(1);

    when(mockFileManager.moveModelToDestinationFolder(any(), any())).thenReturn(testAppModelFile);

    modelFileDownloadService.maybeCheckDownloadingComplete();

    assertEquals(
        sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME), customModelDownloadComplete);
    assertEquals(
        sharedPreferencesUtil.getCustomModelDetails(secondModelName),
        modelFactory.create(secondModelName, MODEL_HASH, 100, 0, testAppModelFile.getPath()));
    verify(mockDownloadManager, times(5)).query(any());
    verify(mockDownloadManager, times(2)).remove(anyLong());
  }

  @Test
  public void maybeCheckDownloadingComplete_noDownloadsInProgress() {
    modelFileDownloadService.maybeCheckDownloadingComplete();
    verify(mockDownloadManager, never()).query(any());
    verify(mockDownloadManager, never()).remove(anyLong());
  }

  @Test
  public void loadNewlyDownloadedModelFile_successFilePresent()
      throws FirebaseMlException, FileNotFoundException {
    // Not found
    assertNull(modelFileDownloadService.getDownloadingModelStatusCode(0L));
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_SUCCESSFUL});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);
    when(mockDownloadManager.openDownloadedFile(anyLong()))
        .thenReturn(
            ParcelFileDescriptor.open(testTempModelFile, ParcelFileDescriptor.MODE_READ_ONLY));
    when(mockDownloadManager.remove(anyLong())).thenReturn(1);

    when(mockFileManager.moveModelToDestinationFolder(any(), any())).thenReturn(testAppModelFile);

    assertEquals(
        modelFileDownloadService.loadNewlyDownloadedModelFile(CUSTOM_MODEL_DOWNLOADING),
        testAppModelFile);

    CustomModel retrievedModel = sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME);
    assertEquals(retrievedModel, customModelDownloadComplete);
    verify(mockDownloadManager, times(1)).remove(anyLong());
    verify(mockFileManager, never()).deleteNonLatestCustomModels();
    verify(mockStatsLogger, times(1))
        .logDownloadEventWithErrorCode(
            eq(CUSTOM_MODEL_DOWNLOADING),
            eq(true),
            eq(DownloadStatus.SUCCEEDED),
            eq(ErrorCode.NO_ERROR));
  }

  @Test
  public void loadNewlyDownloadedModelFile_successNoFile()
      throws FileNotFoundException, FirebaseMlException {
    // Not found
    assertNull(modelFileDownloadService.getDownloadingModelStatusCode(0L));
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_SUCCESSFUL});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);
    doThrow(new FileNotFoundException("File not found."))
        .when(mockDownloadManager)
        .openDownloadedFile(anyLong());
    when(mockDownloadManager.remove(anyLong())).thenReturn(1);

    assertNull(modelFileDownloadService.loadNewlyDownloadedModelFile(CUSTOM_MODEL_DOWNLOADING));
    assertNull(sharedPreferencesUtil.getDownloadingCustomModelDetails(MODEL_NAME));
    verify(mockDownloadManager, times(1)).remove(anyLong());
    verify(mockFileManager, never()).deleteNonLatestCustomModels();
    verify(mockStatsLogger, times(1))
        .logDownloadEventWithErrorCode(
            eq(CUSTOM_MODEL_DOWNLOADING),
            eq(true),
            eq(DownloadStatus.SUCCEEDED),
            eq(ErrorCode.NO_ERROR));
  }

  @Test
  public void loadNewlyDownloadedModelFile_Running() throws FirebaseMlException {
    // Not found
    assertNull(modelFileDownloadService.getDownloadingModelStatusCode(0L));
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_RUNNING});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);
    assertNull(modelFileDownloadService.loadNewlyDownloadedModelFile(CUSTOM_MODEL_DOWNLOADING));
    assertNull(sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME));
    verify(mockDownloadManager, never()).remove(anyLong());
    verify(mockFileManager, never()).deleteNonLatestCustomModels();
    verify(mockStatsLogger, never())
        .logDownloadEventWithErrorCode(any(), anyBoolean(), any(), any());
  }

  @Test
  public void loadNewlyDownloadedModelFile_Failed() throws FirebaseMlException {
    // Not found
    assertNull(modelFileDownloadService.getDownloadingModelStatusCode(0L));
    matrixCursor =
        new MatrixCursor(
            new String[] {DownloadManager.COLUMN_STATUS, DownloadManager.COLUMN_REASON});
    matrixCursor.addRow(
        new Integer[] {DownloadManager.STATUS_FAILED, DownloadManager.ERROR_INSUFFICIENT_SPACE});

    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);
    when(mockDownloadManager.remove(anyLong())).thenReturn(1);

    assertNull(modelFileDownloadService.loadNewlyDownloadedModelFile(CUSTOM_MODEL_DOWNLOADING));
    assertNull(sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME));
    verify(mockDownloadManager, times(1)).remove(anyLong());
    verify(mockStatsLogger, times(1))
        .logDownloadFailureWithReason(
            eq(CUSTOM_MODEL_DOWNLOADING), eq(false), eq(DownloadManager.ERROR_INSUFFICIENT_SPACE));
    verify(mockFileManager, never()).deleteNonLatestCustomModels();
  }

  @Test
  public void loadNewlyDownloadedModelFile_initialLoad_successFilePresent()
      throws FirebaseMlException, FileNotFoundException {
    // Not found
    assertNull(modelFileDownloadServiceInitialLoad.getDownloadingModelStatusCode(0L));
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_SUCCESSFUL});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);
    when(mockDownloadManager.openDownloadedFile(anyLong()))
        .thenReturn(
            ParcelFileDescriptor.open(testTempModelFile, ParcelFileDescriptor.MODE_READ_ONLY));
    when(mockDownloadManager.remove(anyLong())).thenReturn(1);

    when(mockFileManager.moveModelToDestinationFolder(any(), any())).thenReturn(testAppModelFile);

    assertEquals(
        modelFileDownloadServiceInitialLoad.loadNewlyDownloadedModelFile(CUSTOM_MODEL_DOWNLOADING),
        testAppModelFile);

    // second attempt should not call deleteNonLatestCustomModels a second time.
    assertEquals(
        modelFileDownloadServiceInitialLoad.loadNewlyDownloadedModelFile(CUSTOM_MODEL_DOWNLOADING),
        testAppModelFile);

    CustomModel retrievedModel = sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME);
    assertEquals(retrievedModel, customModelDownloadComplete);
    verify(mockDownloadManager, times(2)).remove(anyLong());
    verify(mockFileManager, times(1)).deleteNonLatestCustomModels();
    verify(mockStatsLogger, times(2))
        .logDownloadEventWithErrorCode(
            eq(CUSTOM_MODEL_DOWNLOADING),
            eq(true),
            eq(DownloadStatus.SUCCEEDED),
            eq(ErrorCode.NO_ERROR));
  }
}
