// Copyright 2020 Google LLC
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

package com.google.firebase.perf;

import static com.google.firebase.perf.metrics.validator.PerfMetricValidator.validateAttribute;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.annotation.VisibleForTesting;
import com.google.android.datatransport.TransportFactory;
import com.google.firebase.FirebaseApp;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.config.RemoteConfigManager;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.logging.ConsoleUrlGenerator;
import com.google.firebase.perf.metrics.HttpMetric;
import com.google.firebase.perf.metrics.Trace;
import com.google.firebase.perf.session.SessionManager;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.ImmutableBundle;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.remoteconfig.RemoteConfigComponent;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The Firebase Performance Monitoring API.
 *
 * <p>It is automatically initialized by FirebaseApp.
 *
 * <p>This SDK uses FirebaseInstallations to identify the app instance and periodically sends data
 * to the Firebase backend. To stop sending performance events, call {@link
 * #setPerformanceCollectionEnabled(boolean)
 * FirebasePerformance.setPerformanceCollectionEnabled(false)}.
 */
@Singleton
public class FirebasePerformance implements FirebasePerformanceAttributable {

  private static final AndroidLogger logger = AndroidLogger.getInstance();

  // Redefining some constants for javadoc as Constants class is hidden.

  /** Maximum allowed number of attributes allowed in a trace. */
  @SuppressWarnings("unused") // Used in Javadoc.
  private static final int MAX_TRACE_CUSTOM_ATTRIBUTES = Constants.MAX_TRACE_CUSTOM_ATTRIBUTES;

  /** Maximum allowed length of the Key of the {@link Trace} attribute */
  @SuppressWarnings("unused") // Used in Javadoc.
  private static final int MAX_ATTRIBUTE_KEY_LENGTH = Constants.MAX_ATTRIBUTE_KEY_LENGTH;

  /** Maximum allowed length of the Value of the {@link Trace} attribute */
  @SuppressWarnings("unused") // Used in Javadoc.
  private static final int MAX_ATTRIBUTE_VALUE_LENGTH = Constants.MAX_ATTRIBUTE_VALUE_LENGTH;

  /** Maximum allowed length of the name of the {@link Trace} */
  @SuppressWarnings("unused") // Used in Javadoc.
  public static final int MAX_TRACE_NAME_LENGTH = Constants.MAX_TRACE_ID_LENGTH;

  private final Map<String, String> mCustomAttributes = new ConcurrentHashMap<>();

  private final ConfigResolver configResolver;
  // Extracting the metadata from the application context is expensive and so we only extract it
  // once during initialization and cache it.
  private final ImmutableBundle mMetadataBundle;

  /** Valid HttpMethods for manual network APIs */
  @StringDef({
    HttpMethod.GET,
    HttpMethod.PUT,
    HttpMethod.POST,
    HttpMethod.DELETE,
    HttpMethod.HEAD,
    HttpMethod.PATCH,
    HttpMethod.OPTIONS,
    HttpMethod.TRACE,
    HttpMethod.CONNECT
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface HttpMethod {
    String GET = "GET";
    String PUT = "PUT";
    String POST = "POST";
    String DELETE = "DELETE";
    String HEAD = "HEAD";
    String PATCH = "PATCH";
    String OPTIONS = "OPTIONS";
    String TRACE = "TRACE";
    String CONNECT = "CONNECT";
  }

  /**
   * Returns a singleton of FirebasePerformance.
   *
   * @return the singleton FirebasePerformance object.
   */
  // It is not expected to be null, unless developers augment their app's AndroidManifest.xml in a
  // way that would prevent go/firebase-android-components from discovering FirebasePerf's
  // components via tools:node="remove".
  //
  // See https://developer.android.com/studio/build/manifest-merge#node_markers for details.
  @NonNull
  public static FirebasePerformance getInstance() {
    return FirebaseApp.getInstance().get(FirebasePerformance.class);
  }

  // This is set to true if performance monitoring data collection has been force enabled, it is set
  // to false if it's been force disabled or it is set to null if neither.
  @Nullable private Boolean mPerformanceCollectionForceEnabledState = null;

  private final FirebaseApp firebaseApp;
  private final Provider<RemoteConfigComponent> firebaseRemoteConfigProvider;
  private final FirebaseInstallationsApi firebaseInstallationsApi;
  private final Provider<TransportFactory> transportFactoryProvider;

  /**
   * Constructs the FirebasePerformance class and allows injecting dependencies.
   *
   * <p>TODO(b/172007278): Initialize SDK components in a background thread to avoid cases of cyclic
   * dependency.
   *
   * @param firebaseApp The FirebaseApp instance.
   * @param firebaseRemoteConfigProvider The {@link Provider} for FirebaseRemoteConfig instance.
   * @param firebaseInstallationsApi The FirebaseInstallationsApi instance.
   * @param transportFactoryProvider The {@link Provider} for the the {@link TransportFactory}.
   * @param remoteConfigManager The RemoteConfigManager instance.
   * @param configResolver The ConfigResolver instance.
   * @param sessionManager The SessionManager instance.
   */
  @VisibleForTesting
  @Inject
  FirebasePerformance(
      FirebaseApp firebaseApp,
      Timer initTime,
      Provider<RemoteConfigComponent> firebaseRemoteConfigProvider,
      FirebaseInstallationsApi firebaseInstallationsApi,
      Provider<TransportFactory> transportFactoryProvider,
      RemoteConfigManager remoteConfigManager,
      ConfigResolver configResolver,
      SessionManager sessionManager) {

    this.firebaseApp = firebaseApp;
    this.firebaseRemoteConfigProvider = firebaseRemoteConfigProvider;
    this.firebaseInstallationsApi = firebaseInstallationsApi;
    this.transportFactoryProvider = transportFactoryProvider;

    if (firebaseApp == null) {
      this.mPerformanceCollectionForceEnabledState = false;
      this.configResolver = configResolver;
      this.mMetadataBundle = new ImmutableBundle(new Bundle());
      return;
    }

    TransportManager.getInstance()
        .initialize(firebaseApp, firebaseInstallationsApi, transportFactoryProvider);

    Context appContext = firebaseApp.getApplicationContext();
    // TODO(b/110178816): Explore moving off of main thread.
    mMetadataBundle = extractMetadata(appContext);

    remoteConfigManager.setReferenceTimeInMs(initTime);
    remoteConfigManager.setFirebaseRemoteConfigProvider(firebaseRemoteConfigProvider);
    this.configResolver = configResolver;
    this.configResolver.setMetadataBundle(mMetadataBundle);
    this.configResolver.setApplicationContext(appContext);
    sessionManager.setApplicationContext(appContext);

    mPerformanceCollectionForceEnabledState = configResolver.getIsPerformanceCollectionEnabled();
    if (logger.isLogcatEnabled() && isPerformanceCollectionEnabled()) {
      logger.info(
          String.format(
              "Firebase Performance Monitoring is successfully initialized! In a minute, visit the Firebase console to view your data: %s",
              ConsoleUrlGenerator.generateDashboardUrl(
                  firebaseApp.getOptions().getProjectId(), appContext.getPackageName())));
    }
  }

  /**
   * Creates a Trace object with given name and start the trace.
   *
   * @param traceName name of the trace. Requires no leading or trailing whitespace, no leading
   *     underscore [_] character, max length of {@link #MAX_TRACE_NAME_LENGTH} characters.
   * @return the new Trace object.
   */
  @NonNull
  public static Trace startTrace(@NonNull String traceName) {
    Trace trace = Trace.create(traceName);
    trace.start();
    return trace;
  }

  /**
   * Enables or disables performance monitoring. This setting is persisted and applied on future
   * invocations of your application. By default, performance monitoring is enabled. If you need to
   * change the default (for example, because you want to prompt the user before collecting
   * performance stats), add:
   *
   * <pre>{@code
   * <meta-data android:name=firebase_performance_collection_enabled android:value=false />
   * }</pre>
   *
   * to your application’s manifest. Changing the value during runtime will override the manifest
   * value.
   *
   * <p>If you want to permanently disable sending performance metrics, add
   *
   * <pre>{@code
   * <meta-data android:name="firebase_performance_collection_deactivated" android:value="true" />
   * }</pre>
   *
   * to your application's manifest. Changing the value during runtime will not override the
   * manifest value.
   *
   * <p>This is separate from enabling/disabling instrumentation in Gradle properties.
   *
   * @param enable Should performance monitoring be enabled
   */
  public void setPerformanceCollectionEnabled(boolean enable) {
    setPerformanceCollectionEnabled(Boolean.valueOf(enable));
  }

  /**
   * Enables, disables or clear the existing performance monitoring setting. This setting is
   * persisted and applied on future invocations of your application. By default, performance
   * monitoring is enabled. If you need to change the default (for example, because you want to
   * prompt the user before collecting performance stats), add:
   *
   * <pre>{@code
   * <meta-data android:name=firebase_performance_collection_enabled android:value=false />
   * }</pre>
   *
   * to your application’s manifest. Changing the value during runtime will override the manifest
   * value.
   *
   * <p>If you want to permanently disable sending performance metrics, add
   *
   * <pre>{@code
   * <meta-data android:name="firebase_performance_collection_deactivated" android:value="true" />
   * }</pre>
   *
   * to your application's manifest. Changing the value during runtime will not override the
   * manifest value.
   *
   * <p>This is separate from enabling/disabling instrumentation in Gradle properties.
   *
   * @param enable Should performance monitoring be enabled. "null" value indicates that perf data
   *     collection enablement is dependent on other factors like manifest and the global data
   *     collection enablement
   * @hide
   */
  public synchronized void setPerformanceCollectionEnabled(@Nullable Boolean enable) {
    // If FirebaseApp is not initialized, Firebase Performance API is not effective yet.
    try {
      FirebaseApp.getInstance();
    } catch (IllegalStateException e) {
      return;
    }

    if (configResolver.getIsPerformanceCollectionDeactivated()) {
      logger.info("Firebase Performance is permanently disabled");
      return;
    }

    // setIsPerformanceCollectionEnabled should be called before getIsPerformanceCollectionEnabled
    // bcz we want the mPerformanceCollectionForceEnabledState to reflect the most updated value.
    configResolver.setIsPerformanceCollectionEnabled(enable);
    if (enable != null) {
      mPerformanceCollectionForceEnabledState = enable;
    } else {
      // Get the data collection enablement value based on the manifest configuration.
      mPerformanceCollectionForceEnabledState = configResolver.getIsPerformanceCollectionEnabled();
    }
    if (Boolean.TRUE.equals(mPerformanceCollectionForceEnabledState)) {
      logger.info("Firebase Performance is Enabled");
    } else if (Boolean.FALSE.equals(mPerformanceCollectionForceEnabledState)) {
      logger.info("Firebase Performance is Disabled");
    }
  }

  /**
   * Determines whether performance monitoring is enabled or disabled. This respects the Firebase
   * Performance specific values first, and if these aren't set, uses the Firebase wide data
   * collection switch.
   *
   * @return true if performance monitoring is enabled and false if performance monitoring is
   *     disabled. This is for dynamic enable/disable state. This does not reflect whether
   *     instrumentation is enabled/disabled in Gradle properties.
   */
  public boolean isPerformanceCollectionEnabled() {
    return mPerformanceCollectionForceEnabledState != null
        ? mPerformanceCollectionForceEnabledState
        : FirebaseApp.getInstance().isDataCollectionDefaultEnabled();
  }

  /**
   * Sets a String value for the specified attribute in the global list of attributes. Global
   * attributes will be included in all the traces before the Trace is reported. If the attribute
   * already exists, its value will get updated. Attribute set using {@link
   * Trace#putAttribute(String, String)} will override the attribute with same name set by this
   * method. The maximum number of attributes that can be added are {@link
   * #MAX_TRACE_CUSTOM_ATTRIBUTES}.
   *
   * @param attribute name of the attribute. Leading and trailing white spaces if any, will be
   *     removed from the name. The name must start with letter, must only contain alphanumeric
   *     characters and underscore and must not start with "firebase_", "google_" and "ga_. The max
   *     length is limited to {@link #MAX_ATTRIBUTE_KEY_LENGTH}
   * @param value value of the attribute. The max length is limited to {@link
   *     #MAX_ATTRIBUTE_VALUE_LENGTH}
   * @hide
   */
  @Override
  public void putAttribute(@NonNull String attribute, @NonNull String value) {
    boolean noError = true;
    try {
      attribute = attribute.trim();
      value = value.trim();
      checkAttribute(attribute, value);
    } catch (Exception e) {
      logger.error("Can not set attribute %s with value %s (%s)", attribute, value, e.getMessage());
      noError = false;
    }
    if (noError) {
      mCustomAttributes.put(attribute, value);
    }
  }

  private void checkAttribute(@Nullable String key, @Nullable String value) {
    if (key == null || value == null) {
      throw new IllegalArgumentException("Attribute must not have null key or value.");
    }

    if (!mCustomAttributes.containsKey(key)
        && mCustomAttributes.size() >= Constants.MAX_TRACE_CUSTOM_ATTRIBUTES) {
      throw new IllegalArgumentException(
          String.format(
              Locale.US,
              "Exceeds max limit of number of attributes - %d",
              Constants.MAX_TRACE_CUSTOM_ATTRIBUTES));
    }

    validateAttribute(key, value);
  }

  /**
   * Removes the attribute from the global list of attributes.
   *
   * @param attribute name of the attribute to be removed from the global pool.
   * @hide
   */
  @Override
  public void removeAttribute(@NonNull String attribute) {
    mCustomAttributes.remove(attribute);
  }

  /**
   * Returns the value of an attribute.
   *
   * @param attribute name of the attribute to fetch the value for
   * @return the value of the attribute if it exists or null otherwise.
   * @hide
   */
  @Override
  @Nullable
  public String getAttribute(@NonNull String attribute) {
    return mCustomAttributes.get(attribute);
  }

  /**
   * Returns the map of all the attributes currently added in the global pool.
   *
   * @return map of attributes and its values currently added to the running Traces
   * @hide
   */
  @Override
  @NonNull
  public Map<String, String> getAttributes() {
    return new HashMap<>(mCustomAttributes);
  }

  /**
   * Creates a Trace object with given name.
   *
   * @param traceName name of the trace, requires no leading or trailing whitespace, no leading
   *     underscore '_' character, max length is {@link #MAX_TRACE_NAME_LENGTH} characters.
   * @return the new Trace object.
   */
  @NonNull
  public Trace newTrace(@NonNull String traceName) {
    return Trace.create(traceName);
  }

  /**
   * Creates a HttpMetric object for collecting network performance data for one request/response
   *
   * @param url a valid url String, cannot be empty
   * @param httpMethod One of the values GET, PUT, POST, DELETE, HEAD, PATCH, OPTIONS, TRACE, or
   *     CONNECT
   * @return the new HttpMetric object.
   */
  @NonNull
  public HttpMetric newHttpMetric(@NonNull String url, @NonNull @HttpMethod String httpMethod) {
    return new HttpMetric(url, httpMethod, TransportManager.getInstance(), new Timer());
  }

  /**
   * Creates a HttpMetric object for collecting network performance data for one request/response
   *
   * @param url a valid URL object
   * @param httpMethod One of the values GET, PUT, POST, DELETE, HEAD, PATCH, OPTIONS, TRACE, or
   *     CONNECT
   * @return the new HttpMetric object.
   */
  @NonNull
  public HttpMetric newHttpMetric(@NonNull URL url, @NonNull @HttpMethod String httpMethod) {
    return new HttpMetric(url, httpMethod, TransportManager.getInstance(), new Timer());
  }

  /**
   * Extracts the metadata bundle from the ApplicationContext.
   *
   * @param appContext The ApplicationContext.
   * @return A shallow copy of the bundle containing the metadata extracted from the context.
   */
  private static ImmutableBundle extractMetadata(Context appContext) {
    Bundle bundle = null;
    try {
      ApplicationInfo ai =
          appContext
              .getPackageManager()
              .getApplicationInfo(appContext.getPackageName(), PackageManager.GET_META_DATA);

      bundle = ai.metaData;
    } catch (NameNotFoundException | NullPointerException e) {
      Log.d(Constants.ENABLE_DISABLE, "No perf enable meta data found " + e.getMessage());
    }
    return bundle != null ? new ImmutableBundle(bundle) : new ImmutableBundle();
  }

  @VisibleForTesting
  Boolean getPerformanceCollectionForceEnabledState() {
    return mPerformanceCollectionForceEnabledState;
  }
}
