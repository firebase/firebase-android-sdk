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

import com.google.firebase.crashlytics.internal.common.InstallIdProvider;

/** Immutable value object capturing the data needed to make an settings query SPI web request. */
public class SettingsRequest {
  public final String googleAppId;
  public final String deviceModel;
  public final String osBuildVersion;
  public final String osDisplayVersion;
  public final InstallIdProvider installIdProvider;
  public final String instanceId;
  public final String displayVersion;
  public final String buildVersion;
  public final int source;

  public SettingsRequest(
      String googleAppId,
      String deviceModel,
      String osBuildVersion,
      String osDisplayVersion,
      InstallIdProvider installIdProvier,
      String instanceId,
      String displayVersion,
      String buildVersion,
      int source) {
    this.googleAppId = googleAppId;
    this.deviceModel = deviceModel;
    this.osBuildVersion = osBuildVersion;
    this.osDisplayVersion = osDisplayVersion;
    this.installIdProvider = installIdProvier;
    this.instanceId = instanceId;
    this.displayVersion = displayVersion;
    this.buildVersion = buildVersion;
    this.source = source;
  }
}
