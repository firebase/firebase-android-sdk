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

package com.google.firebase.ml_data_collection_tests;

import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader;
import com.google.firebase.ml.modeldownloader.internal.SharedPreferencesUtil;
import java.util.function.Consumer;

final class MlDataCollectionTestUtil {
  private static final String APP_NAME = "someApp";
  private static final String APP_ID = "appId";
  private static final String TEST_PROJECT_ID = "777777777777";

  private static final FirebaseOptions OPTIONS =
      new FirebaseOptions.Builder().setApplicationId(APP_ID).setProjectId(TEST_PROJECT_ID).build();

  private MlDataCollectionTestUtil() {}

  static void withApp(Consumer<FirebaseApp> callable) {
    withApp(APP_NAME, callable);
  }

  static void withApp(String name, Consumer<FirebaseApp> callable) {
    FirebaseApp app =
        FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), OPTIONS, name);
    try {
      callable.accept(app);
    } finally {
      app.delete();
    }
  }

  static SharedPreferencesUtil getSharedPreferencesUtil(FirebaseApp app) {
    return new SharedPreferencesUtil(
        app, FirebaseModelDownloader.getInstance(app).getModelFactory());
  }

  static void setSharedPreferencesTo(FirebaseApp app, Boolean enabled) {
    getSharedPreferencesUtil(app).setCustomModelStatsCollectionEnabled(enabled);
  }
}
