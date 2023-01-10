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
import static org.junit.Assert.assertTrue;

import android.os.ParcelFileDescriptor;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.FirebaseOptions.Builder;
import com.google.firebase.ml.modeldownloader.CustomModel;
import com.google.firebase.ml.modeldownloader.FirebaseMlException;
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ModelFileManagerTest {

  public static final String TEST_PROJECT_ID = "777777777777";
  public static final FirebaseOptions FIREBASE_OPTIONS =
      new Builder()
          .setApplicationId("1:123456789:android:abcdef")
          .setProjectId(TEST_PROJECT_ID)
          .build();

  public static final String MODEL_NAME = "MODEL_NAME_1";
  public static final String MODEL_HASH = "hash1";

  public static final String MODEL_NAME_2 = "MODEL_NAME_2";
  public static final String MODEL_HASH_2 = "hash2";

  private CustomModel CUSTOM_MODEL_NO_FILE;
  private CustomModel CUSTOM_MODEL_NO_FILE_2;

  private File testModelFile;
  private File testModelFile2;

  ModelFileManager fileManager;
  FirebaseApp app;
  private SharedPreferencesUtil sharedPreferencesUtil;
  private String modelDestinationFolder;
  private CustomModel.Factory modelFactory;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    FirebaseApp.clearInstancesForTest();
    app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), FIREBASE_OPTIONS);

    modelFactory = FirebaseModelDownloader.getInstance(app).getModelFactory();

    sharedPreferencesUtil = new SharedPreferencesUtil(app, modelFactory);
    fileManager =
        new ModelFileManager(
            ApplicationProvider.getApplicationContext(),
            app.getPersistenceKey(),
            sharedPreferencesUtil);

    modelDestinationFolder = setUpTestingFiles(app, MODEL_NAME);
    CUSTOM_MODEL_NO_FILE = modelFactory.create(MODEL_NAME, MODEL_HASH, 100, 0);
    CUSTOM_MODEL_NO_FILE_2 = modelFactory.create(MODEL_NAME_2, MODEL_HASH_2, 101, 0);
  }

  private String setUpTestingFiles(FirebaseApp app, String modelName) throws IOException {
    final File testDir = new File(app.getApplicationContext().getNoBackupFilesDir(), "tmpModels");
    testDir.mkdirs();
    // make sure the directory is empty. Doesn't recurse into subdirs, but that's OK since
    // we're only using this directory for this test and we won't create any subdirs.
    for (File f : testDir.listFiles()) {
      if (f.isFile()) {
        f.delete();
      }
    }

    testModelFile = File.createTempFile("modelFile", "tflite");
    testModelFile2 = File.createTempFile("modelFile2", "tflite");

    assertTrue(testModelFile.exists());
    assertTrue(testModelFile2.exists());
    return new File(
                app.getApplicationContext().getNoBackupFilesDir(),
                ModelFileManager.CUSTOM_MODEL_ROOT_PATH)
            .getAbsolutePath()
        + "/"
        + app.getPersistenceKey()
        + "/"
        + modelName;
  }

  @After
  public void teardown() {
    testModelFile.deleteOnExit();
    testModelFile2.deleteOnExit();

    // clean up files.
    new File(modelDestinationFolder + "/0").deleteOnExit();
    new File(modelDestinationFolder + "/1").deleteOnExit();
  }

  @Test
  public void getDirImpl() throws Exception {
    File modelDirectory = fileManager.getDirImpl(MODEL_NAME);
    assertTrue(modelDirectory.getAbsolutePath().endsWith(MODEL_NAME));
  }

  @Test
  public void getModelFileDestination_noExistingFiles() throws FirebaseMlException {
    File firstFile = fileManager.getModelFileDestination(CUSTOM_MODEL_NO_FILE);
    assertTrue(firstFile.getAbsolutePath().endsWith(String.format("%s/0", MODEL_NAME)));
  }

  @Test
  public void moveModelToDestinationFolder() throws FirebaseMlException, FileNotFoundException {
    MoveFileToDestination(
        modelDestinationFolder, testModelFile, CUSTOM_MODEL_NO_FILE, 0); // clean up files
  }

  @Test
  public void moveModelToDestinationFolder_update()
      throws FirebaseMlException, FileNotFoundException {
    ParcelFileDescriptor fd =
        ParcelFileDescriptor.open(testModelFile, ParcelFileDescriptor.MODE_READ_ONLY);

    assertEquals(
        fileManager.moveModelToDestinationFolder(CUSTOM_MODEL_NO_FILE, fd),
        new File(modelDestinationFolder + "/0"));

    ParcelFileDescriptor fd2 =
        ParcelFileDescriptor.open(testModelFile2, ParcelFileDescriptor.MODE_READ_ONLY);

    assertEquals(
        fileManager.moveModelToDestinationFolder(CUSTOM_MODEL_NO_FILE, fd2),
        new File(modelDestinationFolder + "/1"));
  }

  @Test
  public void deleteAllModels_deleteSingleModel()
      throws FirebaseMlException, FileNotFoundException {
    MoveFileToDestination(modelDestinationFolder, testModelFile, CUSTOM_MODEL_NO_FILE, 0);

    fileManager.deleteAllModels(MODEL_NAME);

    assertFalse(new File(modelDestinationFolder + "/0").exists());
  }

  @Test
  public void deleteAllModels_deleteMultipleModel()
      throws FirebaseMlException, FileNotFoundException {
    MoveFileToDestination(modelDestinationFolder, testModelFile, CUSTOM_MODEL_NO_FILE, 0);

    ParcelFileDescriptor fd2 =
        ParcelFileDescriptor.open(testModelFile2, ParcelFileDescriptor.MODE_READ_ONLY);

    assertEquals(
        fileManager.moveModelToDestinationFolder(CUSTOM_MODEL_NO_FILE, fd2),
        new File(modelDestinationFolder + "/1"));

    fileManager.deleteAllModels(MODEL_NAME);

    assertFalse(new File(modelDestinationFolder + "/0").exists());
    assertFalse(new File(modelDestinationFolder + "/1").exists());
  }

  @Test
  public void deleteNonLatestCustomModels_fileToDelete()
      throws FirebaseMlException, FileNotFoundException {
    MoveFileToDestination(modelDestinationFolder, testModelFile, CUSTOM_MODEL_NO_FILE, 0);
    MoveFileToDestination(modelDestinationFolder, testModelFile2, CUSTOM_MODEL_NO_FILE, 1);

    sharedPreferencesUtil.setLoadedCustomModelDetails(
        modelFactory.create(MODEL_NAME, MODEL_HASH, 100, 0, modelDestinationFolder + "/1"));
    fileManager.deleteNonLatestCustomModels();

    assertFalse(new File(modelDestinationFolder + "/0").exists());
    assertTrue(new File(modelDestinationFolder + "/1").exists());
  }

  @Test
  public void deleteNonLatestCustomModels_whenModelOnDiskButNotInPreferences()
      throws FirebaseMlException, IOException {
    String modelDestinationFolder2 = setUpTestingFiles(app, MODEL_NAME_2);

    // Was just downloaded
    MoveFileToDestination(modelDestinationFolder, testModelFile, CUSTOM_MODEL_NO_FILE, 0);
    // Was downloaded previously via FirebaseModelManager
    MoveFileToDestination(modelDestinationFolder2, testModelFile2, CUSTOM_MODEL_NO_FILE_2, 0);

    sharedPreferencesUtil.setLoadedCustomModelDetails(
        modelFactory.create(MODEL_NAME, MODEL_HASH, 100, 0, modelDestinationFolder + "/0"));

    // Download in progress, hence file path is not present
    sharedPreferencesUtil.setLoadedCustomModelDetails(
        modelFactory.create(MODEL_NAME_2, MODEL_HASH_2, 100, 0));

    fileManager.deleteNonLatestCustomModels();

    assertTrue(new File(modelDestinationFolder + "/0").exists());
    assertTrue(new File(modelDestinationFolder2 + "/0").exists());
  }

  @Test
  public void deleteNonLatestCustomModels_noFileToDelete()
      throws FirebaseMlException, FileNotFoundException {
    MoveFileToDestination(modelDestinationFolder, testModelFile, CUSTOM_MODEL_NO_FILE, 0);

    sharedPreferencesUtil.setLoadedCustomModelDetails(
        modelFactory.create(MODEL_NAME, MODEL_HASH, 100, 0, modelDestinationFolder + "/0"));
    fileManager.deleteNonLatestCustomModels();

    assertTrue(new File(modelDestinationFolder + "/0").exists());
  }

  @Test
  public void deleteNonLatestCustomModels_multipleNamedModels()
      throws FirebaseMlException, IOException {
    MoveFileToDestination(modelDestinationFolder, testModelFile, CUSTOM_MODEL_NO_FILE, 0);
    MoveFileToDestination(modelDestinationFolder, testModelFile2, CUSTOM_MODEL_NO_FILE, 1);

    sharedPreferencesUtil.setLoadedCustomModelDetails(
        modelFactory.create(MODEL_NAME, MODEL_HASH, 100, 0, modelDestinationFolder + "/1"));

    String modelDestinationFolder2 = setUpTestingFiles(app, MODEL_NAME_2);
    MoveFileToDestination(modelDestinationFolder2, testModelFile, CUSTOM_MODEL_NO_FILE_2, 0);
    MoveFileToDestination(modelDestinationFolder2, testModelFile2, CUSTOM_MODEL_NO_FILE_2, 1);

    sharedPreferencesUtil.setLoadedCustomModelDetails(
        modelFactory.create(MODEL_NAME_2, MODEL_HASH_2, 101, 0, modelDestinationFolder2 + "/1"));

    fileManager.deleteNonLatestCustomModels();

    assertFalse(new File(modelDestinationFolder + "/0").exists());
    assertTrue(new File(modelDestinationFolder + "/1").exists());

    assertFalse(new File(modelDestinationFolder2 + "/0").exists());
    assertTrue(new File(modelDestinationFolder2 + "/1").exists());
    new File(modelDestinationFolder2 + "/0").deleteOnExit();
    new File(modelDestinationFolder2 + "/1").deleteOnExit();
  }

  @Test
  public void deleteOldModels_deleteModel() throws FirebaseMlException, FileNotFoundException {
    MoveFileToDestination(modelDestinationFolder, testModelFile, CUSTOM_MODEL_NO_FILE, 0);
    MoveFileToDestination(modelDestinationFolder, testModelFile2, CUSTOM_MODEL_NO_FILE, 1);

    fileManager.deleteOldModels(MODEL_NAME, modelDestinationFolder + "/1");

    assertFalse(new File(modelDestinationFolder + "/0").exists());
    assertTrue(new File(modelDestinationFolder + "/1").exists());
  }

  @Test
  public void deleteOldModels_deleteModel_keepNewer()
      throws FirebaseMlException, FileNotFoundException {
    MoveFileToDestination(modelDestinationFolder, testModelFile, CUSTOM_MODEL_NO_FILE, 0);
    MoveFileToDestination(modelDestinationFolder, testModelFile2, CUSTOM_MODEL_NO_FILE, 1);

    fileManager.deleteOldModels(MODEL_NAME, modelDestinationFolder + "/0");

    assertTrue(new File(modelDestinationFolder + "/0").exists());
    assertTrue(new File(modelDestinationFolder + "/1").exists());
  }

  @Test
  public void deleteOldModels_noModelsToDelete() throws FirebaseMlException, FileNotFoundException {
    MoveFileToDestination(modelDestinationFolder, testModelFile, CUSTOM_MODEL_NO_FILE, 0);

    fileManager.deleteOldModels(MODEL_NAME, modelDestinationFolder + "/0");

    assertTrue(new File(modelDestinationFolder + "/0").exists());
  }

  @Test
  public void deleteOldModels_noLatestFile() throws FirebaseMlException, FileNotFoundException {
    MoveFileToDestination(modelDestinationFolder, testModelFile, CUSTOM_MODEL_NO_FILE, 0);

    fileManager.deleteOldModels(MODEL_NAME, modelDestinationFolder + "/99");

    assertFalse(new File(modelDestinationFolder + "/0").exists());
  }

  @Test
  public void deleteOldModels_multipleNamedModels() throws FirebaseMlException, IOException {
    MoveFileToDestination(modelDestinationFolder, testModelFile, CUSTOM_MODEL_NO_FILE, 0);
    MoveFileToDestination(modelDestinationFolder, testModelFile2, CUSTOM_MODEL_NO_FILE, 1);

    sharedPreferencesUtil.setLoadedCustomModelDetails(
        modelFactory.create(MODEL_NAME, MODEL_HASH, 100, 0, modelDestinationFolder + "/1"));

    String modelDestinationFolder2 = setUpTestingFiles(app, MODEL_NAME_2);
    MoveFileToDestination(modelDestinationFolder2, testModelFile, CUSTOM_MODEL_NO_FILE_2, 0);
    MoveFileToDestination(modelDestinationFolder2, testModelFile2, CUSTOM_MODEL_NO_FILE_2, 1);

    sharedPreferencesUtil.setLoadedCustomModelDetails(
        modelFactory.create(MODEL_NAME_2, MODEL_HASH_2, 101, 0, modelDestinationFolder2 + "/1"));

    fileManager.deleteOldModels(MODEL_NAME, modelDestinationFolder + "/1");

    assertFalse(new File(modelDestinationFolder + "/0").exists());
    assertTrue(new File(modelDestinationFolder + "/1").exists());

    assertTrue(new File(modelDestinationFolder2 + "/0").exists());
    assertTrue(new File(modelDestinationFolder2 + "/1").exists());
    new File(modelDestinationFolder2 + "/0").deleteOnExit();
    new File(modelDestinationFolder2 + "/1").deleteOnExit();
  }

  private void MoveFileToDestination(
      String modelDestinationFolder, File testModelFile, CustomModel custom_model, int index)
      throws FileNotFoundException, FirebaseMlException {
    ParcelFileDescriptor fd =
        ParcelFileDescriptor.open(testModelFile, ParcelFileDescriptor.MODE_READ_ONLY);
    assertEquals(
        fileManager.moveModelToDestinationFolder(custom_model, fd),
        new File(modelDestinationFolder + "/" + index));
    assertTrue(new File(modelDestinationFolder + "/" + index).exists());
  }
}
