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
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.annotation.VisibleForTesting;
import androidx.collection.ArrayMap;
import androidx.core.os.UserManagerCompat;
import com.google.android.gms.common.annotation.KeepForSdk;
import com.google.android.gms.common.api.internal.BackgroundDetector;
import com.google.android.gms.common.internal.Objects;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.common.util.PlatformVersion;
import com.google.android.gms.common.util.ProcessUtils;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentDiscovery;
import com.google.firebase.components.ComponentDiscoveryService;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.ComponentRuntime;
import com.google.firebase.components.Lazy;
import com.google.firebase.events.Publisher;
import com.google.firebase.inject.Provider;
import com.google.firebase.internal.DataCollectionConfigStorage;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
public class FirebaseApp {

  private static final String LOG_TAG = "FirebaseApp";

  public static final @NonNull String DEFAULT_APP_NAME = "[DEFAULT]";

  private static final Object LOCK = new Object();

  private static final Executor UI_EXECUTOR = new UiExecutor();

  /** A map of (name, FirebaseApp) instances. */
  @GuardedBy("LOCK")
  static final Map<String, FirebaseApp> INSTANCES = new ArrayMap<>();

  private static final String FIREBASE_ANDROID = "fire-android";
  private static final String FIREBASE_COMMON = "fire-core";
  private static final String KOTLIN = "kotlin";

  private final Context applicationContext;
  private final String name;
  private final FirebaseOptions options;
  private final ComponentRuntime componentRuntime;

  // Default disabled. We released Firebase publicly without this feature, so making it default
  // enabled is a backwards incompatible change.
  private final AtomicBoolean automaticResourceManagementEnabled = new AtomicBoolean(false);
  private final AtomicBoolean deleted = new AtomicBoolean();
  private final Lazy<DataCollectionConfigStorage> dataCollectionConfigStorage;

  private final List<BackgroundStateChangeListener> backgroundStateChangeListeners =
      new CopyOnWriteArrayList<>();
  private final List<FirebaseAppLifecycleListener> lifecycleListeners =
      new CopyOnWriteArrayList<>();

  /** Returns the application {@link Context}. */
  @NonNull
  public Context getApplicationContext() {
    checkNotDeleted();
    return applicationContext;
  }

  /** Returns the unique name of this app. */
  @NonNull
  public String getName() {
    checkNotDeleted();
    return name;
  }

  /** Returns the specified {@link FirebaseOptions}. */
  @NonNull
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
  @NonNull
  public static List<FirebaseApp> getApps(@NonNull Context context) {
    synchronized (LOCK) {
      return new ArrayList<>(INSTANCES.values());
    }
  }

  /**
   * Returns the default (first initialized) instance of the {@link FirebaseApp}.
   *
   * @throws IllegalStateException if the default app was not initialized.
   */
  @NonNull
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
  @NonNull
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
  public static FirebaseApp initializeApp(@NonNull Context context) {
    synchronized (LOCK) {
      if (INSTANCES.containsKey(DEFAULT_APP_NAME)) {
        return getInstance();
      }
      FirebaseOptions firebaseOptions = FirebaseOptions.fromResource(context);
      if (firebaseOptions == null) {
        Log.w(
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
  @NonNull
  public static FirebaseApp initializeApp(
      @NonNull Context context, @NonNull FirebaseOptions options) {
    return initializeApp(context, options, DEFAULT_APP_NAME);
  }

  /**
   * A factory method to initialize a {@link FirebaseApp}.
   *
   * @param context represents the {@link Context}
   * @param options represents the global {@link FirebaseOptions}
   * @param name unique name for the app. It is an error to initialize an app with an already
   *     existing name. Starting and ending whitespace characters in the name are ignored (trimmed).
   * @return an instance of {@link FirebaseApp}
   * @throws IllegalStateException if an app with the same name has already been initialized.
   */
  @NonNull
  public static FirebaseApp initializeApp(
      @NonNull Context context, @NonNull FirebaseOptions options, @NonNull String name) {
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

  /**
   * Deletes the {@link FirebaseApp} and all its data. All calls to this {@link FirebaseApp}
   * instance will throw once it has been called.
   *
   * <p>A no-op if delete was called before.
   *
   * @hide
   */
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
    return dataCollectionConfigStorage.get().isEnabled();
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
  public void setDataCollectionDefaultEnabled(Boolean enabled) {
    checkNotDeleted();
    dataCollectionConfigStorage.get().setEnabled(enabled);
  }

  /**
   * Enable or disable automatic data collection across all SDKs.
   *
   * <p>Note: this value is respected by all SDKs unless overridden by the developer via SDK
   * specific mechanisms.
   *
   * @hide
   * @deprecated Use {@link #setDataCollectionDefaultEnabled(Boolean)} instead.
   */
  @KeepForSdk
  @Deprecated
  public void setDataCollectionDefaultEnabled(boolean enabled) {
    setDataCollectionDefaultEnabled(Boolean.valueOf(enabled));
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

    List<Provider<ComponentRegistrar>> registrars =
        ComponentDiscovery.forContext(applicationContext, ComponentDiscoveryService.class)
            .discoverLazy();

    componentRuntime =
        ComponentRuntime.builder(UI_EXECUTOR)
            .addLazyComponentRegistrars(registrars)
            .addComponentRegistrar(new FirebaseCommonRegistrar())
            .addComponent(Component.of(applicationContext, Context.class))
            .addComponent(Component.of(this, FirebaseApp.class))
            .addComponent(Component.of(options, FirebaseOptions.class))
            .build();

    dataCollectionConfigStorage =
        new Lazy<>(
            () ->
                new DataCollectionConfigStorage(
                    applicationContext,
                    getPersistenceKey(),
                    componentRuntime.get(Publisher.class)));
  }

  private void checkNotDeleted() {
    Preconditions.checkState(!deleted.get(), "FirebaseApp was deleted");
  }

  /** @hide */
  @KeepForSdk
  @VisibleForTesting
  public boolean isDefaultApp() {
    return DEFAULT_APP_NAME.equals(getName());
  }

  /** @hide */
  @VisibleForTesting
  @RestrictTo(Scope.TESTS)
  void initializeAllComponents() {
    componentRuntime.initializeAllComponentsForTests();
  }

  private void notifyBackgroundStateChangeListeners(boolean background) {
    Log.d(LOG_TAG, "Notifying background state change listeners.");
    for (BackgroundStateChangeListener listener : backgroundStateChangeListeners) {
      listener.onBackgroundStateChanged(background);
    }
  }

  /**
   * Registers a background state change listener. Make sure to call {@link
   * #removeBackgroundStateChangeListener(BackgroundStateChangeListener)} as appropriate to avoid
   * memory leaks.
   *
   * <p>If automatic resource management is enabled and the app is in the background a callback is
   * triggered immediately.
   *
   * @hide
   * @see BackgroundStateChangeListener
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
    boolean inDirectBoot = !UserManagerCompat.isUserUnlocked(applicationContext);
    if (inDirectBoot) {
      Log.i(
          LOG_TAG,
          "Device in Direct Boot Mode: postponing initialization of Firebase APIs for app "
              + getName());
      // Ensure that all APIs are initialized once the user unlocks the phone.
      UserUnlockReceiver.ensureReceiverRegistered(applicationContext);
    } else {
      Log.i(LOG_TAG, "Device unlocked: initializing all Firebase APIs for app " + getName());
      componentRuntime.initializeEagerComponents(isDefaultApp());
    }
  }

  /** Normalizes the app name. */
  private static String normalize(@NonNull String name) {
    return name.trim();
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
