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

package com.google.firebase.database.android;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseApp.BackgroundStateChangeListener;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.connection.ConnectionContext;
import com.google.firebase.database.connection.HostInfo;
import com.google.firebase.database.connection.PersistentConnection;
import com.google.firebase.database.connection.PersistentConnectionImpl;
import com.google.firebase.database.core.EventTarget;
import com.google.firebase.database.core.Platform;
import com.google.firebase.database.core.RunLoop;
import com.google.firebase.database.core.persistence.CachePolicy;
import com.google.firebase.database.core.persistence.DefaultPersistenceManager;
import com.google.firebase.database.core.persistence.LRUCachePolicy;
import com.google.firebase.database.core.persistence.PersistenceManager;
import com.google.firebase.database.core.utilities.DefaultRunLoop;
import com.google.firebase.database.logging.AndroidLogger;
import com.google.firebase.database.logging.LogWrapper;
import com.google.firebase.database.logging.Logger;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AndroidPlatform implements Platform {

  private final Context applicationContext;
  private final Set<String> createdPersistenceCaches = new HashSet<String>();
  private final FirebaseApp firebaseApp;
  private static final String APP_IN_BACKGROUND_INTERRUPT_REASON = "app_in_background";

  public AndroidPlatform(FirebaseApp app) {
    this.firebaseApp = app;
    if (this.firebaseApp == null) {
      // The exception text gets buried in a chain of "caused by ..." stacks, so make the log
      // apparent.
      Log.e(
          "FirebaseDatabase",
          "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
      Log.e(
          "FirebaseDatabase",
          "ERROR: You must call FirebaseApp.initializeApp() before using Firebase Database.");
      Log.e(
          "FirebaseDatabase",
          "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
      throw new RuntimeException(
          "You need to call FirebaseApp.initializeApp() before using Firebase Database.");
    }

    this.applicationContext = firebaseApp.getApplicationContext();
  }

  @Override
  public EventTarget newEventTarget(com.google.firebase.database.core.Context context) {
    return new AndroidEventTarget();
  }

  @Override
  public RunLoop newRunLoop(com.google.firebase.database.core.Context ctx) {
    final LogWrapper logger = ctx.getLogger("RunLoop");
    return new DefaultRunLoop() {
      @Override
      public void handleException(final Throwable e) {
        final String message = DefaultRunLoop.messageForException(e);
        // First log with our logger
        logger.error(message, e);

        // Rethrow on main thread, so the application will crash
        // The exception might indicate that there is something seriously wrong and better crash,
        // than continue run in an undefined state...
        Handler handler = new Handler(applicationContext.getMainLooper());
        handler.post(
            new Runnable() {
              @Override
              public void run() {
                throw new RuntimeException(message, e);
              }
            });

        // In a background process, the app may not actually crash. So we'll shutdown
        // the executor to avoid continuing to run in a corrupted state (and likely causing
        // other exceptions).
        getExecutorService().shutdownNow();
      }
    };
  }

  @Override
  public PersistentConnection newPersistentConnection(
      com.google.firebase.database.core.Context context,
      ConnectionContext connectionContext,
      HostInfo info,
      PersistentConnection.Delegate delegate) {
    final PersistentConnection connection =
        new PersistentConnectionImpl(connectionContext, info, delegate);

    // TODO: Ideally we would remove this listener at some point, but right now
    // there's no cleanup path for PersistentConnection (or Repo, etc.). They live forever.
    this.firebaseApp.addBackgroundStateChangeListener(
        new BackgroundStateChangeListener() {
          @Override
          public void onBackgroundStateChanged(boolean background) {
            if (background) {
              connection.interrupt(APP_IN_BACKGROUND_INTERRUPT_REASON);
            } else {
              connection.resume(APP_IN_BACKGROUND_INTERRUPT_REASON);
            }
          }
        });

    return connection;
  }

  @Override
  public Logger newLogger(
      com.google.firebase.database.core.Context context,
      Logger.Level component,
      List<String> enabledComponents) {
    return new AndroidLogger(component, enabledComponents);
  }

  @Override
  public String getUserAgent(com.google.firebase.database.core.Context context) {
    return Build.VERSION.SDK_INT + "/Android";
  }

  @Override
  public String getPlatformVersion() {
    return "android-" + FirebaseDatabase.getSdkVersion();
  }

  @Override
  public PersistenceManager createPersistenceManager(
      com.google.firebase.database.core.Context firebaseContext, String firebaseId) {
    String sessionId = firebaseContext.getSessionPersistenceKey();
    String cacheId = firebaseId + "_" + sessionId;
    // TODO[persistence]: Do this at a higher level (e.g. rework how RepoManager tracks repos to do
    // it by session id)
    if (createdPersistenceCaches.contains(cacheId)) {
      throw new DatabaseException(
          "SessionPersistenceKey '" + sessionId + "' has already been used.");
    }
    createdPersistenceCaches.add(cacheId);
    SqlPersistenceStorageEngine engine =
        new SqlPersistenceStorageEngine(this.applicationContext, firebaseContext, cacheId);
    CachePolicy cachePolicy = new LRUCachePolicy(firebaseContext.getPersistenceCacheSizeBytes());
    return new DefaultPersistenceManager(firebaseContext, engine, cachePolicy);
  }

  @Override
  public File getSSLCacheDirectory() {
    // Note that this is the same folder that SSLSessionCache uses by default.
    return applicationContext.getApplicationContext().getDir("sslcache", Context.MODE_PRIVATE);
  }
}
