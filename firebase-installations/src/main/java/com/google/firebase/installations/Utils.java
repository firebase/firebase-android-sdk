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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.installations.local.PersistedInstallationEntry;
import com.google.firebase.installations.time.Clock;
import com.google.firebase.installations.time.SystemClock;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Util methods used for {@link FirebaseInstallations}.
 *
 * @hide
 */
public final class Utils {
  public static final long AUTH_TOKEN_EXPIRATION_BUFFER_IN_SECS = TimeUnit.HOURS.toSeconds(1);
  private static final String APP_ID_IDENTIFICATION_SUBSTRING = ":";
  private static final Pattern API_KEY_FORMAT = Pattern.compile("\\AA[\\w-]{38}\\z");
  private static Utils singleton;
  private final Clock clock;

  private Utils(Clock clock) {
    this.clock = clock;
  }

  // Factory method that always returns the same Utils instance.
  public static Utils getInstance() {
    return getInstance(SystemClock.getInstance());
  }

  /**
   * Returns an Utils instance. {@link Utils#getInstance()} defines the clock used. NOTE: If a Utils
   * instance has already been initialized, the parameter will be ignored and the existing instance
   * will be returned.
   */
  public static Utils getInstance(Clock clock) {
    if (singleton == null) {
      singleton = new Utils(clock);
    }
    return singleton;
  }

  /**
   * Checks if the FIS Auth token is expired or going to expire in next 1 hour {@link
   * #AUTH_TOKEN_EXPIRATION_BUFFER_IN_SECS}.
   */
  public boolean isAuthTokenExpired(@NonNull PersistedInstallationEntry entry) {
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
    // Mockito doesn't allow to mock static methods. As a result this util method is not static.
    return TimeUnit.MILLISECONDS.toSeconds(currentTimeInMillis());
  }

  /** Returns current time in milliseconds. */
  public long currentTimeInMillis() {
    // Mockito doesn't allow to mock static methods. As a result this util method is not static.
    return clock.currentTimeMillis();
  }

  static boolean isValidAppIdFormat(@Nullable String appId) {
    return appId.contains(APP_ID_IDENTIFICATION_SUBSTRING);
  }

  static boolean isValidApiKeyFormat(@Nullable String apiKey) {
    return API_KEY_FORMAT.matcher(apiKey).matches();
  }

  /**
   * Returns a random number of milliseconds less than or equal to 1000. This helps to avoid cases
   * where many clients get synchronized by some situation and all retry at once, sending requests
   * in synchronized waves. The value of random_number_milliseconds is recalculated after each retry
   * request.
   */
  public long getRandomDelayForSyncPrevention() {
    // Mockito doesn't allow to mock static methods. As a result this util method is not static.
    // TODO: separate random delay generation into a separate class that can be injected for easy
    // testing.
    return (long) (Math.random() * 1000);
  }
}
