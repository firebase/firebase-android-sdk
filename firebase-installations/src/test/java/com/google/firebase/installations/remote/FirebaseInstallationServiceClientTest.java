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

package com.google.firebase.installations.remote;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.installations.FirebaseInstallationsException;
import com.google.firebase.installations.FirebaseInstallationsException.Status;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link FirebaseInstallationServiceClient}. */
@RunWith(RobolectricTestRunner.class)
public class FirebaseInstallationServiceClientTest {

  private final String TEST_EXPIRATION_TIMESTAMP = "604800s";
  private final long TEST_EXPIRATION_IN_SECS = 604800;
  private final String INCORRECT_EXPIRATION_TIMESTAMP = "2345";

  private static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String TEST_FID = "cccccccccccccccccccccc";
  private static final String TEST_PROJECT_ID = "777777777777";
  private static final String TEST_APP_ID = "1:123456789:android:abcdef";
  private static final String TEST_REFRESH_TOKEN = "1:test-refresh-token";

  @Test
  public void parseTokenExpirationTimestamp_successful() {
    long actual =
        FirebaseInstallationServiceClient.parseTokenExpirationTimestamp(TEST_EXPIRATION_TIMESTAMP);

    assertWithMessage("Exception status doesn't match")
        .that(actual)
        .isEqualTo(TEST_EXPIRATION_IN_SECS);
  }

  @Test
  public void parseTokenExpirationTimestamp_failed() {
    try {
      FirebaseInstallationServiceClient.parseTokenExpirationTimestamp(
          INCORRECT_EXPIRATION_TIMESTAMP);
      fail("Parsing token expiration timestamp failed.");
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo(FirebaseInstallationServiceClient.PARSING_EXPIRATION_TIME_ERROR_MESSAGE);
    }
  }

  @Test
  public void generateAuthToken_validResponse_returnsToken() throws Exception {
    FakeResponseServiceClient client =
        new FakeResponseServiceClient(
            "{\"token\": \"fis.auth.token\", \"expiresIn\": \"604800s\"}");

    TokenResult result =
        client.generateAuthToken(TEST_API_KEY, TEST_FID, TEST_PROJECT_ID, TEST_REFRESH_TOKEN);

    assertThat(result.getResponseCode()).isEqualTo(TokenResult.ResponseCode.OK);
    assertThat(result.getToken()).isEqualTo("fis.auth.token");
    assertThat(result.getTokenExpirationTimestamp()).isEqualTo(604800L);
    assertThat(client.connectionCount).isEqualTo(1);
  }

  @Test
  public void generateAuthToken_expiresInBeyondLongRange_throwsUnavailable() {
    assertGenerateAuthTokenThrowsUnavailable(
        "{\"token\": \"fis.auth.token\", \"expiresIn\": \"99999999999999999999s\"}");
  }

  @Test
  public void generateAuthToken_expiresInWithoutUnit_throwsUnavailable() {
    assertGenerateAuthTokenThrowsUnavailable(
        "{\"token\": \"fis.auth.token\", \"expiresIn\": \"1800\"}");
  }

  @Test
  public void generateAuthToken_expiresInIsObject_throwsUnavailable() {
    assertGenerateAuthTokenThrowsUnavailable("{\"token\": \"fis.auth.token\", \"expiresIn\": {}}");
  }

  @Test
  public void generateAuthToken_expiresInIsNull_throwsUnavailable() {
    assertGenerateAuthTokenThrowsUnavailable(
        "{\"token\": \"fis.auth.token\", \"expiresIn\": null}");
  }

  @Test
  public void createFirebaseInstallation_expiresInBeyondLongRange_throwsUnavailable() {
    FakeResponseServiceClient client =
        new FakeResponseServiceClient(
            "{\"name\": \"projects/777777777777/installations/cccccccccccccccccccccc\", "
                + "\"fid\": \"cccccccccccccccccccccc\", "
                + "\"refreshToken\": \"1:test-refresh-token\", "
                + "\"authToken\": {\"token\": \"fis.auth.token\", "
                + "\"expiresIn\": \"99999999999999999999s\"}}");

    FirebaseInstallationsException expected =
        assertThrows(
            FirebaseInstallationsException.class,
            () ->
                client.createFirebaseInstallation(
                    TEST_API_KEY, TEST_FID, TEST_PROJECT_ID, TEST_APP_ID, /* iidToken= */ null));

    assertThat(expected.getStatus()).isEqualTo(Status.UNAVAILABLE);
    // Like an unreadable response, the request is retried once before failing.
    assertThat(client.connectionCount).isEqualTo(2);
  }

  /**
   * Asserts that a generateAuthToken call whose response contains the given body fails with
   * {@link Status#UNAVAILABLE} after one retry, instead of throwing a runtime exception.
   */
  private static void assertGenerateAuthTokenThrowsUnavailable(String responseBody) {
    FakeResponseServiceClient client = new FakeResponseServiceClient(responseBody);

    FirebaseInstallationsException expected =
        assertThrows(
            FirebaseInstallationsException.class,
            () ->
                client.generateAuthToken(
                    TEST_API_KEY, TEST_FID, TEST_PROJECT_ID, TEST_REFRESH_TOKEN));

    assertThat(expected.getStatus()).isEqualTo(Status.UNAVAILABLE);
    // Like an unreadable response, the request is retried once before failing.
    assertThat(client.connectionCount).isEqualTo(2);
  }

  /** A client whose requests all get an HTTP 200 response with the given body. */
  private static class FakeResponseServiceClient extends FirebaseInstallationServiceClient {
    private final String responseBody;
    int connectionCount;

    FakeResponseServiceClient(String responseBody) {
      super(ApplicationProvider.getApplicationContext(), () -> null);
      this.responseBody = responseBody;
    }

    @Override
    HttpURLConnection openConnection(URL url) {
      connectionCount++;
      return new FakeHttpURLConnection(url, responseBody);
    }
  }

  private static class FakeHttpURLConnection extends HttpURLConnection {
    private final String responseBody;

    FakeHttpURLConnection(URL url, String responseBody) {
      super(url);
      this.responseBody = responseBody;
    }

    @Override
    public int getResponseCode() {
      return HTTP_OK;
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(responseBody.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public OutputStream getOutputStream() {
      return new ByteArrayOutputStream();
    }

    @Override
    public void connect() {}

    @Override
    public void disconnect() {}

    @Override
    public boolean usingProxy() {
      return false;
    }
  }
}
