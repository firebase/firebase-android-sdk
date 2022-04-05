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

import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.settings.SettingsProvider;
import java.util.concurrent.atomic.AtomicBoolean;

class CrashlyticsUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

  interface CrashListener {

    void onUncaughtException(SettingsProvider settingsProvider, Thread thread, Throwable ex);
  }

  private final CrashListener crashListener;
  private final SettingsProvider settingsProvider;
  private final Thread.UncaughtExceptionHandler defaultHandler;
  private final CrashlyticsNativeComponent nativeComponent;

  // use AtomicBoolean because value is accessible from other threads.
  private final AtomicBoolean isHandlingException;

  public CrashlyticsUncaughtExceptionHandler(
      CrashListener crashListener,
      SettingsProvider settingsProvider,
      Thread.UncaughtExceptionHandler defaultHandler,
      CrashlyticsNativeComponent nativeComponent) {
    this.crashListener = crashListener;
    this.settingsProvider = settingsProvider;
    this.defaultHandler = defaultHandler;
    this.isHandlingException = new AtomicBoolean(false);
    this.nativeComponent = nativeComponent;
  }

  @Override
  public void uncaughtException(Thread thread, Throwable ex) {
    isHandlingException.set(true);
    try {
      if (shouldRecordUncaughtException(thread, ex)) {
        crashListener.onUncaughtException(settingsProvider, thread, ex);
      } else {
        Logger.getLogger().d("Uncaught exception will not be recorded by Crashlytics.");
      }
    } catch (Exception e) {
      Logger.getLogger().e("An error occurred in the uncaught exception handler", e);
    } finally {
      Logger.getLogger().d("Completed exception processing. Invoking default exception handler.");
      defaultHandler.uncaughtException(thread, ex);
      isHandlingException.set(false);
    }
  }

  boolean isHandlingException() {
    return isHandlingException.get();
  }

  /**
   * Returns true if Crashlytics should record this exception. The decision to record is different
   * than the decision to report the exception to Crashlytics servers, which is handled by the
   * {@link DataCollectionArbiter}
   *
   * @return false if the thread or exception is null, or if a native crash already exists for this
   *     session.
   */
  private boolean shouldRecordUncaughtException(Thread thread, Throwable ex) {
    if (thread == null) {
      Logger.getLogger().e("Crashlytics will not record uncaught exception; null thread");
      return false;
    }
    if (ex == null) {
      Logger.getLogger().e("Crashlytics will not record uncaught exception; null throwable");
      return false;
    }
    // We should only report at most one fatal event for a session. If a native fatal already exists
    // for this session, ignore the uncaught exception
    if (nativeComponent.hasCrashDataForCurrentSession()) {
      Logger.getLogger()
          .d("Crashlytics will not record uncaught exception; native crash exists for session.");
      return false;
    }
    return true;
  }
}
