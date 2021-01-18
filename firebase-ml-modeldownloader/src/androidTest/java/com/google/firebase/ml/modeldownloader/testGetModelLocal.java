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
package com.google.firebase.ml.modeldownloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.modeldownloader.internal.ModelFileManager;
import com.google.firebase.ml.modeldownloader.internal.SharedPreferencesUtil;
import java.io.File;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Integration tests for Get Model, download type LOCAL_MODEL. */
@RunWith(AndroidJUnit4.class)
public class testGetModelLocal {
  private FirebaseModelDownloader firebaseModelDownloader;
  private static final String MODEL_NAME_LOCAL = "getLocalModel";
  private static final String MODEL_NAME_UPDATED = "getUpdatedModel";
  private static final String MODEL_HASH = "origHash324";
  private final CustomModel SETUP_LOADED_LOCAL_MODEL =
      new CustomModel(MODEL_NAME_LOCAL, MODEL_HASH, 100, 0);

  private FirebaseApp app;

  private String persistenceKey;

  @Before
  public void before() throws ExecutionException, InterruptedException {
    if (firebaseModelDownloader == null) {
      app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext());
      this.persistenceKey = app.getPersistenceKey();
      firebaseModelDownloader = FirebaseModelDownloader.getInstance(app);
    }
    // reset shared preferences and downloads
    Set<CustomModel> downloadedModels = getDownloadedModelList();
    if (downloadedModels != null) {
      for (CustomModel model : downloadedModels) {
        firebaseModelDownloader.deleteDownloadedModel(model.getName());
      }
    }
    SharedPreferences.Editor preferencesEditor =
        ApplicationProvider.getApplicationContext()
            .getSharedPreferences(
                SharedPreferencesUtil.PREFERENCES_PACKAGE_NAME, Context.MODE_PRIVATE)
            .edit();
    preferencesEditor.clear();
    preferencesEditor.apply();
  }

  private void setUpLoadedLocalModelWithFile() throws Exception {
    ModelFileManager fileManager = ModelFileManager.getInstance();
    final File testDir = new File(app.getApplicationContext().getNoBackupFilesDir(), "tmpModels");
    testDir.mkdirs();
    // make sure the directory is empty. Doesn't recurse into subdirs, but that's OK since
    // we're only using this directory for this test and we won't create any subdirs.
    for (File f : testDir.listFiles()) {
      if (f.isFile()) {
        f.delete();
      }
    }

    File firstLoadTempModelFile = File.createTempFile("modelFile", ".tflite");

    String expectedDestinationFolder =
        new File(
                    app.getApplicationContext().getNoBackupFilesDir(),
                    ModelFileManager.CUSTOM_MODEL_ROOT_PATH)
                .getAbsolutePath()
            + "/"
            + app.getPersistenceKey()
            + "/"
            + MODEL_NAME_LOCAL;
    // move test files to expected locations.
    ParcelFileDescriptor fd =
        ParcelFileDescriptor.open(firstLoadTempModelFile, ParcelFileDescriptor.MODE_READ_ONLY);

    File firstDeviceModelFile =
        fileManager.moveModelToDestinationFolder(SETUP_LOADED_LOCAL_MODEL, fd);
    assertEquals(firstDeviceModelFile, new File(expectedDestinationFolder + "/0"));
    assertTrue(firstDeviceModelFile.exists());
    fd.close();

    fakeLoadedCustomModel(
        MODEL_NAME_LOCAL,
        SETUP_LOADED_LOCAL_MODEL.getModelHash(),
        99,
        expectedDestinationFolder + "/0");
  }

  @Test
  public void localModel_successTestPath()
      throws ExecutionException, InterruptedException, FirebaseMlException {

    // no models to start
    Task<Set<CustomModel>> listModelTask =
        FirebaseModelDownloader.getInstance().listDownloadedModels();
    Tasks.await(listModelTask);
    assertTrue(listModelTask.isSuccessful());
    assertEquals(listModelTask.getResult().size(), 0);

    // download 2 models and check they are in the list.
    Task<CustomModel> modelTask =
        FirebaseModelDownloader.getInstance()
            .getModel(
                MODEL_NAME_LOCAL,
                DownloadType.LOCAL_MODEL,
                new CustomModelDownloadConditions.Builder().build());
    Tasks.await(modelTask);
    assertTrue(modelTask.isSuccessful());

    Task<CustomModel> modelUpdatedTask =
        FirebaseModelDownloader.getInstance()
            .getModel(
                MODEL_NAME_UPDATED,
                DownloadType.LOCAL_MODEL,
                new CustomModelDownloadConditions.Builder().build());
    Tasks.await(modelUpdatedTask);
    assertTrue(modelUpdatedTask.isSuccessful());

    listModelTask = FirebaseModelDownloader.getInstance().listDownloadedModels();
    Tasks.await(listModelTask);
    assertTrue(listModelTask.isSuccessful());
    assertTrue(listModelTask.getResult().size() >= 2);

    Set<CustomModel> downloadedModels = listModelTask.getResult();
    assertTrue(downloadedModels.contains(modelTask.getResult()));
    assertTrue(downloadedModels.contains(modelUpdatedTask.getResult()));

    // delete the old model
    FirebaseModelDownloader.getInstance().deleteDownloadedModel(MODEL_NAME_LOCAL);
    listModelTask = FirebaseModelDownloader.getInstance().listDownloadedModels();
    Tasks.await(listModelTask);
    assertTrue(listModelTask.isSuccessful());
    assertEquals(listModelTask.getResult().size(), 1);

    // verify model file was also deleted
    assertNull(modelTask.getResult().getFile());
  }

  @Test
  public void localUpdateBackgroundModel_missingFile_successTestPath()
      throws ExecutionException, InterruptedException, FirebaseMlException {
    // no models to start
    Task<Set<CustomModel>> listModelTask =
        FirebaseModelDownloader.getInstance().listDownloadedModels();
    Tasks.await(listModelTask);
    assertTrue(listModelTask.isSuccessful());
    assertEquals(listModelTask.getResult().size(), 0);

    fakeLoadedCustomModel(MODEL_NAME_UPDATED, MODEL_HASH, 123L, "fake/path/model/0");
    listModelTask = FirebaseModelDownloader.getInstance().listDownloadedModels();
    Tasks.await(listModelTask);
    assertTrue(listModelTask.isSuccessful());
    assertEquals(listModelTask.getResult().size(), 1);

    // bad path doesn't exist
    CustomModel model = listModelTask.getResult().iterator().next();
    assertNull(model.getFile());
    assertEquals(model.getModelHash(), MODEL_HASH);

    // download models and check that it gets new download since file is missing.
    Task<CustomModel> modelTask =
        FirebaseModelDownloader.getInstance()
            .getModel(
                MODEL_NAME_UPDATED,
                DownloadType.LOCAL_MODEL,
                new CustomModelDownloadConditions.Builder().build());
    Tasks.await(modelTask);
    assertTrue(modelTask.isSuccessful());

    listModelTask = FirebaseModelDownloader.getInstance().listDownloadedModels();
    Tasks.await(listModelTask);
    assertTrue(listModelTask.isSuccessful());
    assertEquals(listModelTask.getResult().size(), 1);

    // updated path works.
    model = listModelTask.getResult().iterator().next();
    assertNotNull(model.getFile());
    assertNotEquals(model.getModelHash(), MODEL_HASH);
  }

  @Test
  public void localUpdateBackgroundModel_doNotFetch() throws Exception {
    // no models to start
    Task<Set<CustomModel>> listModelTask =
        FirebaseModelDownloader.getInstance().listDownloadedModels();
    Tasks.await(listModelTask);
    assertTrue(listModelTask.isSuccessful());
    assertEquals(listModelTask.getResult().size(), 0);

    setUpLoadedLocalModelWithFile();
    listModelTask = FirebaseModelDownloader.getInstance().listDownloadedModels();
    Tasks.await(listModelTask);
    assertTrue(listModelTask.isSuccessful());
    assertEquals(listModelTask.getResult().size(), 1);

    // bad path doesn't exist
    CustomModel model = listModelTask.getResult().iterator().next();
    assertNotNull(model.getFile());
    assertEquals(model.getModelHash(), MODEL_HASH);

    // download models and check that it gets new download since file is missing.
    Task<CustomModel> modelTask =
        FirebaseModelDownloader.getInstance()
            .getModel(
                MODEL_NAME_LOCAL,
                DownloadType.LOCAL_MODEL,
                new CustomModelDownloadConditions.Builder().build());
    Tasks.await(modelTask);
    assertTrue(modelTask.isSuccessful());

    listModelTask = FirebaseModelDownloader.getInstance().listDownloadedModels();
    Tasks.await(listModelTask);
    assertTrue(listModelTask.isSuccessful());
    assertEquals(listModelTask.getResult().size(), 1);

    // updated path works.
    model = listModelTask.getResult().iterator().next();
    assertNotNull(model.getFile());
    assertEquals(model.getModelHash(), MODEL_HASH);
  }

  private void fakeLoadedCustomModel(String modelName, String hash, long size, String filePath) {
    SharedPreferences.Editor preferencesEditor =
        ApplicationProvider.getApplicationContext()
            .getSharedPreferences(
                SharedPreferencesUtil.PREFERENCES_PACKAGE_NAME, Context.MODE_PRIVATE)
            .edit();
    preferencesEditor.clear();
    preferencesEditor
        .putString(
            String.format(
                SharedPreferencesUtil.LOCAL_MODEL_HASH_PATTERN, persistenceKey, modelName),
            hash)
        .putLong(
            String.format(
                SharedPreferencesUtil.LOCAL_MODEL_FILE_SIZE_PATTERN, persistenceKey, modelName),
            size)
        .putString(
            String.format(
                SharedPreferencesUtil.LOCAL_MODEL_FILE_PATH_PATTERN, persistenceKey, modelName),
            filePath)
        .commit();
  }

  private Set<CustomModel> getDownloadedModelList()
      throws ExecutionException, InterruptedException {

    Task<Set<CustomModel>> modelSetTask =
        FirebaseModelDownloader.getInstance().listDownloadedModels();

    Tasks.await(modelSetTask);
    if (modelSetTask.isSuccessful()) {
      return modelSetTask.getResult();
    }
    throw new InternalError("listDownloadedModels unexpected failure.");
  }
}
