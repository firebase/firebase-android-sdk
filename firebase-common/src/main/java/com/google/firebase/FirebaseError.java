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

package com.google.firebase;

import com.google.android.gms.common.annotation.KeepForSdk;

/**
 * Represents API errors. This is for internal usage only and we don't expose externally.
 *
 * @hide
 */
@KeepForSdk
public class FirebaseError {

  /** Firebase auth specific error codes. */

  /* bring your own auth error codes. */
  @KeepForSdk public static final int ERROR_INVALID_CUSTOM_TOKEN = 17000;

  @KeepForSdk public static final int ERROR_CUSTOM_TOKEN_MISMATCH = 17002;

  /* sign in with credential error codes. */
  @KeepForSdk public static final int ERROR_INVALID_CREDENTIAL = 17004;
  @KeepForSdk public static final int ERROR_USER_DISABLED = 17005;

  /* set account info error codes. */
  @KeepForSdk public static final int ERROR_OPERATION_NOT_ALLOWED = 17006;
  @KeepForSdk public static final int ERROR_EMAIL_ALREADY_IN_USE = 17007;

  /* sign in with password error codes*/
  @KeepForSdk public static final int ERROR_INVALID_EMAIL = 17008;
  @KeepForSdk public static final int ERROR_WRONG_PASSWORD = 17009;
  @KeepForSdk public static final int ERROR_TOO_MANY_REQUESTS = 17010;

  /* send password request email error codes */
  @KeepForSdk public static final int ERROR_USER_NOT_FOUND = 17011;

  /* sign in with credential error codes. */
  @KeepForSdk public static final int ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL = 17012;

  /* set account info error codes. */
  @KeepForSdk public static final int ERROR_REQUIRES_RECENT_LOGIN = 17014;

  /* link credential error codes */
  @KeepForSdk public static final int ERROR_PROVIDER_ALREADY_LINKED = 17015;

  /* unlink credential */
  @KeepForSdk public static final int ERROR_NO_SUCH_PROVIDER = 17016;

  /* STS codes, any request with STS id token */
  @KeepForSdk public static final int ERROR_INVALID_USER_TOKEN = 17017;

  /* network request failed */
  @KeepForSdk public static final int ERROR_NETWORK_REQUEST_FAILED = 17020;

  /* STS code */
  @KeepForSdk public static final int ERROR_USER_TOKEN_EXPIRED = 17021;

  /**
   * For GmsCore implementation on physical device, Droid Guard takes care of mapping api key. So
   * for now, we are not handling this (2016 v3 release)
   */
  @KeepForSdk public static final int ERROR_INVALID_API_KEY = 17023;

  /* re-auth error codes */
  @KeepForSdk public static final int ERROR_USER_MISMATCH = 17024;

  /* setAccountInfo(...) error codes. */
  @KeepForSdk public static final int ERROR_CREDENTIAL_ALREADY_IN_USE = 17025;

  /* weak passwords */
  @KeepForSdk public static final int ERROR_WEAK_PASSWORD = 17026;

  /**
   * For GmsCore implementation on physical device, Droid Guard takes care of mapping api key. So
   * for now, we are not handling this (2016 v3 release)
   */
  @KeepForSdk public static final int ERROR_APP_NOT_AUTHORIZED = 17028;

  /**
   * Internal api usage error codes (no signed-in user, and getAccessToken is called). This will map
   * to ApiNotAvailableException and please DO NOT REUSE.
   */
  @KeepForSdk public static final int ERROR_NO_SIGNED_IN_USER = 17495;

  /* General backend error. */
  @KeepForSdk public static final int ERROR_INTERNAL_ERROR = 17499;

  private int errorCode;

  /** @hide */
  public FirebaseError(int errorCode) {
    this.errorCode = errorCode;
  }

  /** @return the {@link String} error code. */
  public int getErrorCode() {
    return errorCode;
  }
}
