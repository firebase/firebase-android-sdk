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

package com.google.firebase.database.apitest;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.FirebaseDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class FirebaseDatabaseApiTest {
  public static final String TEST_NAMESPACE = "http://tests.fblocal.com:9000";
  public static final String TEST_ALT_NAMESPACE = "http://tests2.fblocal.com:9000";

  @Test
  public void getInstanceForAppWithUrl() {
    FirebaseApp app = appForDatabaseUrl(TEST_ALT_NAMESPACE, "getInstanceForAppWithUrl");
    FirebaseDatabase db = FirebaseDatabase.getInstance(app, TEST_NAMESPACE);

    assertEquals(TEST_NAMESPACE, db.getReference().toString());
  }

  @Test
  public void getInstanceForAppWithHttpsUrl() {
    FirebaseApp app = appForDatabaseUrl(TEST_ALT_NAMESPACE, "getInstanceForAppWithHttpsUrl");
    FirebaseDatabase db = FirebaseDatabase.getInstance(app, "https://tests.fblocal.com:9000");

    assertEquals("https://tests.fblocal.com:9000", db.getReference().toString());
  }

  @Test
  public void getDifferentInstanceForAppWithUrl() {
    FirebaseApp app = appForDatabaseUrl(TEST_NAMESPACE, "getDifferentInstanceForAppWithUrl");
    FirebaseDatabase unspecified = FirebaseDatabase.getInstance(app);
    FirebaseDatabase original = FirebaseDatabase.getInstance(app, TEST_NAMESPACE);
    FirebaseDatabase alternate = FirebaseDatabase.getInstance(app, TEST_ALT_NAMESPACE);

    assertEquals(TEST_NAMESPACE, unspecified.getReference().toString());
    assertEquals(TEST_NAMESPACE, original.getReference().toString());
    assertEquals(TEST_ALT_NAMESPACE, alternate.getReference().toString());
  }

  private static FirebaseApp appForDatabaseUrl(String url, String name) {
    return FirebaseApp.initializeApp(
        getApplicationContext(),
        new FirebaseOptions.Builder()
            .setApplicationId("appid")
            .setApiKey("apikey")
            .setDatabaseUrl(url)
            .build(),
        name);
  }
}
