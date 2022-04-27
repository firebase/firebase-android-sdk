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
package com.google.firebase.messaging.testing;

import android.content.Context;
import androidx.core.content.ContextCompat;
import java.io.File;

/** Test utilities for API tests that use {@link com.google.firebase.iid.FirebaseInstanceId}. */
public final class FirebaseIidTestUtil {

  // Sender, key, app id, project id from project: FirebaseMessagingApiTests, gcm-eng@ has access
  public static final String GOOGLE_APP_ID = "1:635258614906:android:71ccab5d92c5d7c4";
  public static final String SENDER = "635258614906";
  public static final String KEY = "AIzaSyBF2RrAIm4a0mO64EShQfqfd2AFnzAvvuU";
  public static final String PROJECT_ID = "ghmessagingapitests-dab42";
  public static final String FCM_SCOPE = "fcm";

  private static final String SHARED_PREFERENCES = "com.google.android.gms.appid";
  private static final String PROPERTIES_FILE_NAME_PREFIX = "com.google.InstanceId";

  private FirebaseIidTestUtil() {}

  /** Clear the data persisted by FirebaseInstanceId. */
  public static void clearPersistedData(Context context) {
    // Clear the shared preferences
    context.getSharedPreferences(SHARED_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit();

    // And all the key pair files
    File dir = ContextCompat.getNoBackupFilesDir(context);
    for (File child : dir.listFiles()) {
      if (child.getName().startsWith(PROPERTIES_FILE_NAME_PREFIX)) {
        child.delete();
      }
    }
  }
}
