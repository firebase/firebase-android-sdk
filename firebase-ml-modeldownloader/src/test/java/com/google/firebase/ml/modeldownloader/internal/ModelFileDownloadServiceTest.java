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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.content.Intent;
import android.database.MatrixCursor;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.FirebaseOptions.Builder;
import com.google.firebase.ml.modeldownloader.CustomModel;
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions;
import com.google.firebase.ml.modeldownloader.TestOnCompleteListener;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
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
  private static final long URL_EXPIRATION = 604800L;

  CustomModel CUSTOM_MODEL_NO_URL = new CustomModel(MODEL_NAME, MODEL_HASH, 100, 0);
  CustomModel CUSTOM_MODEL_URL =
      new CustomModel(MODEL_NAME, MODEL_HASH, 100, MODEL_URL, URL_EXPIRATION);
  CustomModel CUSTOM_MODEL_DOWNLOADING = new CustomModel(MODEL_NAME, MODEL_HASH, 100, DOWNLOAD_ID);
  private static final Long DOWNLOAD_ID = 987923L;

  CustomModelDownloadConditions DOWNLOAD_CONDITIONS_CHARGING_IDLE =
      new CustomModelDownloadConditions.Builder().requireCharging().requireDeviceIdle().build();

  private File testModelFile;
  private ModelFileDownloadService modelFileDownloadService;
  @Mock SharedPreferencesUtil sharedPreferencesUtil;
  @Mock DownloadManager downloadManager;

  ExecutorService executor;
  ModelFileManager fileManager;
  private MatrixCursor matrixCursor;
  FirebaseApp app;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    FirebaseApp.clearInstancesForTest();
    app =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApplicationId("1:123456789:android:abcdef")
                .setProjectId(TEST_PROJECT_ID)
                .build());

    executor = Executors.newSingleThreadExecutor();
    fileManager = new ModelFileManager(app);
    modelFileDownloadService =
        new ModelFileDownloadService(
            app, downloadManager, executor, fileManager, sharedPreferencesUtil);

    matrixCursor = new MatrixCursor(new String[] {DownloadManager.COLUMN_STATUS});
  }

  @Test
  public void downloaded_success_chargingAndIdle() throws Exception {
    doNothing()
        .when(sharedPreferencesUtil)
        .setDownloadingCustomModelDetails(eq(CUSTOM_MODEL_DOWNLOADING));

    Request downloadRequest = new Request(Uri.parse(CUSTOM_MODEL_URL.getDownloadUrl()));
    downloadRequest.setRequiresCharging(true);
    downloadRequest.setRequiresDeviceIdle(true);

    when(downloadManager.enqueue(any())).thenReturn(DOWNLOAD_ID);
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_SUCCESSFUL});
    when(downloadManager.query(any())).thenReturn(matrixCursor);

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

    verify(sharedPreferencesUtil, times(1)).setDownloadingCustomModelDetails(any());
    verify(downloadManager, times(1)).enqueue(any());
    verify(downloadManager, atLeastOnce()).query(any());
  }

  @Test
  public void downloaded_success_wifi() throws Exception {
    doNothing()
        .when(sharedPreferencesUtil)
        .setDownloadingCustomModelDetails(eq(CUSTOM_MODEL_DOWNLOADING));

    Request downloadRequest = new Request(Uri.parse(CUSTOM_MODEL_URL.getDownloadUrl()));
    downloadRequest.setRequiresCharging(true);
    downloadRequest.setAllowedNetworkTypes(Request.NETWORK_WIFI);

    when(downloadManager.enqueue(any())).thenReturn(DOWNLOAD_ID);
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_SUCCESSFUL});
    when(downloadManager.query(any())).thenReturn(matrixCursor);

    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    Task<Void> task =
        modelFileDownloadService.download(
            CUSTOM_MODEL_URL, new CustomModelDownloadConditions.Builder().requireWifi().build());

    // Complete the download
    Intent downloadCompleteIntent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
    downloadCompleteIntent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, DOWNLOAD_ID);
    app.getApplicationContext().sendBroadcast(downloadCompleteIntent);

    task.addOnCompleteListener(executor, onCompleteListener);
    onCompleteListener.await();

    assertTrue(task.isComplete());
    assertTrue(task.isSuccessful());
    assertNull(task.getResult());

    verify(sharedPreferencesUtil, times(1)).setDownloadingCustomModelDetails(any());
    verify(downloadManager, times(1)).enqueue(any());
    verify(downloadManager, atLeastOnce()).query(any());
  }

  @Test
  public void ensureModelDownloaded_noUrl() {
    doNothing()
        .when(sharedPreferencesUtil)
        .setDownloadingCustomModelDetails(eq(CUSTOM_MODEL_DOWNLOADING));

    when(downloadManager.enqueue(any())).thenReturn(DOWNLOAD_ID);
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_SUCCESSFUL});
    when(downloadManager.query(any())).thenReturn(matrixCursor);

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

    verify(sharedPreferencesUtil, never()).setDownloadingCustomModelDetails(any());
    verify(downloadManager, never()).enqueue(any());
    verify(downloadManager, never()).query(any());
  }

  @Test
  public void ensureModelDownloaded_success() throws Exception {
    doNothing()
        .when(sharedPreferencesUtil)
        .setDownloadingCustomModelDetails(eq(CUSTOM_MODEL_DOWNLOADING));

    when(downloadManager.enqueue(any())).thenReturn(DOWNLOAD_ID);
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_SUCCESSFUL});
    when(downloadManager.query(any())).thenReturn(matrixCursor);

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

    verify(sharedPreferencesUtil, times(1)).setDownloadingCustomModelDetails(any());
    verify(downloadManager, times(1)).enqueue(any());
    verify(downloadManager, atLeastOnce()).query(any());
  }

  @Test
  public void ensureModelDownloaded_downloadFailed() {
    doNothing()
        .when(sharedPreferencesUtil)
        .setDownloadingCustomModelDetails(eq(CUSTOM_MODEL_DOWNLOADING));

    when(downloadManager.enqueue(any())).thenReturn(DOWNLOAD_ID);
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_FAILED});
    when(downloadManager.query(any())).thenReturn(matrixCursor);

    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    Task<Void> task = modelFileDownloadService.ensureModelDownloaded(CUSTOM_MODEL_URL);

    try {
      // Complete the download
      Intent downloadCompleteIntent = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
      downloadCompleteIntent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, DOWNLOAD_ID);
      app.getApplicationContext().sendBroadcast(downloadCompleteIntent);

      task.addOnCompleteListener(executor, onCompleteListener);
      onCompleteListener.await();
    } catch (Exception ex) {
      assertTrue(ex.getMessage().contains("Failed"));
    }

    assertTrue(task.isComplete());
    assertFalse(task.isSuccessful());
    assertTrue(task.getException().getMessage().contains("Failed"));

    verify(sharedPreferencesUtil, times(1)).setDownloadingCustomModelDetails(any());
    verify(downloadManager, times(1)).enqueue(any());
    verify(downloadManager, atLeastOnce()).query(any());
  }

  @Test
  public void scheduleModelDownload_success() {
    when(downloadManager.enqueue(any())).thenReturn(DOWNLOAD_ID);
    doNothing()
        .when(sharedPreferencesUtil)
        .setDownloadingCustomModelDetails(eq(CUSTOM_MODEL_DOWNLOADING));
    Long id = modelFileDownloadService.scheduleModelDownload(CUSTOM_MODEL_URL);
    assertEquals(DOWNLOAD_ID, id);
    verify(sharedPreferencesUtil, times(1)).setDownloadingCustomModelDetails(any());
    verify(downloadManager, times(1)).enqueue(any());
  }

  @Test
  public void scheduleModelDownload_noUri() {
    assertNull(modelFileDownloadService.scheduleModelDownload(CUSTOM_MODEL_NO_URL));
    verify(downloadManager, never()).enqueue(any());
  }

  @Test
  public void scheduleModelDownload_failed() {
    when(downloadManager.enqueue(any())).thenThrow(new IllegalArgumentException("bad enqueue"));
    assertThrows(
        IllegalArgumentException.class,
        () -> modelFileDownloadService.scheduleModelDownload(CUSTOM_MODEL_URL));
    verify(sharedPreferencesUtil, never()).setDownloadingCustomModelDetails(any());
    verify(downloadManager, times(1)).enqueue(any());
  }

  @Test
  public void testGetDownloadStatus_NullCursor() {
    // Not found
    assertNull(modelFileDownloadService.getDownloadingModelStatusCode(0L));
    when(downloadManager.query(any())).thenReturn(null);
    assertNull(modelFileDownloadService.getDownloadingModelStatusCode(DOWNLOAD_ID));
  }

  @Test
  public void testGetDownloadStatus_Success() {
    // Not found
    assertNull(modelFileDownloadService.getDownloadingModelStatusCode(0L));
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_SUCCESSFUL});
    when(downloadManager.query(any())).thenReturn(matrixCursor);
    assertTrue(
        modelFileDownloadService.getDownloadingModelStatusCode(DOWNLOAD_ID)
            == DownloadManager.STATUS_SUCCESSFUL);
  }
}
