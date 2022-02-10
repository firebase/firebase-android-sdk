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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.FirebaseException;

/** The class for all Exceptions thrown by {@link FirebaseAppDistribution}. */
public class FirebaseAppDistributionException extends FirebaseException {
  /** Enum for potential error statuses that caused the {@link FirebaseAppDistributionException}. */
  public enum Status {
    /** Unknown error or an error from a different error domain. */
    UNKNOWN,

    /** The authentication process failed. The tester was either not signed in, or something went wrong. Try signing in again by calling {@link FirebaseAppDistribution#signInTester}. */
    AUTHENTICATION_FAILURE,

    /** The authentication process was canceled (typically by the user). */
    AUTHENTICATION_CANCELED,

    /** No network is available to make requests, or the request timed out. Check your internet connection and try again. */
    NETWORK_FAILURE,

    /** The new release failed to download. This is a most likely due to a transient condition and
     * may be corrected by retrying. */
    DOWNLOAD_FAILURE,

    /** The new release failed to install. Verify that the new release has the same signing key as the version running on device. */
    INSTALLATION_FAILURE,

    /** The installation was canceled (typically by the user). */
    INSTALLATION_CANCELED,

    /** An update is not available for the current tester and app. Make sure that {@link FirebaseAppDistribution#checkForNewRelease()} returns with a non-null {@link AppDistributionRelease} before calling {@link FirebaseAppDistribution#updateApp()} */
    UPDATE_NOT_AVAILABLE,

    /** The app is running in production. The App Distribution SDK is intended for beta testing usage only. Do not include the App Distribution SDK in production builds. */
    APP_RUNNING_IN_PRODUCTION,

    /** The host activity for a confirmation dialog was destroyed or pushed to the backstack. Try calling {@link FirebaseAppDistribution#updateIfNewReleaseAvailable()} again */
    HOST_ACTIVITY_INTERRUPTED,
  }

  @NonNull private final Status status;
  @Nullable private final AppDistributionRelease release;

  FirebaseAppDistributionException(@NonNull String message, @NonNull Status status) {
    super(message);
    this.status = status;
    this.release = null;
  }

  FirebaseAppDistributionException(
      @NonNull String message, @NonNull Status status, @Nullable AppDistributionRelease release) {
    super(message);
    this.status = status;
    this.release = release;
  }

  FirebaseAppDistributionException(
      @NonNull String message, @NonNull Status status, @NonNull Throwable cause) {
    super(message, cause);
    this.status = status;
    this.release = null;
  }

  FirebaseAppDistributionException(
      @NonNull String message,
      @NonNull Status status,
      @Nullable AppDistributionRelease release,
      @NonNull Throwable cause) {
    super(message, cause);
    this.status = status;
    this.release = release;
  }

  /** Returns the release that was ready to be installed when the error was thrown. */
  @Nullable
  public AppDistributionRelease getRelease() {
    return release;
  }

  /** Returns the {@link FirebaseAppDistributionException.Status} that caused the exception. */
  @NonNull
  public Status getErrorCode() {
    return status;
  }

  static FirebaseAppDistributionException wrap(Throwable t) {
    // We never want to wrap a FirebaseAppDistributionException
    if (t instanceof FirebaseAppDistributionException) {
      return (FirebaseAppDistributionException) t;
    }
    return new FirebaseAppDistributionException(
        String.format("%s: %s", ErrorMessages.UNKNOWN_ERROR, t.getMessage()), Status.UNKNOWN, t);
  }

  static class ErrorMessages {
    public static final String NETWORK_ERROR =
        "Failed to fetch releases due to unknown network error.";

    public static final String JSON_PARSING_ERROR = "Error parsing service response.";

    public static final String AUTHENTICATION_ERROR = "Failed to authenticate the tester.";

    public static final String AUTHORIZATION_ERROR = "Failed to authorize the tester.";

    public static final String AUTHENTICATION_CANCELED = "Tester canceled the authentication flow.";

    public static final String NOT_FOUND_ERROR = "Tester or release not found.";

    public static final String TIMEOUT_ERROR = "Failed to fetch releases due to timeout.";

    public static final String UPDATE_CANCELED = "Update canceled.";

    public static final String UNKNOWN_ERROR = "Unknown error.";

    public static final String DOWNLOAD_URL_NOT_FOUND = "Download URL not found.";

    public static final String HOST_ACTIVITY_INTERRUPTED =
        "Host activity interrupted while dialog was showing.";

    public static final String APK_INSTALLATION_FAILED =
        "The APK failed to install or installation was canceled.";
  }
}
