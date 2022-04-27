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

package com.google.firebase.perf.metrics.validator;

import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.v1.ApplicationInfo;

/** Utility class that provides methods for validating application info log entries. */
public class FirebasePerfApplicationInfoValidator extends PerfMetricValidator {

  private static final AndroidLogger logger = AndroidLogger.getInstance();

  private final ApplicationInfo applicationInfo;

  FirebasePerfApplicationInfoValidator(ApplicationInfo applicationInfo) {
    this.applicationInfo = applicationInfo;
  }

  /**
   * Validates the application info, it validates if all the required fields are present.
   *
   * @return a boolean which indicates if the ApplicationInfo is valid.
   */
  @Override
  public boolean isValidPerfMetric() {
    if (!isValidApplicationInfo()) {
      logger.warn("ApplicationInfo is invalid");
      return false;
    }
    return true;
  }

  private boolean isValidApplicationInfo() {
    if (applicationInfo == null) {
      logger.warn("ApplicationInfo is null");
      return false;
    }
    if (!applicationInfo.hasGoogleAppId()) {
      logger.warn("GoogleAppId is null");
      return false;
    }
    if (!applicationInfo.hasAppInstanceId()) {
      logger.warn("AppInstanceId is null");
      return false;
    }
    if (!applicationInfo.hasApplicationProcessState()) {
      logger.warn("ApplicationProcessState is null");
      return false;
    }
    // androidAppInfo is not required, but if it exists, we have to validate its required fields.
    if (applicationInfo.hasAndroidAppInfo()) {
      if (!applicationInfo.getAndroidAppInfo().hasPackageName()) {
        logger.warn("AndroidAppInfo.packageName is null");
        return false;
      }
      if (!applicationInfo.getAndroidAppInfo().hasSdkVersion()) {
        logger.warn("AndroidAppInfo.sdkVersion is null");
        return false;
      }
    }
    return true;
  }
}
