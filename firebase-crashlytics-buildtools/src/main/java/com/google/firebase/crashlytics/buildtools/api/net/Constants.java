/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.buildtools.api.net;

public class Constants {
  public static class Http {
    public static final String HTTP = "HTTP";
    public static final String HTTPS = "HTTPS";

    public static final String USER_AGENT_HEADER = "User-Agent";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String API_CLIENT_ID = "X-CRASHLYTICS-API-CLIENT-ID";
    public static final String API_CLIENT_BUILD_VERSION = "X-CRASHLYTICS-API-CLIENT-BUILD-VERSION";
    public static final String API_CLIENT_DISPLAY_VERSION =
        "X-CRASHLYTICS-API-CLIENT-DISPLAY-VERSION";
    public static final String API_OPERATING_SYSTEM_HEADER = "X-CRASHLYTICS-API-OPERATING-SYSTEM";
    public static final String API_CLIENT_TYPE_HEADER = "X-CRASHLYTICS-API-CLIENT-TYPE";
    public static final String API_CLIENT_VERSION_HEADER = "X-CRASHLYTICS-API-CLIENT-VERSION";
    public static final String API_ENVIRONMENT_VERSION_HEADER =
        "X-CRASHLYTICS-API-ENVIRONMENT-VERSION";
    public static final String API_ENVIRONMENT_ID_HEADER = "X-CRASHLYTICS-API-ENVIRONMENT-ID";
  }
}
