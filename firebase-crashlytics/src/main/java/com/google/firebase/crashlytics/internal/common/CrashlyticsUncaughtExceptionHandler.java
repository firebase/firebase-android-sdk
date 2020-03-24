// Copyright 2020 Google LLC
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

package com.google.firebase.crashlytics.internal.common;

import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.settings.SettingsDataProvider;
import java.util.concurrent.atomic.AtomicBoolean;

class CrashlyticsUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

  interface CrashListener {
    void onUncaughtException(
        SettingsDataProvider settingsDataProvider, Thread thread, Throwable ex);
  }

  private final CrashListener crashListener;
  private final SettingsDataProvider settingsDataProvider;
  private final Thread.UncaughtExceptionHandler defaultHandler;

  // use AtomicBoolean because value is accessible from other threads.
  private final AtomicBoolean isHandlingException;

  public CrashlyticsUncaughtExceptionHandler(
      CrashListener crashListener,
      SettingsDataProvider settingsProvider,
      Thread.UncaughtExceptionHandler defaultHandler) {
    this.crashListener = crashListener;
    this.settingsDataProvider = settingsProvider;
    this.defaultHandler = defaultHandler;
    this.isHandlingException = new AtomicBoolean(false);
  }

  @Override
  public void uncaughtException(Thread thread, Throwable ex) {
    isHandlingException.set(true);
    try {
      if (thread == null) {
        Logger.getLogger().e("Could not handle uncaught exception; null thread");
      } else if (ex == null) {
        Logger.getLogger().e("Could not handle uncaught exception; null throwable");
      } else {
        crashListener.onUncaughtException(settingsDataProvider, thread, ex);
      }
    } catch (Exception e) {
      Logger.getLogger().e("An error occurred in the uncaught exception handler", e);
    } finally {
      Logger.getLogger()
          .d(
              "Crashlytics completed exception processing."
                  + " Invoking default exception handler.");
      defaultHandler.uncaughtException(thread, ex);
      isHandlingException.set(false);
    }
  }

  boolean isHandlingException() {
    return isHandlingException.get();
  }
}
