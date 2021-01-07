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

package com.google.firebase.crashlytics.ndk;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.NativeSessionFileProvider;
import java.io.File;

/** The Crashlytics NDK Kit provides crash reporting functionality for Android NDK users. */
class FirebaseCrashlyticsNdk implements CrashlyticsNativeComponent {

  /** Relative sub-path to use for storing files. */
  private static final String FILES_PATH = ".com.google.firebase.crashlytics-ndk";

  static FirebaseCrashlyticsNdk create(@NonNull Context context) {
    final File rootDir = new File(context.getFilesDir(), FILES_PATH);

    final NativeComponentController controller =
        new BreakpadController(
            context, new JniNativeApi(context), new NdkCrashFilesManager(rootDir));
    return new FirebaseCrashlyticsNdk(controller);
  }

  private final NativeComponentController controller;

  FirebaseCrashlyticsNdk(@NonNull NativeComponentController controller) {
    this.controller = controller;
  }

  @Override
  public boolean hasCrashDataForSession(@NonNull String sessionId) {
    return controller.hasCrashDataForSession(sessionId);
  }

  @Override
  public boolean openSession(String sessionId) {
    final boolean initSuccess = controller.initialize(sessionId);
    Logger.getLogger()
        .i("Crashlytics NDK initialization " + (initSuccess ? "successful" : "FAILED"));
    return initSuccess;
  }

  @Override
  public boolean finalizeSession(@NonNull String sessionId) {
    return controller.finalizeSession(sessionId);
  }

  @NonNull
  @Override
  public NativeSessionFileProvider getSessionFileProvider(@NonNull String sessionId) {
    // TODO: If this is called more than once with the same ID, it should return
    // equivalent objects.
    return new SessionFilesProvider(controller.getFilesForSession(sessionId));
  }

  @Override
  public void writeBeginSession(
      @NonNull String sessionId, @NonNull String generator, long startedAtSeconds) {
    controller.writeBeginSession(sessionId, generator, startedAtSeconds);
  }

  @Override
  public void writeSessionApp(
      @NonNull String sessionId,
      @NonNull String appIdentifier,
      @NonNull String versionCode,
      @NonNull String versionName,
      @NonNull String installUuid,
      int deliveryMechanism,
      @NonNull String unityVersion) {
    controller.writeSessionApp(
        sessionId,
        appIdentifier,
        versionCode,
        versionName,
        installUuid,
        deliveryMechanism,
        unityVersion);
  }

  @Override
  public void writeSessionOs(
      @NonNull String sessionId,
      @NonNull String osRelease,
      @NonNull String osCodeName,
      boolean isRooted) {
    controller.writeSessionOs(sessionId, osRelease, osCodeName, isRooted);
  }

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
      @NonNull String modelClass) {
    controller.writeSessionDevice(
        sessionId,
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
