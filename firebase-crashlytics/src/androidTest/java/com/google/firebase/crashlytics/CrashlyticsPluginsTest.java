/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.crashlytics.internal.common.CrashlyticsCore;
import com.google.firebase.crashlytics.internal.common.DataCollectionArbiter;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Tests for the internal apis that development platform plugins call. */
public class CrashlyticsPluginsTest {
  private static final String APP_ID = "1:1:android:1a";
  private static final String API_KEY = "API-KEY-API-KEY-API-KEY-API-KEY-API-KEY";
  private static final String PROJECT_ID = "PROJECT-ID";

  @Before
  public void setUp() {
    FirebaseApp.initializeApp(
        ApplicationProvider.getApplicationContext(),
        new FirebaseOptions.Builder()
            .setApplicationId(APP_ID)
            .setApiKey(API_KEY)
            .setProjectId(PROJECT_ID)
            .build());
  }

  @After
  public void tearDown() {
    FirebaseApp.clearInstancesForTest();
  }

  @Test
  public void accessCrashlyticsCore() {
    // Both Flutter and Unity plugins access CrashlyticsCore from FirebaseCrashlytics.core field.
    CrashlyticsCore core = FirebaseCrashlytics.getInstance().core;
    assertThat(core).isNotNull();

    // Verify the internal method logFatalException exists without reflection.
    Runnable logFatalException = () -> core.logFatalException(new Throwable());
    assertThat(logFatalException).isNotNull();

    // Verify the internal method setInternalKey exists without reflection.
    Runnable setInternalKey = () -> core.setInternalKey("", "");
    assertThat(setInternalKey).isNotNull();
  }

  @Test
  public void accessDataCollection() throws Exception {
    // The Unity plugin accesses CrashlyticsCore.dataCollectionArbiter this via reflection.
    CrashlyticsCore core = FirebaseCrashlytics.getInstance().core;
    Field field = core.getClass().getDeclaredField("dataCollectionArbiter");
    field.setAccessible(true); // The dataCollectionArbiter field is private in CrashlyticsCore.
    DataCollectionArbiter dataCollectionArbiter = (DataCollectionArbiter) field.get(core);

    assertThat(dataCollectionArbiter).isNotNull();
  }
}
