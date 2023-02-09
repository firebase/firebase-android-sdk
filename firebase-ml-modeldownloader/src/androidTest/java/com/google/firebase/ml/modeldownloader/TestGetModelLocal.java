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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Integration tests for Get Model, download type LOCAL_MODEL. Only use getLocalModel* models for
 * this test.
 */
@RunWith(AndroidJUnit4.class)
public class TestGetModelLocal {

  private static final String MODEL_NAME_LOCAL = "getLocalModel";
  private static final String MODEL_NAME_LOCAL_2 = "getLocalModel2";
  private static final String MODEL_HASH = "origHash324";

  private FirebaseApp app;
  private File firstDeviceModelFile;
  private File firstLoadTempModelFile;

  private CustomModel.Factory modelFactory;
  private CustomModel setupLoadedLocalModel;

  @Before
  public void before() {
    app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext());
    app.setDataCollectionDefaultEnabled(Boolean.FALSE);
    FirebaseModelDownloader firebaseModelDownloader = FirebaseModelDownloader.getInstance(app);

    modelFactory = firebaseModelDownloader.getModelFactory();

    setupLoadedLocalModel = modelFactory.create(MODEL_NAME_LOCAL, MODEL_HASH, 100, 0);

    SharedPreferencesUtil sharedPreferencesUtil = new SharedPreferencesUtil(app, modelFactory);
    // reset shared preferences and downloads for models used by this test.
    firebaseModelDownloader.deleteDownloadedModel(MODEL_NAME_LOCAL);
    firebaseModelDownloader.deleteDownloadedModel(MODEL_NAME_LOCAL_2);

    // Equivalent to clearing the cache for the models used by this test.
    sharedPreferencesUtil.clearModelDetails(MODEL_NAME_LOCAL);
    sharedPreferencesUtil.clearModelDetails(MODEL_NAME_LOCAL_2);
  }

  @After
  public void teardown() {
    if (firstLoadTempModelFile != null) {
      firstLoadTempModelFile.deleteOnExit();
    }
    if (firstDeviceModelFile != null) {
      firstDeviceModelFile.deleteOnExit();
    }
  }

  private void setUpLoadedLocalModelWithFile() throws Exception {
    ModelFileManager fileManager =
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

    firstDeviceModelFile = fileManager.moveModelToDestinationFolder(setupLoadedLocalModel, fd);
    assertEquals(firstDeviceModelFile, new File(expectedDestinationFolder + "/0"));
    assertTrue(firstDeviceModelFile.exists());
    fd.close();

    fakePreloadedCustomModel(
        MODEL_NAME_LOCAL,
        setupLoadedLocalModel.getModelHash(),
        99,
        expectedDestinationFolder + "/0");
  }

  @Test
  public void localModel_successTestPath()
      throws ExecutionException, InterruptedException, FirebaseMlException {

    // no models to start
    Set<CustomModel> listModelTask = getDownloadedModelList();
    assertEquals(listModelTask.size(), 0);

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
                MODEL_NAME_LOCAL_2,
                DownloadType.LOCAL_MODEL,
                new CustomModelDownloadConditions.Builder().build());
    Tasks.await(modelUpdatedTask);
    assertTrue(modelUpdatedTask.isSuccessful());

    listModelTask = getDownloadedModelList();
    assertTrue(listModelTask.size() >= 2);
    assertTrue(listModelTask.contains(modelTask.getResult()));
    assertTrue(listModelTask.contains(modelUpdatedTask.getResult()));

    // delete the old model
    Task deleteDownloadedModelTask =
        FirebaseModelDownloader.getInstance().deleteDownloadedModel(MODEL_NAME_LOCAL);
    Tasks.await(deleteDownloadedModelTask);
    listModelTask = getDownloadedModelList();
    assertEquals(1, listModelTask.size());

    // verify model file was also deleted
    assertNull(modelTask.getResult().getFile());
  }

  @Test
  public void localModel_fetchDueToMissingFile()
      throws ExecutionException, InterruptedException, FirebaseMlException {
    // no models to start
    Set<CustomModel> listModelTask = getDownloadedModelList();
    assertEquals(listModelTask.size(), 0);

    fakePreloadedCustomModel(MODEL_NAME_LOCAL_2, MODEL_HASH, 123L, "fake/path/model/0");
    listModelTask = getDownloadedModelList();
    assertEquals(listModelTask.size(), 1);

    // bad path doesn't exist
    CustomModel model = listModelTask.iterator().next();
    assertNull(model.getFile());
    assertEquals(model.getModelHash(), MODEL_HASH);

    // download models and check that it gets new download since file is missing.
    Task<CustomModel> modelTask =
        FirebaseModelDownloader.getInstance()
            .getModel(
                MODEL_NAME_LOCAL_2,
                DownloadType.LOCAL_MODEL,
                new CustomModelDownloadConditions.Builder().build());
    Tasks.await(modelTask);
    assertTrue(modelTask.isSuccessful());

    listModelTask = getDownloadedModelList();
    assertEquals(listModelTask.size(), 1);

    // updated path works.
    model = listModelTask.iterator().next();
    assertNotNull(model.getFile());
    assertNotEquals(model.getModelHash(), MODEL_HASH);
  }

  @Test
  public void localModel_preloadedDoNotFetchUpdate() throws Exception {
    // no models to start
    Set<CustomModel> listModelTask = getDownloadedModelList();
    assertEquals(listModelTask.size(), 0);

    setUpLoadedLocalModelWithFile();
    listModelTask = getDownloadedModelList();
    assertEquals(listModelTask.size(), 1);

    // bad path doesn't exist
    CustomModel model = listModelTask.iterator().next();
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

    listModelTask = getDownloadedModelList();
    assertEquals(listModelTask.size(), 1);

    // updated path works.
    model = listModelTask.iterator().next();
    assertNotNull(model.getFile());
    assertEquals(model.getModelHash(), MODEL_HASH);
  }

  private void fakePreloadedCustomModel(String modelName, String hash, long size, String filePath) {
    SharedPreferencesUtil sharedPreferencesUtil = new SharedPreferencesUtil(app, modelFactory);
    sharedPreferencesUtil.setLoadedCustomModelDetails(
        modelFactory.create(modelName, hash, size, 0L, filePath));
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
