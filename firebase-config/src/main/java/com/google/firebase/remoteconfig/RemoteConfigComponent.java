// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.remoteconfig;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.annotation.KeepForSdk;
import com.google.android.gms.common.api.internal.BackgroundDetector;
import com.google.android.gms.common.util.Clock;
import com.google.android.gms.common.util.DefaultClock;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.abt.FirebaseABTesting;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.remoteconfig.internal.ConfigCacheClient;
import com.google.firebase.remoteconfig.internal.ConfigFetchHandler;
import com.google.firebase.remoteconfig.internal.ConfigFetchHttpClient;
import com.google.firebase.remoteconfig.internal.ConfigGetParameterHandler;
import com.google.firebase.remoteconfig.internal.ConfigRealtimeHandler;
import com.google.firebase.remoteconfig.internal.ConfigSharedPrefsClient;
import com.google.firebase.remoteconfig.internal.ConfigStorageClient;
import com.google.firebase.remoteconfig.internal.Personalization;
import com.google.firebase.remoteconfig.internal.rollouts.RolloutsStateFactory;
import com.google.firebase.remoteconfig.internal.rollouts.RolloutsStateSubscriptionsHandler;
import com.google.firebase.remoteconfig.interop.FirebaseRemoteConfigInterop;
import com.google.firebase.remoteconfig.interop.rollouts.RolloutsStateSubscriber;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Component for providing multiple Firebase Remote Config (FRC) instances. Firebase Android
 * Components uses this class to retrieve instances of FRC for dependency injection.
 *
 * <p>A unique FRC instance is returned for each {{@link FirebaseApp}, {@code namespace}}
 * combination.
 *
 * @hide
 */
@KeepForSdk
public class RemoteConfigComponent implements FirebaseRemoteConfigInterop {
  /** Name of the file where activated configs are stored. */
  public static final String ACTIVATE_FILE_NAME = "activate";

  /** Name of the file where fetched configs are stored. */
  public static final String FETCH_FILE_NAME = "fetch";

  /** Name of the file where defaults configs are stored. */
  public static final String DEFAULTS_FILE_NAME = "defaults";

  /** Timeout for the call to the Firebase Remote Config servers in second. */
  public static final long CONNECTION_TIMEOUT_IN_SECONDS = 60;

  private static final String FIREBASE_REMOTE_CONFIG_FILE_NAME_PREFIX = "frc";
  private static final String PREFERENCES_FILE_NAME = "settings";

  @VisibleForTesting public static final String DEFAULT_NAMESPACE = "firebase";

  private static final Clock DEFAULT_CLOCK = DefaultClock.getInstance();
  private static final Random DEFAULT_RANDOM = new Random();

  @GuardedBy("this")
  private final Map<String, FirebaseRemoteConfig> frcNamespaceInstances = new HashMap<>();

  private static final Map<String, FirebaseRemoteConfig> frcNamespaceInstancesStatic =
      new HashMap<>();

  private final Context context;
  private final ScheduledExecutorService executor;
  private final FirebaseApp firebaseApp;
  private final FirebaseInstallationsApi firebaseInstallations;
  private final FirebaseABTesting firebaseAbt;
  @Nullable private final Provider<AnalyticsConnector> analyticsConnector;

  private final String appId;

  @GuardedBy("this")
  private Map<String, String> customHeaders = new HashMap<>();

  /** Firebase Remote Config Component constructor. */
  RemoteConfigComponent(
      Context context,
      @Blocking ScheduledExecutorService executor,
      FirebaseApp firebaseApp,
      FirebaseInstallationsApi firebaseInstallations,
      FirebaseABTesting firebaseAbt,
      Provider<AnalyticsConnector> analyticsConnector) {
    this(
        context,
        executor,
        firebaseApp,
        firebaseInstallations,
        firebaseAbt,
        analyticsConnector,
        /* loadGetDefault= */ true);
  }

  /** Firebase Remote Config Component constructor for testing component logic. */
  @VisibleForTesting
  protected RemoteConfigComponent(
      Context context,
      ScheduledExecutorService executor,
      FirebaseApp firebaseApp,
      FirebaseInstallationsApi firebaseInstallations,
      FirebaseABTesting firebaseAbt,
      Provider<AnalyticsConnector> analyticsConnector,
      boolean loadGetDefault) {
    this.context = context;
    this.executor = executor;
    this.firebaseApp = firebaseApp;
    this.firebaseInstallations = firebaseInstallations;
    this.firebaseAbt = firebaseAbt;
    this.analyticsConnector = analyticsConnector;

    this.appId = firebaseApp.getOptions().getApplicationId();
    GlobalBackgroundListener.ensureBackgroundListenerIsRegistered(context);

    // When the component is first loaded, it will use a cached executor.
    // The getDefault call creates race conditions in tests, where the getDefault might be executing
    // while another test has already cleared the component but hasn't gotten a new one yet.
    if (loadGetDefault) {
      // Loads the default namespace's configs from disk on App startup.
      Tasks.call(executor, this::getDefault);
    }
  }

  /**
   * Returns the default Firebase Remote Config instance for this component's {@link FirebaseApp}.
   */
  FirebaseRemoteConfig getDefault() {
    return get(DEFAULT_NAMESPACE);
  }

  /**
   * Returns the Firebase Remote Config instance associated with the given {@code namespace} and
   * this component's {@link FirebaseApp}.
   *
   * @param namespace a 2P's namespace, or, for the 3P App, the default namespace.
   */
  @VisibleForTesting
  @KeepForSdk
  public synchronized FirebaseRemoteConfig get(String namespace) {
    ConfigCacheClient fetchedCacheClient = getCacheClient(namespace, FETCH_FILE_NAME);
    ConfigCacheClient activatedCacheClient = getCacheClient(namespace, ACTIVATE_FILE_NAME);
    ConfigCacheClient defaultsCacheClient = getCacheClient(namespace, DEFAULTS_FILE_NAME);
    ConfigSharedPrefsClient sharedPrefsClient = getSharedPrefsClient(context, appId, namespace);

    ConfigGetParameterHandler getHandler = getGetHandler(activatedCacheClient, defaultsCacheClient);
    Personalization personalization =
        getPersonalization(firebaseApp, namespace, analyticsConnector);
    if (personalization != null) {
      getHandler.addListener(personalization::logArmActive);
    }

    RolloutsStateSubscriptionsHandler rolloutsStateSubscriptionsHandler =
        getRolloutsStateSubscriptionsHandler(activatedCacheClient, defaultsCacheClient);

    return get(
        firebaseApp,
        namespace,
        firebaseInstallations,
        firebaseAbt,
        executor,
        fetchedCacheClient,
        activatedCacheClient,
        defaultsCacheClient,
        getFetchHandler(namespace, fetchedCacheClient, sharedPrefsClient),
        getHandler,
        sharedPrefsClient,
        rolloutsStateSubscriptionsHandler);
  }

  // TODO: Pass ConfigRealtimeHandler so it's mock-able in tests.
  @VisibleForTesting
  synchronized FirebaseRemoteConfig get(
      FirebaseApp firebaseApp,
      String namespace,
      FirebaseInstallationsApi firebaseInstallations,
      FirebaseABTesting firebaseAbt,
      Executor executor,
      ConfigCacheClient fetchedClient,
      ConfigCacheClient activatedClient,
      ConfigCacheClient defaultsClient,
      ConfigFetchHandler fetchHandler,
      ConfigGetParameterHandler getHandler,
      ConfigSharedPrefsClient sharedPrefsClient,
      RolloutsStateSubscriptionsHandler rolloutsStateSubscriptionsHandler) {
    if (!frcNamespaceInstances.containsKey(namespace)) {
      FirebaseRemoteConfig in =
          new FirebaseRemoteConfig(
              context,
              firebaseApp,
              firebaseInstallations,
              isAbtSupported(firebaseApp, namespace) ? firebaseAbt : null,
              executor,
              fetchedClient,
              activatedClient,
              defaultsClient,
              fetchHandler,
              getHandler,
              sharedPrefsClient,
              getRealtime(
                  firebaseApp,
                  firebaseInstallations,
                  fetchHandler,
                  activatedClient,
                  context,
                  namespace,
                  sharedPrefsClient),
              rolloutsStateSubscriptionsHandler);
      in.startLoadingConfigsFromDisk();
      frcNamespaceInstances.put(namespace, in);
      frcNamespaceInstancesStatic.put(namespace, in);
    }
    return frcNamespaceInstances.get(namespace);
  }

  @VisibleForTesting
  public synchronized void setCustomHeaders(Map<String, String> customHeaders) {
    this.customHeaders = customHeaders;
  }

  private ConfigCacheClient getCacheClient(String namespace, String configStoreType) {
    String fileName =
        String.format(
            "%s_%s_%s_%s.json",
            FIREBASE_REMOTE_CONFIG_FILE_NAME_PREFIX, appId, namespace, configStoreType);
    return ConfigCacheClient.getInstance(
        executor, ConfigStorageClient.getInstance(context, fileName));
  }

  @VisibleForTesting
  ConfigFetchHttpClient getFrcBackendApiClient(
      String apiKey, String namespace, ConfigSharedPrefsClient sharedPrefsClient) {
    String appId = firebaseApp.getOptions().getApplicationId();
    return new ConfigFetchHttpClient(
        context,
        appId,
        apiKey,
        namespace,
        /* connectTimeoutInSeconds= */ sharedPrefsClient.getFetchTimeoutInSeconds(),
        /* readTimeoutInSeconds= */ sharedPrefsClient.getFetchTimeoutInSeconds());
  }

  @VisibleForTesting
  synchronized ConfigFetchHandler getFetchHandler(
      String namespace,
      ConfigCacheClient fetchedCacheClient,
      ConfigSharedPrefsClient sharedPrefsClient) {
    return new ConfigFetchHandler(
        firebaseInstallations,
        isPrimaryApp(firebaseApp) ? analyticsConnector : () -> null,
        executor,
        DEFAULT_CLOCK,
        DEFAULT_RANDOM,
        fetchedCacheClient,
        getFrcBackendApiClient(firebaseApp.getOptions().getApiKey(), namespace, sharedPrefsClient),
        sharedPrefsClient,
        this.customHeaders);
  }

  synchronized ConfigRealtimeHandler getRealtime(
      FirebaseApp firebaseApp,
      FirebaseInstallationsApi firebaseInstallations,
      ConfigFetchHandler configFetchHandler,
      ConfigCacheClient activatedCacheClient,
      Context context,
      String namespace,
      ConfigSharedPrefsClient sharedPrefsClient) {
    return new ConfigRealtimeHandler(
        firebaseApp,
        firebaseInstallations,
        configFetchHandler,
        activatedCacheClient,
        context,
        namespace,
        sharedPrefsClient,
        executor);
  }

  private ConfigGetParameterHandler getGetHandler(
      ConfigCacheClient activatedCacheClient, ConfigCacheClient defaultsCacheClient) {
    return new ConfigGetParameterHandler(executor, activatedCacheClient, defaultsCacheClient);
  }

  @VisibleForTesting
  static ConfigSharedPrefsClient getSharedPrefsClient(
      Context context, String appId, String namespace) {
    String fileName =
        String.format(
            "%s_%s_%s_%s",
            FIREBASE_REMOTE_CONFIG_FILE_NAME_PREFIX, appId, namespace, PREFERENCES_FILE_NAME);
    SharedPreferences preferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE);
    return new ConfigSharedPrefsClient(preferences);
  }

  @Nullable
  private static Personalization getPersonalization(
      FirebaseApp firebaseApp, String namespace, Provider<AnalyticsConnector> analyticsConnector) {
    if (isPrimaryApp(firebaseApp) && namespace.equals(DEFAULT_NAMESPACE)) {
      return new Personalization(analyticsConnector);
    }
    return null;
  }

  private RolloutsStateSubscriptionsHandler getRolloutsStateSubscriptionsHandler(
      ConfigCacheClient activatedConfigsCache, ConfigCacheClient defaultConfigsCache) {
    RolloutsStateFactory rolloutsStateFactory =
        RolloutsStateFactory.create(activatedConfigsCache, defaultConfigsCache);

    return new RolloutsStateSubscriptionsHandler(
        activatedConfigsCache, rolloutsStateFactory, executor);
  }

  /**
   * Checks if ABT can be used in the given {@code firebaseApp} and {@code namespace}.
   *
   * <p>The Firebase A/B Testing SDK uses Analytics to update experiments, so, since Analytics does
   * not work outside the primary {@link FirebaseApp}, ABT should not be used outside the primary
   * App.
   *
   * <p>The ABT product is only available to 3P developers and does not work for other Firebase
   * SDKs, so ABT should not be used outside the 3P namespace.
   *
   * @return True if {@code firebaseApp} is the main {@link FirebaseApp} and {@code namespace} is
   *     the 3P namespace.
   */
  private static boolean isAbtSupported(FirebaseApp firebaseApp, String namespace) {
    return namespace.equals(DEFAULT_NAMESPACE) && isPrimaryApp(firebaseApp);
  }

  /**
   * Returns true if {@code firebaseApp} is the main {@link FirebaseApp}.
   *
   * <p>Analytics and, by extension, Firebase A/B Testing only support the primary {@link
   * FirebaseApp}.
   */
  private static boolean isPrimaryApp(FirebaseApp firebaseApp) {
    return firebaseApp.getName().equals(FirebaseApp.DEFAULT_APP_NAME);
  }

  private static synchronized void notifyRCInstances(boolean isInBackground) {
    for (FirebaseRemoteConfig frc : frcNamespaceInstancesStatic.values()) {
      frc.setConfigUpdateBackgroundState(isInBackground);
    }
  }

  /**
   * Register a {@link RolloutsStateSubscriber} {@code subscriber} in for the given Remote Config
   * {@code namespace}.
   *
   * <p>This implements {@link FirebaseRemoteConfigInterop} for use by other Firebase SDKs. See
   * {@link FirebaseRemoteConfigInterop#registerRolloutsStateSubscriber(String,
   * RolloutsStateSubscriber)} for more details.
   */
  @Override
  public void registerRolloutsStateSubscriber(
      @NonNull String namespace, @NonNull RolloutsStateSubscriber subscriber) {
    get(namespace)
        .getRolloutsStateSubscriptionsHandler()
        .registerRolloutsStateSubscriber(subscriber);
  }

  private static class GlobalBackgroundListener
      implements BackgroundDetector.BackgroundStateChangeListener {
    private static final AtomicReference<GlobalBackgroundListener> INSTANCE =
        new AtomicReference<>();

    private static void ensureBackgroundListenerIsRegistered(Context context) {
      Application application = (Application) context.getApplicationContext();
      if (INSTANCE.get() == null) {
        GlobalBackgroundListener globalBackgroundListener = new GlobalBackgroundListener();
        if (INSTANCE.compareAndSet(null, globalBackgroundListener)) {
          BackgroundDetector.initialize(application);
          BackgroundDetector.getInstance().addListener(globalBackgroundListener);
        }
      }
    }

    @Override
    public void onBackgroundStateChanged(boolean isInBackground) {
      notifyRCInstances(isInBackground);
    }
  }
}
