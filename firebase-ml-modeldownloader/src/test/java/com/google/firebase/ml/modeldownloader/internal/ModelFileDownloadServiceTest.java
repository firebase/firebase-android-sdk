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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
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
import com.google.firebase.ml.modeldownloader.TestOnCompleteListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.After;
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

  private static final Long DOWNLOAD_ID = 987923L;

  private static final CustomModel CUSTOM_MODEL_NO_URL =
      new CustomModel(MODEL_NAME, MODEL_HASH, 100, 0);
  private static final CustomModel CUSTOM_MODEL_URL =
      new CustomModel(MODEL_NAME, MODEL_HASH, 100, MODEL_URL, URL_EXPIRATION);
  private static final CustomModel CUSTOM_MODEL_DOWNLOADING =
      new CustomModel(MODEL_NAME, MODEL_HASH, 100, DOWNLOAD_ID);
  CustomModel customModelDownloadComplete;

  private static final CustomModelDownloadConditions DOWNLOAD_CONDITIONS_CHARGING_IDLE =
      new CustomModelDownloadConditions.Builder().requireCharging().requireDeviceIdle().build();

  File testTempModelFile;
  File testAppModelFile;

  private ModelFileDownloadService modelFileDownloadService;
  private SharedPreferencesUtil sharedPreferencesUtil;
  @Mock DownloadManager mockDownloadManager;
  @Mock ModelFileManager mockFileManager;

  ExecutorService executor;
  private MatrixCursor matrixCursor;
  FirebaseApp app;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    FirebaseApp.clearInstancesForTest();
    app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), FIREBASE_OPTIONS);

    executor = Executors.newSingleThreadExecutor();
    sharedPreferencesUtil = new SharedPreferencesUtil(app);
    sharedPreferencesUtil.clearModelDetails(MODEL_NAME);

    modelFileDownloadService =
        new ModelFileDownloadService(
            app, mockDownloadManager, mockFileManager, sharedPreferencesUtil);

    matrixCursor = new MatrixCursor(new String[] {DownloadManager.COLUMN_STATUS});
    try {
      testTempModelFile = File.createTempFile("fakeTempFile", ".tflite");

      testAppModelFile = File.createTempFile("fakeAppFile", ".tflite");
      customModelDownloadComplete =
          new CustomModel(MODEL_NAME, MODEL_HASH, 100, 0, testAppModelFile.getPath());
    } catch (IOException ex) {
      System.out.println("Error creating test files");
    }
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
    verify(mockDownloadManager, times(1)).enqueue(any());
    verify(mockDownloadManager, atLeastOnce()).query(any());
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

    verify(mockDownloadManager, times(1)).enqueue(any());
    verify(mockDownloadManager, atLeastOnce()).query(any());
  }

  @Test
  public void ensureModelDownloaded_downloadFailed() {
    when(mockDownloadManager.enqueue(any())).thenReturn(DOWNLOAD_ID);
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_FAILED});
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
    } catch (Exception ex) {
      assertTrue(ex.getMessage().contains("Failed"));
    }

    assertTrue(task.isComplete());
    assertFalse(task.isSuccessful());
    assertTrue(task.getException().getMessage().contains("Failed"));
    assertEquals(
        sharedPreferencesUtil.getDownloadingCustomModelDetails(MODEL_NAME),
        CUSTOM_MODEL_DOWNLOADING);

    verify(mockDownloadManager, times(1)).enqueue(any());
    verify(mockDownloadManager, atLeastOnce()).query(any());
  }

  @Test
  public void scheduleModelDownload_success() {
    when(mockDownloadManager.enqueue(any())).thenReturn(DOWNLOAD_ID);
    Long id = modelFileDownloadService.scheduleModelDownload(CUSTOM_MODEL_URL);
    assertEquals(DOWNLOAD_ID, id);
    assertEquals(
        sharedPreferencesUtil.getDownloadingCustomModelDetails(MODEL_NAME),
        CUSTOM_MODEL_DOWNLOADING);
    verify(mockDownloadManager, times(1)).enqueue(any());
  }

  @Test
  public void scheduleModelDownload_noUri() {
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

    when(mockFileManager.moveModelToDestinationFolder(any(), any())).thenReturn(testAppModelFile);

    modelFileDownloadService.maybeCheckDownloadingComplete();

    assertEquals(
        sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME), customModelDownloadComplete);
    verify(mockDownloadManager, times(3)).query(any());
  }

  @Test
  public void maybeCheckDownloadingComplete_downloadInprogress() throws Exception {
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
    CustomModel downloading2 = new CustomModel(secondModelName, MODEL_HASH, 100, DOWNLOAD_ID + 1);
    sharedPreferencesUtil.setDownloadingCustomModelDetails(downloading2);

    assertNull(modelFileDownloadService.getDownloadingModelStatusCode(0L));
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_SUCCESSFUL});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);
    when(mockDownloadManager.openDownloadedFile(anyLong()))
        .thenReturn(
            ParcelFileDescriptor.open(testTempModelFile, ParcelFileDescriptor.MODE_READ_ONLY));

    when(mockFileManager.moveModelToDestinationFolder(any(), any())).thenReturn(testAppModelFile);

    modelFileDownloadService.maybeCheckDownloadingComplete();

    assertEquals(
        sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME), customModelDownloadComplete);
    assertEquals(
        sharedPreferencesUtil.getCustomModelDetails(secondModelName),
        new CustomModel(secondModelName, MODEL_HASH, 100, 0, testAppModelFile.getPath()));
    verify(mockDownloadManager, times(5)).query(any());
  }

  @Test
  public void maybeCheckDownloadingComplete_noDownloadsInProgress() throws Exception {
    modelFileDownloadService.maybeCheckDownloadingComplete();
    verify(mockDownloadManager, never()).query(any());
  }

  @Test
  public void loadNewlyDownloadedModelFile_successFilePresent() throws Exception {
    // Not found
    assertNull(modelFileDownloadService.getDownloadingModelStatusCode(0L));
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_SUCCESSFUL});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);
    when(mockDownloadManager.openDownloadedFile(anyLong()))
        .thenReturn(
            ParcelFileDescriptor.open(testTempModelFile, ParcelFileDescriptor.MODE_READ_ONLY));

    when(mockFileManager.moveModelToDestinationFolder(any(), any())).thenReturn(testAppModelFile);

    assertEquals(
        modelFileDownloadService.loadNewlyDownloadedModelFile(CUSTOM_MODEL_DOWNLOADING),
        testAppModelFile);

    CustomModel retrievedModel = sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME);
    assertEquals(retrievedModel, customModelDownloadComplete);
  }

  @Test
  public void loadNewlyDownloadedModelFile_successNoFile() throws Exception {
    // Not found
    assertNull(modelFileDownloadService.getDownloadingModelStatusCode(0L));
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_SUCCESSFUL});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);
    doThrow(new FileNotFoundException("File not found."))
        .when(mockDownloadManager)
        .openDownloadedFile(anyLong());

    assertNull(modelFileDownloadService.loadNewlyDownloadedModelFile(CUSTOM_MODEL_DOWNLOADING));
    assertNull(sharedPreferencesUtil.getDownloadingCustomModelDetails(MODEL_NAME));
  }

  @Test
  public void loadNewlyDownloadedModelFile_Running() throws Exception {
    // Not found
    assertNull(modelFileDownloadService.getDownloadingModelStatusCode(0L));
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_RUNNING});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);
    assertNull(modelFileDownloadService.loadNewlyDownloadedModelFile(CUSTOM_MODEL_DOWNLOADING));
    assertNull(sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME));
  }

  @Test
  public void loadNewlyDownloadedModelFile_Failed() throws Exception {
    // Not found
    assertNull(modelFileDownloadService.getDownloadingModelStatusCode(0L));
    matrixCursor.addRow(new Integer[] {DownloadManager.STATUS_FAILED});
    when(mockDownloadManager.query(any())).thenReturn(matrixCursor);
    assertNull(modelFileDownloadService.loadNewlyDownloadedModelFile(CUSTOM_MODEL_DOWNLOADING));
    assertNull(sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME));
  }
}
