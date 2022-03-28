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

/** Immutable value object used to represent settings in memory. */
public class SettingsData implements Settings {
  public final SessionSettingsData sessionData;
  public final FeaturesSettingsData featuresData;
  public final long expiresAtMillis;
  public final int settingsVersion;
  public final int cacheDuration;
  public final double onDemandUploadRatePerMinute;
  public final double onDemandBackoffBase;
  public final int onDemandBackoffStepDurationSeconds;

  public SettingsData(
      long expiresAtMillis,
      SessionSettingsData sessionData,
      FeaturesSettingsData featuresData,
      int settingsVersion,
      int cacheDuration,
      double onDemandUploadRatePerMinute,
      double onDemandBackoffBase,
      int onDemandBackoffStepDurationSeconds) {
    this.expiresAtMillis = expiresAtMillis;
    this.sessionData = sessionData;
    this.featuresData = featuresData;
    this.settingsVersion = settingsVersion;
    this.cacheDuration = cacheDuration;
    this.onDemandUploadRatePerMinute = onDemandUploadRatePerMinute;
    this.onDemandBackoffBase = onDemandBackoffBase;
    this.onDemandBackoffStepDurationSeconds = onDemandBackoffStepDurationSeconds;
  }

  @Override
  public boolean isExpired(long currentTimeMillis) {
    return expiresAtMillis < currentTimeMillis;
  }

  @Override
  public SessionSettingsData getSessionData() {
    return sessionData;
  }

  @Override
  public FeaturesSettingsData getFeaturesData() {
    return featuresData;
  }

  @Override
  public long getExpiresAtMillis() {
    return expiresAtMillis;
  }

  @Override
  public double onDemandUploadRatePerMinute() {
    return onDemandUploadRatePerMinute;
  }

  @Override
  public double onDemandBackoffBase() {
    return onDemandBackoffBase;
  }

  @Override
  public int onDemandBackoffStepDurationSeconds() {
    return onDemandBackoffStepDurationSeconds;
  }
}
