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

  public static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  public static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  public static final String TEST_AUTH_TOKEN = "fad.auth.token";
  public static final String TEST_FID_1 = "cccccccccccccccccccccc";
  public static final String INVALID_RESPONSE = "InvalidResponse";
  public static final String SUCCESSFUL_RESPONSE =
      "{\"releases\": [{\"name\":\"devices/3d6942794f7cecca44e0ff4f/testerApps/1:378474073654:android:f886de0625fd488dc21297/releases/2g0n7rk01mjdo\",\"releaseTime\":\"2021-07-07T17:10:03Z\",\"buildVersion\":\"3\",\"displayVersion\":\"3.0\",\"releaseNotes\":\"This is a test release.\",\"downloadUrl\":\"https://firebaseapptesters.googleapis.com/v1alpha/devices/3d6942794f7cecca44e0ff4f/testerApps/1:378474073654:android:f886de0625fd488dc21297/releases/2g0n7rk01mjdo:download?tester_client=ios_sdk&token=AFb1MRwAAAAAYO3Cpy79kVVQDN4P1P-ija4QSO3MKnkfrYvx1XYkcbQ_1mCZ6mUjto5d4NG77CnEL0lFLqLGuSTi9DbTzAa-tKCukMP2yuvif8Rm9wtCO9k8KekV7mHNNNlWs9WczBc1quZjJ8pwHCGE-XAlXxR5hN7ZxNFXns0igQlRZoryjmADZdnvfHlJ3-dC616_g93vRsAHmjBVMz-gO1Wv1iuYhw9Des0Eh0VOmzqLgYZxaRdcdJOzpPijHzQAYiyMS3VkOhrdAQKsWZ48NwYHJMbI_o0G9WLElH1wlL63Vs9DAeBwZNRCqtFpAkcpzO9UuTipP-UsFRmanf7IH6QceOda4dM8vzM&key=AIzaSyCT43_YT7B59u5KtzIlbJrwANinug62pHo\",\"latest\":true,\"codeHash\":\"aa8002876b1e9b90b81df4ca3bb80529be670f35\",\"fileSize\":\"3725041\",\"expirationTime\":\"2021-12-04T17:10:03Z\",\"binaryType\":\"APK\"}]}";
  private static final int DEFAULT_BUFFER_SIZE = 8192;

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
    AppDistributionRelease release = null;
    try {
      release =
          firebaseAppDistributionTesterApiClient.fetchLatestRelease(
              TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN);
    } catch (Exception e) {
      assertEquals(e.getClass(), FirebaseAppDistributionException.class);
    }
    assertNull(release);
  }

  @Test
  public void fetchLatestRelease_whenInvalidJson_throwsError() throws Exception {
    InputStream response =
        new ByteArrayInputStream(INVALID_RESPONSE.getBytes(StandardCharsets.UTF_8));
    when(mockHttpsURLConnection.getInputStream()).thenReturn(response);
    AppDistributionRelease release = null;
    try {
      release =
          firebaseAppDistributionTesterApiClient.fetchLatestRelease(
              TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN);
    } catch (Exception e) {
      assertEquals(e.getClass(), FirebaseAppDistributionException.class);
    }
    assertNull(release);
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
