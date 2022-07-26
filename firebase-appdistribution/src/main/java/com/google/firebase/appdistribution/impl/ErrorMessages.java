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

package com.google.firebase.appdistribution.impl;

class ErrorMessages {

  static final String NETWORK_ERROR = "Request failed with unknown network error.";

  static final String JSON_PARSING_ERROR =
      "Error parsing service response. This was most likely due to a transient condition and may be corrected by retrying.";

  static final String JSON_SERIALIZATION_ERROR = "Error building request body.";

  static final String AUTHENTICATION_ERROR =
      "Failed to authenticate the tester. The tester was either not signed in, or something went wrong. Try signing in again.";

  static final String AUTHORIZATION_ERROR =
      "Failed to authorize the tester. The tester does not have access to this resource (or it may not exist).";

  static final String AUTHENTICATION_CANCELED = "Tester canceled the authentication flow.";

  static final String RELEASE_NOT_FOUND_ERROR =
      "Release not found. An update was not available for the current tester and app. Make sure that checkForNewRelease() returns with a non-null AppDistributionRelease before calling updateApp()";

  static final String NOT_FOUND_ERROR = "Resource not found (or the tester may not have access).";

  static final String TIMEOUT_ERROR =
      "Request timed out. Check the tester's internet connection and try again.";

  static final String UPDATE_CANCELED = "Tester canceled the update.";

  static final String UNKNOWN_ERROR = "Unknown error.";

  static final String DOWNLOAD_URL_NOT_FOUND =
      "Download URL not found. This was a most likely due to a transient condition and may be corrected by retrying.";

  static final String HOST_ACTIVITY_INTERRUPTED =
      "Host activity interrupted while dialog was showing. Try calling the API again.";

  static final String APK_INSTALLATION_FAILED =
      "The APK failed to install or installation was canceled by the tester.";

  static final String API_DISABLED =
      "The App Distribution Tester API is disabled; you must enable it in the Google Cloud console. If you enabled this API recently, wait a few minutes for the action to propagate to our systems and retry.";

  private ErrorMessages() {}
}
