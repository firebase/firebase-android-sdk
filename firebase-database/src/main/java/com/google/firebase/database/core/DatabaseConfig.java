// Copyright 2018 Google LLC
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

package com.google.firebase.database.core;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.Logger;
import java.util.List;

/**
 * TODO: Merge this class with Context and clean this up. Some methods may need to be re-added to
 * FirebaseDatabase if we want to still expose them.
 */
public class DatabaseConfig extends Context {

  // TODO: Remove this from the public API since we currently can't pass logging
  // across AIDL interface.
  /**
   * If you would like to provide a custom log target, pass an object that implements the {@link
   * com.google.firebase.database.Logger Logger} interface.
   *
   * @hide
   * @param logger The custom logger that will be called with all log messages
   */
  public synchronized void setLogger(com.google.firebase.database.logging.Logger logger) {
    assertUnfrozen();
    this.logger = logger;
  }

  /**
   * In the default setup, the Firebase Database library will create a thread to handle all
   * callbacks. On Android, it will attempt to use the main <a
   * href="http://developer.android.com/reference/android/os/Looper.html"
   * target="_blank">Looper</a>. <br>
   * <br>
   * In the event that you would like more control over how your callbacks are triggered, you can
   * provide an object that implements {@link EventTarget EventTarget}. It will be passed a {@link
   * java.lang.Runnable Runnable} for each callback.
   *
   * @param eventTarget The object that will be responsible for triggering callbacks
   */
  public synchronized void setEventTarget(EventTarget eventTarget) {
    assertUnfrozen();
    this.eventTarget = eventTarget;
  }

  /**
   * By default, this is set to {@link Logger.Level#INFO INFO}. This includes any internal errors
   * ({@link Logger.Level#ERROR ERROR}) and any security debug messages ({@link Logger.Level#INFO
   * INFO}) that the client receives. Set to {@link Logger.Level#DEBUG DEBUG} to turn on the
   * diagnostic logging, and {@link Logger.Level#NONE NONE} to disable all logging.
   *
   * @param logLevel The desired minimum log level
   */
  public synchronized void setLogLevel(Logger.Level logLevel) {
    assertUnfrozen();
    switch (logLevel) {
      case DEBUG:
        this.logLevel = com.google.firebase.database.logging.Logger.Level.DEBUG;
        break;
      case INFO:
        this.logLevel = com.google.firebase.database.logging.Logger.Level.INFO;
        break;
      case WARN:
        this.logLevel = com.google.firebase.database.logging.Logger.Level.WARN;
        break;
      case ERROR:
        this.logLevel = com.google.firebase.database.logging.Logger.Level.ERROR;
        break;
      case NONE:
        this.logLevel = com.google.firebase.database.logging.Logger.Level.NONE;
        break;
      default:
        throw new IllegalArgumentException("Unknown log level: " + logLevel);
    }
  }

  /**
   * Used primarily for debugging. Limits the debug output to the specified components. By default,
   * this is null, which enables logging from all components. Setting this explicitly will also set
   * the log level to {@link Logger.Level#DEBUG DEBUG}.
   *
   * @param debugComponents A list of components for which logs are desired, or null to enable all
   *     components
   */
  public synchronized void setDebugLogComponents(List<String> debugComponents) {
    assertUnfrozen();
    setLogLevel(Logger.Level.DEBUG);
    loggedComponents = debugComponents;
  }

  public void setRunLoop(RunLoop runLoop) {
    this.runLoop = runLoop;
  }

  public void setAuthTokenProvider(AuthTokenProvider provider) {
    this.authTokenProvider = provider;
  }

  /**
   * Sets the session identifier for this Firebase Database connection.
   *
   * <p>Use session identifiers to enable multiple persisted authentication sessions on the same
   * device. There is no need to use this method if there will only be one user per device.
   *
   * @param sessionKey The session key to identify the session with.
   * @since 1.1
   */
  public synchronized void setSessionPersistenceKey(String sessionKey) {
    assertUnfrozen();
    if (sessionKey == null || sessionKey.isEmpty()) {
      throw new IllegalArgumentException("Session identifier is not allowed to be empty or null!");
    }
    this.persistenceKey = sessionKey;
  }

  /**
   * By default the Firebase Database client will keep data in memory while your application is
   * running, but not when it is restarted. By setting this value to `true`, the data will be
   * persisted to on-device (disk) storage and will thus be available again when the app is
   * restarted (even when there is no network connectivity at that time). Note that this method must
   * be called before creating your first Database reference and only needs to be called once per
   * application.
   *
   * @since 2.3
   * @param isEnabled Set to true to enable disk persistence, set to false to disable it.
   */
  public synchronized void setPersistenceEnabled(boolean isEnabled) {
    assertUnfrozen();
    this.persistenceEnabled = isEnabled;
  }

  /**
   * By default Firebase Database will use up to 10MB of disk space to cache data. If the cache
   * grows beyond this size, Firebase Database will start removing data that hasn't been recently
   * used. If you find that your application caches too little or too much data, call this method to
   * change the cache size. This method must be called before creating your first Database reference
   * and only needs to be called once per application.
   *
   * <p>Note that the specified cache size is only an approximation and the size on disk may
   * temporarily exceed it at times.
   *
   * @since 2.3
   * @param cacheSizeInBytes The new size of the cache in bytes.
   */
  public synchronized void setPersistenceCacheSizeBytes(long cacheSizeInBytes) {
    assertUnfrozen();

    if (cacheSizeInBytes < 1024 * 1024) {
      throw new DatabaseException("The minimum cache size must be at least 1MB");
    }
    if (cacheSizeInBytes > 100 * 1024 * 1024) {
      throw new DatabaseException(
          "Firebase Database currently doesn't support a cache size larger than 100MB");
    }

    this.cacheSize = cacheSizeInBytes;
  }

  public synchronized void setFirebaseApp(FirebaseApp app) {
    this.firebaseApp = app;
  }
}
