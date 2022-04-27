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
import com.google.firebase.crashlytics.internal.persistence.FileStore;

/** The Crashlytics NDK Kit provides crash reporting functionality for Android NDK users. */
class FirebaseCrashlyticsNdk implements CrashlyticsNativeComponent {

  private static FirebaseCrashlyticsNdk instance;

  static FirebaseCrashlyticsNdk create(
      @NonNull Context context, boolean installHandlerDuringPrepareSession) {

    final CrashpadController controller =
        new CrashpadController(context, new JniNativeApi(context), new FileStore(context));

    instance = new FirebaseCrashlyticsNdk(controller, installHandlerDuringPrepareSession);
    return instance;
  }

  private final CrashpadController controller;

  private interface SignalHandlerInstaller {
    void installHandler();
  }

  private boolean installHandlerDuringPrepareSession;
  private String currentSessionId;
  private SignalHandlerInstaller signalHandlerInstaller;

  FirebaseCrashlyticsNdk(
      @NonNull CrashpadController controller, boolean installHandlerDuringPrepareSession) {
    this.controller = controller;
    this.installHandlerDuringPrepareSession = installHandlerDuringPrepareSession;
  }

  @Override
  public boolean hasCrashDataForCurrentSession() {
    return currentSessionId != null && hasCrashDataForSession(currentSessionId);
  }

  @Override
  public boolean hasCrashDataForSession(@NonNull String sessionId) {
    return controller.hasCrashDataForSession(sessionId);
  }

  /**
   * Prepares the session to be opened. If installSignalHandlerDuringPrepareSession was false at the
   * constructor, the signal handler will not be fully installed until {@link
   * FirebaseCrashlyticsNdk#installSignalHandler()} is called.
   */
  @Override
  public synchronized void prepareNativeSession(
      @NonNull String sessionId,
      @NonNull String generator,
      long startedAtSeconds,
      @NonNull StaticSessionData sessionData) {

    currentSessionId = sessionId;
    signalHandlerInstaller =
        () -> {
          Logger.getLogger().d("Initializing native session: " + sessionId);
          if (!controller.initialize(sessionId, generator, startedAtSeconds, sessionData)) {
            Logger.getLogger().w("Failed to initialize Crashlytics NDK for session " + sessionId);
          }
        };

    if (installHandlerDuringPrepareSession) {
      signalHandlerInstaller.installHandler();
    }
  }

  @NonNull
  @Override
  public NativeSessionFileProvider getSessionFileProvider(@NonNull String sessionId) {
    // TODO: If this is called more than once with the same ID, it should return
    // equivalent objects.
    return new SessionFilesProvider(controller.getFilesForSession(sessionId));
  }

  /**
   * Installs the native signal handler, if the session has already been prepared. Otherwise,
   * calling this method will result in the native signal handler being installed as soon as the
   * session is prepared. Used by Firebase Crashlytics for Unity.
   */
  public synchronized void installSignalHandler() {
    // If the handler is already initialized, execute it immediately.
    // Otherwise, set installHandlerDuringPrepareSession=true so it will be installed as soon as it
    // is available.
    if (signalHandlerInstaller != null) {
      signalHandlerInstaller.installHandler();
      return;
    }
    if (installHandlerDuringPrepareSession) {
      // If installHandlerDuringPrepareSession is already true, we can no-op. The signal handler
      // was likely already installed (during prep). This method probably should not have been
      // called, so log a warning.
      Logger.getLogger().w("Native signal handler already installed; skipping re-install.");
    } else {
      Logger.getLogger()
          .d(
              "Deferring signal handler installation until the FirebaseCrashlyticsNdk session has been prepared");
      installHandlerDuringPrepareSession = true;
    }
  }

  /**
   * Gets the singleton {@link FirebaseCrashlyticsNdk} instance. Used by Firebase Unity.
   *
   * @throws NullPointerException if create() has not already been called.
   */
  @NonNull
  public static FirebaseCrashlyticsNdk getInstance() {
    if (instance == null) {
      throw new NullPointerException("FirebaseCrashlyticsNdk component is not present.");
    }
    return instance;
  }
}
