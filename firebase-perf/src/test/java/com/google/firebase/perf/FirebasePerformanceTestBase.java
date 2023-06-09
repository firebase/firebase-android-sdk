// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf;

import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.session.PerfSession;
import com.google.firebase.perf.session.SessionManager;
import com.google.firebase.perf.util.ImmutableBundle;
import org.junit.After;
import org.junit.Before;
import org.robolectric.shadows.ShadowPackageManager;

public class FirebasePerformanceTestBase {

  /**
   * The following values are needed by Firebase to identify the project and application that all
   * data stored in Firebase databases gets associated with. This is important to determine data
   * ownership for wipeout compliance and similar policies.
   *
   * <p>You can find (or update) these values in the Firebase Console: <br>
   * https://firebase.corp.google.com/u/0/project/fir-perftestapp/settings/general/
   *
   * <p>You can find (or update) the API key in the Cloud Console: <br>
   * https://pantheon.corp.google.com/apis/credentials?project=fir-perftestapp
   *
   * <p>Note: The Firebase App is not an actual application, but a dummy app for data ownership.
   */
  protected static final String FAKE_FIREBASE_APPLICATION_ID =
      "1:149208680807:android:0000000000000000";

  protected static final String FAKE_FIREBASE_API_KEY = "AIzaSyBcE-OOIbhjyR83gm4r2MFCu4MJmprNXsw";
  protected static final String FAKE_FIREBASE_DB_URL = "https://fir-perftestapp.firebaseio.com";
  protected static final String FAKE_FIREBASE_PROJECT_ID = "fir-perftestapp";

  protected Context appContext;

  @Before
  public void setUpFirebaseApp() {
    appContext = ApplicationProvider.getApplicationContext();

    ShadowPackageManager shadowPackageManager = shadowOf(appContext.getPackageManager());

    PackageInfo packageInfo =
        shadowPackageManager.getInternalMutablePackageInfo(appContext.getPackageName());
    packageInfo.versionName = "1.0.0";

    FirebaseOptions options =
        new FirebaseOptions.Builder()
            .setApplicationId(FAKE_FIREBASE_APPLICATION_ID)
            .setApiKey(FAKE_FIREBASE_API_KEY)
            .setDatabaseUrl(FAKE_FIREBASE_DB_URL)
            .setProjectId(FAKE_FIREBASE_PROJECT_ID)
            .build();
    FirebaseApp.initializeApp(appContext, options);
  }

  @After
  public void tearDownFirebaseApp() {
    FirebaseApp.clearInstancesForTest();
  }

  protected static void forceSessionsFeatureDisabled() {
    Bundle bundle = new Bundle();
    bundle.putFloat("sessions_sampling_percentage", 0);
    ConfigResolver.getInstance().setMetadataBundle(new ImmutableBundle(bundle));
  }

  protected static void forceVerboseSession() {
    forceVerboseSessionWithSamplingPercentage(100);
  }

  protected static void forceNonVerboseSession() {
    forceVerboseSessionWithSamplingPercentage(0);
  }

  private static void forceVerboseSessionWithSamplingPercentage(long samplingPercentage) {
    Bundle bundle = new Bundle();
    bundle.putFloat("sessions_sampling_percentage", samplingPercentage);
    ConfigResolver.getInstance().setMetadataBundle(new ImmutableBundle(bundle));

    SessionManager.getInstance().setPerfSession(PerfSession.createWithId("sessionId"));
  }
}
