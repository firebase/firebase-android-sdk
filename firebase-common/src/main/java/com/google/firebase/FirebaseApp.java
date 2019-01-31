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

package com.google.firebase;

import static com.google.android.gms.common.util.Base64Utils.encodeUrlSafeNoPadding;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.VisibleForTesting;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.ArrayMap;
import android.text.TextUtils;
import android.util.Log;
import com.google.android.gms.common.annotation.KeepForSdk;
import com.google.android.gms.common.api.internal.BackgroundDetector;
import com.google.android.gms.common.internal.Objects;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.common.util.PlatformVersion;
import com.google.android.gms.common.util.ProcessUtils;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.annotations.PublicApi;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentDiscovery;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.ComponentRuntime;
import com.google.firebase.events.Event;
import com.google.firebase.events.Publisher;
import com.google.firebase.internal.DefaultIdTokenListenersCountChangedListener;
import com.google.firebase.internal.InternalTokenProvider;
import com.google.firebase.internal.InternalTokenResult;
import com.google.firebase.platforminfo.DefaultUserAgentPublisher;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.concurrent.GuardedBy;

/**
 * The entry point of Firebase SDKs. It holds common configuration and state for Firebase APIs. Most
 * applications don't need to directly interact with FirebaseApp.
 *
 * <p>For a vast majority of apps, {@link com.google.firebase.provider.FirebaseInitProvider} will
 * handle the initialization of Firebase for the default project that it's configured to work with,
 * via the data contained in the app's <code>google-services.json</code> file. This <code>
 * ContentProvider</code> is merged into the app's manifest by default when building with Gradle,
 * and it runs automatically at app launch. <strong>No additional lines of code are needed in this
 * case.</strong>
 *
 * <p>In the event that an app requires access to another Firebase project <strong>in addition
 * to</strong> the default project, {@link FirebaseApp#initializeApp(Context, FirebaseOptions,
 * String)} must be used to create that relationship programmatically. The name parameter must be
 * unique. To connect to the resources exposed by that project, use the {@link FirebaseApp} object
 * returned by {@link FirebaseApp#getInstance(String)}, passing it the same name used with <code>
 * initializeApp</code>. This object must be passed to the static accessor of the feature that
 * provides the resource. For example, {@link
 * com.google.firebase.storage.FirebaseStorage#getInstance(FirebaseApp)} is used to access the
 * storage bucket provided by the additional project, whereas {@link
 * com.google.firebase.storage.FirebaseStorage#getInstance()} is used to access the default project.
 *
 * <p>Any <code>FirebaseApp</code> initialization must occur only in the main process of the app.
 * Use of Firebase in processes other than the main process is not supported and will likely cause
 * problems related to resource contention.
 */
@PublicApi
public class FirebaseApp {

  private static final String LOG_TAG = "FirebaseApp";

  public static final String DEFAULT_APP_NAME = "[DEFAULT]";

  private static final String FIREBASE_APP_PREFS = "com.google.firebase.common.prefs:";

  @VisibleForTesting
  static final String DATA_COLLECTION_DEFAULT_ENABLED = "firebase_data_collection_default_enabled";

  private static final String MEASUREMENT_CLASSNAME =
      "com.google.android.gms.measurement.AppMeasurement";
  private static final String AUTH_CLASSNAME = "com.google.firebase.auth.FirebaseAuth";
  private static final String IID_CLASSNAME = "com.google.firebase.iid.FirebaseInstanceId";
  private static final String CRASH_CLASSNAME = "com.google.firebase.crash.FirebaseCrash";

  /**
   * Firebase APIs in order of their initialization. To be initialized for each FirebaseApp
   * instance.
   */
  private static final List<String> API_INITIALIZERS = Arrays.asList(AUTH_CLASSNAME, IID_CLASSNAME);

  /**
   * Default Firebase APIs requiring a FirebaseApp in order of their initialization.
   *
   * <p>These APIs are initialized for the default app.
   */
  private static final List<String> DEFAULT_APP_API_INITITALIZERS =
      Collections.singletonList(CRASH_CLASSNAME);

  /**
   * Default Firebase APIs requiring a Context in order of their initialization.
   *
   * <p>These APIs are initialized for the default app.
   */
  private static final List<String> DEFAULT_CONTEXT_API_INITITALIZERS =
      Arrays.asList(MEASUREMENT_CLASSNAME);

  /** Firebase APIs that are initialized in direct boot mode. */
  private static final List<String> DIRECT_BOOT_COMPATIBLE_API_INITIALIZERS = Arrays.asList();

  /** Set of APIs that are part of Firebase Core and should always be initialized. */
  private static final Set<String> CORE_CLASSES = Collections.emptySet();

  private static final Object LOCK = new Object();

  private static final Executor UI_EXECUTOR = new UiExecutor();

  /** A map of (name, FirebaseApp) instances. */
  @GuardedBy("LOCK")
  static final Map<String, FirebaseApp> INSTANCES = new ArrayMap<>();

  private static final String FIREBASE_COMMON = "firebase-common";

  private final Context applicationContext;
  private final String name;
  private final FirebaseOptions options;
  private final ComponentRuntime componentRuntime;
  private final SharedPreferences sharedPreferences;
  private final Publisher publisher;

  // Default disabled. We released Firebase publicly without this feature, so making it default
  // enabled is a backwards incompatible change.
  private final AtomicBoolean automaticResourceManagementEnabled = new AtomicBoolean(false);
  private final AtomicBoolean deleted = new AtomicBoolean();
  private final AtomicBoolean dataCollectionDefaultEnabled;

  private final List<IdTokenListener> idTokenListeners = new CopyOnWriteArrayList<>();
  private final List<BackgroundStateChangeListener> backgroundStateChangeListeners =
      new CopyOnWriteArrayList<>();
  private final List<FirebaseAppLifecycleListener> lifecycleListeners =
      new CopyOnWriteArrayList<>();

  private InternalTokenProvider tokenProvider;
  private IdTokenListenersCountChangedListener idTokenListenersCountChangedListener;

  /** Returns the application {@link Context}. */
  @NonNull
  @PublicApi
  public Context getApplicationContext() {
    checkNotDeleted();
    return applicationContext;
  }

  /** Returns the unique name of this app. */
  @NonNull
  @PublicApi
  public String getName() {
    checkNotDeleted();
    return name;
  }

  /** Returns the specified {@link FirebaseOptions}. */
  @NonNull
  @PublicApi
  public FirebaseOptions getOptions() {
    checkNotDeleted();
    return options;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof FirebaseApp)) {
      return false;
    }
    return name.equals(((FirebaseApp) o).getName());
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this).add("name", name).add("options", options).toString();
  }

  /** Returns a mutable list of all FirebaseApps. */
  @PublicApi
  public static List<FirebaseApp> getApps(Context context) {
    synchronized (LOCK) {
      return new ArrayList<>(INSTANCES.values());
    }
  }

  /**
   * Returns the default (first initialized) instance of the {@link FirebaseApp}.
   *
   * @throws IllegalStateException if the default app was not initialized.
   */
  @Nullable
  @PublicApi
  public static FirebaseApp getInstance() {
    synchronized (LOCK) {
      FirebaseApp defaultApp = INSTANCES.get(DEFAULT_APP_NAME);
      if (defaultApp == null) {
        throw new IllegalStateException(
            "Default FirebaseApp is not initialized in this "
                + "process "
                + ProcessUtils.getMyProcessName()
                + ". Make sure to call "
                + "FirebaseApp.initializeApp(Context) first.");
      }
      return defaultApp;
    }
  }

  /**
   * Returns the instance identified by the unique name, or throws if it does not exist.
   *
   * @param name represents the name of the {@link FirebaseApp} instance.
   * @throws IllegalStateException if the {@link FirebaseApp} was not initialized, either via {@link
   *     #initializeApp(Context, FirebaseOptions, String)}.
   */
  @PublicApi
  public static FirebaseApp getInstance(@NonNull String name) {
    synchronized (LOCK) {
      FirebaseApp firebaseApp = INSTANCES.get(normalize(name));
      if (firebaseApp != null) {
        return firebaseApp;
      }

      List<String> availableAppNames = getAllAppNames();
      String availableAppNamesMessage;
      if (availableAppNames.isEmpty()) {
        availableAppNamesMessage = "";
      } else {
        availableAppNamesMessage =
            "Available app names: " + TextUtils.join(", ", availableAppNames);
      }
      String errorMessage =
          String.format(
              "FirebaseApp with name %s doesn't exist. %s", name, availableAppNamesMessage);
      throw new IllegalStateException(errorMessage);
    }
  }

  /**
   * Initializes the default FirebaseApp instance using string resource values - populated from
   * google-services.json. It also initializes Firebase Analytics for the current process.
   *
   * <p>This method is called at app startup time by {@link
   * com.google.firebase.provider.FirebaseInitProvider}. Call this method before any Firebase APIs
   * in components outside the main process.
   *
   * <p>The {@link FirebaseOptions} values used by the default app instance are read from string
   * resources.
   *
   * <p>
   *
   * @return the default FirebaseApp, if either it has been initialized previously, or Firebase API
   *     keys are present in string resources. Returns null otherwise.
   */
  @Nullable
  @PublicApi
  public static FirebaseApp initializeApp(Context context) {
    synchronized (LOCK) {
      if (INSTANCES.containsKey(DEFAULT_APP_NAME)) {
        return getInstance();
      }
      FirebaseOptions firebaseOptions = FirebaseOptions.fromResource(context);
      if (firebaseOptions == null) {
        Log.d(
            LOG_TAG,
            "Default FirebaseApp failed to initialize because no default "
                + "options were found. This usually means that com.google.gms:google-services was "
                + "not applied to your gradle project.");
        return null;
      }
      return initializeApp(context, firebaseOptions);
    }
  }

  /**
   * Initializes the default {@link FirebaseApp} instance. Same as {@link #initializeApp(Context,
   * FirebaseOptions, String)}, but it uses {@link #DEFAULT_APP_NAME} as name.
   *
   * <p>It's only required to call this to initialize Firebase if it's <strong>not possible</strong>
   * to do so automatically in {@link com.google.firebase.provider.FirebaseInitProvider}. Automatic
   * initialization that way is the expected situation.
   */
  @PublicApi
  public static FirebaseApp initializeApp(Context context, FirebaseOptions options) {
    return initializeApp(context, options, DEFAULT_APP_NAME);
  }

  /**
   * A factory method to initialize a {@link FirebaseApp}.
   *
   * @param context represents the {@link Context}
   * @param options represents the global {@link FirebaseOptions}
   * @param name unique name for the app. It is an error to initialize an app with an already
   *     existing name. Starting and ending whitespace characters in the name are ignored (trimmed).
   * @throws IllegalStateException if an app with the same name has already been initialized.
   * @return an instance of {@link FirebaseApp}
   */
  @PublicApi
  public static FirebaseApp initializeApp(Context context, FirebaseOptions options, String name) {
    GlobalBackgroundStateListener.ensureBackgroundStateListenerRegistered(context);
    String normalizedName = normalize(name);
    final FirebaseApp firebaseApp;
    Context applicationContext;
    if (context.getApplicationContext() == null) {
      // In shared processes' content providers getApplicationContext() can return null.
      applicationContext = context;
    } else {
      applicationContext = context.getApplicationContext();
    }
    synchronized (LOCK) {
      Preconditions.checkState(
          !INSTANCES.containsKey(normalizedName),
          "FirebaseApp name " + normalizedName + " already exists!");

      Preconditions.checkNotNull(applicationContext, "Application context cannot be null.");
      firebaseApp = new FirebaseApp(applicationContext, normalizedName, options);
      INSTANCES.put(normalizedName, firebaseApp);
    }

    firebaseApp.initializeAllApis();
    return firebaseApp;
  }

  /** @hide */
  @Deprecated
  @KeepForSdk
  public void setTokenProvider(@NonNull InternalTokenProvider tokenProvider) {
    this.tokenProvider = Preconditions.checkNotNull(tokenProvider);
  }

  /** @hide */
  @Deprecated
  @KeepForSdk
  public void setIdTokenListenersCountChangedListener(
      @NonNull IdTokenListenersCountChangedListener listener) {
    idTokenListenersCountChangedListener = Preconditions.checkNotNull(listener);
    // Immediately trigger so that the listenerlistener can properly decide if it needs to
    // start out as active.
    idTokenListenersCountChangedListener.onListenerCountChanged(idTokenListeners.size());
  }

  /**
   * Fetch a valid STS Token.
   *
   * @param forceRefresh force refreshes the token. Should only be set to <code>true</code> if the
   *     token is invalidated out of band.
   * @return a {@link Task}
   * @deprecated use {@link
   *     com.google.firebase.auth.internal.InternalAuthProvider#getToken(boolean)} from
   *     firebase-auth-interop instead.
   * @hide
   */
  @Deprecated
  @KeepForSdk
  public Task<GetTokenResult> getToken(boolean forceRefresh) {
    checkNotDeleted();

    if (tokenProvider == null) {
      return Tasks.forException(
          new FirebaseApiNotAvailableException(
              "firebase-auth is not " + "linked, please fall back to unauthenticated mode."));
    } else {
      return tokenProvider.getAccessToken(forceRefresh);
    }
  }

  /**
   * Fetch the UID of the currently logged-in user.
   *
   * @deprecated use {@link com.google.firebase.auth.internal.InternalAuthProvider#getUid()} from
   *     firebase-auth-interop instead.
   * @hide
   */
  @Deprecated
  @Nullable
  @KeepForSdk
  public String getUid() throws FirebaseApiNotAvailableException {
    checkNotDeleted();
    if (tokenProvider == null) {
      throw new FirebaseApiNotAvailableException(
          "firebase-auth is not " + "linked, please fall back to unauthenticated mode.");
    }
    return tokenProvider.getUid();
  }

  /**
   * Deletes the {@link FirebaseApp} and all its data. All calls to this {@link FirebaseApp}
   * instance will throw once it has been called.
   *
   * <p>A no-op if delete was called before.
   *
   * @hide
   */
  @PublicApi
  public void delete() {
    boolean valueChanged = deleted.compareAndSet(false /* expected */, true);
    if (!valueChanged) {
      return;
    }

    synchronized (LOCK) {
      INSTANCES.remove(this.name);
    }

    notifyOnAppDeleted();
  }

  /**
   * Returns an instance of the requested component.
   *
   * @hide
   */
  @KeepForSdk
  public <T> T get(Class<T> anInterface) {
    checkNotDeleted();
    return componentRuntime.get(anInterface);
  }

  /**
   * If set to true it indicates that Firebase should close database connections automatically when
   * the app is in the background. Disabled by default.
   */
  @PublicApi
  public void setAutomaticResourceManagementEnabled(boolean enabled) {
    checkNotDeleted();
    boolean updated =
        automaticResourceManagementEnabled.compareAndSet(
            !enabled /* expect */, enabled /* update */);
    if (updated) {
      boolean inBackground = BackgroundDetector.getInstance().isInBackground();
      if (enabled && inBackground) {
        // Automatic resource management has been enabled while the app is in the
        // background, notify the listeners of the app being in the background.
        notifyBackgroundStateChangeListeners(true);
      } else if (!enabled && inBackground) {
        // Automatic resource management has been disabled while the app is in the
        // background, act as if we were in the foreground.
        notifyBackgroundStateChangeListeners(false);
      }
    }
  }

  /**
   * Determine whether automatic data collection is enabled or disabled by default in all SDKs.
   *
   * <p>Note: this value is respected by all SDKs unless overridden by the developer via SDK
   * specific mechanisms.
   *
   * @return true if automatic data collection is enabled by default and false otherwise
   * @hide
   */
  @KeepForSdk
  public boolean isDataCollectionDefaultEnabled() {
    checkNotDeleted();
    return dataCollectionDefaultEnabled.get();
  }

  /**
   * Enable or disable automatic data collection across all SDKs.
   *
   * <p>Note: this value is respected by all SDKs unless overridden by the developer via SDK
   * specific mechanisms.
   *
   * @hide
   */
  @KeepForSdk
  public void setDataCollectionDefaultEnabled(boolean enabled) {
    checkNotDeleted();
    if (dataCollectionDefaultEnabled.compareAndSet(!enabled, enabled)) {
      sharedPreferences.edit().putBoolean(DATA_COLLECTION_DEFAULT_ENABLED, enabled).commit();

      publisher.publish(
          new Event<>(DataCollectionDefaultChange.class, new DataCollectionDefaultChange(enabled)));
    }
  }

  /**
   * Default constructor.
   *
   * @hide
   */
  protected FirebaseApp(Context applicationContext, String name, FirebaseOptions options) {
    this.applicationContext = Preconditions.checkNotNull(applicationContext);
    this.name = Preconditions.checkNotEmpty(name);
    this.options = Preconditions.checkNotNull(options);
    idTokenListenersCountChangedListener = new DefaultIdTokenListenersCountChangedListener();

    sharedPreferences =
        applicationContext.getSharedPreferences(getSharedPrefsName(name), Context.MODE_PRIVATE);
    dataCollectionDefaultEnabled = new AtomicBoolean(readAutoDataCollectionEnabled());

    List<ComponentRegistrar> registrars =
        ComponentDiscovery.forContext(applicationContext).discover();
    componentRuntime =
        new ComponentRuntime(
            UI_EXECUTOR,
            registrars,
            Component.of(applicationContext, Context.class),
            Component.of(this, FirebaseApp.class),
            Component.of(options, FirebaseOptions.class),
            LibraryVersionComponent.create(FIREBASE_COMMON, BuildConfig.VERSION_NAME),
            DefaultUserAgentPublisher.component());
    publisher = componentRuntime.get(Publisher.class);
  }

  private static String getSharedPrefsName(String appName) {
    return FIREBASE_APP_PREFS + appName;
  }

  private boolean readAutoDataCollectionEnabled() {
    if (sharedPreferences.contains(DATA_COLLECTION_DEFAULT_ENABLED)) {
      return sharedPreferences.getBoolean(DATA_COLLECTION_DEFAULT_ENABLED, true);
    }
    try {
      PackageManager packageManager = applicationContext.getPackageManager();
      if (packageManager != null) {
        ApplicationInfo applicationInfo =
            packageManager.getApplicationInfo(
                applicationContext.getPackageName(), PackageManager.GET_META_DATA);
        if (applicationInfo != null
            && applicationInfo.metaData != null
            && applicationInfo.metaData.containsKey(DATA_COLLECTION_DEFAULT_ENABLED)) {
          return applicationInfo.metaData.getBoolean(DATA_COLLECTION_DEFAULT_ENABLED);
        }
      }
    } catch (PackageManager.NameNotFoundException e) {
      // This shouldn't happen since it's this app's package, but fall through to default if so.
    }
    return true;
  }

  private void checkNotDeleted() {
    Preconditions.checkState(!deleted.get(), "FirebaseApp was deleted");
  }

  /** @hide */
  @Deprecated
  @KeepForSdk
  public List<IdTokenListener> getListeners() {
    checkNotDeleted();
    return idTokenListeners;
  }

  /** @hide */
  @KeepForSdk
  @VisibleForTesting
  public boolean isDefaultApp() {
    return DEFAULT_APP_NAME.equals(getName());
  }

  /** @hide */
  @Deprecated
  @KeepForSdk
  @UiThread
  public void notifyIdTokenListeners(@NonNull InternalTokenResult tokenResult) {
    Log.d(LOG_TAG, "Notifying auth state listeners.");
    int size = 0;
    for (IdTokenListener listener : idTokenListeners) {
      listener.onIdTokenChanged(tokenResult);
      size++;
    }
    Log.d(LOG_TAG, String.format("Notified %d auth state listeners.", size));
  }

  private void notifyBackgroundStateChangeListeners(boolean background) {
    Log.d(LOG_TAG, "Notifying background state change listeners.");
    for (BackgroundStateChangeListener listener : backgroundStateChangeListeners) {
      listener.onBackgroundStateChanged(background);
    }
  }

  /**
   * Adds a {@link com.google.firebase.FirebaseApp.IdTokenListener} to the list of interested
   * listeners.
   *
   * @param listener represents the {@link com.google.firebase.FirebaseApp.IdTokenListener} that
   *     needs to be notified when we have changes in user state.
   * @deprecated use {@link
   *     com.google.firebase.auth.internal.InternalAuthProvider#addIdTokenListener(IdTokenListener)}
   *     from firebase-auth-interop instead.
   * @hide
   */
  @Deprecated
  @KeepForSdk
  public void addIdTokenListener(@NonNull IdTokenListener listener) {
    checkNotDeleted();
    Preconditions.checkNotNull(listener);
    idTokenListeners.add(listener);
    idTokenListenersCountChangedListener.onListenerCountChanged(idTokenListeners.size());
  }

  /**
   * Removes a {@link com.google.firebase.FirebaseApp.IdTokenListener} from the list of interested
   * listeners.
   *
   * @param listenerToRemove represents the instance of {@link
   *     com.google.firebase.FirebaseApp.IdTokenListener} to be removed.
   * @deprecated use {@link
   *     com.google.firebase.auth.internal.InternalAuthProvider#removeIdTokenListener(IdTokenListener)}
   *     from firebase-auth-interop instead.
   * @hide
   */
  @Deprecated
  @KeepForSdk
  public void removeIdTokenListener(@NonNull IdTokenListener listenerToRemove) {
    checkNotDeleted();
    Preconditions.checkNotNull(listenerToRemove);
    idTokenListeners.remove(listenerToRemove);
    idTokenListenersCountChangedListener.onListenerCountChanged(idTokenListeners.size());
  }

  /**
   * Registers a background state change listener. Make sure to call {@link
   * #removeBackgroundStateChangeListener(BackgroundStateChangeListener)} as appropriate to avoid
   * memory leaks.
   *
   * <p>If automatic resource management is enabled and the app is in the background a callback is
   * triggered immediately.
   *
   * @see BackgroundStateChangeListener
   * @hide
   */
  @KeepForSdk
  public void addBackgroundStateChangeListener(BackgroundStateChangeListener listener) {
    checkNotDeleted();
    if (automaticResourceManagementEnabled.get()
        && BackgroundDetector.getInstance().isInBackground()) {
      listener.onBackgroundStateChanged(true /* isInBackground */);
    }
    backgroundStateChangeListeners.add(listener);
  }

  /**
   * Unregisters the background state change listener.
   *
   * @hide
   */
  @KeepForSdk
  public void removeBackgroundStateChangeListener(BackgroundStateChangeListener listener) {
    checkNotDeleted();
    backgroundStateChangeListeners.remove(listener);
  }

  /**
   * Use this key to store data per FirebaseApp.
   *
   * @hide
   */
  @KeepForSdk
  public String getPersistenceKey() {
    return encodeUrlSafeNoPadding(getName().getBytes(Charset.defaultCharset()))
        + "+"
        + encodeUrlSafeNoPadding(
            getOptions().getApplicationId().getBytes(Charset.defaultCharset()));
  }

  /**
   * If an API has locally stored data it must register lifecycle listeners at initialization time.
   *
   * @hide
   */
  // TODO: make sure that all APIs that are interested in these events are
  // initialized using reflection when an app is deleted (for v5).
  @KeepForSdk
  public void addLifecycleEventListener(@NonNull FirebaseAppLifecycleListener listener) {
    checkNotDeleted();
    Preconditions.checkNotNull(listener);
    lifecycleListeners.add(listener);
  }

  /** @hide */
  @KeepForSdk
  public void removeLifecycleEventListener(@NonNull FirebaseAppLifecycleListener listener) {
    checkNotDeleted();
    Preconditions.checkNotNull(listener);
    lifecycleListeners.remove(listener);
  }

  /**
   * Notifies all listeners with the name and options of the deleted {@link FirebaseApp} instance.
   */
  private void notifyOnAppDeleted() {
    for (FirebaseAppLifecycleListener listener : lifecycleListeners) {
      listener.onDeleted(name, options);
    }
  }

  /** @hide */
  @VisibleForTesting
  public static void clearInstancesForTest() {
    // TODO: also delete, once functionality is implemented.
    synchronized (LOCK) {
      INSTANCES.clear();
    }
  }

  /**
   * Returns persistence key. Exists to support getting {@link FirebaseApp} persistence key after
   * the app has been deleted.
   *
   * @hide
   */
  @KeepForSdk
  public static String getPersistenceKey(String name, FirebaseOptions options) {
    return encodeUrlSafeNoPadding(name.getBytes(Charset.defaultCharset()))
        + "+"
        + encodeUrlSafeNoPadding(options.getApplicationId().getBytes(Charset.defaultCharset()));
  }

  private static List<String> getAllAppNames() {
    List<String> allAppNames = new ArrayList<>();
    synchronized (LOCK) {
      for (FirebaseApp app : INSTANCES.values()) {
        allAppNames.add(app.getName());
      }
    }
    Collections.sort(allAppNames);
    return allAppNames;
  }

  /** Initializes all appropriate APIs for this instance. */
  private void initializeAllApis() {
    boolean isDeviceProtectedStorage = ContextCompat.isDeviceProtectedStorage(applicationContext);
    if (isDeviceProtectedStorage) {
      // Ensure that all APIs are initialized once the user unlocks the phone.
      UserUnlockReceiver.ensureReceiverRegistered(applicationContext);
    } else {
      componentRuntime.initializeEagerComponents(isDefaultApp());
    }
    initializeApis(FirebaseApp.class, this, API_INITIALIZERS, isDeviceProtectedStorage);
    if (isDefaultApp()) {
      initializeApis(
          FirebaseApp.class, this, DEFAULT_APP_API_INITITALIZERS, isDeviceProtectedStorage);
      initializeApis(
          Context.class,
          applicationContext,
          DEFAULT_CONTEXT_API_INITITALIZERS,
          isDeviceProtectedStorage);
    }
  }

  /**
   * Calls getInstance(FirebaseApp) API entry points using reflection.
   *
   * @param <T> Type parameter for the initializer method. Either {@link Context} or {@link
   *     FirebaseApp}.
   */
  private <T> void initializeApis(
      Class<T> parameterClass,
      T parameter,
      Iterable<String> apiInitClasses,
      boolean isDeviceProtectedStorage) {
    for (String apiInitClass : apiInitClasses) {
      try {
        if (!isDeviceProtectedStorage
            || DIRECT_BOOT_COMPATIBLE_API_INITIALIZERS.contains(apiInitClass)) {
          // If the device is in direct boot mode, do not initialize APIs that don't
          // support it.
          Class<?> initializerClass = Class.forName(apiInitClass);
          Method initMethod = initializerClass.getMethod("getInstance", parameterClass);
          int initMethodModifiers = initMethod.getModifiers();

          if (Modifier.isPublic(initMethodModifiers) && Modifier.isStatic(initMethodModifiers)) {
            initMethod.invoke(null /* static */, parameter);
          }
        }

      } catch (ClassNotFoundException e) {
        if (CORE_CLASSES.contains(apiInitClass)) {
          throw new IllegalStateException(
              apiInitClass
                  + " is missing, "
                  + "but is required. Check if it has been removed by Proguard.");
        }
        Log.d(LOG_TAG, apiInitClass + " is not linked. Skipping initialization.");
      } catch (NoSuchMethodException e) {
        // TODO: add doc link in error message.
        throw new IllegalStateException(
            apiInitClass
                + "#getInstance has been removed by Proguard."
                + " Add keep rule to prevent it.");
      } catch (InvocationTargetException e) {
        Log.wtf(LOG_TAG, "Firebase API initialization failure.", e);
      } catch (IllegalAccessException e) {
        // We check modifiers above, this shouldn't happen.
        Log.wtf(LOG_TAG, "Failed to initialize " + apiInitClass, e);
      }
    }
  }

  /** Normalizes the app name. */
  private static String normalize(@NonNull String name) {
    return name.trim();
  }

  /**
   * Used to deliver notifications when authentication state changes.
   *
   * @deprecated Use {@link com.google.firebase.auth.internal.IdTokenListener} in
   *     firebase-auth-interop.
   * @hide
   */
  @Deprecated
  @KeepForSdk
  public interface IdTokenListener {
    /**
     * The method which gets invoked authentication state has changed.
     *
     * @param tokenResult represents the {@link InternalTokenResult} interface, which can be used to
     *     obtain a cached access token.
     */
    @KeepForSdk
    void onIdTokenChanged(@NonNull InternalTokenResult tokenResult);
  }

  /**
   * Interface used to signal to FirebaseAuth when there are internal listeners, so that we know
   * whether or not to do proactive token refreshing.
   *
   * @hide
   */
  @Deprecated
  @KeepForSdk
  public interface IdTokenListenersCountChangedListener {
    /**
     * To be called with the new number of auth state listeners on any events which change the
     * number of listeners. Also triggered when {@link
     * #setIdTokenListenersCountChangedListener(IdTokenListenersCountChangedListener)} is called.
     */
    @KeepForSdk
    void onListenerCountChanged(int numListeners);
  }

  /**
   * Used to deliver notifications about whether the app is in the background. The first callback is
   * invoked inline if the app is in the background.
   *
   * <p>Doesn't fire on pre-ICS devices.
   *
   * <p>If the app is in the background and {@link #setAutomaticResourceManagementEnabled(boolean)}
   * is set to false.
   *
   * <p>
   *
   * @hide
   */
  @KeepForSdk
  public interface BackgroundStateChangeListener {

    /**
     * @param background True, if the app is in the background and automatic resource management is
     *     enabled.
     */
    @KeepForSdk
    void onBackgroundStateChanged(boolean background);
  }

  /**
   * Utility class that initializes Firebase APIs when the user unlocks the device, if the app is
   * first started in direct boot mode.
   *
   * @hide
   */
  @TargetApi(Build.VERSION_CODES.N)
  private static class UserUnlockReceiver extends BroadcastReceiver {

    private static AtomicReference<UserUnlockReceiver> INSTANCE = new AtomicReference<>();
    private final Context applicationContext;

    public UserUnlockReceiver(Context applicationContext) {
      this.applicationContext = applicationContext;
    }

    private static void ensureReceiverRegistered(Context applicationContext) {
      if (INSTANCE.get() == null) {
        UserUnlockReceiver receiver = new UserUnlockReceiver(applicationContext);
        if (INSTANCE.compareAndSet(null /* expected */, receiver)) {
          IntentFilter intentFilter = new IntentFilter(Intent.ACTION_USER_UNLOCKED);
          applicationContext.registerReceiver(receiver, intentFilter);
        }
      }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
      // API initialization is idempotent.
      synchronized (LOCK) {
        for (FirebaseApp app : INSTANCES.values()) {
          app.initializeAllApis();
        }
      }
      unregister();
    }

    public void unregister() {
      applicationContext.unregisterReceiver(this);
    }
  }

  /** Registers an activity lifecycle listener on ICS+ devices. */
  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  private static class GlobalBackgroundStateListener
      implements BackgroundDetector.BackgroundStateChangeListener {

    private static AtomicReference<GlobalBackgroundStateListener> INSTANCE =
        new AtomicReference<>();

    private static void ensureBackgroundStateListenerRegistered(Context context) {
      if (!(PlatformVersion.isAtLeastIceCreamSandwich()
          && context.getApplicationContext() instanceof Application)) {
        return;
      }
      Application application = (Application) context.getApplicationContext();
      if (INSTANCE.get() == null) {
        GlobalBackgroundStateListener listener = new GlobalBackgroundStateListener();
        if (INSTANCE.compareAndSet(null /* expected */, listener)) {
          BackgroundDetector.initialize(application);
          BackgroundDetector.getInstance().addListener(listener);
        }
      }
    }

    @Override
    public void onBackgroundStateChanged(boolean background) {
      synchronized (LOCK) {
        for (FirebaseApp app : new ArrayList<>(INSTANCES.values())) {
          if (app.automaticResourceManagementEnabled.get()) {
            app.notifyBackgroundStateChangeListeners(background);
          }
        }
      }
    }
  }

  private static class UiExecutor implements Executor {
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    @Override
    public void execute(@NonNull Runnable command) {
      HANDLER.post(command);
    }
  }
}
