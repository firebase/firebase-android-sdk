// Copyright 2019 Google LLC
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

package com.google.firebase.datatransport;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.runner.AndroidJUnit4;
import com.google.android.datatransport.TransportFactory;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

@RunWith(AndroidJUnit4.class)
public class TransportRegistrationTest {
  private static void withApp(Consumer<FirebaseApp> consumer) {
    FirebaseApp app =
        FirebaseApp.initializeApp(
            RuntimeEnvironment.application,
            new FirebaseOptions.Builder()
                .setApplicationId("appId")
                .setProjectId("123")
                .setApiKey("apiKey")
                .build(),
            "datatransport-test-app");
    try {
      consumer.accept(app);
    } finally {
      app.delete();
    }
  }

  @Test
  public void test_componentIsRegisteredAndAvailable() {
    withApp(
        app -> {
          TransportFactory transportFactory = app.get(TransportFactory.class);
          assertThat(transportFactory).isNotNull();
        });
  }
}
