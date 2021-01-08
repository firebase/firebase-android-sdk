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

import android.text.TextUtils;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsException.Status;
import com.google.firebase.installations.internal.FidListener;
import com.google.firebase.installations.internal.FidListenerHandle;
import com.google.firebase.installations.local.IidStore;
import com.google.firebase.installations.local.PersistedInstallation;
import com.google.firebase.installations.local.PersistedInstallationEntry;
import com.google.firebase.installations.remote.FirebaseInstallationServiceClient;
import com.google.firebase.installations.remote.InstallationResponse;
import com.google.firebase.installations.remote.TokenResult;
import com.google.firebase.platforminfo.UserAgentPublisher;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Entry point for Firebase installations.
 *
 * <p>The Firebase installations service:
 *
 * <ul>
 *   <li>provides a unique identifier for a Firebase installation
 *   <li>provides an auth token for a Firebase installation
 *   <li>provides a API to perform GDPR-compliant deletion of a Firebase installation.
 * </ul>
 */
public class FirebaseInstallations implements FirebaseInstallationsApi {
  private final FirebaseApp firebaseApp;
  private final FirebaseInstallationServiceClient serviceClient;
  private final PersistedInstallation persistedInstallation;
  private final Utils utils;
  private final IidStore iidStore;
  private final RandomFidGenerator fidGenerator;
  private final Object lock = new Object();
  private final ExecutorService backgroundExecutor;
  private final ExecutorService networkExecutor;
  /* FID of this Firebase Installations instance. Cached after successfully registering and
  persisting the FID locally. NOTE: cachedFid resets if FID is deleted.*/
  @GuardedBy("this")
  private String cachedFid;

  @GuardedBy("FirebaseInstallations.this")
  private Set<FidListener> fidListeners = new HashSet<>();

  @GuardedBy("lock")
  private final List<StateListener> listeners = new ArrayList<>();

  /* used for thread-level synchronization of generating and persisting fids */
  private static final Object lockGenerateFid = new Object();

  /* file used for process-level synchronization of generating and persisting fids */
  private static final String LOCKFILE_NAME_GENERATE_FID = "generatefid.lock";
  private static final String CHIME_FIREBASE_APP_NAME = "CHIME_ANDROID_SDK";
  private static final int CORE_POOL_SIZE = 0;
  private static final int MAXIMUM_POOL_SIZE = 1;
  private static final long KEEP_ALIVE_TIME_IN_SECONDS = 30L;
  private static final ThreadFactory THREAD_FACTORY =
      new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
          return new Thread(
              r, String.format("firebase-installations-executor-%d", mCount.getAndIncrement()));
        }
      };

  private static final String API_KEY_VALIDATION_MSG =
      "Please set a valid API key. A Firebase API key is required to communicate with "
          + "Firebase server APIs: It authenticates your project with Google."
          + "Please refer to https://firebase.google.com/support/privacy/init-options.";

  private static final String APP_ID_VALIDATION_MSG =
      "Please set your Application ID. A valid Firebase App ID is required to communicate "
          + "with Firebase server APIs: It identifies your application with Firebase."
          + "Please refer to https://firebase.google.com/support/privacy/init-options.";

  private static final String PROJECT_ID_VALIDATION_MSG =
      "Please set your Project ID. A valid Firebase Project ID is required to communicate "
          + "with Firebase server APIs: It identifies your application with Firebase."
          + "Please refer to https://firebase.google.com/support/privacy/init-options.";

  private static final String AUTH_ERROR_MSG =
      "Installation ID could not be validated with the Firebase servers (maybe it was deleted). "
          + "Firebase Installations will need to create a new Installation ID and auth token. "
          + "Please retry your last request.";

  /** package private constructor. */
  FirebaseInstallations(
      FirebaseApp firebaseApp,
      @NonNull Provider<UserAgentPublisher> publisher,
      @NonNull Provider<HeartBeatInfo> heartbeatInfo) {
    this(
        new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAXIMUM_POOL_SIZE,
            KEEP_ALIVE_TIME_IN_SECONDS,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            THREAD_FACTORY),
        firebaseApp,
        new FirebaseInstallationServiceClient(
            firebaseApp.getApplicationContext(), publisher, heartbeatInfo),
        new PersistedInstallation(firebaseApp),
        Utils.getInstance(),
        new IidStore(firebaseApp),
        new RandomFidGenerator());
  }

  FirebaseInstallations(
      ExecutorService backgroundExecutor,
      FirebaseApp firebaseApp,
      FirebaseInstallationServiceClient serviceClient,
      PersistedInstallation persistedInstallation,
      Utils utils,
      IidStore iidStore,
      RandomFidGenerator fidGenerator) {
    this.firebaseApp = firebaseApp;
    this.serviceClient = serviceClient;
    this.persistedInstallation = persistedInstallation;
    this.utils = utils;
    this.iidStore = iidStore;
    this.fidGenerator = fidGenerator;
    this.backgroundExecutor = backgroundExecutor;
    this.networkExecutor =
        new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAXIMUM_POOL_SIZE,
            KEEP_ALIVE_TIME_IN_SECONDS,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            THREAD_FACTORY);
  }

  /**
   * Perform pre-condition checks to make sure {@link FirebaseOptions#getApiKey()}, {@link
   * FirebaseOptions#getApplicationId()} , and ({@link FirebaseOptions#getProjectId()} are not null
   * or empty.
   */
  private void preConditionChecks() {
    Preconditions.checkNotEmpty(getApplicationId(), APP_ID_VALIDATION_MSG);
    Preconditions.checkNotEmpty(getProjectIdentifier(), PROJECT_ID_VALIDATION_MSG);
    Preconditions.checkNotEmpty(getApiKey(), API_KEY_VALIDATION_MSG);
    Preconditions.checkArgument(
        Utils.isValidAppIdFormat(getApplicationId()), APP_ID_VALIDATION_MSG);
    Preconditions.checkArgument(Utils.isValidApiKeyFormat(getApiKey()), API_KEY_VALIDATION_MSG);
  }

  /** Returns the Project Id or Project Number for the Firebase Project. */
  @Nullable
  String getProjectIdentifier() {
    return firebaseApp.getOptions().getProjectId();
  }

  /**
   * Returns the {@link FirebaseInstallations} initialized with the default {@link FirebaseApp}.
   *
   * @return a {@link FirebaseInstallations} instance
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

  /** API key used to identify your app to Google servers. */
  @Nullable
  String getApiKey() {
    return firebaseApp.getOptions().getApiKey();
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
    preConditionChecks();

    // Return cached fid if available.
    String fid = getCacheFid();
    if (fid != null) {
      return Tasks.forResult(fid);
    }

    Task<String> task = addGetIdListener();
    backgroundExecutor.execute(() -> doRegistrationOrRefresh(false));
    return task;
  }

  /**
   * Returns a valid authentication token for the Firebase installation. Generates a new token if
   * one doesn't exist, is expired, or is about to expire.
   *
   * <p>Should only be called if the Firebase installation is registered.
   *
   * @param forceRefresh Options to get an auth token either by force refreshing or not.
   */
  @NonNull
  @Override
  public Task<InstallationTokenResult> getToken(boolean forceRefresh) {
    preConditionChecks();
    Task<InstallationTokenResult> task = addGetAuthTokenListener();
    backgroundExecutor.execute(() -> doRegistrationOrRefresh(forceRefresh));
    return task;
  }

  /**
   * Call to delete this Firebase app installation from the Firebase backend. This call may cause
   * Firebase Cloud Messaging, Firebase Remote Config, Firebase Predictions, or Firebase In-App
   * Messaging to not function properly.
   */
  @NonNull
  @Override
  public Task<Void> delete() {
    return Tasks.call(backgroundExecutor, this::deleteFirebaseInstallationId);
  }

  /**
   * Register a callback {@link FidListener} to receive fid changes.
   *
   * @hide
   */
  @NonNull
  @Override
  public synchronized FidListenerHandle registerFidListener(@NonNull FidListener listener) {
    fidListeners.add(listener);
    return new FidListenerHandle() {
      @Override
      public void unregister() {
        synchronized (FirebaseInstallations.this) {
          fidListeners.remove(listener);
        }
      }
    };
  }

  private Task<String> addGetIdListener() {
    TaskCompletionSource<String> taskCompletionSource = new TaskCompletionSource<>();
    StateListener l = new GetIdListener(taskCompletionSource);
    addStateListeners(l);
    return taskCompletionSource.getTask();
  }

  private Task<InstallationTokenResult> addGetAuthTokenListener() {
    TaskCompletionSource<InstallationTokenResult> taskCompletionSource =
        new TaskCompletionSource<>();
    StateListener l = new GetAuthTokenListener(utils, taskCompletionSource);
    addStateListeners(l);
    return taskCompletionSource.getTask();
  }

  private void addStateListeners(StateListener l) {
    synchronized (lock) {
      listeners.add(l);
    }
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

  private void triggerOnException(Exception exception) {
    synchronized (lock) {
      Iterator<StateListener> it = listeners.iterator();
      while (it.hasNext()) {
        StateListener l = it.next();
        boolean doneListening = l.onException(exception);
        if (doneListening) {
          it.remove();
        }
      }
    }
  }

  private synchronized void updateCacheFid(String cachedFid) {
    this.cachedFid = cachedFid;
  }

  private synchronized String getCacheFid() {
    return cachedFid;
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
  private final void doRegistrationOrRefresh(boolean forceRefresh) {

    PersistedInstallationEntry prefs = getPrefsWithGeneratedIdMultiProcessSafe();

    // Since the caller wants to force an authtoken refresh remove the authtoken from the
    // prefs we are working with, so the following steps know a new token is required.
    if (forceRefresh) {
      prefs = prefs.withClearedAuthToken();
    }

    triggerOnStateReached(prefs);
    // Execute network calls (CreateInstallations or GenerateAuthToken) to the FIS Servers on
    // a separate executor i.e networkExecutor
    networkExecutor.execute(() -> doNetworkCallIfNecessary(forceRefresh));
  }

  private void doNetworkCallIfNecessary(boolean forceRefresh) {
    PersistedInstallationEntry prefs = getMultiProcessSafePrefs();
    // There are two possible cleanup steps to perform at this stage: the FID may need to
    // be registered with the server or the FID is registered but we need a fresh authtoken.
    // Registering will also result in a fresh authtoken. Do the appropriate step here.
    PersistedInstallationEntry updatedPrefs;
    try {
      if (prefs.isErrored() || prefs.isUnregistered()) {
        updatedPrefs = registerFidWithServer(prefs);
      } else if (forceRefresh || utils.isAuthTokenExpired(prefs)) {
        updatedPrefs = fetchAuthTokenFromServer(prefs);
      } else {
        // nothing more to do, get out now
        return;
      }
    } catch (FirebaseInstallationsException e) {
      triggerOnException(e);
      return;
    }

    // Store the prefs to persist the result of the previous step.
    insertOrUpdatePrefs(updatedPrefs);

    // Update FidListener if a fid has changed.
    updateFidListener(prefs, updatedPrefs);

    prefs = updatedPrefs;

    // Update cachedFID, if FID is successfully REGISTERED and persisted.
    if (prefs.isRegistered()) {
      updateCacheFid(prefs.getFirebaseInstallationId());
    }

    // Let the caller know about the result.
    if (prefs.isErrored()) {
      triggerOnException(new FirebaseInstallationsException(Status.BAD_CONFIG));
    } else if (prefs.isNotGenerated()) {
      // If there is no fid it means the call failed with an auth error. Simulate an
      // IOException so that the caller knows to try again.
      triggerOnException(new IOException(AUTH_ERROR_MSG));
    } else {
      triggerOnStateReached(prefs);
    }
  }

  private synchronized void updateFidListener(
      PersistedInstallationEntry prefs, PersistedInstallationEntry updatedPrefs) {
    if (fidListeners.size() != 0
        && !prefs.getFirebaseInstallationId().equals(updatedPrefs.getFirebaseInstallationId())) {
      // Update all the registered FidListener about fid changes.
      for (FidListener listener : fidListeners) {
        listener.onFidChanged(updatedPrefs.getFirebaseInstallationId());
      }
    }
  }

  /**
   * Inserting or Updating the prefs. This operation is made cross-process and cross-thread safe by
   * wrapping all the processing first in a java synchronization block and wrapping that in a
   * cross-process lock created using FileLocks.
   */
  private void insertOrUpdatePrefs(PersistedInstallationEntry prefs) {
    synchronized (lockGenerateFid) {
      CrossProcessLock lock =
          CrossProcessLock.acquire(firebaseApp.getApplicationContext(), LOCKFILE_NAME_GENERATE_FID);
      try {
        // Store the prefs to persist the result of the previous step.
        persistedInstallation.insertOrUpdatePersistedInstallationEntry(prefs);
      } finally {
        // It is possible that the lock acquisition failed, resulting in lock being null.
        // We handle this case by going on with our business even if the acquisition failed
        // but we need to be sure to only release if we got a lock.
        if (lock != null) {
          lock.releaseAndClose();
        }
      }
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
    synchronized (lockGenerateFid) {
      CrossProcessLock lock =
          CrossProcessLock.acquire(firebaseApp.getApplicationContext(), LOCKFILE_NAME_GENERATE_FID);
      try {
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

      } finally {
        // It is possible that the lock acquisition failed, resulting in lock being null.
        // We handle this case by going on with our business even if the acquisition failed
        // but we need to be sure to only release if we got a lock.
        if (lock != null) {
          lock.releaseAndClose();
        }
      }
    }
  }

  private String readExistingIidOrCreateFid(PersistedInstallationEntry prefs) {
    // Check if this firebase app is the default (first initialized) instance or is a chime app
    if ((!firebaseApp.getName().equals(CHIME_FIREBASE_APP_NAME) && !firebaseApp.isDefaultApp())
        || !prefs.shouldAttemptMigration()) {
      return fidGenerator.createRandomFid();
    }
    // For a default/chime firebase installation, read the existing iid from shared prefs
    String fid = iidStore.readIid();
    if (TextUtils.isEmpty(fid)) {
      fid = fidGenerator.createRandomFid();
    }
    return fid;
  }

  /** Registers the created Fid with FIS servers and update the persisted state. */
  private PersistedInstallationEntry registerFidWithServer(PersistedInstallationEntry prefs)
      throws FirebaseInstallationsException {

    // Note: Default value of instanceIdMigrationAuth: null
    String iidToken = null;

    if (prefs.getFirebaseInstallationId() != null
        && prefs.getFirebaseInstallationId().length() == 11) {
      // For a default firebase installation, read the stored star scoped iid token. This token
      // will be used for authenticating Instance-ID when migrating to FIS.
      iidToken = iidStore.readToken();
    }

    InstallationResponse response =
        serviceClient.createFirebaseInstallation(
            /*apiKey= */ getApiKey(),
            /*fid= */ prefs.getFirebaseInstallationId(),
            /*projectID= */ getProjectIdentifier(),
            /*appId= */ getApplicationId(),
            /* migration-header= */ iidToken);

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
        throw new FirebaseInstallationsException(
            "Firebase Installations Service is unavailable. Please try again later.",
            Status.UNAVAILABLE);
    }
  }

  /**
   * Calls the FIS servers to generate an auth token for this Firebase installation. Returns a
   * PersistedInstallationEntry with the new authtoken. The authtoken in the returned
   * PersistedInstallationEntry will be "expired" if the server refuses to generate an auth token
   * for the fid.
   */
  private PersistedInstallationEntry fetchAuthTokenFromServer(
      @NonNull PersistedInstallationEntry prefs) throws FirebaseInstallationsException {
    TokenResult tokenResult =
        serviceClient.generateAuthToken(
            /*apiKey= */ getApiKey(),
            /*fid= */ prefs.getFirebaseInstallationId(),
            /*projectID= */ getProjectIdentifier(),
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
        updateCacheFid(null);
        return prefs.withNoGeneratedFid();
      default:
        throw new FirebaseInstallationsException(
            "Firebase Installations Service is unavailable. Please try again later.",
            Status.UNAVAILABLE);
    }
  }

  /**
   * Deletes the firebase installation id of the {@link FirebaseApp} from FIS servers and local
   * storage.
   */
  private Void deleteFirebaseInstallationId() throws FirebaseInstallationsException {
    updateCacheFid(null);
    PersistedInstallationEntry entry = getMultiProcessSafePrefs();
    if (entry.isRegistered()) {
      // Call the FIS servers to delete this Firebase Installation Id.
      serviceClient.deleteFirebaseInstallation(
          /*apiKey= */ getApiKey(),
          /*fid= */ entry.getFirebaseInstallationId(),
          /*projectID= */ getProjectIdentifier(),
          /*refreshToken= */ entry.getRefreshToken());
    }
    insertOrUpdatePrefs(entry.withNoGeneratedFid());
    return null;
  }

  /**
   * Loads the persisted prefs. This operation is made cross-process and cross-thread safe by
   * wrapping all the processing first in a java synchronization block and wrapping that in a
   * cross-process lock created using FileLocks.
   *
   * @return a persisted prefs
   */
  private PersistedInstallationEntry getMultiProcessSafePrefs() {
    synchronized (lockGenerateFid) {
      CrossProcessLock lock =
          CrossProcessLock.acquire(firebaseApp.getApplicationContext(), LOCKFILE_NAME_GENERATE_FID);
      try {
        PersistedInstallationEntry prefs =
            persistedInstallation.readPersistedInstallationEntryValue();
        return prefs;

      } finally {
        // It is possible that the lock acquisition failed, resulting in lock being null.
        // We handle this case by going on with our business even if the acquisition failed
        // but we need to be sure to only release if we got a lock.
        if (lock != null) {
          lock.releaseAndClose();
        }
      }
    }
  }
}
