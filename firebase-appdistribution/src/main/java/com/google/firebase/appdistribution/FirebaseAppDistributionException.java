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

    /**
     * The authentication process failed. The tester was either not signed in, or something went
     * wrong. Try signing in again by calling {@link FirebaseAppDistribution#signInTester}.
     */
    AUTHENTICATION_FAILURE,

    /** The authentication process was canceled (typically by the tester). */
    AUTHENTICATION_CANCELED,

    /**
     * No network was available to make requests, or the request timed out. Check the tester's
     * internet connection and retry the call to {@link FirebaseAppDistribution}.
     */
    NETWORK_FAILURE,

    /**
     * The new release failed to download. This was a most likely due to a transient condition and
     * may be corrected by retrying the call to {@link
     * FirebaseAppDistribution#updateIfNewReleaseAvailable} or {@link
     * FirebaseAppDistribution#updateApp}.
     */
    DOWNLOAD_FAILURE,

    /**
     * The new release failed to install. Verify that the new release has the same signing key as
     * the version running on device.
     */
    INSTALLATION_FAILURE,

    /** The installation was canceled (typically by the tester). */
    INSTALLATION_CANCELED,

    /**
     * An update was not available for the current tester and app. Make sure that {@link
     * FirebaseAppDistribution#checkForNewRelease} returns with a non-null {@link
     * AppDistributionRelease} before calling {@link FirebaseAppDistribution#updateApp},
     */
    UPDATE_NOT_AVAILABLE,

    /**
     * The host activity for a confirmation dialog was destroyed or pushed to the backstack. Try
     * calling {@link FirebaseAppDistribution#updateIfNewReleaseAvailable()} again.
     */
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

    public static final String JSON_PARSING_ERROR =
        "Error parsing service response when checking for new release. This was most likely due to a transient condition and may be corrected by retrying.";

    public static final String AUTHENTICATION_ERROR =
        "Failed to authenticate the tester. The tester was either not signed in, or something went wrong. Try signing in again.";

    public static final String AUTHORIZATION_ERROR =
        "Failed to authorize the tester. The tester is not authorized to test this app. Verify that the tester has accepted an invitation to test this app.";

    public static final String AUTHENTICATION_CANCELED = "Tester canceled the authentication flow.";

    public static final String NOT_FOUND_ERROR =
        "Release not found. An update was not available for the current tester and app. Make sure that FirebaseAppDistribution#checkForNewRelease returns with a non-null  AppDistributionRelease before calling FirebaseAppDistribution#updateApp";

    public static final String TIMEOUT_ERROR =
        "Failed to fetch releases due to timeout. Check the tester's internet connection and try again.";

    public static final String UPDATE_CANCELED = "Tester canceled the update.";

    public static final String UNKNOWN_ERROR = "Unknown error.";

    public static final String DOWNLOAD_URL_NOT_FOUND =
        "Download URL not found. This was a most likely due to a transient condition and may be corrected by retrying.";

    public static final String HOST_ACTIVITY_INTERRUPTED =
        "Host activity interrupted while dialog was showing. Try calling FirebaseAppDistribution#updateIfNewReleaseAvailable again.";

    public static final String APK_INSTALLATION_FAILED =
        "The APK failed to install or installation was canceled by the tester.";
  }
}
