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
package com.google.firebase.functions

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.functions.FirebaseFunctions.Companion.getInstance
import junit.framework.TestCase.assertEquals
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirebaseFunctionsTest {
  @Test
  fun testGetUrl() {
    val app = getApp("testGetUrl")
    var functions = getInstance(app, "my-region")
    var url = functions.getURL("my-endpoint")
    assertEquals("https://my-region-my-project.cloudfunctions.net/my-endpoint", url.toString())

    functions = getInstance(app)
    url = functions.getURL("my-endpoint")
    assertEquals("https://us-central1-my-project.cloudfunctions.net/my-endpoint", url.toString())

    functions = getInstance(app, "https://mydomain.com")
    url = functions.getURL("my-endpoint")
    Assert.assertEquals("https://mydomain.com/my-endpoint", url.toString())

    functions = getInstance(app, "https://mydomain.com/foo")
    url = functions.getURL("my-endpoint")
    assertEquals("https://mydomain.com/foo/my-endpoint", url.toString())
  }

  @Test
  fun testGetUrl_withEmulator() {
    val app = getApp("testGetUrl_withEmulator")
    val functions = getInstance(app)
    functions.useEmulator("10.0.2.2", 5001)
    val functionsWithoutRegion = getInstance(app)
    val withoutRegion = functionsWithoutRegion.getURL("my-endpoint")
    assertEquals(
      "http://10.0.2.2:5001/my-project/us-central1/my-endpoint",
      withoutRegion.toString()
    )

    val functionsWithRegion = getInstance(app, "my-region")
    functionsWithRegion.useEmulator("10.0.2.2", 5001)
    val withRegion = functionsWithRegion.getURL("my-endpoint")
    assertEquals("http://10.0.2.2:5001/my-project/my-region/my-endpoint", withRegion.toString())

    val functionsWithCustomDomain = getInstance(app, "https://mydomain.com")
    functionsWithCustomDomain.useEmulator("10.0.2.2", 5001)
    val withCustomDOmain = functionsWithCustomDomain.getURL("my-endpoint")
    assertEquals(
      "http://10.0.2.2:5001/my-project/us-central1/my-endpoint",
      withCustomDOmain.toString()
    )
  }

  @Test
  fun testGetUrl_withEmulator_matchesOldImpl() {
    val app = getApp("testGetUrl_withEmulator_matchesOldImpl")
    val functions = getInstance(app)
    functions.useEmulator("10.0.2.2", 5001)
    val newImplUrl = functions.getURL("my-endpoint")
    functions.useFunctionsEmulator("http://10.0.2.2:5001")
    val oldImplUrl = functions.getURL("my-endpoint")
    assertEquals(newImplUrl.toString(), oldImplUrl.toString())
  }

  @Test
  fun testEmulatorSettings() {
    val app = getApp("testEmulatorSettings")
    val functions1 = getInstance(app)
    functions1.useEmulator("10.0.2.2", 5001)
    val functions2 = getInstance(app)
    assertEquals(functions1.getURL("foo").toString(), functions2.getURL("foo").toString())
  }

  private fun getApp(name: String): FirebaseApp {
    return FirebaseApp.initializeApp(
      InstrumentationRegistry.getInstrumentation().targetContext,
      FirebaseOptions.Builder()
        .setProjectId("my-project")
        .setApplicationId("appid")
        .setApiKey("apikey")
        .build(),
      name
    )
  }
}
