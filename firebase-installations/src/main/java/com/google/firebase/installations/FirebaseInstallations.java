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

package com.google.firebase.installations;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.installations.FirebaseInstallationsException.Status;
import com.google.firebase.installations.local.IidStore;
import com.google.firebase.installations.local.PersistedInstallation;
import com.google.firebase.installations.local.PersistedInstallationEntry;
import com.google.firebase.installations.remote.FirebaseInstallationServiceClient;
import com.google.firebase.installations.remote.InstallationResponse;
import com.google.firebase.installations.remote.TokenResult;
import com.google.firebase.platforminfo.UserAgentPublisher;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Entry point for Firebase Installations.
 *
 * <p>Firebase Installations does
 *
 * <ul>
 *   <li>provide unique identifier for a Firebase installation
 *   <li>provide auth token of a Firebase installation
 *   <li>provide a API to GDPR-delete a Firebase installation
 * </ul>
 */
public class FirebaseInstallations implements FirebaseInstallationsApi {
  private final FirebaseApp firebaseApp;
  private final FirebaseInstallationServiceClient serviceClient;
  private final PersistedInstallation persistedInstallation;
  private final ExecutorService executor;
  private final Utils utils;
  private final IidStore iidStore;
  private final RandomFidGenerator fidGenerator;
  private final Object lock = new Object();

  @GuardedBy("lock")
  private final List<StateListener> listeners = new ArrayList<>();

  /* used for thread-level synchronization of generating and persisting fids */
  private final Object lockGenerateFid = new Object();
  /* file used for process-level syncronization of generating and persisting fids */
  private static final String LOCKFILE_NAME_GENERATE_FID = "generatefid.lock";

  /** package private constructor. */
  FirebaseInstallations(
      FirebaseApp firebaseApp,
      @Nullable UserAgentPublisher publisher,
      @Nullable HeartBeatInfo heartbeatInfo) {
    this(
        new ThreadPoolExecutor(0, 1, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>()),
        firebaseApp,
        new FirebaseInstallationServiceClient(
            firebaseApp.getApplicationContext(), publisher, heartbeatInfo),
        new PersistedInstallation(firebaseApp),
        new Utils(Calendar.getInstance()),
        new IidStore(),
        new RandomFidGenerator());
  }

  FirebaseInstallations(
      ExecutorService executor,
      FirebaseApp firebaseApp,
      FirebaseInstallationServiceClient serviceClient,
      PersistedInstallation persistedInstallation,
      Utils utils,
      IidStore iidStore,
      RandomFidGenerator fidGenerator) {
    this.firebaseApp = firebaseApp;
    this.serviceClient = serviceClient;
    this.executor = executor;
    this.persistedInstallation = persistedInstallation;
    this.utils = utils;
    this.iidStore = iidStore;
    this.fidGenerator = fidGenerator;
  }

  /**
   * Returns the {@link FirebaseInstallationsApi} initialized with the default {@link FirebaseApp}.
   *
   * @return a {@link FirebaseInstallationsApi} instance
   */
  @NonNull
  public static FirebaseInstallations getInstance() {
    FirebaseApp defaultFirebaseApp = FirebaseApp.getInstance();
    return getInstance(defaultFirebaseApp);
  }

  /**
   * Returns the {@link FirebaseInstallations} initialized with a custom {@link FirebaseApp}.
   *
   * @param app a custom {@link FirebaseApp}
   * @return a {@link FirebaseInstallations} instance
   */
  @NonNull
  public static FirebaseInstallations getInstance(@NonNull FirebaseApp app) {
    Preconditions.checkArgument(app != null, "Null is not a valid value of FirebaseApp.");
    return (FirebaseInstallations) app.get(FirebaseInstallationsApi.class);
  }

  /** Returns the application id of the {@link FirebaseApp} of this {@link FirebaseInstallations} */
  @VisibleForTesting
  String getApplicationId() {
    return firebaseApp.getOptions().getApplicationId();
  }

  /** Returns the nick name of the {@link FirebaseApp} of this {@link FirebaseInstallations} */
  @VisibleForTesting
  String getName() {
    return firebaseApp.getName();
  }

  /**
   * Returns a globally unique identifier of this Firebase app installation. This is a url-safe
   * base64 string of a 128-bit integer.
   */
  @NonNull
  @Override
  public Task<String> getId() {
    Task<String> task = addGetIdListener();
    executor.execute(this::doGetId);
    return task;
  }

  /**
   * Returns a valid authentication token for the Firebase installation. Generates a new token if
   * one doesn't exist, is expired or about to expire.
   *
   * <p>Should only be called if the Firebase Installation is registered.
   *
   * @param authTokenOption Options to get FIS Auth Token either by force refreshing or not. Accepts
   *     {@link AuthTokenOption} values. Default value of AuthTokenOption = DO_NOT_FORCE_REFRESH.
   */
  @NonNull
  @Override
  public Task<InstallationTokenResult> getToken(@AuthTokenOption int authTokenOption) {
    Task<InstallationTokenResult> task = addGetAuthTokenListener();
    if (authTokenOption == FORCE_REFRESH) {
      executor.execute(this::doGetAuthTokenForceRefresh);
    } else {
      executor.execute(this::doGetAuthTokenWithoutForceRefresh);
    }
    return task;
  }

  /**
   * Call to delete this Firebase app installation from Firebase backend. This call would possibly
   * lead Firebase Notification, Firebase RemoteConfig, Firebase Predictions or Firebase In-App
   * Messaging not function properly.
   */
  @NonNull
  @Override
  public Task<Void> delete() {
    return Tasks.call(executor, this::deleteFirebaseInstallationId);
  }

  private Task<String> addGetIdListener() {
    TaskCompletionSource<String> taskCompletionSource = new TaskCompletionSource<>();
    StateListener l = new GetIdListener(taskCompletionSource);
    synchronized (lock) {
      listeners.add(l);
    }
    return taskCompletionSource.getTask();
  }

  private Task<InstallationTokenResult> addGetAuthTokenListener() {
    TaskCompletionSource<InstallationTokenResult> taskCompletionSource =
        new TaskCompletionSource<>();
    StateListener l = new GetAuthTokenListener(utils, taskCompletionSource);
    synchronized (lock) {
      listeners.add(l);
    }
    return taskCompletionSource.getTask();
  }

  private void triggerOnStateReached(PersistedInstallationEntry persistedInstallationEntry) {
    synchronized (lock) {
      Iterator<StateListener> it = listeners.iterator();
      while (it.hasNext()) {
        StateListener l = it.next();
        boolean doneListening = l.onStateReached(persistedInstallationEntry);
        if (doneListening) {
          it.remove();
        }
      }
    }
  }

  private void triggerOnException(PersistedInstallationEntry prefs, Exception exception) {
    synchronized (lock) {
      Iterator<StateListener> it = listeners.iterator();
      while (it.hasNext()) {
        StateListener l = it.next();
        boolean doneListening = l.onException(prefs, exception);
        if (doneListening) {
          it.remove();
        }
      }
    }
  }

  private final void doGetId() {
    doRegistrationInternal(false);
  }

  private final void doGetAuthTokenWithoutForceRefresh() {
    doRegistrationInternal(false);
  }

  private final void doGetAuthTokenForceRefresh() {
    doRegistrationInternal(true);
  }

  /**
   * Logic for handling get id and the two forms of get auth token. This handles all the work,
   * including creating a new FID if one hasn't been generated yet and making the network calls to
   * create an installation and to retrieve a new auth token. Also contains the error handling for
   * when the server says that credentials are bad and that a new Fid needs to be generated.
   *
   * @param forceRefresh true if this is for a getAuthToken call and if the caller wants to fetch a
   *     new auth token from the server even if an unexpired auth token exists on the client.
   */
  private final void doRegistrationInternal(boolean forceRefresh) {
    PersistedInstallationEntry prefs = getPrefsWithGeneratedIdMultiProcessSafe();

    // Since the caller wants to force an authtoken refresh remove the authtoken from the
    // prefs we are working with, so the following steps know a new token is required.
    if (forceRefresh) {
      prefs = prefs.withClearedAuthToken();
    }

    triggerOnStateReached(prefs);

    // There are two possible cleanup steps to perform at this stage: the FID may need to
    // be registered with the server or the FID is registered but we need a fresh authtoken.
    // Registering will also result in a fresh authtoken. Do the appropriate step here.
    try {
      if (prefs.isErrored() || prefs.isUnregistered()) {
        prefs = registerFidWithServer(prefs);
      } else if (forceRefresh || utils.isAuthTokenExpired(prefs)) {
        prefs = fetchAuthTokenFromServer(prefs);
      } else {
        // nothing more to do, get out now
        return;
      }
    } catch (IOException e) {
      triggerOnException(prefs, e);
      return;
    }

    // Store the prefs to persist the result of the previous step.
    persistedInstallation.insertOrUpdatePersistedInstallationEntry(prefs);

    // Let the caller know about the result.
    if (prefs.isErrored()) {
      triggerOnException(prefs, new FirebaseInstallationsException(Status.BAD_CONFIG));
    } else if (prefs.isNotGenerated()) {
      // If there is no fid it means the call failed with an auth error. Simulate an
      // IOException so that the caller knows to try again.
      triggerOnException(prefs, new IOException("cleared fid due to auth error"));
    } else {
      triggerOnStateReached(prefs);
    }
  }

  /**
   * Loads the prefs, generating a new ID if necessary. This operation is made cross-process and
   * cross-thread safe by wrapping all the processing first in a java synchronization block and
   * wrapping that in a cross-process lock created using FileLocks.
   *
   * <p>If a FID does not yet exist it generate a new FID, either from an existing IID or generated
   * randomly. If an IID exists and this is the first time a FID has been generated for this
   * installation, the IID will be used as the FID. If the FID is ever cleared then the next time a
   * FID is generated the IID is ignored and a FID is generated randomly.
   *
   * @return a new version of the prefs that includes the new FID. These prefs will have already
   *     been persisted.
   */
  private PersistedInstallationEntry getPrefsWithGeneratedIdMultiProcessSafe() {
    CrossProcessLock lock =
        CrossProcessLock.acquire(firebaseApp.getApplicationContext(), LOCKFILE_NAME_GENERATE_FID);
    try {
      synchronized (lockGenerateFid) {
        PersistedInstallationEntry prefs =
            persistedInstallation.readPersistedInstallationEntryValue();
        // Check if a new FID needs to be created
        if (prefs.isNotGenerated()) {
          // For a default firebase installation read the existing iid. For other custom firebase
          // installations create a new fid

          // Only one single thread from one single process can execute this block
          // at any given time.
          String fid = readExistingIidOrCreateFid(prefs);
          prefs =
              persistedInstallation.insertOrUpdatePersistedInstallationEntry(
                  prefs.withUnregisteredFid(fid));
        }
        return prefs;
      }

    } finally {
      lock.releaseAndClose();
    }
  }

  private String readExistingIidOrCreateFid(PersistedInstallationEntry prefs) {
    // Check if this firebase app is the default (first initialized) instance
    if (!firebaseApp.equals(FirebaseApp.getInstance()) || !prefs.shouldAttemptMigration()) {
      return fidGenerator.createRandomFid();
    }
    // For a default firebase installation, read the existing iid from shared prefs
    String fid = iidStore.readIid();
    if (fid == null) {
      fid = fidGenerator.createRandomFid();
    }
    return fid;
  }

  /** Registers the created Fid with FIS servers and update the persisted state. */
  private PersistedInstallationEntry registerFidWithServer(PersistedInstallationEntry prefs)
      throws IOException {
    InstallationResponse response =
        serviceClient.createFirebaseInstallation(
            /*apiKey= */ firebaseApp.getOptions().getApiKey(),
            /*fid= */ prefs.getFirebaseInstallationId(),
            /*projectID= */ firebaseApp.getOptions().getProjectId(),
            /*appId= */ getApplicationId());

    switch (response.getResponseCode()) {
      case OK:
        return prefs.withRegisteredFid(
            response.getFid(),
            response.getRefreshToken(),
            utils.currentTimeInSecs(),
            response.getAuthToken().getToken(),
            response.getAuthToken().getTokenExpirationTimestamp());
      case BAD_CONFIG:
        return prefs.withFisError("BAD CONFIG");
      default:
        throw new IOException();
    }
  }

  /**
   * Calls the FIS servers to generate an auth token for this Firebase installation. Returns a
   * PersistedInstallationEntry with the new authtoken. The authtoken in the returned
   * PersistedInstallationEntry will be "expired" if the server refuses to generate an auth token
   * for the fid.
   */
  private PersistedInstallationEntry fetchAuthTokenFromServer(
      @NonNull PersistedInstallationEntry prefs) throws IOException {
    TokenResult tokenResult =
        serviceClient.generateAuthToken(
            /*apiKey= */ firebaseApp.getOptions().getApiKey(),
            /*fid= */ prefs.getFirebaseInstallationId(),
            /*projectID= */ firebaseApp.getOptions().getProjectId(),
            /*refreshToken= */ prefs.getRefreshToken());

    switch (tokenResult.getResponseCode()) {
      case OK:
        return prefs.withAuthToken(
            tokenResult.getToken(),
            tokenResult.getTokenExpirationTimestamp(),
            utils.currentTimeInSecs());
      case BAD_CONFIG:
        return prefs.withFisError("BAD CONFIG");
      case AUTH_ERROR:
        // The the server refused to generate a new auth token due to bad credentials, clear the
        // FID to force the generation of a new one.
        return prefs.withNoGeneratedFid();
      default:
        throw new IOException();
    }
  }

  /**
   * Deletes the firebase installation id of the {@link FirebaseApp} from FIS servers and local
   * storage.
   */
  private Void deleteFirebaseInstallationId() throws FirebaseInstallationsException, IOException {
    PersistedInstallationEntry entry = persistedInstallation.readPersistedInstallationEntryValue();
    if (entry.isRegistered()) {
      // Call the FIS servers to delete this Firebase Installation Id.
      try {
        serviceClient.deleteFirebaseInstallation(
            firebaseApp.getOptions().getApiKey(),
            entry.getFirebaseInstallationId(),
            firebaseApp.getOptions().getProjectId(),
            entry.getRefreshToken());

      } catch (FirebaseException exception) {
        throw new FirebaseInstallationsException(
            "Failed to delete a Firebase Installation.", Status.BAD_CONFIG);
      }
    }

    persistedInstallation.insertOrUpdatePersistedInstallationEntry(entry.withNoGeneratedFid());
    return null;
  }
}
