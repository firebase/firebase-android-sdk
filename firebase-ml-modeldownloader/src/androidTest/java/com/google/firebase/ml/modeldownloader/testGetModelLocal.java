package com.google.firebase.ml.modeldownloader;

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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.modeldownloader.internal.SharedPreferencesUtil;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Integration tests for Get Model, download type LOCAL_MODEL. */
@RunWith(AndroidJUnit4.class)
class testGetModelLocal {
  private FirebaseModelDownloader firebaseModelDownloader;
  private static final String MODEL_NAME_LOCAL = "getLocalModel";

  @Before
  public void before() throws ExecutionException, InterruptedException {
    if (firebaseModelDownloader == null) {
      FirebaseApp app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext());
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

  @Test
  public void getLocalModel() throws ExecutionException, InterruptedException, FirebaseMlException {

    Task<CustomModel> modelTask =
        FirebaseModelDownloader.getInstance()
            .getModel(
                MODEL_NAME_LOCAL,
                DownloadType.LOCAL_MODEL,
                new CustomModelDownloadConditions.Builder().build());
    Tasks.await(modelTask);
    assertTrue(modelTask.isSuccessful());
    assertEquals(modelTask.getResult().getName(), MODEL_NAME_LOCAL);
    assertNotNull(modelTask.getResult().getFile());
    fail("force failure");
  }

  private Set<CustomModel> getDownloadedModelList()
      throws ExecutionException, InterruptedException {

    Task<Set<CustomModel>> modelSetTask =
        FirebaseModelDownloader.getInstance().listDownloadedModels();

    //    Set<CustomModel> mylist;

    //    modelSetTask.addOnSuccessListener(new OnSuccessListener<Set<CustomModel>>() {
    //      @Override
    //      public void onSuccess(Set<CustomModel> customModelSet) {
    //        // Task completed successfully
    //        // ...
    //        return;
    //      }
    //    });
    //    modelSetTask.addOnFailureListener(new OnFailureListener() {
    //      @Override
    //      public void onFailure(@NonNull Exception e) {
    //        throw new Exception("Unexpected error calling list downloaded models");
    //      }
    //    });

    Tasks.await(modelSetTask);
    if (modelSetTask.isSuccessful()) {
      return modelSetTask.getResult();
    }
    throw new InternalError("listDownloadedModels unexpected failure.");
  }
}
