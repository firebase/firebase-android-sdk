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

package com.google.firebase.ml.modeldownloader;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.ParcelFileDescriptor;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.FirebaseOptions.Builder;
import com.google.firebase.concurrent.TestOnlyExecutors;
import com.google.firebase.ml.modeldownloader.internal.CustomModelDownloadService;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.DownloadStatus;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.ErrorCode;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogger;
import com.google.firebase.ml.modeldownloader.internal.ModelFileDownloadService;
import com.google.firebase.ml.modeldownloader.internal.ModelFileManager;
import com.google.firebase.ml.modeldownloader.internal.SharedPreferencesUtil;
import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirebaseModelDownloaderTest {

  private static final String TEST_PROJECT_ID = "777777777777";
  private static final FirebaseOptions FIREBASE_OPTIONS =
      new Builder()
          .setApplicationId("1:123456789:android:abcdef")
          .setProjectId(TEST_PROJECT_ID)
          .build();
  private static final String MODEL_NAME = "MODEL_NAME_1";
  private static final String MODEL_URL = "https://project.firebase.com/modelName/23424.jpg";
  private static final long URL_EXPIRATION = 604800L;
  private static final long DOWNLOAD_ID = 99;

  private static final CustomModelDownloadConditions DEFAULT_DOWNLOAD_CONDITIONS =
      new CustomModelDownloadConditions.Builder().build();

  private static final String MODEL_HASH = "origHash324";
  private static final String UPDATE_MODEL_HASH = "upHash564";
  private static final CustomModelDownloadConditions DOWNLOAD_CONDITIONS =
      new CustomModelDownloadConditions.Builder().requireWifi().build();

  private CustomModel CUSTOM_MODEL;
  private CustomModel ORIG_CUSTOM_MODEL_URL;
  private CustomModel UPDATE_CUSTOM_MODEL_URL;
  private CustomModel UPDATE_IN_PROGRESS_CUSTOM_MODEL;
  private CustomModel customModelUpdateLoaded;
  private CustomModel customModelLoaded;

  private final SharedPreferencesUtil mockPrefs = mock(SharedPreferencesUtil.class);
  private final ModelFileDownloadService mockFileDownloadService =
      mock(ModelFileDownloadService.class);
  private final CustomModelDownloadService mockModelDownloadService =
      mock(CustomModelDownloadService.class);
  private final ModelFileManager mockFileManager = mock(ModelFileManager.class);
  private final FirebaseMlLogger mockEventLogger = mock(FirebaseMlLogger.class);

  private final CustomModel.Factory modelFactory =
      (name, modelHash, fileSize, downloadId, localFilePath, downloadUrl, downloadUrlExpiry) ->
          new CustomModel(
              mockFileDownloadService,
              name,
              modelHash,
              fileSize,
              downloadId,
              localFilePath,
              downloadUrl,
              downloadUrlExpiry);

  private FirebaseModelDownloader firebaseModelDownloader;
  private ExecutorService executor;

  private File firstLoadTempModelFile;
  private File secondLoadTempModelFile;
  private File firstDeviceModelFile;
  private File secondDeviceModelFile;

  String expectedDestinationFolder;
  ModelFileManager fileManager;

  @Before
  public void setUp() throws Exception {
    CUSTOM_MODEL = modelFactory.create(MODEL_NAME, MODEL_HASH, 100, 0);
    ORIG_CUSTOM_MODEL_URL =
        modelFactory.create(MODEL_NAME, MODEL_HASH, 100, MODEL_URL, URL_EXPIRATION + 10L);
    UPDATE_CUSTOM_MODEL_URL =
        modelFactory.create(MODEL_NAME, UPDATE_MODEL_HASH, 100, MODEL_URL, URL_EXPIRATION + 10L);
    UPDATE_IN_PROGRESS_CUSTOM_MODEL =
        modelFactory.create(MODEL_NAME, UPDATE_MODEL_HASH, 100, DOWNLOAD_ID);

    FirebaseApp.clearInstancesForTest();
    FirebaseApp app =
        FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), FIREBASE_OPTIONS);
    executor = Executors.newSingleThreadExecutor();
    firebaseModelDownloader =
        new FirebaseModelDownloader(
            FIREBASE_OPTIONS,
            mockPrefs,
            mockFileDownloadService,
            mockModelDownloadService,
            mockFileManager,
            mockEventLogger,
            TestOnlyExecutors.background(),
            TestOnlyExecutors.blocking(),
            modelFactory);
    setUpTestingFiles(app);

    doNothing().when(mockEventLogger).logDownloadEventWithExactDownloadTime(any(), any(), any());
    doNothing().when(mockEventLogger).logDownloadFailureWithReason(any(), anyBoolean(), anyInt());
    doNothing()
        .when(mockEventLogger)
        .logDownloadEventWithErrorCode(any(), anyBoolean(), any(), any());
  }

  private void setUpTestingFiles(FirebaseApp app) throws Exception {
    fileManager =
        new ModelFileManager(
            app.getApplicationContext(),
            app.getPersistenceKey(),
            new SharedPreferencesUtil(app, modelFactory));
    final File testDir = new File(app.getApplicationContext().getNoBackupFilesDir(), "tmpModels");
    testDir.mkdirs();
    // make sure the directory is empty. Doesn't recurse into subdirs, but that's OK since
    // we're only using this directory for this test and we won't create any subdirs.
    for (File f : testDir.listFiles()) {
      if (f.isFile()) {
        f.delete();
      }
    }

    firstLoadTempModelFile = File.createTempFile("modelFile", ".tflite");
    secondLoadTempModelFile = File.createTempFile("modelFileUpdated", ".tflite");

    expectedDestinationFolder =
        new File(
                    app.getApplicationContext().getNoBackupFilesDir(),
                    ModelFileManager.CUSTOM_MODEL_ROOT_PATH)
                .getAbsolutePath()
            + "/"
            + app.getPersistenceKey()
            + "/"
            + MODEL_NAME;
    // move test files to expected locations.
    ParcelFileDescriptor fd =
        ParcelFileDescriptor.open(firstLoadTempModelFile, ParcelFileDescriptor.MODE_READ_ONLY);

    firstDeviceModelFile = fileManager.moveModelToDestinationFolder(CUSTOM_MODEL, fd);
    assertEquals(firstDeviceModelFile, new File(expectedDestinationFolder + "/0"));
    assertTrue(firstDeviceModelFile.exists());
    fd.close();

    ParcelFileDescriptor fd2 =
        ParcelFileDescriptor.open(secondLoadTempModelFile, ParcelFileDescriptor.MODE_READ_ONLY);

    secondDeviceModelFile = fileManager.moveModelToDestinationFolder(CUSTOM_MODEL, fd2);
    assertEquals(secondDeviceModelFile, new File(expectedDestinationFolder + "/1"));
    assertTrue(secondDeviceModelFile.exists());
    fd2.close();

    customModelLoaded =
        modelFactory.create(MODEL_NAME, MODEL_HASH, 100, 0, expectedDestinationFolder + "/0");
    customModelUpdateLoaded =
        modelFactory.create(MODEL_NAME, MODEL_HASH, 100, 0, expectedDestinationFolder + "/1");
  }

  @After
  public void teardown() {
    firstLoadTempModelFile.deleteOnExit();
    secondLoadTempModelFile.deleteOnExit();
    firstDeviceModelFile.deleteOnExit();
    secondDeviceModelFile.deleteOnExit();
  }

  // Todo(annzimmer) add download in progress tests when code complete.

  @Test
  public void getModel_latestModel_localExists_noUpdate() throws Exception {
    when(mockPrefs.getCustomModelDetails(eq(MODEL_NAME))).thenReturn(customModelLoaded);
    when(mockModelDownloadService.getCustomModelDetails(
            eq(TEST_PROJECT_ID), eq(MODEL_NAME), eq(MODEL_HASH)))
        .thenReturn(Tasks.forResult(null)); // no change found

    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    Task<CustomModel> task =
        firebaseModelDownloader.getModel(
            MODEL_NAME, DownloadType.LATEST_MODEL, DEFAULT_DOWNLOAD_CONDITIONS);
    task.addOnCompleteListener(executor, onCompleteListener);
    CustomModel customModel = onCompleteListener.await();

    verify(mockPrefs, times(2)).getCustomModelDetails(eq(MODEL_NAME));
    assertThat(task.isComplete()).isTrue();
    assertEquals(customModel, customModelLoaded);
  }

  @Test
  public void getModel_latestModel_localExists_noUpdate_MissingFile() throws Exception {
    // model with missing file.
    CustomModel missingFileModel =
        modelFactory.create(
            MODEL_NAME, UPDATE_MODEL_HASH, 100, 0, expectedDestinationFolder + "/4");
    when(mockPrefs.getCustomModelDetails(eq(MODEL_NAME)))
        .thenReturn(missingFileModel)
        .thenReturn(customModelUpdateLoaded);
    when(mockModelDownloadService.getCustomModelDetails(
            eq(TEST_PROJECT_ID), eq(MODEL_NAME), eq(null)))
        .thenReturn(Tasks.forResult(UPDATE_CUSTOM_MODEL_URL));

    when(mockFileDownloadService.download(any(), eq(DEFAULT_DOWNLOAD_CONDITIONS)))
        .thenReturn(Tasks.forResult(null));
    when(mockFileDownloadService.getExistingDownloadTask(0)).thenReturn(null);
    when(mockFileDownloadService.loadNewlyDownloadedModelFile(eq(customModelUpdateLoaded)))
        .thenReturn(secondDeviceModelFile);

    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    Task<CustomModel> task =
        firebaseModelDownloader.getModel(
            MODEL_NAME, DownloadType.LATEST_MODEL, DEFAULT_DOWNLOAD_CONDITIONS);
    task.addOnCompleteListener(executor, onCompleteListener);
    CustomModel customModel = onCompleteListener.await();

    verify(mockPrefs, times(4)).getCustomModelDetails(eq(MODEL_NAME));
    verify(mockPrefs, times(1)).clearModelDetails(eq(MODEL_NAME));
    verify(mockFileManager, times(1)).deleteAllModels(eq(MODEL_NAME));
    assertThat(task.isComplete()).isTrue();
    assertEquals(customModel, customModelUpdateLoaded);
  }

  @Test
  public void getModel_latestModel_localExists_noUpdate_MissingDownloadId() throws Exception {
    CustomModel badLocalModel = modelFactory.create(MODEL_NAME, UPDATE_MODEL_HASH, 100, 0);
    when(mockPrefs.getCustomModelDetails(eq(MODEL_NAME)))
        .thenReturn(badLocalModel) // getlocalModelDetails 1
        .thenReturn(null) // getCustomModelTask 1
        .thenReturn(customModelUpdateLoaded); // finishModelDownload
    when(mockPrefs.getDownloadingCustomModelDetails(eq(MODEL_NAME)))
        .thenReturn(UPDATE_IN_PROGRESS_CUSTOM_MODEL); // getLocalModelDetails 2, finishModelDownload

    when(mockModelDownloadService.getCustomModelDetails(
            eq(TEST_PROJECT_ID), eq(MODEL_NAME), eq(null)))
        .thenReturn(Tasks.forResult(UPDATE_CUSTOM_MODEL_URL));

    when(mockFileDownloadService.download(any(), eq(DEFAULT_DOWNLOAD_CONDITIONS)))
        .thenReturn(Tasks.forResult(null));
    when(mockFileDownloadService.getExistingDownloadTask(0)).thenReturn(null);
    when(mockFileDownloadService.loadNewlyDownloadedModelFile(eq(customModelUpdateLoaded)))
        .thenReturn(secondDeviceModelFile);

    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    Task<CustomModel> task =
        firebaseModelDownloader.getModel(
            MODEL_NAME, DownloadType.LATEST_MODEL, DEFAULT_DOWNLOAD_CONDITIONS);
    task.addOnCompleteListener(executor, onCompleteListener);
    CustomModel customModel = onCompleteListener.await();

    verify(mockPrefs, times(3)).getCustomModelDetails(eq(MODEL_NAME));
    verify(mockPrefs, times(1)).clearModelDetails(eq(MODEL_NAME));
    verify(mockFileManager, times(1)).deleteAllModels(eq(MODEL_NAME));
    assertThat(task.isComplete()).isTrue();
    assertEquals(customModel, customModelUpdateLoaded);
  }

  @Test
  public void getModel_latestModel_localExists_noUpdate_inProgress() throws Exception {
    // model with no file yet.
    CustomModel inProgressLocalModel = modelFactory.create(MODEL_NAME, UPDATE_MODEL_HASH, 100, 88);
    when(mockPrefs.getCustomModelDetails(eq(MODEL_NAME)))
        .thenReturn(inProgressLocalModel) // getlocalModelDetails 1
        .thenReturn(inProgressLocalModel) // getCustomModelTask 1
        .thenReturn(customModelUpdateLoaded); // finishModelDownload
    when(mockPrefs.getDownloadingCustomModelDetails(eq(MODEL_NAME)))
        .thenReturn(inProgressLocalModel); // getLocalModelDetails 2, finishModelDownload

    when(mockModelDownloadService.getCustomModelDetails(
            eq(TEST_PROJECT_ID), eq(MODEL_NAME), eq(UPDATE_MODEL_HASH)))
        .thenReturn(Tasks.forResult(null));
    when(mockFileDownloadService.getExistingDownloadTask(88)).thenReturn(Tasks.forResult(null));
    when(mockFileDownloadService.download(any(), eq(DEFAULT_DOWNLOAD_CONDITIONS)))
        .thenReturn(Tasks.forResult(null));
    when(mockFileDownloadService.loadNewlyDownloadedModelFile(eq(customModelUpdateLoaded)))
        .thenReturn(secondDeviceModelFile);

    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    Task<CustomModel> task =
        firebaseModelDownloader.getModel(
            MODEL_NAME, DownloadType.LATEST_MODEL, DEFAULT_DOWNLOAD_CONDITIONS);
    task.addOnCompleteListener(executor, onCompleteListener);
    CustomModel customModel = onCompleteListener.await();

    verify(mockPrefs, times(3)).getCustomModelDetails(eq(MODEL_NAME));
    verify(mockPrefs, never()).clearModelDetails(eq(MODEL_NAME));
    verify(mockFileManager, never()).deleteAllModels(eq(MODEL_NAME));
    verify(mockFileDownloadService, times(1)).getExistingDownloadTask(eq(88L));
    assertThat(task.isComplete()).isTrue();
    assertEquals(customModel, customModelUpdateLoaded);
  }

  @Test
  public void getModel_latestModel_localExists_sameHash() throws Exception {
    when(mockPrefs.getCustomModelDetails(eq(MODEL_NAME))).thenReturn(customModelLoaded);
    when(mockModelDownloadService.getCustomModelDetails(
            eq(TEST_PROJECT_ID), eq(MODEL_NAME), eq(MODEL_HASH)))
        .thenReturn(Tasks.forResult(ORIG_CUSTOM_MODEL_URL)); // would normally return null

    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    Task<CustomModel> task =
        firebaseModelDownloader.getModel(
            MODEL_NAME, DownloadType.LATEST_MODEL, DEFAULT_DOWNLOAD_CONDITIONS);
    task.addOnCompleteListener(executor, onCompleteListener);
    CustomModel customModel = onCompleteListener.await();

    verify(mockPrefs, times(2)).getCustomModelDetails(eq(MODEL_NAME));
    assertThat(task.isComplete()).isTrue();
    assertEquals(customModel, customModelLoaded);
  }

  @Test
  public void getModel_latestModel_localExists_UpdateFound() throws Exception {
    when(mockPrefs.getCustomModelDetails(eq(MODEL_NAME)))
        .thenReturn(customModelLoaded)
        .thenReturn(customModelLoaded)
        .thenReturn(customModelUpdateLoaded);

    when(mockModelDownloadService.getCustomModelDetails(
            eq(TEST_PROJECT_ID), eq(MODEL_NAME), eq(MODEL_HASH)))
        .thenReturn(Tasks.forResult(UPDATE_CUSTOM_MODEL_URL));
    when(mockFileDownloadService.download(any(), eq(DEFAULT_DOWNLOAD_CONDITIONS)))
        .thenReturn(Tasks.forResult(null));
    when(mockFileDownloadService.loadNewlyDownloadedModelFile(eq(customModelUpdateLoaded)))
        .thenReturn(secondDeviceModelFile);

    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    Task<CustomModel> task =
        firebaseModelDownloader.getModel(
            MODEL_NAME, DownloadType.LATEST_MODEL, DEFAULT_DOWNLOAD_CONDITIONS);
    task.addOnCompleteListener(executor, onCompleteListener);
    CustomModel customModel = onCompleteListener.await();

    verify(mockPrefs, times(4)).getCustomModelDetails(eq(MODEL_NAME));
    assertThat(task.isComplete()).isTrue();
    assertEquals(customModel, customModelUpdateLoaded);
  }

  @Test
  public void getModel_latestModel_localExists_DownloadInProgress() throws Exception {
    CustomModel customModelLoadedWithDownload =
        modelFactory.create(MODEL_NAME, MODEL_HASH, 100, 99, expectedDestinationFolder + "/0");

    when(mockPrefs.getCustomModelDetails(eq(MODEL_NAME))).thenReturn(customModelLoadedWithDownload);
    when(mockPrefs.getDownloadingCustomModelDetails(eq(MODEL_NAME)))
        .thenReturn(UPDATE_IN_PROGRESS_CUSTOM_MODEL);

    when(mockModelDownloadService.getCustomModelDetails(
            eq(TEST_PROJECT_ID), eq(MODEL_NAME), eq(MODEL_HASH)))
        .thenReturn(Tasks.forResult(UPDATE_CUSTOM_MODEL_URL));

    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    Task<CustomModel> task =
        firebaseModelDownloader.getModel(
            MODEL_NAME, DownloadType.LATEST_MODEL, DEFAULT_DOWNLOAD_CONDITIONS);
    task.addOnCompleteListener(executor, onCompleteListener);
    CustomModel customModel = onCompleteListener.await();

    verify(mockPrefs, times(2)).getCustomModelDetails(eq(MODEL_NAME));
    assertThat(task.isComplete()).isTrue();
    assertEquals(customModel, UPDATE_IN_PROGRESS_CUSTOM_MODEL);
  }

  @Test
  public void getModel_latestModel_noLocalModel() throws Exception {
    when(mockPrefs.getCustomModelDetails(eq(MODEL_NAME)))
        .thenReturn(null) // getLocalModelDetails - original call
        .thenReturn(CUSTOM_MODEL);
    when(mockModelDownloadService.getCustomModelDetails(
            eq(TEST_PROJECT_ID), eq(MODEL_NAME), eq(null)))
        .thenReturn(Tasks.forResult(ORIG_CUSTOM_MODEL_URL));
    when(mockFileDownloadService.download(any(), eq(DEFAULT_DOWNLOAD_CONDITIONS)))
        .thenReturn(Tasks.forResult(null));
    when(mockFileDownloadService.loadNewlyDownloadedModelFile(eq(customModelUpdateLoaded)))
        .thenReturn(firstDeviceModelFile);
    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    Task<CustomModel> task =
        firebaseModelDownloader.getModel(
            MODEL_NAME, DownloadType.LATEST_MODEL, DEFAULT_DOWNLOAD_CONDITIONS);
    task.addOnCompleteListener(executor, onCompleteListener);
    CustomModel customModel = onCompleteListener.await();

    verify(mockPrefs, times(4)).getCustomModelDetails(eq(MODEL_NAME));
    assertThat(task.isComplete()).isTrue();
    assertEquals(customModel, CUSTOM_MODEL);
  }

  @Test
  public void getModel_latestModel_noLocalModel_modelDownloadServiceFails() throws Exception {
    when(mockPrefs.getCustomModelDetails(eq(MODEL_NAME)))
        .thenReturn(null) // getLocalModelDetails 1
        .thenReturn(ORIG_CUSTOM_MODEL_URL);

    when(mockModelDownloadService.getCustomModelDetails(
            eq(TEST_PROJECT_ID), eq(MODEL_NAME), eq(null)))
        .thenReturn(Tasks.forResult(null));
    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    Task<CustomModel> task =
        firebaseModelDownloader.getModel(
            MODEL_NAME, DownloadType.LATEST_MODEL, DEFAULT_DOWNLOAD_CONDITIONS);
    task.addOnCompleteListener(executor, onCompleteListener);
    try {
      onCompleteListener.await();
    } catch (FirebaseMlException ex) {
      assertEquals(ex.getCode(), FirebaseMlException.INTERNAL);
    }

    verify(mockPrefs, times(2)).getCustomModelDetails(eq(MODEL_NAME));
    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isFalse();
    assertTrue(task.getException().getMessage().contains("bad state"));
  }

  @Test
  public void getModel_latestModel_noLocalModel_error() {
    when(mockPrefs.getCustomModelDetails(eq(MODEL_NAME)))
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(CUSTOM_MODEL);
    when(mockModelDownloadService.getCustomModelDetails(
            eq(TEST_PROJECT_ID), eq(MODEL_NAME), eq(null)))
        .thenReturn(Tasks.forResult(CUSTOM_MODEL));
    when(mockFileDownloadService.download(any(), eq(DEFAULT_DOWNLOAD_CONDITIONS)))
        .thenReturn(Tasks.forException(new Exception("bad download")));
    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    Task<CustomModel> task =
        firebaseModelDownloader.getModel(
            MODEL_NAME, DownloadType.LATEST_MODEL, DEFAULT_DOWNLOAD_CONDITIONS);
    task.addOnCompleteListener(executor, onCompleteListener);
    try {
      onCompleteListener.await();
    } catch (FirebaseMlException ex) {
      assertEquals(ex.getCode(), FirebaseMlException.INTERNAL);
      assertThat(ex.getMessage().contains("download failed")).isTrue();
    } catch (Exception ex) {
      fail("Unexpected error message: " + ex.getMessage());
    }

    verify(mockPrefs, times(2)).getCustomModelDetails(eq(MODEL_NAME));
    verify(mockFileDownloadService, never()).loadNewlyDownloadedModelFile(any());
    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isFalse();
    assertTrue(task.getException().getMessage().contains("download failed"));
  }

  @Test
  public void getModel_updateBackground_localExists_noUpdate() throws Exception {
    when(mockPrefs.getCustomModelDetails(eq(MODEL_NAME))).thenReturn(customModelLoaded);
    when(mockModelDownloadService.getCustomModelDetails(
            eq(TEST_PROJECT_ID), eq(MODEL_NAME), eq(MODEL_HASH)))
        .thenReturn(Tasks.forResult(null)); // no change found

    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    Task<CustomModel> task =
        firebaseModelDownloader.getModel(
            MODEL_NAME, DownloadType.LOCAL_MODEL_UPDATE_IN_BACKGROUND, DOWNLOAD_CONDITIONS);
    task.addOnCompleteListener(executor, onCompleteListener);
    CustomModel customModel = onCompleteListener.await();

    verify(mockPrefs, times(2)).getCustomModelDetails(eq(MODEL_NAME));
    assertThat(task.isComplete()).isTrue();
    assertEquals(customModel, customModelLoaded);
  }

  @Test
  public void getModel_updateBackground_localExists_sameHash() throws Exception {
    when(mockPrefs.getCustomModelDetails(eq(MODEL_NAME))).thenReturn(customModelLoaded);
    when(mockModelDownloadService.getCustomModelDetails(
            eq(TEST_PROJECT_ID), eq(MODEL_NAME), eq(MODEL_HASH)))
        .thenReturn(Tasks.forResult(ORIG_CUSTOM_MODEL_URL)); // no change found

    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    Task<CustomModel> task =
        firebaseModelDownloader.getModel(
            MODEL_NAME, DownloadType.LOCAL_MODEL_UPDATE_IN_BACKGROUND, DOWNLOAD_CONDITIONS);
    task.addOnCompleteListener(executor, onCompleteListener);
    CustomModel customModel = onCompleteListener.await();

    verify(mockPrefs, times(2)).getCustomModelDetails(eq(MODEL_NAME));
    assertThat(task.isComplete()).isTrue();
    assertEquals(customModel, customModelLoaded);
  }

  @Test
  public void getModel_updateBackground_localExists_UpdateFound() throws Exception {
    when(mockPrefs.getCustomModelDetails(eq(MODEL_NAME))).thenReturn(customModelLoaded);
    when(mockModelDownloadService.getCustomModelDetails(
            eq(TEST_PROJECT_ID), eq(MODEL_NAME), eq(MODEL_HASH)))
        .thenReturn(Tasks.forResult(UPDATE_CUSTOM_MODEL_URL));

    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    Task<CustomModel> task =
        firebaseModelDownloader.getModel(
            MODEL_NAME, DownloadType.LOCAL_MODEL_UPDATE_IN_BACKGROUND, DOWNLOAD_CONDITIONS);
    task.addOnCompleteListener(executor, onCompleteListener);
    CustomModel customModel = onCompleteListener.await();

    verify(mockPrefs, times(2)).getCustomModelDetails(eq(MODEL_NAME));
    assertThat(task.isComplete()).isTrue();
    assertEquals(customModel, customModelLoaded);
    verify(mockEventLogger)
        .logDownloadEventWithErrorCode(
            UPDATE_CUSTOM_MODEL_URL, false, DownloadStatus.UPDATE_AVAILABLE, ErrorCode.NO_ERROR);
  }

  @Test
  public void getModel_updateBackground_noLocalModel() throws Exception {
    when(mockPrefs.getCustomModelDetails(eq(MODEL_NAME))).thenReturn(null).thenReturn(CUSTOM_MODEL);
    when(mockModelDownloadService.getCustomModelDetails(
            eq(TEST_PROJECT_ID), eq(MODEL_NAME), eq(null)))
        .thenReturn(Tasks.forResult(ORIG_CUSTOM_MODEL_URL));
    when(mockFileDownloadService.download(any(), eq(DOWNLOAD_CONDITIONS)))
        .thenReturn(Tasks.forResult(null));
    when(mockFileDownloadService.loadNewlyDownloadedModelFile(eq(customModelUpdateLoaded)))
        .thenReturn(firstDeviceModelFile);
    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    Task<CustomModel> task =
        firebaseModelDownloader.getModel(
            MODEL_NAME, DownloadType.LOCAL_MODEL_UPDATE_IN_BACKGROUND, DOWNLOAD_CONDITIONS);
    task.addOnCompleteListener(executor, onCompleteListener);
    CustomModel customModel = onCompleteListener.await();

    verify(mockPrefs, times(4)).getCustomModelDetails(eq(MODEL_NAME));
    assertThat(task.isComplete()).isTrue();
    assertEquals(customModel, CUSTOM_MODEL);
  }

  @Test
  public void getModel_updateBackground_noLocalModel_error() {
    when(mockPrefs.getCustomModelDetails(eq(MODEL_NAME)))
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(CUSTOM_MODEL);
    when(mockModelDownloadService.getCustomModelDetails(
            eq(TEST_PROJECT_ID), eq(MODEL_NAME), eq(null)))
        .thenReturn(Tasks.forResult(ORIG_CUSTOM_MODEL_URL));
    when(mockFileDownloadService.download(any(), eq(DOWNLOAD_CONDITIONS)))
        .thenReturn(Tasks.forException(new Exception("bad download")));
    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    Task<CustomModel> task =
        firebaseModelDownloader.getModel(
            MODEL_NAME, DownloadType.LOCAL_MODEL_UPDATE_IN_BACKGROUND, DOWNLOAD_CONDITIONS);
    task.addOnCompleteListener(executor, onCompleteListener);
    try {
      onCompleteListener.await();
    } catch (FirebaseMlException ex) {
      assertEquals(ex.getCode(), FirebaseMlException.INTERNAL);
      assertThat(ex.getMessage().contains("download failed")).isTrue();
    } catch (Exception ex) {
      fail("Unexpected error message: " + ex.getMessage());
    }

    verify(mockPrefs, times(2)).getCustomModelDetails(eq(MODEL_NAME));
    verify(mockFileDownloadService, never()).loadNewlyDownloadedModelFile(any());
    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isFalse();
    assertTrue(task.getException().getMessage().contains("download failed"));
  }

  @Test
  public void getModel_local_localExists() throws Exception {
    when(mockPrefs.getCustomModelDetails(eq(MODEL_NAME))).thenReturn(customModelLoaded);
    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    Task<CustomModel> task =
        firebaseModelDownloader.getModel(MODEL_NAME, DownloadType.LOCAL_MODEL, DOWNLOAD_CONDITIONS);
    task.addOnCompleteListener(executor, onCompleteListener);
    CustomModel customModel = onCompleteListener.await();

    verify(mockPrefs, times(1)).getCustomModelDetails(eq(MODEL_NAME));
    assertThat(task.isComplete()).isTrue();
    assertEquals(customModel, customModelLoaded);
  }

  @Test
  public void getModel_local_noLocalModel() throws Exception {
    when(mockPrefs.getCustomModelDetails(eq(MODEL_NAME))).thenReturn(null).thenReturn(CUSTOM_MODEL);
    when(mockModelDownloadService.getCustomModelDetails(
            eq(TEST_PROJECT_ID), eq(MODEL_NAME), eq(null)))
        .thenReturn(Tasks.forResult(ORIG_CUSTOM_MODEL_URL));
    when(mockPrefs.getDownloadingCustomModelDetails(eq(MODEL_NAME)))
        .thenReturn(UPDATE_IN_PROGRESS_CUSTOM_MODEL); // getLocalModelDetails
    when(mockFileDownloadService.download(any(), eq(DOWNLOAD_CONDITIONS)))
        .thenReturn(Tasks.forResult(null));
    when(mockFileDownloadService.loadNewlyDownloadedModelFile(eq(customModelUpdateLoaded)))
        .thenReturn(firstDeviceModelFile);
    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    Task<CustomModel> task =
        firebaseModelDownloader.getModel(MODEL_NAME, DownloadType.LOCAL_MODEL, DOWNLOAD_CONDITIONS);
    task.addOnCompleteListener(executor, onCompleteListener);
    CustomModel customModel = onCompleteListener.await();

    verify(mockPrefs, times(3)).getCustomModelDetails(eq(MODEL_NAME));
    assertThat(task.isComplete()).isTrue();
    assertEquals(customModel, CUSTOM_MODEL);
  }

  @Test
  public void getModel_local_noLocalModel_urlRetry() throws Exception {
    when(mockPrefs.getCustomModelDetails(eq(MODEL_NAME))).thenReturn(null).thenReturn(CUSTOM_MODEL);
    when(mockModelDownloadService.getCustomModelDetails(
            eq(TEST_PROJECT_ID), eq(MODEL_NAME), eq(null)))
        .thenReturn(Tasks.forResult(ORIG_CUSTOM_MODEL_URL));
    when(mockModelDownloadService.getNewDownloadUrlWithExpiry(eq(TEST_PROJECT_ID), eq(MODEL_NAME)))
        .thenReturn(Tasks.forResult(ORIG_CUSTOM_MODEL_URL));
    when(mockFileDownloadService.download(any(), eq(DOWNLOAD_CONDITIONS)))
        .thenReturn(
            Tasks.forException(
                new FirebaseMlException(
                    "Retry: Expired URL", FirebaseMlException.DOWNLOAD_URL_EXPIRED)))
        .thenReturn(Tasks.forResult(null));
    when(mockFileDownloadService.loadNewlyDownloadedModelFile(eq(customModelUpdateLoaded)))
        .thenReturn(firstDeviceModelFile);
    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    Task<CustomModel> task =
        firebaseModelDownloader.getModel(MODEL_NAME, DownloadType.LOCAL_MODEL, DOWNLOAD_CONDITIONS);
    task.addOnCompleteListener(executor, onCompleteListener);
    CustomModel customModel = onCompleteListener.await();

    verify(mockPrefs, times(4)).getCustomModelDetails(eq(MODEL_NAME));
    verify(mockFileDownloadService, times(2)).download(any(), eq(DOWNLOAD_CONDITIONS));
    assertThat(task.isComplete()).isTrue();
    assertEquals(customModel, CUSTOM_MODEL);
  }

  @Test
  public void getModel_local_noLocalModel_urlRetry_maxTries() {
    when(mockPrefs.getCustomModelDetails(eq(MODEL_NAME)))
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(CUSTOM_MODEL);
    when(mockModelDownloadService.getNewDownloadUrlWithExpiry(eq(TEST_PROJECT_ID), eq(MODEL_NAME)))
        .thenReturn(Tasks.forResult(ORIG_CUSTOM_MODEL_URL));
    when(mockModelDownloadService.getCustomModelDetails(
            eq(TEST_PROJECT_ID), eq(MODEL_NAME), eq(null)))
        .thenReturn(Tasks.forResult(ORIG_CUSTOM_MODEL_URL));
    when(mockFileDownloadService.download(any(), eq(DOWNLOAD_CONDITIONS)))
        .thenReturn(
            Tasks.forException(
                new FirebaseMlException(
                    "Retry: Expired URL", FirebaseMlException.DOWNLOAD_URL_EXPIRED)));
    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    Task<CustomModel> task =
        firebaseModelDownloader.getModel(MODEL_NAME, DownloadType.LOCAL_MODEL, DOWNLOAD_CONDITIONS);
    task.addOnCompleteListener(executor, onCompleteListener);
    try {
      onCompleteListener.await();
    } catch (FirebaseMlException ex) {
      assertEquals(ex.getCode(), FirebaseMlException.DOWNLOAD_URL_EXPIRED);
      assertThat(ex.getMessage().contains("multiple attempts")).isTrue();
    } catch (Exception ex) {
      fail("Unexpected error message: " + ex.getMessage());
    }

    verify(mockPrefs, times(2)).getCustomModelDetails(eq(MODEL_NAME));
    verify(mockFileDownloadService, times(3)).download(any(), eq(DOWNLOAD_CONDITIONS));
    verify(mockFileDownloadService, never()).loadNewlyDownloadedModelFile(any());
    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isFalse();
  }

  @Test
  public void getModel_local_noLocalModel_error() {
    when(mockPrefs.getCustomModelDetails(eq(MODEL_NAME)))
        .thenReturn(null)
        .thenReturn(null)
        .thenReturn(CUSTOM_MODEL);
    when(mockModelDownloadService.getCustomModelDetails(
            eq(TEST_PROJECT_ID), eq(MODEL_NAME), eq(null)))
        .thenReturn(Tasks.forResult(ORIG_CUSTOM_MODEL_URL));
    when(mockFileDownloadService.download(any(), eq(DOWNLOAD_CONDITIONS)))
        .thenReturn(
            Tasks.forException(
                new FirebaseMlException("bad download", FirebaseMlException.INVALID_ARGUMENT)));
    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    Task<CustomModel> task =
        firebaseModelDownloader.getModel(MODEL_NAME, DownloadType.LOCAL_MODEL, DOWNLOAD_CONDITIONS);
    task.addOnCompleteListener(executor, onCompleteListener);
    try {
      onCompleteListener.await();
    } catch (FirebaseMlException ex) {
      assertEquals(ex.getCode(), FirebaseMlException.INVALID_ARGUMENT);
      assertThat(ex.getMessage().contains("bad download")).isTrue();
    } catch (Exception ex) {
      fail("Unexpected error message: " + ex.getMessage());
    }

    verify(mockPrefs, times(2)).getCustomModelDetails(eq(MODEL_NAME));
    verify(mockFileDownloadService, never()).loadNewlyDownloadedModelFile(any());
    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isFalse();
  }

  @Test
  public void listDownloadedModels_returnsEmptyModelList() throws Exception {
    when(mockPrefs.listDownloadedModels()).thenReturn(Collections.emptySet());
    doNothing().when(mockFileDownloadService).maybeCheckDownloadingComplete();

    TestOnCompleteListener<Set<CustomModel>> onCompleteListener = new TestOnCompleteListener<>();
    Task<Set<CustomModel>> task = firebaseModelDownloader.listDownloadedModels();
    task.addOnCompleteListener(executor, onCompleteListener);
    Set<CustomModel> customModelSet = onCompleteListener.await();

    assertThat(task.isComplete()).isTrue();
    assertEquals(customModelSet, Collections.EMPTY_SET);
  }

  @Test
  public void listDownloadedModels_returnsModelList() throws Exception {
    when(mockPrefs.listDownloadedModels()).thenReturn(Collections.singleton(CUSTOM_MODEL));
    doNothing().when(mockFileDownloadService).maybeCheckDownloadingComplete();

    TestOnCompleteListener<Set<CustomModel>> onCompleteListener = new TestOnCompleteListener<>();
    Task<Set<CustomModel>> task = firebaseModelDownloader.listDownloadedModels();
    task.addOnCompleteListener(executor, onCompleteListener);
    Set<CustomModel> customModelSet = onCompleteListener.await();

    assertThat(task.isComplete()).isTrue();
    assertEquals(customModelSet, Collections.singleton(CUSTOM_MODEL));
  }

  @Test
  public void deleteDownloadedModel() throws Exception {
    doNothing().when(mockPrefs).clearModelDetails(eq(MODEL_NAME));
    when(mockFileManager.deleteAllModels(eq(MODEL_NAME))).thenReturn(true);

    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    Task<Void> task = firebaseModelDownloader.deleteDownloadedModel(MODEL_NAME);
    task.addOnCompleteListener(executor, onCompleteListener);
    onCompleteListener.await();

    assertThat(task.isComplete()).isTrue();
    verify(mockPrefs, times(1)).clearModelDetails(eq(MODEL_NAME));
    verify(mockFileManager, times(1)).deleteAllModels(eq(MODEL_NAME));
    verify(mockEventLogger, times(1)).logDeleteModel(eq(true));
  }

  @Test
  public void setStatsCollectionEnabled() {
    doNothing().when(mockPrefs).setCustomModelStatsCollectionEnabled(anyBoolean());
    firebaseModelDownloader.setModelDownloaderCollectionEnabled(true);
    verify(mockPrefs, times(1)).setCustomModelStatsCollectionEnabled(eq(true));

    firebaseModelDownloader.setModelDownloaderCollectionEnabled(false);
    verify(mockPrefs, times(1)).setCustomModelStatsCollectionEnabled(eq(false));

    firebaseModelDownloader.setModelDownloaderCollectionEnabled(null);
    verify(mockPrefs, times(1)).setCustomModelStatsCollectionEnabled(eq(null));
  }

  @Test
  public void getModelDownloadId_noDownload_noTask() {
    when(mockPrefs.getDownloadingCustomModelDetails(eq(MODEL_NAME))).thenReturn(customModelLoaded);
    assertTrue(firebaseModelDownloader.getModelDownloadId(MODEL_NAME, null).getResult() == 0L);
  }

  @Test
  public void getModelDownloadId_noNamedModel_noTask() {
    when(mockPrefs.getDownloadingCustomModelDetails(eq(MODEL_NAME))).thenReturn(null);
    assertTrue(firebaseModelDownloader.getModelDownloadId(MODEL_NAME, null).getResult() == 0L);
  }

  @Test
  public void getModelDownloadId_download_noTask() {
    when(mockPrefs.getDownloadingCustomModelDetails(eq(MODEL_NAME)))
        .thenReturn(UPDATE_IN_PROGRESS_CUSTOM_MODEL);
    assertTrue(
        firebaseModelDownloader.getModelDownloadId(MODEL_NAME, null).getResult() == DOWNLOAD_ID);
  }

  @Test
  public void getModelDownloadId_noDownload_task() throws Exception {
    Task<CustomModel> task = getModelTask();
    when(mockPrefs.getDownloadingCustomModelDetails(eq(MODEL_NAME))).thenReturn(customModelLoaded);

    firebaseModelDownloader
        .getModelDownloadId(MODEL_NAME, task)
        .addOnCompleteListener(
            new OnCompleteListener<Long>() {
              @Override
              public void onComplete(Task<Long> idTask) {
                assertTrue(idTask.isSuccessful());
                assertTrue(idTask.getResult() == 0L);
              }
            });
    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    task.addOnCompleteListener(executor, onCompleteListener);
    onCompleteListener.await();
    assertTrue(task.isComplete());
  }

  @Test
  public void getModelDownloadId_noNamedModel_task() throws Exception {
    Task<CustomModel> task = getModelTask();
    when(mockPrefs.getDownloadingCustomModelDetails(eq(MODEL_NAME))).thenReturn(null);
    firebaseModelDownloader
        .getModelDownloadId(MODEL_NAME, task)
        .addOnCompleteListener(
            new OnCompleteListener<Long>() {
              @Override
              public void onComplete(Task<Long> idTask) {
                assertTrue(idTask.isSuccessful());
                assertTrue(idTask.getResult() == 0L);
              }
            });
    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    task.addOnCompleteListener(executor, onCompleteListener);
    onCompleteListener.await();
    assertTrue(task.isComplete());
  }

  @Test
  public void getModelDownloadId_download_task() throws Exception {
    Task<CustomModel> task = getModelTask();
    when(mockPrefs.getDownloadingCustomModelDetails(eq(MODEL_NAME)))
        .thenReturn(UPDATE_IN_PROGRESS_CUSTOM_MODEL);
    firebaseModelDownloader
        .getModelDownloadId(MODEL_NAME, task)
        .addOnCompleteListener(
            new OnCompleteListener<Long>() {
              @Override
              public void onComplete(Task<Long> idTask) {
                assertTrue(idTask.isSuccessful());
                assertTrue(idTask.getResult() == DOWNLOAD_ID);
              }
            });
    TestOnCompleteListener<CustomModel> onCompleteListener = new TestOnCompleteListener<>();
    task.addOnCompleteListener(executor, onCompleteListener);
    onCompleteListener.await();
    assertTrue(task.isComplete());
  }

  private Task<CustomModel> getModelTask() {
    when(mockPrefs.getCustomModelDetails(eq(MODEL_NAME))).thenReturn(null).thenReturn(CUSTOM_MODEL);
    when(mockModelDownloadService.getCustomModelDetails(
            eq(TEST_PROJECT_ID), eq(MODEL_NAME), eq(null)))
        .thenReturn(Tasks.forResult(ORIG_CUSTOM_MODEL_URL));
    when(mockFileDownloadService.download(any(), eq(DOWNLOAD_CONDITIONS)))
        .thenReturn(Tasks.forResult(null));
    when(mockFileDownloadService.loadNewlyDownloadedModelFile(eq(customModelUpdateLoaded)))
        .thenReturn(firstDeviceModelFile);
    return firebaseModelDownloader.getModel(
        MODEL_NAME, DownloadType.LOCAL_MODEL, DOWNLOAD_CONDITIONS);
  }
}
