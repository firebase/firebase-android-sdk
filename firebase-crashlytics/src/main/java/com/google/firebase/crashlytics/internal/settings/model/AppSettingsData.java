// Copyright 2019 Google LLC
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

package com.google.firebase.crashlytics.internal.settings.model;

/** Immutable value object used to represent the App portion of settings in memory. */
public class AppSettingsData {
  public static final String STATUS_NEW = "new";
  public static final String STATUS_CONFIGURED = "configured";
  public static final String STATUS_ACTIVATED = "activated";

  public final String status;
  public final String url;
  public final String reportsUrl;
  public final String ndkReportsUrl;
  public final String bundleId;
  public final String organizationId;
  public final boolean updateRequired;
  public final int reportUploadVariant;
  public final int nativeReportUploadVariant;

  public AppSettingsData(
      String status,
      String url,
      String reportsUrl,
      String ndkReportsUrl,
      String bundleId,
      String organizationId,
      boolean updateRequired,
      int reportUploadVariant,
      int nativeReportUploadVariant) {
    this.status = status;
    this.url = url;
    this.reportsUrl = reportsUrl;
    this.ndkReportsUrl = ndkReportsUrl;
    this.bundleId = bundleId;
    this.organizationId = organizationId;
    this.updateRequired = updateRequired;
    this.reportUploadVariant = reportUploadVariant;
    this.nativeReportUploadVariant = nativeReportUploadVariant;
  }

  public AppSettingsData(
      String status, String url, String reportsUrl, String ndkReportsUrl, boolean updateRequired) {
    this(status, url, reportsUrl, ndkReportsUrl, null, null, updateRequired, 0, 0);
  }
}
