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
import java.io.File;
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
  public static final String MODEL_HASH = "dsf324";

  final CustomModel CUSTOM_MODEL_NO_FILE = new CustomModel(MODEL_NAME, MODEL_HASH, 100, 0);

  private File testModelFile;
  private File testModelFile2;

  ModelFileManager fileManager;
  String expectedDestinationFolder;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    FirebaseApp.clearInstancesForTest();
    FirebaseApp app =
        FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), FIREBASE_OPTIONS);

    fileManager = new ModelFileManager(app);

    setUpTestingFiles(app);
  }

  private void setUpTestingFiles(FirebaseApp app) throws IOException {
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
    expectedDestinationFolder =
        new File(
                    app.getApplicationContext().getNoBackupFilesDir(),
                    ModelFileManager.CUSTOM_MODEL_ROOT_PATH)
                .getAbsolutePath()
            + "/"
            + app.getPersistenceKey()
            + "/"
            + MODEL_NAME;
  }

  @After
  public void teardown() {
    testModelFile.deleteOnExit();
    testModelFile2.deleteOnExit();
  }

  @Test
  public void getDirImpl() throws Exception {
    File modelDirectory = fileManager.getDirImpl(MODEL_NAME);
    assertTrue(modelDirectory.getAbsolutePath().endsWith(MODEL_NAME));
  }

  @Test
  public void getModelFileDestination_noExistingFiles() throws Exception {
    File firstFile = fileManager.getModelFileDestination(CUSTOM_MODEL_NO_FILE);
    assertTrue(firstFile.getAbsolutePath().endsWith(String.format("%s/0", MODEL_NAME)));
  }

  @Test
  public void moveModelToDestinationFolder() throws Exception {
    ParcelFileDescriptor fd =
        ParcelFileDescriptor.open(testModelFile, ParcelFileDescriptor.MODE_READ_ONLY);

    assertEquals(
        fileManager.moveModelToDestinationFolder(CUSTOM_MODEL_NO_FILE, fd),
        new File(expectedDestinationFolder + "/0"));
    // clean up files
    new File(expectedDestinationFolder + "/0").delete();
  }

  @Test
  public void moveModelToDestinationFolder_update() throws Exception {
    ParcelFileDescriptor fd =
        ParcelFileDescriptor.open(testModelFile, ParcelFileDescriptor.MODE_READ_ONLY);

    assertEquals(
        fileManager.moveModelToDestinationFolder(CUSTOM_MODEL_NO_FILE, fd),
        new File(expectedDestinationFolder + "/0"));

    ParcelFileDescriptor fd2 =
        ParcelFileDescriptor.open(testModelFile2, ParcelFileDescriptor.MODE_READ_ONLY);

    assertEquals(
        fileManager.moveModelToDestinationFolder(CUSTOM_MODEL_NO_FILE, fd2),
        new File(expectedDestinationFolder + "/1"));
    // clean up files.
    new File(expectedDestinationFolder + "/0").delete();
    new File(expectedDestinationFolder + "/1").delete();
  }

  @Test
  public void deleteAllModels_deleteSingleModel() throws Exception {
    ParcelFileDescriptor fd =
        ParcelFileDescriptor.open(testModelFile, ParcelFileDescriptor.MODE_READ_ONLY);
    assertEquals(
        fileManager.moveModelToDestinationFolder(CUSTOM_MODEL_NO_FILE, fd),
        new File(expectedDestinationFolder + "/0"));
    assertTrue(new File(expectedDestinationFolder + "/0").exists());

    fileManager.deleteAllModels(MODEL_NAME);

    assertFalse(new File(expectedDestinationFolder + "/0").exists());
  }

  @Test
  public void deleteAllModels_deleteMultipleModel() throws Exception {
    ParcelFileDescriptor fd =
        ParcelFileDescriptor.open(testModelFile, ParcelFileDescriptor.MODE_READ_ONLY);
    assertEquals(
        fileManager.moveModelToDestinationFolder(CUSTOM_MODEL_NO_FILE, fd),
        new File(expectedDestinationFolder + "/0"));
    assertTrue(new File(expectedDestinationFolder + "/0").exists());

    ParcelFileDescriptor fd2 =
        ParcelFileDescriptor.open(testModelFile2, ParcelFileDescriptor.MODE_READ_ONLY);

    assertEquals(
        fileManager.moveModelToDestinationFolder(CUSTOM_MODEL_NO_FILE, fd2),
        new File(expectedDestinationFolder + "/1"));

    fileManager.deleteAllModels(MODEL_NAME);

    assertFalse(new File(expectedDestinationFolder + "/0").exists());
    assertFalse(new File(expectedDestinationFolder + "/1").exists());
  }
}
