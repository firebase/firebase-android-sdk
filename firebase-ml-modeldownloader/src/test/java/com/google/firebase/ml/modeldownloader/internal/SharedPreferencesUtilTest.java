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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.ml.modeldownloader.CustomModel;
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader;
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
  private CustomModel CUSTOM_MODEL_DOWNLOAD_COMPLETE;
  private CustomModel CUSTOM_MODEL_UPDATE_IN_BACKGROUND;
  private CustomModel CUSTOM_MODEL_DOWNLOADING;
  private SharedPreferencesUtil sharedPreferencesUtil;
  private FirebaseApp app;
  private CustomModel.Factory modelFactory;

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

    modelFactory = FirebaseModelDownloader.getInstance(app).getModelFactory();

    app.setDataCollectionDefaultEnabled(Boolean.TRUE);
    // default sharedPreferenceUtil
    sharedPreferencesUtil = new SharedPreferencesUtil(app, modelFactory);
    assertNotNull(sharedPreferencesUtil);
    CUSTOM_MODEL_DOWNLOAD_COMPLETE =
        modelFactory.create(MODEL_NAME, MODEL_HASH, 100, 0, "file/path/store/ModelName/1");
    CUSTOM_MODEL_UPDATE_IN_BACKGROUND =
        modelFactory.create(MODEL_NAME, MODEL_HASH, 100, 986, "file/path/store/ModelName/1");
    CUSTOM_MODEL_DOWNLOADING = modelFactory.create(MODEL_NAME, MODEL_HASH, 100, 986);
  }

  @Test
  public void setDownloadingCustomModelDetails_initializeDownload()
      throws IllegalArgumentException {
    sharedPreferencesUtil.setDownloadingCustomModelDetails(CUSTOM_MODEL_DOWNLOADING);
    CustomModel retrievedModel = sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME);
    assertEquals(retrievedModel, CUSTOM_MODEL_DOWNLOADING);
  }

  @Test
  public void setLoadedCustomModelDetails_localModelPresent() throws IllegalArgumentException {
    sharedPreferencesUtil.setLoadedCustomModelDetails(CUSTOM_MODEL_DOWNLOAD_COMPLETE);
    CustomModel retrievedModel = sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME);
    assertEquals(retrievedModel, CUSTOM_MODEL_DOWNLOAD_COMPLETE);
  }

  @Test
  public void setLoadedCustomModelDetails_initialDownloadStartedAndCompleted()
      throws IllegalArgumentException {
    sharedPreferencesUtil.setDownloadingCustomModelDetails(CUSTOM_MODEL_DOWNLOADING);
    sharedPreferencesUtil.setLoadedCustomModelDetails(CUSTOM_MODEL_DOWNLOAD_COMPLETE);
    CustomModel retrievedModel = sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME);
    assertEquals(retrievedModel, CUSTOM_MODEL_DOWNLOAD_COMPLETE);
  }

  @Test
  public void setLoadedCustomModelDetails_localModelWithDownloadInBackGround()
      throws IllegalArgumentException {
    sharedPreferencesUtil.setLoadedCustomModelDetails(CUSTOM_MODEL_DOWNLOAD_COMPLETE);
    sharedPreferencesUtil.setDownloadingCustomModelDetails(CUSTOM_MODEL_DOWNLOADING);
    CustomModel retrievedModel = sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME);
    assertEquals(retrievedModel, CUSTOM_MODEL_UPDATE_IN_BACKGROUND);
  }

  @Test
  public void clearModelDetails_clearLocalAndDownloadingInfo() throws IllegalArgumentException {
    sharedPreferencesUtil.setLoadedCustomModelDetails(CUSTOM_MODEL_DOWNLOAD_COMPLETE);
    sharedPreferencesUtil.setDownloadingCustomModelDetails(CUSTOM_MODEL_DOWNLOADING);
    CustomModel retrievedModel = sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME);
    assertEquals(retrievedModel, CUSTOM_MODEL_UPDATE_IN_BACKGROUND);
    sharedPreferencesUtil.clearModelDetails(CUSTOM_MODEL_DOWNLOAD_COMPLETE.getName());
    retrievedModel = sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME);
    assertNull(retrievedModel);
  }

  @Test
  public void clearDownloadingModelDetails_keepsLocalModel() throws IllegalArgumentException {
    sharedPreferencesUtil.setLoadedCustomModelDetails(CUSTOM_MODEL_DOWNLOAD_COMPLETE);
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
  public void clearDownloadCustomModelDetails_keepsLocalModel() throws IllegalArgumentException {
    sharedPreferencesUtil.setLoadedCustomModelDetails(CUSTOM_MODEL_DOWNLOAD_COMPLETE);
    sharedPreferencesUtil.setDownloadingCustomModelDetails(CUSTOM_MODEL_DOWNLOADING);
    CustomModel retrievedModel = sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME);
    assertEquals(retrievedModel, CUSTOM_MODEL_UPDATE_IN_BACKGROUND);
    sharedPreferencesUtil.clearDownloadCustomModelDetails(CUSTOM_MODEL_DOWNLOAD_COMPLETE.getName());
    retrievedModel = sharedPreferencesUtil.getCustomModelDetails(MODEL_NAME);
    assertEquals(retrievedModel, CUSTOM_MODEL_DOWNLOAD_COMPLETE);
  }

  @Test
  public void listDownloadedModels_localModelFound() {
    sharedPreferencesUtil.setLoadedCustomModelDetails(CUSTOM_MODEL_DOWNLOAD_COMPLETE);
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
    sharedPreferencesUtil.setLoadedCustomModelDetails(CUSTOM_MODEL_DOWNLOAD_COMPLETE);

    CustomModel model2 =
        modelFactory.create(
            MODEL_NAME + "2", MODEL_HASH + "2", 102, 0, "file/path/store/ModelName2/1");
    sharedPreferencesUtil.setLoadedCustomModelDetails(model2);

    CustomModel model3 =
        modelFactory.create(
            MODEL_NAME + "3", MODEL_HASH + "3", 103, 0, "file/path/store/ModelName3/1");

    sharedPreferencesUtil.setLoadedCustomModelDetails(model3);

    Set<CustomModel> retrievedModel = sharedPreferencesUtil.listDownloadedModels();
    assertEquals(retrievedModel.size(), 3);
    assertTrue(retrievedModel.contains(CUSTOM_MODEL_DOWNLOAD_COMPLETE));
    assertTrue(retrievedModel.contains(model2));
    assertTrue(retrievedModel.contains(model3));
  }

  @Test
  public void getCustomModelStatsCollectionFlag_defaultFirebaseAppTrue() {
    assertEquals(
        sharedPreferencesUtil.getCustomModelStatsCollectionFlag(),
        app.isDataCollectionDefaultEnabled());
    assertTrue(sharedPreferencesUtil.getCustomModelStatsCollectionFlag());
  }

  @Test
  public void getCustomModelStatsCollectionFlag_defaultFirebaseAppFalse() {
    app.setDataCollectionDefaultEnabled(Boolean.FALSE);
    // default sharedPreferenceUtil
    SharedPreferencesUtil disableLogUtil = new SharedPreferencesUtil(app, modelFactory);
    assertEquals(
        disableLogUtil.getCustomModelStatsCollectionFlag(), app.isDataCollectionDefaultEnabled());
    assertFalse(disableLogUtil.getCustomModelStatsCollectionFlag());
  }

  @Test
  public void getCustomModelStatsCollectionFlag_overrideFirebaseAppFalse() {
    app.setDataCollectionDefaultEnabled(Boolean.FALSE);
    // default sharedPreferenceUtil
    SharedPreferencesUtil sharedPreferencesUtil2 = new SharedPreferencesUtil(app, modelFactory);
    sharedPreferencesUtil2.setCustomModelStatsCollectionEnabled(true);
    assertEquals(sharedPreferencesUtil2.getCustomModelStatsCollectionFlag(), true);
    assertTrue(sharedPreferencesUtil2.getCustomModelStatsCollectionFlag());
  }

  @Test
  public void setCustomModelStatsCollectionFlag_updates() {
    sharedPreferencesUtil.setCustomModelStatsCollectionEnabled(false);
    assertFalse(sharedPreferencesUtil.getCustomModelStatsCollectionFlag());
    sharedPreferencesUtil.setCustomModelStatsCollectionEnabled(true);
    assertTrue(sharedPreferencesUtil.getCustomModelStatsCollectionFlag());
  }

  @Test
  public void setCustomModelStatsCollectionFlag_nullUpdates() {
    sharedPreferencesUtil.setCustomModelStatsCollectionEnabled(false);
    sharedPreferencesUtil.setCustomModelStatsCollectionEnabled(null);
    assertEquals(
        sharedPreferencesUtil.getCustomModelStatsCollectionFlag(),
        app.isDataCollectionDefaultEnabled());
    app.setDataCollectionDefaultEnabled(Boolean.FALSE);
    assertFalse(sharedPreferencesUtil.getCustomModelStatsCollectionFlag());
    app.setDataCollectionDefaultEnabled(Boolean.TRUE);
    assertTrue(sharedPreferencesUtil.getCustomModelStatsCollectionFlag());
  }

  @Test
  public void getModelDownloadBeginTimeMs_default0() {
    assertEquals(sharedPreferencesUtil.getModelDownloadBeginTimeMs(CUSTOM_MODEL_DOWNLOADING), 0L);
  }

  @Test
  public void setModelDownloadBeginTimeMs_updates() {
    SystemClock.setCurrentTimeMillis(100L);
    sharedPreferencesUtil.setDownloadingCustomModelDetails(CUSTOM_MODEL_DOWNLOADING);
    assertEquals(sharedPreferencesUtil.getModelDownloadBeginTimeMs(CUSTOM_MODEL_DOWNLOADING), 100L);

    // Completing the download clears the begin time.
    SystemClock.setCurrentTimeMillis(200L);
    sharedPreferencesUtil.setLoadedCustomModelDetails(CUSTOM_MODEL_DOWNLOAD_COMPLETE);
    assertEquals(sharedPreferencesUtil.getModelDownloadBeginTimeMs(CUSTOM_MODEL_DOWNLOADING), 0L);
  }

  @Test
  public void getModelDownloadCompleteTimeMs_default0() {
    assertEquals(
        sharedPreferencesUtil.getModelDownloadCompleteTimeMs(CUSTOM_MODEL_DOWNLOADING), 0L);
  }

  @Test
  public void setModelDownloadCompleteTimeMs_updates() {
    sharedPreferencesUtil.setModelDownloadCompleteTimeMs(CUSTOM_MODEL_DOWNLOADING, 100L);
    assertEquals(
        sharedPreferencesUtil.getModelDownloadCompleteTimeMs(CUSTOM_MODEL_DOWNLOADING), 100L);

    // Completing the download clears the completion time.
    sharedPreferencesUtil.setModelDownloadCompleteTimeMs(CUSTOM_MODEL_DOWNLOADING, 250L);
    assertEquals(
        sharedPreferencesUtil.getModelDownloadCompleteTimeMs(CUSTOM_MODEL_DOWNLOADING), 250L);

    // Completing the download clears the begin time.
    SystemClock.setCurrentTimeMillis(300L);
    sharedPreferencesUtil.setLoadedCustomModelDetails(CUSTOM_MODEL_DOWNLOAD_COMPLETE);
    assertEquals(
        sharedPreferencesUtil.getModelDownloadCompleteTimeMs(CUSTOM_MODEL_DOWNLOADING), 0L);
  }
}
