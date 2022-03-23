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

import static com.google.android.gms.common.internal.Preconditions.checkNotEmpty;
import static com.google.android.gms.common.internal.Preconditions.checkNotNull;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.internal.util.Clock;
import com.google.firebase.appcheck.internal.util.TokenParser;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/** Default implementation for {@link AppCheckToken} */
public final class DefaultAppCheckToken extends AppCheckToken {

  private static final String TAG = DefaultAppCheckToken.class.getName();

  @VisibleForTesting static final String TOKEN_KEY = "token";
  @VisibleForTesting static final String RECEIVED_AT_TIMESTAMP_KEY = "receivedAt";
  @VisibleForTesting static final String EXPIRES_IN_MILLIS_KEY = "expiresIn";

  // Keys for reading the JWT claims (for use in constructing a token from the raw JWT)
  @VisibleForTesting static final String ISSUED_AT_KEY = "iat";
  @VisibleForTesting static final String EXPIRATION_TIME_KEY = "exp";

  // Raw token value
  private final String token;
  // Timestamp in MS at which this token was generated
  private final long receivedAtTimestamp;
  // Amount of time since token issuance in MS until the expiry of this token; kept separately to
  // account for clock drift
  private final long expiresInMillis;

  @VisibleForTesting
  DefaultAppCheckToken(@NonNull String tokenJwt, long expiresInMillis) {
    this(tokenJwt, expiresInMillis, new Clock.DefaultClock().currentTimeMillis());
  }

  @VisibleForTesting
  DefaultAppCheckToken(@NonNull String tokenJwt, long expiresInMillis, long receivedAtTimestamp) {
    checkNotEmpty(tokenJwt);
    this.token = tokenJwt;
    this.expiresInMillis = expiresInMillis;
    this.receivedAtTimestamp = receivedAtTimestamp;
  }

  @NonNull
  public static DefaultAppCheckToken constructFromAppCheckTokenResponse(
      @NonNull AppCheckTokenResponse tokenResponse) {
    checkNotNull(tokenResponse);

    long expiresInMillis;
    try {
      // Expect a string like "3600s" representing a time interval in seconds.
      String timeToLiveSeconds = tokenResponse.getTimeToLive().replace("s", "");
      expiresInMillis = (long) (Double.parseDouble(timeToLiveSeconds) * 1000);
    } catch (NumberFormatException e) {
      // If parsing the duration string returned by the server fails for any reason, fall back to
      // computing the timeToLive from the token claims directly.
      Map<String, Object> claimsMap =
          TokenParser.parseTokenClaims(tokenResponse.getAttestationToken());
      long iat = getLongFromClaimsSafely(claimsMap, ISSUED_AT_KEY);
      long exp = getLongFromClaimsSafely(claimsMap, EXPIRATION_TIME_KEY);
      expiresInMillis = exp - iat;
    }

    return new DefaultAppCheckToken(tokenResponse.getAttestationToken(), expiresInMillis);
  }

  @NonNull
  @Override
  public String getToken() {
    return token;
  }

  @Override
  public long getExpireTimeMillis() {
    return receivedAtTimestamp + expiresInMillis;
  }

  long getReceivedAtTimestamp() {
    return receivedAtTimestamp;
  }

  long getExpiresInMillis() {
    return expiresInMillis;
  }

  @Nullable
  String serializeTokenToString() {
    try {
      JSONObject jsonObject = new JSONObject();
      jsonObject.put(TOKEN_KEY, token);
      jsonObject.put(RECEIVED_AT_TIMESTAMP_KEY, receivedAtTimestamp);
      jsonObject.put(EXPIRES_IN_MILLIS_KEY, expiresInMillis);
      return jsonObject.toString();
    } catch (JSONException e) {
      Log.e(TAG, "Could not serialize token: " + e.getMessage());
      return null;
    }
  }

  @Nullable
  static DefaultAppCheckToken deserializeTokenFromJsonString(@NonNull String tokenJsonString) {
    try {
      JSONObject jsonObject = new JSONObject(tokenJsonString);
      String tokenJwt = jsonObject.getString(TOKEN_KEY);
      long receivedAtTimestamp = jsonObject.getLong(RECEIVED_AT_TIMESTAMP_KEY);
      long expiresInMillis = jsonObject.getLong(EXPIRES_IN_MILLIS_KEY);
      return new DefaultAppCheckToken(tokenJwt, expiresInMillis, receivedAtTimestamp);
    } catch (JSONException e) {
      Log.e(TAG, "Could not deserialize token: " + e.getMessage());
      return null;
    }
  }

  @NonNull
  public static DefaultAppCheckToken constructFromRawToken(@NonNull String token) {
    checkNotNull(token);
    Map<String, Object> claimsMap = TokenParser.parseTokenClaims(token);
    long iat = getLongFromClaimsSafely(claimsMap, ISSUED_AT_KEY);
    long exp = getLongFromClaimsSafely(claimsMap, EXPIRATION_TIME_KEY);
    long expiresInMillis = (exp - iat) * 1000L;
    // We use iat for receivedAtTimestamp as an approximation since we have to guess for raw JWTs
    // that we recovered from storage
    return new DefaultAppCheckToken(token, expiresInMillis, iat * 1000L);
  }

  private static long getLongFromClaimsSafely(
      @NonNull Map<String, Object> claimsMap, @NonNull String key) {
    checkNotNull(claimsMap);
    checkNotEmpty(key);
    Integer result = ((Integer) claimsMap.get(key));
    return result == null ? 0L : result.longValue();
  }
}
