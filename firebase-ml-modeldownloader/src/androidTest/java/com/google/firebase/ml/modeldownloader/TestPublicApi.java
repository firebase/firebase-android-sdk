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
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.modeldownloader.internal.SharedPreferencesUtil;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Integration tests for public API methods, excluding getModel (these have their own tests). */
@RunWith(AndroidJUnit4.class)
public class TestPublicApi {

  private static final String MODEL_NAME_LOCAL = "getLocalModel";
  private static final String MODEL_NAME_LOCAL_2 = "getLocalModel2";
  private static final String MODEL_HASH = "origHash324";

  private FirebaseApp app;

  @Before
  public void before() {
    app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext());
    app.setDataCollectionDefaultEnabled(Boolean.FALSE);
    FirebaseModelDownloader firebaseModelDownloader = FirebaseModelDownloader.getInstance(app);

    SharedPreferencesUtil sharedPreferencesUtil =
        new SharedPreferencesUtil(app, firebaseModelDownloader.getModelFactory());
    // reset shared preferences and downloads for models used by this test.
    firebaseModelDownloader.deleteDownloadedModel(MODEL_NAME_LOCAL);
    firebaseModelDownloader.deleteDownloadedModel(MODEL_NAME_LOCAL_2);

    // Equivalent to clearing the cache for the models used by this test.
    sharedPreferencesUtil.clearModelDetails(MODEL_NAME_LOCAL);
    sharedPreferencesUtil.clearModelDetails(MODEL_NAME_LOCAL_2);
  }

  @Test
  public void listModels() throws ExecutionException, InterruptedException {
    Task<Set<CustomModel>> modelSetTask =
        FirebaseModelDownloader.getInstance().listDownloadedModels();
    Tasks.await(modelSetTask);
    assertEquals(modelSetTask.getResult().size(), 0);

    Task<CustomModel> modelTask =
        FirebaseModelDownloader.getInstance()
            .getModel(
                MODEL_NAME_LOCAL,
                DownloadType.LATEST_MODEL,
                new CustomModelDownloadConditions.Builder().build());
    Tasks.await(modelTask);
    assertTrue(modelTask.isSuccessful());

    modelTask =
        FirebaseModelDownloader.getInstance()
            .getModel(
                MODEL_NAME_LOCAL_2,
                DownloadType.LOCAL_MODEL,
                new CustomModelDownloadConditions.Builder().build());
    Tasks.await(modelTask);
    assertTrue(modelTask.isSuccessful());

    modelSetTask = FirebaseModelDownloader.getInstance().listDownloadedModels();
    Tasks.await(modelSetTask);
    assertEquals(modelSetTask.getResult().size(), 2);
  }

  @Test
  public void deleteModels() throws ExecutionException, InterruptedException {
    Task<CustomModel> modelTask =
        FirebaseModelDownloader.getInstance()
            .getModel(
                MODEL_NAME_LOCAL,
                DownloadType.LATEST_MODEL,
                new CustomModelDownloadConditions.Builder().build());
    Tasks.await(modelTask);
    assertTrue(modelTask.isSuccessful());

    modelTask =
        FirebaseModelDownloader.getInstance()
            .getModel(
                MODEL_NAME_LOCAL_2,
                DownloadType.LOCAL_MODEL,
                new CustomModelDownloadConditions.Builder().build());
    Tasks.await(modelTask);
    assertTrue(modelTask.isSuccessful());
    Task<Set<CustomModel>> modelSetTask =
        FirebaseModelDownloader.getInstance().listDownloadedModels();
    Tasks.await(modelSetTask);
    assertEquals(modelSetTask.getResult().size(), 2);

    Task<Void> deleteTask =
        FirebaseModelDownloader.getInstance().deleteDownloadedModel(MODEL_NAME_LOCAL);
    Tasks.await(deleteTask);

    modelSetTask = FirebaseModelDownloader.getInstance().listDownloadedModels();
    Tasks.await(modelSetTask);
    assertEquals(modelSetTask.getResult().size(), 1);
  }

  @Test
  public void getDownloadId() throws ExecutionException, InterruptedException {
    Task<CustomModel> modelTask =
        FirebaseModelDownloader.getInstance()
            .getModel(
                MODEL_NAME_LOCAL,
                DownloadType.LATEST_MODEL,
                new CustomModelDownloadConditions.Builder().build());
    Task<Long> downloadIdTask =
        FirebaseModelDownloader.getInstance().getModelDownloadId(MODEL_NAME_LOCAL, modelTask);
    Tasks.await(downloadIdTask);

    // small possibility this will be 0 due to speed of task completion above but extremely unlikely
    assertTrue(downloadIdTask.getResult() > 0);

    Tasks.await(modelTask);
    assertTrue(modelTask.isSuccessful());

    downloadIdTask =
        FirebaseModelDownloader.getInstance().getModelDownloadId(MODEL_NAME_LOCAL, modelTask);
    Tasks.await(downloadIdTask);

    assertTrue(downloadIdTask.getResult() == 0);
  }
}
