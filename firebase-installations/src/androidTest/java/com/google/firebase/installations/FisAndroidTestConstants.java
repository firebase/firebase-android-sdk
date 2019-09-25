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

package com.google.firebase.installations;

import com.google.firebase.installations.local.PersistedFidEntry;
import com.google.firebase.installations.remote.InstallationResponse;

public final class FisAndroidTestConstants {
  public static final String TEST_FID_1 = "cccccccccccccccccccccc";

  public static final String TEST_PROJECT_ID = "777777777777";

  public static final String TEST_AUTH_TOKEN = "fis.auth.token";
  public static final String TEST_AUTH_TOKEN_2 = "fis.auth.token2";
  public static final String TEST_AUTH_TOKEN_3 = "fis.auth.token3";
  public static final String TEST_AUTH_TOKEN_4 = "fis.auth.token4";

  public static final String TEST_API_KEY = "apiKey";

  public static final String TEST_REFRESH_TOKEN = "1:test-refresh-token";

  public static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  public static final String TEST_APP_ID_2 = "1:987654321:android:abcdef";

  public static final long TEST_TOKEN_EXPIRATION_TIMESTAMP = 1000L;
  public static final long TEST_TOKEN_EXPIRATION_TIMESTAMP_2 = 2000L;

  public static final long TEST_CREATION_TIMESTAMP_1 = 2000L;
  public static final long TEST_CREATION_TIMESTAMP_2 = 2L;

  public static final PersistedFidEntry DEFAULT_PERSISTED_FID_ENTRY =
      PersistedFidEntry.builder().build();
  public static final InstallationResponse TEST_INSTALLATION_RESPONSE =
      InstallationResponse.builder()
          .setName("/projects/" + TEST_PROJECT_ID + "/installations/" + TEST_FID_1)
          .setRefreshToken(TEST_REFRESH_TOKEN)
          .setAuthToken(
              InstallationTokenResult.builder()
                  .setToken(TEST_AUTH_TOKEN)
                  .setTokenExpirationInSecs(TEST_TOKEN_EXPIRATION_TIMESTAMP)
                  .build())
          .build();

  public static final InstallationTokenResult TEST_INSTALLATION_TOKEN_RESULT =
      InstallationTokenResult.builder()
          .setToken(TEST_AUTH_TOKEN_2)
          .setTokenExpirationInSecs(TEST_TOKEN_EXPIRATION_TIMESTAMP)
          .build();
}
