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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import com.google.firebase.FirebaseException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Possible exceptions thrown in FirebaseAppDistribution */
public abstract class FirebaseAppDistributionException extends FirebaseException {
  // Unknown error.
  public static final int UNKNOWN_ERROR = 1;

  // Authentication failed
  public static final int AUTHENTICATION_FAILURE_ERROR = 2;

  // Authentication canceled
  public static final int AUTHENTICATION_CANCELED_ERROR = 3;

  // No Network available to make requests or the request timed out
  public static final int NETWORK_FAILURE_ERROR = 4;

  // Download failed
  public static final int DOWNLOAD_FAILURE_ERROR = 5;

  // Installation failed
  public static final int INSTALLATION_FAILURE_ERROR = 6;

  // Installation canceled
  public static final int INSTALLATION_CANCELED_ERROR = 7;

  // Update not available for the current tester and app
  public static final int UPDATE_NOT_AVAILABLE_ERROR = 8;

  // Installation failed due to signature mismatch
  public static final int INSTALLATION_FAILURE_SIGNATURE_MISMATCH_ERROR = 9;
  // App is in production
  public static final int APP_RUNNING_IN_PRODUCTION_ERROR = 10;

  // Download URL for release expired
  public static final int RELEASE_URL_EXPIRED_ERROR = 11;

  public abstract int getCode();

  @NonNull
  public abstract AppDistributionRelease getRelease();

  @IntDef({
    FirebaseAppDistributionException.UNKNOWN_ERROR,
    FirebaseAppDistributionException.AUTHENTICATION_FAILURE_ERROR,
    FirebaseAppDistributionException.AUTHENTICATION_CANCELED_ERROR,
    FirebaseAppDistributionException.NETWORK_FAILURE_ERROR,
    FirebaseAppDistributionException.DOWNLOAD_FAILURE_ERROR,
    FirebaseAppDistributionException.INSTALLATION_FAILURE_ERROR,
    FirebaseAppDistributionException.INSTALLATION_CANCELED_ERROR,
    FirebaseAppDistributionException.UPDATE_NOT_AVAILABLE_ERROR,
    FirebaseAppDistributionException.INSTALLATION_FAILURE_SIGNATURE_MISMATCH_ERROR,
    FirebaseAppDistributionException.APP_RUNNING_IN_PRODUCTION_ERROR,
    FirebaseAppDistributionException.RELEASE_URL_EXPIRED_ERROR,
  })
  @Retention(RetentionPolicy.CLASS)
  public @interface Code {}
}
