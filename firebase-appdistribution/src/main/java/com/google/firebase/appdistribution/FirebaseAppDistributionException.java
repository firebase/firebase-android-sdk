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
import com.google.firebase.appdistribution.Constants.ErrorMessages;

/** The class for all Exceptions thrown by {@link FirebaseAppDistribution}. */
public class FirebaseAppDistributionException extends FirebaseException {
  public enum Status {
    /** Unknown error. */
    UNKNOWN,

    /** The authentication process failed. */
    AUTHENTICATION_FAILURE,

    /** The authentication process was canceled. */
    AUTHENTICATION_CANCELED,

    /** No network is available to make requests or the request timed out. */
    NETWORK_FAILURE,

    /** The new release failed to download. */
    DOWNLOAD_FAILURE,

    /** The new release failed to install. */
    INSTALLATION_FAILURE,

    /** The installation was canceled. */
    INSTALLATION_CANCELED,

    /** An update is not available for the current tester and app. */
    UPDATE_NOT_AVAILABLE,

    /** The app is running in production. */
    APP_RUNNING_IN_PRODUCTION,

    /** The host activity for a confirmation dialog was destroyed or pushed to the backstack. */
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
}
