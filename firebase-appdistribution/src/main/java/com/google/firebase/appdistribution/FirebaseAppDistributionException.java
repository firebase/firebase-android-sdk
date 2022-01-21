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

/** Possible exceptions thrown in FirebaseAppDistribution */
public class FirebaseAppDistributionException extends FirebaseException {
  public enum Status {
    /** Unknown error. */
    UNKNOWN,

    /** Authentication failed */
    AUTHENTICATION_FAILURE,

    /** Authentication canceled */
    AUTHENTICATION_CANCELED,

    /** No Network available to make requests or the request timed out */
    NETWORK_FAILURE,

    /** Download failed */
    DOWNLOAD_FAILURE,

    /** Installation failed */
    INSTALLATION_FAILURE,

    /** Installation canceled */
    INSTALLATION_CANCELED,

    /** Update not available for the current tester and app */
    UPDATE_NOT_AVAILABLE,

    /** Installation failed due to signature mismatch */
    INSTALLATION_FAILURE_SIGNATURE_MISMATCH,

    /** App is in production */
    APP_RUNNING_IN_PRODUCTION,

    /** Download URL for release expired */
    RELEASE_URL_EXPIRED,

    /** Host activity for confirmation dialog destroyed or pushed to the backstack */
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

  /** Get cached release when error was thrown */
  @Nullable
  public AppDistributionRelease getRelease() {
    return release;
  }

  @NonNull
  public Status getErrorCode() {
    return status;
  }
}
