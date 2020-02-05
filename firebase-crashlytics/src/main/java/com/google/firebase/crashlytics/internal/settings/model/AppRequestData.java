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

/** Immutable value object capturing the data needed to make an App-related SPI web request. */
public class AppRequestData {

  public final String organizationId;
  public final String googleAppId;
  public final String appId;
  public final String displayVersion;
  public final String buildVersion;

  public final String instanceIdentifier;
  public final String name;
  public final int source;
  public final String minSdkVersion;
  public final String builtSdkVersion;

  public AppRequestData(
      String organizationId,
      String googleAppId,
      String appId,
      String displayVersion,
      String buildVersion,
      String instanceIdentifier,
      String name,
      int source,
      String minSdkVersion,
      String builtSdkVersion) {
    this.organizationId = organizationId;
    this.googleAppId = googleAppId;
    this.appId = appId;
    this.displayVersion = displayVersion;
    this.buildVersion = buildVersion;

    this.instanceIdentifier = instanceIdentifier;
    this.name = name;
    this.source = source;
    this.minSdkVersion = minSdkVersion;
    this.builtSdkVersion = builtSdkVersion;
  }
}
