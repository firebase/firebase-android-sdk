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

package com.google.firebase;

import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.os.StrictMode.VmPolicy;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseOptions.Builder;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4.class)
public class StrictModeTest {

  interface Fn<E extends Throwable> {
    void call() throws E;
  }

  static <E extends Throwable> void withStrictMode(Fn<E> fn) throws E {
    ThreadPolicy threadPolicy = StrictMode.getThreadPolicy();
    VmPolicy vmPolicy = StrictMode.getVmPolicy();

    StrictMode.setThreadPolicy(new ThreadPolicy.Builder().detectAll().penaltyDeath().build());
    StrictMode.setVmPolicy(new VmPolicy.Builder().detectAll().penaltyDeath().build());
    try {
      fn.call();
    } finally {
      StrictMode.setThreadPolicy(threadPolicy);
      StrictMode.setVmPolicy(vmPolicy);
    }
  }

  @Test
  public void initializingFirebaseApp_shouldNotViolateStrictMode() {
    withStrictMode(
        () -> {
          FirebaseApp app =
              FirebaseApp.initializeApp(
                  ApplicationProvider.getApplicationContext(),
                  new Builder()
                      .setApiKey("api")
                      .setProjectId("123")
                      .setApplicationId("appId")
                      .build(),
                  "hello");

          app.initializeAllComponents();
        });
  }
}
