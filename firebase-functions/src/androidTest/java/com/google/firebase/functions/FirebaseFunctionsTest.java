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

package com.google.firebase.functions;

import static org.junit.Assert.assertEquals;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import java.net.URL;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FirebaseFunctionsTest {

  @Test
  public void testGetUrl() {
    FirebaseApp app = getApp("testGetUrl");

    FirebaseFunctions functions = FirebaseFunctions.getInstance(app, "my-region");
    URL url = functions.getURL("my-endpoint");
    assertEquals("https://my-region-my-project.cloudfunctions.net/my-endpoint", url.toString());

    functions = FirebaseFunctions.getInstance(app);
    url = functions.getURL("my-endpoint");
    assertEquals("https://us-central1-my-project.cloudfunctions.net/my-endpoint", url.toString());

    functions = FirebaseFunctions.getInstance(app, "https://mydomain.com");
    url = functions.getURL("my-endpoint");
    assertEquals("https://mydomain.com/my-endpoint", url.toString());

    functions = FirebaseFunctions.getInstance(app, "https://mydomain.com/foo");
    url = functions.getURL("my-endpoint");
    assertEquals("https://mydomain.com/foo/my-endpoint", url.toString());
  }

  @Test
  public void testGetUrl_withEmulator() {
    FirebaseApp app = getApp("testGetUrl_withEmulator");

    FirebaseFunctions functions = FirebaseFunctions.getInstance(app);
    functions.useEmulator("10.0.2.2", 5001);

    FirebaseFunctions functionsWithoutRegion = FirebaseFunctions.getInstance(app);
    URL withoutRegion = functionsWithoutRegion.getURL("my-endpoint");
    assertEquals(
        "http://10.0.2.2:5001/my-project/us-central1/my-endpoint", withoutRegion.toString());

    FirebaseFunctions functionsWithRegion = FirebaseFunctions.getInstance(app, "my-region");
    functionsWithRegion.useEmulator("10.0.2.2", 5001);

    URL withRegion = functionsWithRegion.getURL("my-endpoint");
    assertEquals("http://10.0.2.2:5001/my-project/my-region/my-endpoint", withRegion.toString());

    FirebaseFunctions functionsWithCustomDomain =
        FirebaseFunctions.getInstance(app, "https://mydomain.com");
    functionsWithCustomDomain.useEmulator("10.0.2.2", 5001);

    URL withCustomDOmain = functionsWithCustomDomain.getURL("my-endpoint");
    assertEquals(
        "http://10.0.2.2:5001/my-project/us-central1/my-endpoint", withCustomDOmain.toString());
  }

  @Test
  public void testGetUrl_withEmulator_matchesOldImpl() {
    FirebaseApp app = getApp("testGetUrl_withEmulator_matchesOldImpl");

    FirebaseFunctions functions = FirebaseFunctions.getInstance(app);
    functions.useEmulator("10.0.2.2", 5001);
    URL newImplUrl = functions.getURL("my-endpoint");

    functions.useFunctionsEmulator("http://10.0.2.2:5001");
    URL oldImplUrl = functions.getURL("my-endpoint");

    assertEquals(newImplUrl.toString(), oldImplUrl.toString());
  }

  @Test
  public void testEmulatorSettings() {
    FirebaseApp app = getApp("testEmulatorSettings");

    FirebaseFunctions functions1 = FirebaseFunctions.getInstance(app);
    functions1.useEmulator("10.0.2.2", 5001);

    FirebaseFunctions functions2 = FirebaseFunctions.getInstance(app);

    assertEquals(functions1.getURL("foo").toString(), functions2.getURL("foo").toString());
  }

  private FirebaseApp getApp(String name) {
    return FirebaseApp.initializeApp(
        InstrumentationRegistry.getInstrumentation().getTargetContext(),
        new FirebaseOptions.Builder()
            .setProjectId("my-project")
            .setApplicationId("appid")
            .setApiKey("apikey")
            .build(),
        name);
  }
}
