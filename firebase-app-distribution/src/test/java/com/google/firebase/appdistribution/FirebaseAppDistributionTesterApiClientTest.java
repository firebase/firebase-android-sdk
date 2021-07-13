// Copyright 2021 Google LLC
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

package com.google.firebase.appdistribution;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirebaseAppDistributionTesterApiClientTest {

  private static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  private static final String TEST_AUTH_TOKEN = "fad.auth.token";
  private static final String TEST_FID_1 = "cccccccccccccccccccccc";
  private static final String INVALID_RESPONSE = "InvalidResponse";

  private FirebaseAppDistributionTesterApiClient firebaseAppDistributionTesterApiClient;
  @Mock private HttpsURLConnection mockHttpsURLConnection;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    // using spy allows using doReturn to specify output
    // of a method while leaving other methods unmocked
    firebaseAppDistributionTesterApiClient =
        Mockito.spy(new FirebaseAppDistributionTesterApiClient());

    Mockito.doReturn(mockHttpsURLConnection)
        .when(firebaseAppDistributionTesterApiClient)
        .openHttpsUrlConnection(TEST_APP_ID_1, TEST_FID_1);
  }

  @Test
  public void fetchLatestRelease_whenResponseSuccessful_returnsRelease() throws Exception {
    JSONObject releaseJson = getTestJSON("testReleaseResponse.json");
    InputStream response =
        new ByteArrayInputStream(releaseJson.toString().getBytes(StandardCharsets.UTF_8));
    when(mockHttpsURLConnection.getInputStream()).thenReturn(response);
    AppDistributionRelease release =
        firebaseAppDistributionTesterApiClient.fetchLatestRelease(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN);
    assertEquals(release.getBinaryType(), BinaryType.APK);
    assertEquals(release.getBuildVersion(), "3");
    assertEquals(release.getDisplayVersion(), "3.0");
    assertEquals(release.getReleaseNotes(), "This is a test release.");
  }

  @Test
  public void fetchLatestRelease_whenResponseFails_throwsError() throws Exception {
    when(mockHttpsURLConnection.getInputStream()).thenThrow(new IOException());
    assertThrows(FirebaseAppDistributionException.class, () -> firebaseAppDistributionTesterApiClient.fetchLatestRelease(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN));
  }

  @Test
  public void fetchLatestRelease_whenInvalidJson_throwsError() throws Exception {
    InputStream response =
        new ByteArrayInputStream(INVALID_RESPONSE.getBytes(StandardCharsets.UTF_8));
    when(mockHttpsURLConnection.getInputStream()).thenReturn(response);
    assertThrows(FirebaseAppDistributionException.class, () -> firebaseAppDistributionTesterApiClient.fetchLatestRelease(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN));
  }

  private JSONObject getTestJSON(String fileName) throws IOException, JSONException {
    final InputStream jsonInputStream = getContext().getResources().getAssets().open(fileName);
    final String testJsonString = streamToString(jsonInputStream);
    final JSONObject testJson = new JSONObject(testJsonString);
    return testJson;
  }

  private static String streamToString(InputStream is) {
    final java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
    return s.hasNext() ? s.next() : "";
  }
}
