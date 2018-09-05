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

package com.google.firebase.auth;

import android.support.annotation.Nullable;
import com.google.android.gms.common.annotation.KeepForSdk;
import com.google.firebase.annotations.PublicApi;
import java.util.Map;

/** Result object that contains a Firebase Auth ID Token. */
@PublicApi
public class GetTokenResult {

  private static final String EXPIRATION_TIMESTAMP = "exp";
  private static final String AUTH_TIMESTAMP = "auth_time";
  private static final String ISSUED_AT_TIMESTAMP = "iat";
  private static final String FIREBASE_KEY = "firebase";
  private static final String SIGN_IN_PROVIDER = "sign_in_provider";

  private String token;
  private Map<String, Object> claims;

  /**
   * @hide
   * @param token represents the {@link String} access token.
   */
  @KeepForSdk
  public GetTokenResult(String token, Map<String, Object> claims) {
    this.token = token;
    this.claims = claims;
  }

  /**
   * Firebase Auth ID Token. Useful for authenticating calls against your own backend. Verify the
   * integrity and validity of the token in your server either by using our server SDKs or following
   * the documentation.
   */
  @Nullable
  @PublicApi
  public String getToken() {
    return token;
  }

  /** Returns the time in milliseconds since epoch at which this ID token will expire */
  @PublicApi
  public long getExpirationTimestamp() {
    return getLongFromClaimsSafely(EXPIRATION_TIMESTAMP);
  }

  /**
   * Returns the authentication timestamp in milliseconds since epoch. This is the time the user
   * authenticated (signed in) and not the time the token was refreshed.
   */
  @PublicApi
  public long getAuthTimestamp() {
    return getLongFromClaimsSafely(AUTH_TIMESTAMP);
  }

  /**
   * Returns the issued at timestamp in milliseconds since epoch. This is the time the ID token was
   * last refreshed and not the authentication timestamp.
   */
  @PublicApi
  public long getIssuedAtTimestamp() {
    return getLongFromClaimsSafely(ISSUED_AT_TIMESTAMP);
  }

  /**
   * Returns the sign-in provider through which the ID token was obtained (anonymous, custom, phone,
   * password, etc). Note, this does not map to provider IDs. For example, anonymous and custom
   * authentications are not considered providers. We chose the name here to map the name used in
   * the ID token.
   */
  @Nullable
  @PublicApi
  public String getSignInProvider() {
    // Sign in provider lives inside the 'firebase' element of the JSON
    Map<String, Object> firebaseElem = (Map<String, Object>) claims.get(FIREBASE_KEY);
    if (firebaseElem != null) {
      return (String) firebaseElem.get(SIGN_IN_PROVIDER);
    }
    return null;
  }

  /**
   * Returns the entire payload claims of the ID token including the standard reserved claims as
   * well as the custom claims (set by developer via Admin SDK). Developers should verify the ID
   * token and parse claims from its payload on the backend and never trust this value on the
   * client. Returns an empty map if no claims are present.
   */
  @PublicApi
  public Map<String, Object> getClaims() {
    return claims;
  }

  private long getLongFromClaimsSafely(String key) {
    Integer result = ((Integer) claims.get(key));
    return result == null ? 0L : result.longValue();
  }
}
