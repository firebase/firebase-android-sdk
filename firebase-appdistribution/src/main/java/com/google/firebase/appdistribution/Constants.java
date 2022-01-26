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

class Constants {
  static class ErrorMessages {
    public static final String NETWORK_ERROR =
        "Failed to fetch releases due to unknown network error";

    public static final String JSON_PARSING_ERROR = "Error parsing service response";

    public static final String AUTHENTICATION_ERROR = "Failed to authenticate the tester";

    public static final String AUTHORIZATION_ERROR = "Failed to authorize the tester";

    public static final String AUTHENTICATION_CANCELED = "Tester canceled the authentication flow";

    public static final String NOT_FOUND_ERROR = "Tester or release not found";

    public static final String TIMEOUT_ERROR = "Failed to fetch releases due to timeout";

    public static final String UPDATE_CANCELED = "Update canceled";

    public static final String UNKNOWN_ERROR = "Unknown Error";

    public static final String DOWNLOAD_URL_NOT_FOUND = "Download URL not found";

    public static final String APK_INSTALLATION_FAILED =
        "The APK failed to install or installation was cancelled";
  }
}
