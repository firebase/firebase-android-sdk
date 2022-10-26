// Copyright 2022 Google LLC
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

package com.google.firebase.appcheck;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.appcheck.interop.InternalAppCheckTokenProvider;
import com.google.firebase.testing.integ.StrictModeRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StrictModeTest {

  @Rule public StrictModeRule strictMode = new StrictModeRule();

  @Test
  public void initializingFirebaseAppcheck_shouldNotViolateStrictMode() {
    strictMode.runOnMainThread(
        () -> {
          FirebaseApp app =
              FirebaseApp.initializeApp(
                  ApplicationProvider.getApplicationContext(),
                  new FirebaseOptions.Builder()
                      .setApiKey("api")
                      .setProjectId("123")
                      .setApplicationId("appId")
                      .build(),
                  "hello");
          app.get(FirebaseAppCheck.class);
          app.get(InternalAppCheckTokenProvider.class);
        });
  }
}
