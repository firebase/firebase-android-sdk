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

package com.google.firebase.appcheck.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import android.util.Base64;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link DefaultAppCheckToken} */
@RunWith(RobolectricTestRunner.class)
public class DefaultAppCheckTokenTest {

  private static final String TOKEN_PAYLOAD = "tokenPayload";
  private static final String TIME_TO_LIVE_ONE_HOUR = "3600s"; // 1 hour
  private static final String TIME_TO_LIVE_ONE_HOUR_PLUS_NANOS =
      "3600.000000001s"; // 1 hour and 1 nanosecond
  private static final String INVALID_TIME_TO_LIVE = "notanumber";
  private static final long EXPIRES_IN_ONE_HOUR = 60L * 60L * 1000L; // 1 hour in millis
  private static final long RECEIVED_AT_TIMESTAMP = 1L;
  private static final long ONE_SECOND_MILLIS = 1000L;
  private static final long IAT = 10L;
  private static final long EXP = 30L;
  // 2040-01-01T00:00:00Z and one hour earlier, in seconds. Both are larger than Integer.MAX_VALUE.
  private static final long IAT_AFTER_2038 = 2208985200L;
  private static final long EXP_AFTER_2038 = 2208988800L;
  // RFC 7519 allows NumericDate claims with fractional seconds.
  private static final double FRACTIONAL_IAT = 1700000000.5;
  private static final double FRACTIONAL_EXP = 1700003600.75;
  private static final String TOKEN_PREFIX = "prefix";
  private static final String TOKEN_SUFFIX = "suffix";
  private static final String SEPARATOR = ".";

  @Mock AppCheckTokenResponse mockAppCheckTokenResponse;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testGenerateToken_noClaims_expectedFields() {
    DefaultAppCheckToken defaultAppCheckToken =
        new DefaultAppCheckToken(TOKEN_PAYLOAD, EXPIRES_IN_ONE_HOUR, RECEIVED_AT_TIMESTAMP);

    assertThat(defaultAppCheckToken).isNotNull();
    assertThat(defaultAppCheckToken.getToken()).isEqualTo(TOKEN_PAYLOAD);
    assertThat(defaultAppCheckToken.getExpireTimeMillis())
        .isEqualTo(RECEIVED_AT_TIMESTAMP + EXPIRES_IN_ONE_HOUR);
  }

  @Test
  public void testSerializeTokenToString_normalToken_serializesToJsonString() throws Exception {
    DefaultAppCheckToken defaultAppCheckToken =
        new DefaultAppCheckToken(TOKEN_PAYLOAD, EXPIRES_IN_ONE_HOUR, RECEIVED_AT_TIMESTAMP);

    String serializedToken = defaultAppCheckToken.serializeTokenToString();

    assertThat(serializedToken).isNotEmpty();
    JSONObject jsonObject = new JSONObject(serializedToken);
    assertThat(jsonObject.getString(DefaultAppCheckToken.TOKEN_KEY)).isEqualTo(TOKEN_PAYLOAD);
    assertThat(jsonObject.getLong(DefaultAppCheckToken.RECEIVED_AT_TIMESTAMP_KEY))
        .isEqualTo(RECEIVED_AT_TIMESTAMP);
    assertThat(jsonObject.getLong(DefaultAppCheckToken.EXPIRES_IN_MILLIS_KEY))
        .isEqualTo(EXPIRES_IN_ONE_HOUR);
  }

  @Test
  public void testDeserializeTokenFromJsonString_normalToken_expectSameToken() {
    DefaultAppCheckToken defaultAppCheckToken =
        new DefaultAppCheckToken(TOKEN_PAYLOAD, EXPIRES_IN_ONE_HOUR, RECEIVED_AT_TIMESTAMP);

    String serializedToken = defaultAppCheckToken.serializeTokenToString();

    DefaultAppCheckToken newToken =
        DefaultAppCheckToken.deserializeTokenFromJsonString(serializedToken);

    assertThat(newToken).isNotNull();
    assertThat(newToken.getExpireTimeMillis())
        .isEqualTo(defaultAppCheckToken.getExpireTimeMillis());
    assertThat(newToken.getToken()).isEqualTo(defaultAppCheckToken.getToken());
    assertThat(newToken.getExpiresInMillis()).isEqualTo(defaultAppCheckToken.getExpiresInMillis());
    assertThat(newToken.getReceivedAtTimestamp())
        .isEqualTo(defaultAppCheckToken.getReceivedAtTimestamp());
  }

  @Test
  public void testConstructFromRawToken_normalToken_expectSuccess() throws Exception {
    String rawToken = constructFakeRawToken();
    DefaultAppCheckToken defaultAppCheckToken =
        DefaultAppCheckToken.constructFromRawToken(rawToken);

    assertThat(defaultAppCheckToken).isNotNull();
    assertThat(defaultAppCheckToken.getToken()).isEqualTo(rawToken);
    assertThat(defaultAppCheckToken.getReceivedAtTimestamp()).isEqualTo(IAT * ONE_SECOND_MILLIS);
    assertThat(defaultAppCheckToken.getExpiresInMillis())
        .isEqualTo((EXP - IAT) * ONE_SECOND_MILLIS);
  }

  @Test
  public void testConstructFromRawToken_claimsAfter2038_expectSuccess() throws Exception {
    String rawToken = constructFakeRawToken(IAT_AFTER_2038, EXP_AFTER_2038);

    DefaultAppCheckToken defaultAppCheckToken =
        DefaultAppCheckToken.constructFromRawToken(rawToken);

    assertThat(defaultAppCheckToken.getToken()).isEqualTo(rawToken);
    assertThat(defaultAppCheckToken.getReceivedAtTimestamp())
        .isEqualTo(IAT_AFTER_2038 * ONE_SECOND_MILLIS);
    assertThat(defaultAppCheckToken.getExpiresInMillis()).isEqualTo(EXPIRES_IN_ONE_HOUR);
  }

  @Test
  public void testConstructFromRawToken_fractionalClaims_expectTruncatedToSeconds()
      throws Exception {
    String rawToken = constructFakeRawToken(FRACTIONAL_IAT, FRACTIONAL_EXP);

    DefaultAppCheckToken defaultAppCheckToken =
        DefaultAppCheckToken.constructFromRawToken(rawToken);

    assertThat(defaultAppCheckToken.getToken()).isEqualTo(rawToken);
    assertThat(defaultAppCheckToken.getReceivedAtTimestamp())
        .isEqualTo(1700000000L * ONE_SECOND_MILLIS);
    assertThat(defaultAppCheckToken.getExpiresInMillis()).isEqualTo(EXPIRES_IN_ONE_HOUR);
  }

  @Test
  public void testConstructFromRawToken_stringClaims_expectTreatedAsMissing() throws Exception {
    String rawToken = constructFakeRawToken(String.valueOf(IAT), String.valueOf(EXP));

    DefaultAppCheckToken defaultAppCheckToken =
        DefaultAppCheckToken.constructFromRawToken(rawToken);

    assertThat(defaultAppCheckToken.getToken()).isEqualTo(rawToken);
    assertThat(defaultAppCheckToken.getReceivedAtTimestamp()).isEqualTo(0L);
    assertThat(defaultAppCheckToken.getExpiresInMillis()).isEqualTo(0L);
  }

  @Test
  public void testConstructFromAppCheckTokenResponse_success() {
    when(mockAppCheckTokenResponse.getToken()).thenReturn(TOKEN_PAYLOAD);
    when(mockAppCheckTokenResponse.getTimeToLive()).thenReturn(TIME_TO_LIVE_ONE_HOUR);

    DefaultAppCheckToken defaultAppCheckToken =
        DefaultAppCheckToken.constructFromAppCheckTokenResponse(mockAppCheckTokenResponse);

    assertThat(defaultAppCheckToken.getToken()).isEqualTo(TOKEN_PAYLOAD);
    assertThat(defaultAppCheckToken.getExpiresInMillis()).isEqualTo(EXPIRES_IN_ONE_HOUR);
  }

  @Test
  public void testConstructFromAppCheckTokenResponse_withNanoSecondsDuration_success() {
    when(mockAppCheckTokenResponse.getToken()).thenReturn(TOKEN_PAYLOAD);
    when(mockAppCheckTokenResponse.getTimeToLive()).thenReturn(TIME_TO_LIVE_ONE_HOUR_PLUS_NANOS);

    DefaultAppCheckToken defaultAppCheckToken =
        DefaultAppCheckToken.constructFromAppCheckTokenResponse(mockAppCheckTokenResponse);

    assertThat(defaultAppCheckToken.getToken()).isEqualTo(TOKEN_PAYLOAD);
    assertThat(defaultAppCheckToken.getExpiresInMillis()).isEqualTo(EXPIRES_IN_ONE_HOUR);
  }

  @Test
  public void testConstructFromAppCheckTokenResponse_invalidTimeToLiveFormat_fallbackToTokenClaims()
      throws Exception {
    String rawToken = constructFakeRawToken();
    when(mockAppCheckTokenResponse.getToken()).thenReturn(rawToken);
    when(mockAppCheckTokenResponse.getTimeToLive()).thenReturn(INVALID_TIME_TO_LIVE);

    DefaultAppCheckToken defaultAppCheckToken =
        DefaultAppCheckToken.constructFromAppCheckTokenResponse(mockAppCheckTokenResponse);

    assertThat(defaultAppCheckToken.getToken()).isEqualTo(rawToken);
    assertThat(defaultAppCheckToken.getExpiresInMillis())
        .isEqualTo((EXP - IAT) * ONE_SECOND_MILLIS);
  }

  @Test
  public void testConstructFromAppCheckTokenResponse_invalidTimeToLiveAndClaimsAfter2038_success()
      throws Exception {
    String rawToken = constructFakeRawToken(IAT_AFTER_2038, EXP_AFTER_2038);
    when(mockAppCheckTokenResponse.getToken()).thenReturn(rawToken);
    when(mockAppCheckTokenResponse.getTimeToLive()).thenReturn(INVALID_TIME_TO_LIVE);

    DefaultAppCheckToken defaultAppCheckToken =
        DefaultAppCheckToken.constructFromAppCheckTokenResponse(mockAppCheckTokenResponse);

    assertThat(defaultAppCheckToken.getToken()).isEqualTo(rawToken);
    assertThat(defaultAppCheckToken.getExpiresInMillis()).isEqualTo(EXPIRES_IN_ONE_HOUR);
  }

  private String constructFakeRawToken() throws Exception {
    return constructFakeRawToken(IAT, EXP);
  }

  private String constructFakeRawToken(Object iat, Object exp) throws Exception {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(DefaultAppCheckToken.ISSUED_AT_KEY, iat);
    jsonObject.put(DefaultAppCheckToken.EXPIRATION_TIME_KEY, exp);
    String tokenValue = jsonObject.toString();
    // Raw tokens are JWTs with 3 parts which are split by '.'; we attach a prefix and suffix so
    // it can be parsed properly
    return TOKEN_PREFIX
        + SEPARATOR
        + Base64.encodeToString(
            tokenValue.getBytes(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING)
        + SEPARATOR
        + TOKEN_SUFFIX;
  }
}
