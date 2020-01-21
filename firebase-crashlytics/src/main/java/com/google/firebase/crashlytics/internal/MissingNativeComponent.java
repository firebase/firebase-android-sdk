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

package com.google.firebase.crashlytics.internal;

import androidx.annotation.NonNull;
import java.io.File;

public final class MissingNativeComponent implements CrashlyticsNativeComponent {

  private static final NativeSessionFileProvider MISSING_NATIVE_SESSION_FILE_PROVIDER =
      new MissingNativeSessionFileProvider();

  @Override
  public boolean hasCrashDataForSession(@NonNull String sessionId) {
    return false;
  }

  @Override
  public boolean openSession(@NonNull String sessionId) {
    return true;
  }

  @Override
  public boolean finalizeSession(@NonNull String sessionId) {
    return true;
  }

  @NonNull
  @Override
  public NativeSessionFileProvider getSessionFileProvider(@NonNull String sessionId) {
    return MISSING_NATIVE_SESSION_FILE_PROVIDER;
  }

  @Override
  public void writeBeginSession(
      @NonNull String sessionId, @NonNull String generator, long startedAtSeconds) {}

  @Override
  public void writeSessionApp(
      @NonNull String sessionId,
      @NonNull String appIdentifier,
      @NonNull String versionCode,
      @NonNull String versionName,
      @NonNull String installUuid,
      int deliveryMechanism,
      @NonNull String unityVersion) {}

  @Override
  public void writeSessionOs(
      @NonNull String sessionId,
      @NonNull String osRelease,
      @NonNull String osCodeName,
      boolean isRooted) {}

  @Override
  public void writeSessionDevice(
      @NonNull String sessionId,
      int arch,
      @NonNull String model,
      int availableProcessors,
      long totalRam,
      long diskSpace,
      boolean isEmulator,
      int state,
      @NonNull String manufacturer,
      @NonNull String modelClass) {}

  private static final class MissingNativeSessionFileProvider implements NativeSessionFileProvider {

    @Override
    public File getMinidumpFile() {
      return null;
    }

    @Override
    public File getBinaryImagesFile() {
      return null;
    }

    @Override
    public File getMetadataFile() {
      return null;
    }

    @Override
    public File getSessionFile() {
      return null;
    }

    @Override
    public File getAppFile() {
      return null;
    }

    @Override
    public File getDeviceFile() {
      return null;
    }

    @Override
    public File getOsFile() {
      return null;
    }
  }
}
