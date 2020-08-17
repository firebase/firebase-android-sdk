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

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.iid.FirebaseInstanceId;
import org.robolectric.RuntimeEnvironment;

/** Test helper for Firebase IID robolectric tests. */
public class FirebaseIidRoboTestHelper {

  private static final String TAG = "FIIDTestUtil";

  public static final String ACTION_IID_TOKEN_REQUEST = "com.google.iid.TOKEN_REQUEST";
  public static final String ACTION_REGISTER = "com.google.android.c2dm.intent.REGISTER";
  public static final String ACTION_REGISTRATION_RESULT =
      "com.google.android.c2dm.intent.REGISTRATION";
  public static final String PACKAGE_GMS = "com.google.android.gms";

  public static final String GOOGLE_PLAY_SERVICES_PACKAGE = "com.google.android.gms";

  /** GMScore holds this permission. Use it for security checks. */
  private static final String GMSCORE_SEND_PERMISSION = "com.google.android.c2dm.permission.SEND";

  public static final String APP_ID = "1:635258614906:android:71ccab5d92c5d7c4";
  public static final String API_KEY = "AIzaSyBF2RrAIm4a0mO64EShQfqfd2AFnzAvvuU";
  public static final String SENDER_ID = "635258614906";
  public static final String PROJECT_ID = "ghmessagingapitests-dab42";

  public static FirebaseInstanceId initSpyFirebaseIid(Context context) {
    FirebaseOptions.Builder firebaseOptionsBuilder =
        new FirebaseOptions.Builder()
            .setApplicationId(APP_ID)
            .setApiKey(API_KEY)
            .setGcmSenderId(SENDER_ID)
            .setProjectId(PROJECT_ID);
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(context, firebaseOptionsBuilder.build());

    @SuppressWarnings("FirebaseUseExplicitDependencies")
    FirebaseInstanceId iid = firebaseApp.get(FirebaseInstanceId.class);
    assertNotNull(iid);
    return iid;
  }

  public static FirebaseInstanceId initMockFirebaseIid(Context context) {
    return mock(FirebaseInstanceId.class);
  }

  /**
   * Add a fake GmsCore package manager entry so doesn't fail which will block all getToken
   * requests.
   */
  public static void addGmsCorePackageInfo() {
    PackageManager pm = RuntimeEnvironment.application.getPackageManager();
    PackageInfo gmsPackageInfo = new PackageInfo();
    gmsPackageInfo.packageName = GOOGLE_PLAY_SERVICES_PACKAGE;
    gmsPackageInfo.applicationInfo = new ApplicationInfo();
    gmsPackageInfo.applicationInfo.packageName = gmsPackageInfo.packageName;
    gmsPackageInfo.requestedPermissions = new String[] {GMSCORE_SEND_PERMISSION};
    shadowOf(pm).addPackage(gmsPackageInfo);
  }

  public static void setGmsCoreVersion(int versionCode) throws NameNotFoundException {
    PackageManager pm = RuntimeEnvironment.application.getPackageManager();
    PackageInfo pi = shadowOf(pm).getInternalMutablePackageInfo(GOOGLE_PLAY_SERVICES_PACKAGE);
    pi.versionCode = versionCode;
  }
}
