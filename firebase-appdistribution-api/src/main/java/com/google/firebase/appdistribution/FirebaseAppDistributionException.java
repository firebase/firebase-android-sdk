// Copyright 2022 Google LLC
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
     * The authentication process failed. The tester was either not signed in, does not have access,
     * or something went wrong. Try signing in again by calling {@link
     * FirebaseAppDistribution#signInTester}.
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
     * FirebaseAppDistribution#checkForNewRelease} returns with a non-null <br>
     * {@link AppDistributionRelease} before calling {@link FirebaseAppDistribution#updateApp}
     */
    UPDATE_NOT_AVAILABLE,

    /**
     * The host activity for a confirmation dialog was destroyed or pushed to the backstack. Try
     * calling {@link FirebaseAppDistribution#updateIfNewReleaseAvailable()} again.
     */
    HOST_ACTIVITY_INTERRUPTED,

    /**
     * This API is not implemented.
     *
     * <p>This build was compiled against the API only. This may be intentional if this variant is
     * intended for use in production. Otherwise you may need to include a dependency on {@code
     * com.google.firebase:firebase-appdistribution}.
     */
    NOT_IMPLEMENTED,

    /**
     * The Firebase App Distribution Tester API is disabled for this project.
     *
     * <p>Before you use the App Distribution SDK in your app, you must enable the API in the Google
     * Cloud console. For more information, see the <a
     * href="https://firebase.google.com/docs/app-distribution/set-up-alerts?platform=android">documentation</a>.
     * If you enabled this API recently, wait a few minutes for the action to propagate to the App
     * Distribution systems, and retry.
     */
    API_DISABLED,
  }

  @NonNull private final Status status;
  @Nullable private final AppDistributionRelease release;

  /** @hide */
  public FirebaseAppDistributionException(@NonNull String message, @NonNull Status status) {
    this(message, status, (AppDistributionRelease) null);
  }

  /** @hide */
  public FirebaseAppDistributionException(
      @NonNull String message, @NonNull Status status, @Nullable AppDistributionRelease release) {
    super(message);
    this.status = status;
    this.release = release;
  }

  /** @hide */
  public FirebaseAppDistributionException(
      @NonNull String message, @NonNull Status status, @NonNull Throwable cause) {
    this(message, status, null, cause);
  }

  /** @hide */
  public FirebaseAppDistributionException(
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
}
