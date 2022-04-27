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

package com.google.firebase.crashlytics.internal.model;

import com.google.auto.value.AutoValue;
import com.google.firebase.crashlytics.internal.DevelopmentPlatformProvider;

@AutoValue
public abstract class StaticSessionData {

  public abstract AppData appData();

  public abstract OsData osData();

  public abstract DeviceData deviceData();

  public static StaticSessionData create(AppData appData, OsData osData, DeviceData deviceData) {
    return new AutoValue_StaticSessionData(appData, osData, deviceData);
  }

  // TODO consider simplifying by combining with
  // com.google.firebase.crashlytics.internal.common.AppData
  @AutoValue
  public abstract static class AppData {
    public abstract String appIdentifier();

    public abstract String versionCode();

    public abstract String versionName();

    public abstract String installUuid();

    public abstract int deliveryMechanism();

    public abstract DevelopmentPlatformProvider developmentPlatformProvider();

    public static AppData create(
        String appIdentifier,
        String versionCode,
        String versionName,
        String installUuid,
        int deliveryMechanism,
        DevelopmentPlatformProvider developmentPlatformProvider) {
      return new AutoValue_StaticSessionData_AppData(
          appIdentifier,
          versionCode,
          versionName,
          installUuid,
          deliveryMechanism,
          developmentPlatformProvider);
    }
  }

  @AutoValue
  public abstract static class OsData {
    public abstract String osRelease();

    public abstract String osCodeName();

    public abstract boolean isRooted();

    public static OsData create(String osRelease, String osCodeName, boolean isRooted) {
      return new AutoValue_StaticSessionData_OsData(osRelease, osCodeName, isRooted);
    }
  }

  @AutoValue
  public abstract static class DeviceData {

    public abstract int arch();

    public abstract String model();

    public abstract int availableProcessors();

    public abstract long totalRam();

    public abstract long diskSpace();

    public abstract boolean isEmulator();

    public abstract int state();

    public abstract String manufacturer();

    public abstract String modelClass();

    public static DeviceData create(
        int arch,
        String model,
        int availableProcessors,
        long totalRam,
        long diskSpace,
        boolean isEmulator,
        int state,
        String manufacturer,
        String modelClass) {
      return new AutoValue_StaticSessionData_DeviceData(
          arch,
          model,
          availableProcessors,
          totalRam,
          diskSpace,
          isEmulator,
          state,
          manufacturer,
          modelClass);
    }
  }
}
