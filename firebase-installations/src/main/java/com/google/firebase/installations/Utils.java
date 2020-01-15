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
import com.google.firebase.installations.local.PersistedInstallationEntry;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/** Util methods used for {@link FirebaseInstallations} */
class Utils {
  public static final long AUTH_TOKEN_EXPIRATION_BUFFER_IN_SECS = TimeUnit.HOURS.toSeconds(1);
  private final Calendar calendar;

  Utils(Calendar calendar) {
    this.calendar = calendar;
  }

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
    return TimeUnit.MILLISECONDS.toSeconds(calendar.getTimeInMillis());
  }

  /**
   * Compares two version strings.
   *
   * @note It assumes version is in the "1.0.1" format.
   * @param version1 a string of ordinal numbers separated by decimal points.
   * @param version2 a string of ordinal numbers separated by decimal points.
   * @return The result is -1 if {@code version1} is numerically less than {@code version2}. The
   *     result is 1 integer if {@code version1} is numerically greater than {@code version2}. The
   *     result is zero if the strings are numerically equal.
   */
  public static int compareVersion(String version1, String version2) {
    @SuppressWarnings("StringSplitter")
    String[] version1Parts = version1.split("\\.");
    @SuppressWarnings("StringSplitter")
    String[] version2Parts = version2.split("\\.");
    for (int i = 0; i < Math.min(version1Parts.length, version2Parts.length); i++) {
      int comparison = version1Parts[i].compareTo(version2Parts[i]);
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(version1Parts.length, version2Parts.length);
  }
}
