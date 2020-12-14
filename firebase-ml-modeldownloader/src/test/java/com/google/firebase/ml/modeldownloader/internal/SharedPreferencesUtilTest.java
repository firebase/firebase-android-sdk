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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.ml.modeldownloader.CustomModel;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link SharedPreferencesUtil}. */
@RunWith(RobolectricTestRunner.class)
public class SharedPreferencesUtilTest {

  private static final String TEST_PROJECT_ID = "777777777777";
  private static final String MODEL_NAME = "ModelName";
  private static final String MODEL_HASH = "dsf324";
  private static final CustomModel CUSTOM_MODEL_DOWNLOAD_COMPLETE =
      new CustomModel(MODEL_NAME, MODEL_HASH, 100, 0, "file/path/store/ModelName/1");
  private static final CustomModel CUSTOM_MODEL_UPDATE_IN_BACKGROUND =
      new CustomModel(MODEL_NAME, MODEL_HASH, 100, 986, "file/path/store/ModelName/1");
  private static final CustomModel CUSTOM_MODEL_DOWNLOADING =
      new CustomModel(MODEL_NAME, MODEL_HASH, 100, 986);
  private SharedPreferencesUtil sharedPreferencesUtil;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    FirebaseApp.clearInstancesForTest();
    FirebaseApp app =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApplicationId("1:123456789:android:abcdef")
                .setProjectId(TEST_PROJECT_ID)
                .build());

    // default sharedPreferenceUtil
    sharedPreferencesUtil = new SharedPreferencesUtil(app);
    assertNotNull(sharedPreferencesUtil);
  }

  @Test
  public void setDownloadingCustomModelDetails_initializeDownload()
      throws IllegalArgumentException {
    sharedPreferencesUtil.setDownloadingCustomModelDetails(CUSTOM_MODEL_DOWNLOADING);
    CustomModel retrievedModel = sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME);
    assertEquals(retrievedModel, CUSTOM_MODEL_DOWNLOADING);
  }

  @Test
  public void setUploadedCustomModelDetails_localModelPresent() throws IllegalArgumentException {
    sharedPreferencesUtil.setUploadedCustomModelDetails(CUSTOM_MODEL_DOWNLOAD_COMPLETE);
    CustomModel retrievedModel = sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME);
    assertEquals(retrievedModel, CUSTOM_MODEL_DOWNLOAD_COMPLETE);
  }

  @Test
  public void setUploadedCustomModelDetails_initialDownloadStartedAndCompleted()
      throws IllegalArgumentException {
    sharedPreferencesUtil.setDownloadingCustomModelDetails(CUSTOM_MODEL_DOWNLOADING);
    sharedPreferencesUtil.setUploadedCustomModelDetails(CUSTOM_MODEL_DOWNLOAD_COMPLETE);
    CustomModel retrievedModel = sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME);
    assertEquals(retrievedModel, CUSTOM_MODEL_DOWNLOAD_COMPLETE);
  }

  @Test
  public void setUploadedCustomModelDetails_localModelWithUploadInBackGround()
      throws IllegalArgumentException {
    sharedPreferencesUtil.setUploadedCustomModelDetails(CUSTOM_MODEL_DOWNLOAD_COMPLETE);
    sharedPreferencesUtil.setDownloadingCustomModelDetails(CUSTOM_MODEL_DOWNLOADING);
    CustomModel retrievedModel = sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME);
    assertEquals(retrievedModel, CUSTOM_MODEL_UPDATE_IN_BACKGROUND);
  }

  @Test
  public void clearModelDetails_clearLocalAndDownloadingInfo() throws IllegalArgumentException {
    sharedPreferencesUtil.setUploadedCustomModelDetails(CUSTOM_MODEL_DOWNLOAD_COMPLETE);
    sharedPreferencesUtil.setDownloadingCustomModelDetails(CUSTOM_MODEL_DOWNLOADING);
    CustomModel retrievedModel = sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME);
    assertEquals(retrievedModel, CUSTOM_MODEL_UPDATE_IN_BACKGROUND);
    sharedPreferencesUtil.clearModelDetails(CUSTOM_MODEL_DOWNLOAD_COMPLETE.getName());
    retrievedModel = sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME);
    assertNull(retrievedModel);
  }

  @Test
  public void clearDownloadingModelDetails_keepsLocalModel() throws IllegalArgumentException {
    sharedPreferencesUtil.setUploadedCustomModelDetails(CUSTOM_MODEL_DOWNLOAD_COMPLETE);
    sharedPreferencesUtil.setDownloadingCustomModelDetails(CUSTOM_MODEL_DOWNLOADING);
    CustomModel retrievedModel = sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME);
    assertEquals(retrievedModel, CUSTOM_MODEL_UPDATE_IN_BACKGROUND);
    sharedPreferencesUtil.clearDownloadingModelDetails(
        sharedPreferencesUtil.getSharedPreferences().edit(),
        CUSTOM_MODEL_DOWNLOAD_COMPLETE.getName());
    retrievedModel = sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME);
    assertEquals(retrievedModel, CUSTOM_MODEL_DOWNLOAD_COMPLETE);
  }

  @Test
  public void listDownloadedModels_localModelFound() {
    sharedPreferencesUtil.setUploadedCustomModelDetails(CUSTOM_MODEL_DOWNLOAD_COMPLETE);
    Set<CustomModel> retrievedModel = sharedPreferencesUtil.listDownloadedModels();
    assertEquals(retrievedModel.size(), 1);
    assertEquals(retrievedModel.iterator().next(), CUSTOM_MODEL_DOWNLOAD_COMPLETE);
  }

  @Test
  public void listDownloadedModels_downloadingModelNotFound() {
    sharedPreferencesUtil.setDownloadingCustomModelDetails(CUSTOM_MODEL_DOWNLOADING);
    assertEquals(sharedPreferencesUtil.listDownloadedModels().size(), 0);
  }

  @Test
  public void listDownloadedModels_noModels() {
    assertEquals(sharedPreferencesUtil.listDownloadedModels().size(), 0);
  }

  @Test
  public void listDownloadedModels_multipleModels() {
    sharedPreferencesUtil.setUploadedCustomModelDetails(CUSTOM_MODEL_DOWNLOAD_COMPLETE);

    CustomModel model2 =
        new CustomModel(MODEL_NAME + "2", MODEL_HASH + "2", 102, 0, "file/path/store/ModelName2/1");
    sharedPreferencesUtil.setUploadedCustomModelDetails(model2);

    CustomModel model3 =
        new CustomModel(MODEL_NAME + "3", MODEL_HASH + "3", 103, 0, "file/path/store/ModelName3/1");

    sharedPreferencesUtil.setUploadedCustomModelDetails(model3);

    Set<CustomModel> retrievedModel = sharedPreferencesUtil.listDownloadedModels();
    assertEquals(retrievedModel.size(), 3);
    assertTrue(retrievedModel.contains(CUSTOM_MODEL_DOWNLOAD_COMPLETE));
    assertTrue(retrievedModel.contains(model2));
    assertTrue(retrievedModel.contains(model3));
  }
}
