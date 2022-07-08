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

package com.google.firebase.perf.logging;

public final class ConsoleUrlGenerator {
  private static final String URL_BASE_PATH = "https://console.firebase.google.com";
  private static final String UTM_MEDIUM = "android-ide";
  private static final String UTM_SOURCE = "perf-android-sdk";

  /**
   * Generate the console URL for Firebase Performance dashboard page.
   *
   * @param projectId the Firebase project ID
   * @param packageName the name of this application's package
   */
  public static String generateDashboardUrl(String projectId, String packageName) {
    String rootUrl = getRootUrl(projectId, packageName);
    return String.format("%s/trends?utm_source=%s&utm_medium=%s", rootUrl, UTM_SOURCE, UTM_MEDIUM);
  }

  /**
   * Generate the console URL for the custom trace.
   *
   * @param projectId the Firebase project ID
   * @param packageName the name of this application's package
   * @param name the trace name
   */
  public static String generateCustomTraceUrl(String projectId, String packageName, String name) {
    String rootUrl = getRootUrl(projectId, packageName);
    return String.format(
        "%s/troubleshooting/trace/DURATION_TRACE/%s?utm_source=%s&utm_medium=%s",
        rootUrl, name, UTM_SOURCE, UTM_MEDIUM);
  }

  /**
   * Generate the console URL for the screen trace.
   *
   * @param projectId the Firebase project ID
   * @param packageName the name of this application's package
   * @param name the trace name
   */
  public static String generateScreenTraceUrl(String projectId, String packageName, String name) {
    String rootUrl = getRootUrl(projectId, packageName);
    return String.format(
        "%s/troubleshooting/trace/SCREEN_TRACE/%s?utm_source=%s&utm_medium=%s",
        rootUrl, name, UTM_SOURCE, UTM_MEDIUM);
  }

  /**
   * Get the root URL for the project and package.
   *
   * @param projectId the Firebase project ID
   * @param packageName the name of this application's package
   */
  private static String getRootUrl(String projectId, String packageName) {
    return String.format(
        "%s/project/%s/performance/app/android:%s", URL_BASE_PATH, projectId, packageName);
  }
}
