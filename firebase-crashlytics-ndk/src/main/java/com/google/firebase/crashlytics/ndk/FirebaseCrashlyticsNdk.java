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
import com.google.firebase.crashlytics.internal.model.StaticSessionData;
import java.io.File;

/** The Crashlytics NDK Kit provides crash reporting functionality for Android NDK users. */
class FirebaseCrashlyticsNdk implements CrashlyticsNativeComponent {

  /** Relative sub-path to use for storing files. */
  private static final String FILES_PATH = ".com.google.firebase.crashlytics-ndk";

  static FirebaseCrashlyticsNdk create(@NonNull Context context) {
    final File rootDir = new File(context.getFilesDir(), FILES_PATH);

    final CrashpadController controller =
        new CrashpadController(
            context, new JniNativeApi(context), new NdkCrashFilesManager(rootDir));
    return new FirebaseCrashlyticsNdk(controller);
  }

  private final CrashpadController controller;

  FirebaseCrashlyticsNdk(@NonNull CrashpadController controller) {
    this.controller = controller;
  }

  @Override
  public boolean hasCrashDataForSession(@NonNull String sessionId) {
    return controller.hasCrashDataForSession(sessionId);
  }

  @Override
  public void openSession(
      @NonNull String sessionId,
      @NonNull String generator,
      long startedAtSeconds,
      @NonNull StaticSessionData sessionData) {

    Logger.getLogger().d("Opening native session: " + sessionId);
    if (!controller.initialize(sessionId, generator, startedAtSeconds, sessionData)) {
      Logger.getLogger().w("Failed to initialize Crashlytics NDK for session " + sessionId);
    }
  }

  @Override
  public void finalizeSession(@NonNull String sessionId) {

    Logger.getLogger().d("Finalizing native session: " + sessionId);
    if (!controller.finalizeSession(sessionId)) {
      Logger.getLogger().w("Could not finalize native session: " + sessionId);
    }
  }

  @NonNull
  @Override
  public NativeSessionFileProvider getSessionFileProvider(@NonNull String sessionId) {
    // TODO: If this is called more than once with the same ID, it should return
    // equivalent objects.
    return new SessionFilesProvider(controller.getFilesForSession(sessionId));
  }
}
