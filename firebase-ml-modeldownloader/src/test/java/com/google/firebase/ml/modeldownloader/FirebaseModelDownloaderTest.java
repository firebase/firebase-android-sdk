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
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.FirebaseOptions.Builder;
import com.google.firebase.ml.modeldownloader.internal.SharedPreferencesUtil;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirebaseModelDownloaderTest {

  public static final String TEST_PROJECT_ID = "777777777777";
  public static final FirebaseOptions FIREBASE_OPTIONS =
      new Builder()
          .setApplicationId("1:123456789:android:abcdef")
          .setProjectId(TEST_PROJECT_ID)
          .build();
  public static final String MODEL_NAME = "MODEL_NAME_1";
  public static final CustomModelDownloadConditions DEFAULT_DOWNLOAD_CONDITIONS =
      new CustomModelDownloadConditions.Builder().build();

  public static final String MODEL_HASH = "dsf324";
  // TODO replace with uploaded model.
  CustomModel CUSTOM_MODEL = new CustomModel(MODEL_NAME, MODEL_HASH, 100, 0);

  FirebaseModelDownloader firebaseModelDownloader;
  @Mock SharedPreferencesUtil mockPrefs;

  ExecutorService executor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    FirebaseApp.clearInstancesForTest();
    // default app
    FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), FIREBASE_OPTIONS);

    executor = Executors.newSingleThreadExecutor();
    firebaseModelDownloader = new FirebaseModelDownloader(FIREBASE_OPTIONS, mockPrefs, executor);
  }

  @Test
  public void getModel_unimplemented() {
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            FirebaseModelDownloader.getInstance()
                .getModel(MODEL_NAME, DownloadType.LOCAL_MODEL, DEFAULT_DOWNLOAD_CONDITIONS));
  }

  @Test
  public void listDownloadedModels_returnsEmptyModelList()
      throws ExecutionException, InterruptedException {
    when(mockPrefs.listDownloadedModels()).thenReturn(Collections.emptySet());
    TestOnCompleteListener<Set<CustomModel>> onCompleteListener = new TestOnCompleteListener<>();
    Task<Set<CustomModel>> task = firebaseModelDownloader.listDownloadedModels();
    task.addOnCompleteListener(executor, onCompleteListener);
    Set<CustomModel> customModelSet = onCompleteListener.await();

    assertThat(task.isComplete()).isTrue();
    assertEquals(customModelSet, Collections.EMPTY_SET);
  }

  @Test
  public void listDownloadedModels_returnsModelList()
      throws ExecutionException, InterruptedException {
    when(mockPrefs.listDownloadedModels()).thenReturn(Collections.singleton(CUSTOM_MODEL));

    TestOnCompleteListener<Set<CustomModel>> onCompleteListener = new TestOnCompleteListener<>();
    Task<Set<CustomModel>> task = firebaseModelDownloader.listDownloadedModels();
    task.addOnCompleteListener(executor, onCompleteListener);
    Set<CustomModel> customModelSet = onCompleteListener.await();

    assertThat(task.isComplete()).isTrue();
    assertEquals(customModelSet, Collections.singleton(CUSTOM_MODEL));
  }

  @Test
  public void deleteDownloadedModel_unimplemented() {
    assertThrows(
        UnsupportedOperationException.class,
        () -> FirebaseModelDownloader.getInstance().deleteDownloadedModel(MODEL_NAME));
  }
}
