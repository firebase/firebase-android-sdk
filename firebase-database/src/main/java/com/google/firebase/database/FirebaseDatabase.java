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

package com.google.firebase.database;

import static com.google.android.gms.common.internal.Preconditions.checkNotNull;

import android.text.TextUtils;
import androidx.annotation.NonNull;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.annotations.Nullable;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.Repo;
import com.google.firebase.database.core.RepoInfo;
import com.google.firebase.database.core.RepoManager;
import com.google.firebase.database.core.utilities.ParsedUrl;
import com.google.firebase.database.core.utilities.Utilities;
import com.google.firebase.database.core.utilities.Validation;
import com.google.firebase.emulators.EmulatedServiceSettings;

/**
 * The entry point for accessing a Firebase Database. You can get an instance by calling {@link
 * FirebaseDatabase#getInstance()}. To access a location in the database and read or write data, use
 * {@link FirebaseDatabase#getReference()}.
 */
public class FirebaseDatabase {

  private static final String SDK_VERSION = BuildConfig.VERSION_NAME;

  private final FirebaseApp app;
  private final RepoInfo repoInfo;
  private final DatabaseConfig config;
  @Nullable private EmulatedServiceSettings emulatorSettings;
  private Repo repo; // Usage must be guarded by a call to ensureRepo().

  /**
   * Gets the default FirebaseDatabase instance.
   *
   * @return A FirebaseDatabase instance.
   */
  @NonNull
  public static FirebaseDatabase getInstance() {
    FirebaseApp instance = FirebaseApp.getInstance();
    if (instance == null) {
      throw new DatabaseException("You must call FirebaseApp.initialize() first.");
    }
    return getInstance(instance);
  }

  /**
   * Gets a FirebaseDatabase instance for the specified URL.
   *
   * @param url The URL to the Firebase Database instance you want to access.
   * @return A FirebaseDatabase instance.
   */
  @NonNull
  public static FirebaseDatabase getInstance(@NonNull String url) {
    FirebaseApp instance = FirebaseApp.getInstance();
    if (instance == null) {
      throw new DatabaseException("You must call FirebaseApp.initialize() first.");
    }
    return getInstance(instance, url);
  }

  /**
   * Gets an instance of FirebaseDatabase for a specific FirebaseApp.
   *
   * @param app The FirebaseApp to get a FirebaseDatabase for.
   * @return A FirebaseDatabase instance.
   */
  @NonNull
  public static FirebaseDatabase getInstance(@NonNull FirebaseApp app) {
    String databaseUrl = app.getOptions().getDatabaseUrl();
    if (databaseUrl == null) {
      if (app.getOptions().getProjectId() == null) {
        throw new DatabaseException(
            "Failed to get FirebaseDatabase instance: Can't determine Firebase Database URL. "
                + "Be sure to include a Project ID in your configuration.");
      }
      databaseUrl = "https://" + app.getOptions().getProjectId() + "-default-rtdb.firebaseio.com";
    }
    return getInstance(app, databaseUrl);
  }

  /**
   * Gets a FirebaseDatabase instance for the specified URL, using the specified FirebaseApp.
   *
   * @param app The FirebaseApp to get a FirebaseDatabase for.
   * @param url The URL to the Firebase Database instance you want to access.
   * @return A FirebaseDatabase instance.
   */
  @NonNull
  public static synchronized FirebaseDatabase getInstance(
      @NonNull FirebaseApp app, @NonNull String url) {
    if (TextUtils.isEmpty(url)) {
      throw new DatabaseException(
          "Failed to get FirebaseDatabase instance: Specify DatabaseURL within "
              + "FirebaseApp or from your getInstance() call.");
    }

    checkNotNull(app, "Provided FirebaseApp must not be null.");
    FirebaseDatabaseComponent component = app.get(FirebaseDatabaseComponent.class);
    checkNotNull(component, "Firebase Database component is not present.");

    ParsedUrl parsedUrl = Utilities.parseUrl(url);
    if (!parsedUrl.path.isEmpty()) {
      throw new DatabaseException(
          "Specified Database URL '"
              + url
              + "' is invalid. It should point to the root of a "
              + "Firebase Database but it includes a path: "
              + parsedUrl.path.toString());
    }

    return component.get(parsedUrl.repoInfo);
  }

  /** This exists so Repo can create FirebaseDatabase objects to keep legacy tests working. */
  static FirebaseDatabase createForTests(
      FirebaseApp app, RepoInfo repoInfo, DatabaseConfig config) {
    FirebaseDatabase db = new FirebaseDatabase(app, repoInfo, config);
    db.ensureRepo();
    return db;
  }

  FirebaseDatabase(
      @NonNull FirebaseApp app, @NonNull RepoInfo repoInfo, @NonNull DatabaseConfig config) {
    this.app = app;
    this.repoInfo = repoInfo;
    this.config = config;
  }

  /**
   * Returns the FirebaseApp instance to which this FirebaseDatabase belongs.
   *
   * @return The FirebaseApp instance to which this FirebaseDatabase belongs.
   */
  @NonNull
  public FirebaseApp getApp() {
    return this.app;
  }

  /**
   * Gets a DatabaseReference for the database root node.
   *
   * @return A DatabaseReference pointing to the root node.
   */
  @NonNull
  public DatabaseReference getReference() {
    ensureRepo();
    return new DatabaseReference(this.repo, Path.getEmptyPath());
  }

  /**
   * Gets a DatabaseReference for the provided path.
   *
   * @param path Path to a location in your FirebaseDatabase.
   * @return A DatabaseReference pointing to the specified path.
   */
  @NonNull
  public DatabaseReference getReference(@NonNull String path) {
    ensureRepo();

    if (path == null) {
      throw new NullPointerException(
          "Can't pass null for argument 'pathString' in " + "FirebaseDatabase.getReference()");
    }
    Validation.validateRootPathString(path);

    Path childPath = new Path(path);
    return new DatabaseReference(this.repo, childPath);
  }

  /**
   * Gets a DatabaseReference for the provided URL. The URL must be a URL to a path within this
   * FirebaseDatabase. To create a DatabaseReference to a different database, create a {@link
   * FirebaseApp} with a {@link FirebaseOptions} object configured with the appropriate database
   * URL.
   *
   * @param url A URL to a path within your database.
   * @return A DatabaseReference for the provided URL.
   */
  @NonNull
  public DatabaseReference getReferenceFromUrl(@NonNull String url) {
    ensureRepo();

    if (url == null) {
      throw new NullPointerException(
          "Can't pass null for argument 'url' in " + "FirebaseDatabase.getReferenceFromUrl()");
    }

    ParsedUrl parsedUrl = Utilities.parseUrl(url);
    parsedUrl.repoInfo.applyEmulatorSettings(this.emulatorSettings);

    if (!parsedUrl.repoInfo.host.equals(this.repo.getRepoInfo().host)) {
      throw new DatabaseException(
          "Invalid URL ("
              + url
              + ") passed to getReference().  "
              + "URL was expected to match configured Database URL: "
              + getReference().toString());
    }

    return new DatabaseReference(this.repo, parsedUrl.path);
  }

  /**
   * The Firebase Database client automatically queues writes and sends them to the server at the
   * earliest opportunity, depending on network connectivity. In some cases (e.g. offline usage)
   * there may be a large number of writes waiting to be sent. Calling this method will purge all
   * outstanding writes so they are abandoned.
   *
   * <p>All writes will be purged, including transactions and {@link DatabaseReference#onDisconnect}
   * writes. The writes will be rolled back locally, perhaps triggering events for affected event
   * listeners, and the client will not (re-)send them to the Firebase backend.
   */
  public void purgeOutstandingWrites() {
    ensureRepo();
    this.repo.scheduleNow(
        new Runnable() {
          @Override
          public void run() {
            repo.purgeOutstandingWrites();
          }
        });
  }

  /**
   * Resumes our connection to the Firebase Database backend after a previous {@link #goOffline()}
   * call.
   */
  public void goOnline() {
    ensureRepo();
    RepoManager.resume(this.repo);
  }

  /**
   * Shuts down our connection to the Firebase Database backend until {@link #goOnline()} is called.
   */
  public void goOffline() {
    ensureRepo();
    RepoManager.interrupt(this.repo);
  }

  /**
   * By default, this is set to {@link Logger.Level#INFO INFO}. This includes any internal errors
   * ({@link Logger.Level#ERROR ERROR}) and any security debug messages ({@link Logger.Level#INFO
   * INFO}) that the client receives. Set to {@link Logger.Level#DEBUG DEBUG} to turn on the
   * diagnostic logging, and {@link Logger.Level#NONE NONE} to disable all logging.
   *
   * @param logLevel The desired minimum log level
   */
  public synchronized void setLogLevel(@NonNull Logger.Level logLevel) {
    assertUnfrozen("setLogLevel");
    this.config.setLogLevel(logLevel);
  }

  /**
   * The Firebase Database client will cache synchronized data and keep track of all writes you've
   * initiated while your application is running. It seamlessly handles intermittent network
   * connections and re-sends write operations when the network connection is restored.
   *
   * <p>However by default your write operations and cached data are only stored in-memory and will
   * be lost when your app restarts. By setting this value to `true`, the data will be persisted to
   * on-device (disk) storage and will thus be available again when the app is restarted (even when
   * there is no network connectivity at that time). Note that this method must be called before
   * creating your first Database reference and only needs to be called once per application.
   *
   * @param isEnabled Set to true to enable disk persistence, set to false to disable it.
   */
  public synchronized void setPersistenceEnabled(boolean isEnabled) {
    assertUnfrozen("setPersistenceEnabled");
    this.config.setPersistenceEnabled(isEnabled);
  }

  /**
   * By default Firebase Database will use up to 10MB of disk space to cache data. If the cache
   * grows beyond this size, Firebase Database will start removing data that hasn't been recently
   * used. If you find that your application caches too little or too much data, call this method to
   * change the cache size. This method must be called before creating your first Database reference
   * and only needs to be called once per application.
   *
   * <p>Note that the specified cache size is only an approximation and the size on disk may
   * temporarily exceed it at times. Cache sizes smaller than 1 MB or greater than 100 MB are not
   * supported.
   *
   * @param cacheSizeInBytes The new size of the cache in bytes.
   */
  public synchronized void setPersistenceCacheSizeBytes(long cacheSizeInBytes) {
    assertUnfrozen("setPersistenceCacheSizeBytes");
    this.config.setPersistenceCacheSizeBytes(cacheSizeInBytes);
  }

  /**
   * Modifies this FirebaseDatabase instance to communicate with the Realtime Database emulator.
   *
   * <p>Note: Call this method before using the instance to do any database operations.
   *
   * @param host the emulator host (for example, 10.0.2.2)
   * @param port the emulator port (for example, 9000)
   */
  public void useEmulator(@NonNull String host, int port) {
    if (this.repo != null) {
      throw new IllegalStateException(
          "Cannot call useEmulator() after instance has already been initialized.");
    }

    this.emulatorSettings = new EmulatedServiceSettings(host, port);
  }

  /** @return The semver version for this build of the Firebase Database client */
  @NonNull
  public static String getSdkVersion() {
    return SDK_VERSION;
  }

  private void assertUnfrozen(String methodCalled) {
    if (this.repo != null) {
      throw new DatabaseException(
          "Calls to "
              + methodCalled
              + "() must be made before any "
              + "other usage of FirebaseDatabase instance.");
    }
  }

  private synchronized void ensureRepo() {
    if (this.repo == null) {
      this.repoInfo.applyEmulatorSettings(this.emulatorSettings);
      repo = RepoManager.createRepo(this.config, this.repoInfo, this);
    }
  }

  // for testing
  DatabaseConfig getConfig() {
    return this.config;
  }
}
