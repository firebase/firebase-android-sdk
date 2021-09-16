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
import com.google.firebase.crashlytics.internal.unity.ResourceUnityVersionProvider;
import java.io.File;

/** The Crashlytics NDK Kit provides crash reporting functionality for Android NDK users. */
class FirebaseCrashlyticsNdk implements CrashlyticsNativeComponent {

  /** Relative sub-path to use for storing files. */
  private static final String FILES_PATH = ".com.google.firebase.crashlytics-ndk";

  private static FirebaseCrashlyticsNdk instance;

  static FirebaseCrashlyticsNdk create(@NonNull Context context) {
    final File rootDir = new File(context.getFilesDir(), FILES_PATH);

    final CrashpadController controller =
        new CrashpadController(
            context, new JniNativeApi(context), new NdkCrashFilesManager(rootDir));

    // The signal handler is installed immediately for non-Unity apps. For Unity apps, it will
    // be installed when the Firebase Unity SDK explicitly calls installSignalHandler().
    boolean installHandlerOnPrepSession =
        (ResourceUnityVersionProvider.resolveUnityEditorVersion(context) == null);
    instance = new FirebaseCrashlyticsNdk(controller, installHandlerOnPrepSession);
    return instance;
  }

  private final CrashpadController controller;

  private interface SignalHandlerInstaller {
    void installHandler();
  }

  private volatile boolean installHandlerDuringPrepareSession;
  private volatile SignalHandlerInstaller signalHandlerInstaller;

  FirebaseCrashlyticsNdk(
      @NonNull CrashpadController controller, boolean installHandlerDuringPrepareSession) {
    this.controller = controller;
    this.installHandlerDuringPrepareSession = installHandlerDuringPrepareSession;
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

  /**
   * Installs the native signal handler, if the session has already been prepared. Otherwise,
   * calling this method will result in the native signal handler being installed as soon as the
   * session is prepared. Used by Firebase Crashlytics for Unity.
   */
  public synchronized void installSignalHandler() {
    if (signalHandlerInstaller == null) {
      Logger.getLogger()
          .d(
              "Native signal handler install requested, but the FirebaseCrashlyticsNdk session"
                  + "has not been prepared...Deferring installation.");
      installHandlerDuringPrepareSession = true;
    } else {
      signalHandlerInstaller.installHandler();
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
