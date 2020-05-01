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

import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.firebase.installations.local.PersistedInstallationEntry;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Util methods used for {@link FirebaseInstallations} */
class Utils {
  public static final long AUTH_TOKEN_EXPIRATION_BUFFER_IN_SECS = TimeUnit.HOURS.toSeconds(1);
  private static final String APP_ID_IDENTIFICATION_SUBSTRING = ":";
  private static final Pattern API_KEY_FORMAT = Pattern.compile("\\AA[\\w-]{38}\\z");

  /**
   * Checks if the FIS Auth token is expired or going to expire in next 1 hour {@link
   * #AUTH_TOKEN_EXPIRATION_BUFFER_IN_SECS}.
   */
  public boolean isAuthTokenExpired(PersistedInstallationEntry entry) {
    if (TextUtils.isEmpty(entry.getAuthToken())) {
      return true;
    }
    if ((entry.getTokenCreationEpochInSecs() + entry.getExpiresInSecs())
        < (currentTimeInSecs() + AUTH_TOKEN_EXPIRATION_BUFFER_IN_SECS)) {
      return true;
    }
    return false;
  }

  /** Returns current time in seconds. */
  public long currentTimeInSecs() {
    return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
  }

  static boolean isValidAppIdFormat(@Nullable String appId) {
    return appId.contains(APP_ID_IDENTIFICATION_SUBSTRING);
  }

  static boolean isValidApiKeyFormat(@Nullable String apiKey) {
    return API_KEY_FORMAT.matcher(apiKey).matches();
  }
}
