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

package com.google.firebase.crashlytics.internal.settings;

/** Immutable value object used to represent settings in memory. */
public class Settings {

  // Subsections of the Settings payload include session-specific params & feature flags.
  public static class SessionData {
    public final int maxCustomExceptionEvents;
    public final int maxCompleteSessionsCount;

    public SessionData(int maxCustomExceptionEvents, int maxCompleteSessionsCount) {
      this.maxCustomExceptionEvents = maxCustomExceptionEvents;
      this.maxCompleteSessionsCount = maxCompleteSessionsCount;
    }
  }

  public static class FeatureFlagData {
    public final boolean collectReports;
    public final boolean collectAnrs;
    public final boolean collectBuildIds;

    public FeatureFlagData(boolean collectReports, boolean collectAnrs, boolean collectBuildIds) {
      this.collectReports = collectReports;
      this.collectAnrs = collectAnrs;
      this.collectBuildIds = collectBuildIds;
    }
  }

  public final SessionData sessionData;
  public final FeatureFlagData featureFlagData;
  public final long expiresAtMillis;
  public final int settingsVersion;
  public final int cacheDuration;
  public final double onDemandUploadRatePerMinute;
  public final double onDemandBackoffBase;
  public final int onDemandBackoffStepDurationSeconds;

  public Settings(
      long expiresAtMillis,
      SessionData sessionData,
      FeatureFlagData featureFlagData,
      int settingsVersion,
      int cacheDuration,
      double onDemandUploadRatePerMinute,
      double onDemandBackoffBase,
      int onDemandBackoffStepDurationSeconds) {
    this.expiresAtMillis = expiresAtMillis;
    this.sessionData = sessionData;
    this.featureFlagData = featureFlagData;
    this.settingsVersion = settingsVersion;
    this.cacheDuration = cacheDuration;
    this.onDemandUploadRatePerMinute = onDemandUploadRatePerMinute;
    this.onDemandBackoffBase = onDemandBackoffBase;
    this.onDemandBackoffStepDurationSeconds = onDemandBackoffStepDurationSeconds;
  }

  public boolean isExpired(long currentTimeMillis) {
    return expiresAtMillis < currentTimeMillis;
  }
}
