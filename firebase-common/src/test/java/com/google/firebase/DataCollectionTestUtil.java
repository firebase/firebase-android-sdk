// Copyright 2018 Google LLC
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

package com.google.firebase;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.internal.DataCollectionConfigStorage;
import java.util.function.Consumer;
import org.robolectric.RuntimeEnvironment;

final class DataCollectionTestUtil {
  private static final String APP_NAME = "someApp";
  private static final String APP_ID = "appId";

  private static final String FIREBASE_APP_PREFS = "com.google.firebase.common.prefs:";

  private static final FirebaseOptions OPTIONS =
      new FirebaseOptions.Builder().setApplicationId(APP_ID).build();

  private DataCollectionTestUtil() {}

  static void withApp(Consumer<FirebaseApp> callable) {
    withApp(APP_NAME, callable);
  }

  static void withApp(String name, Consumer<FirebaseApp> callable) {
    FirebaseApp app =
        FirebaseApp.initializeApp(
            RuntimeEnvironment.application.getApplicationContext(), OPTIONS, name);
    try {
      callable.accept(app);
    } finally {
      app.delete();
    }
  }

  static SharedPreferences getSharedPreferences() {
    return RuntimeEnvironment.application.getSharedPreferences(
        FIREBASE_APP_PREFS + FirebaseApp.getPersistenceKey(APP_NAME, OPTIONS),
        Context.MODE_PRIVATE);
  }

  static void setSharedPreferencesTo(boolean enabled) {
    getSharedPreferences()
        .edit()
        .putBoolean(DataCollectionConfigStorage.DATA_COLLECTION_DEFAULT_ENABLED, enabled)
        .commit();
  }
}
