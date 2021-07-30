// Copyright 2021 Google LLC
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

package com.google.firebase.crashlytics;

import static com.google.firebase.crashlytics.FirebaseCrashlytics.APP_EXCEPTION_CALLBACK_TIMEOUT_MS;
import static com.google.firebase.crashlytics.FirebaseCrashlytics.FIREBASE_CRASHLYTICS_ANALYTICS_ORIGIN;
import static com.google.firebase.crashlytics.FirebaseCrashlytics.LEGACY_CRASH_ANALYTICS_ORIGIN;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.annotations.DeferredApi;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.analytics.AnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.analytics.BlockingAnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.analytics.BreadcrumbAnalyticsEventReceiver;
import com.google.firebase.crashlytics.internal.analytics.CrashlyticsOriginAnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.analytics.UnavailableAnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbHandler;
import com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbSource;
import com.google.firebase.crashlytics.internal.breadcrumbs.DisabledBreadcrumbSource;
import com.google.firebase.inject.Deferred;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** @hide */
public class AnalyticsDeferredProxy {
  private final Deferred<AnalyticsConnector> analyticsConnectorDeferred;
  private volatile AnalyticsEventLogger analyticsEventLogger;
  private volatile BreadcrumbSource breadcrumbSource;

  @GuardedBy("this")
  private final List<BreadcrumbHandler> breadcrumbHandlerList;

  public AnalyticsDeferredProxy(Deferred<AnalyticsConnector> analyticsConnectorDeferred) {
    this(
        analyticsConnectorDeferred,
        new DisabledBreadcrumbSource(),
        new UnavailableAnalyticsEventLogger());
  }

  public AnalyticsDeferredProxy(
      Deferred<AnalyticsConnector> analyticsConnectorDeferred,
      @NonNull BreadcrumbSource breadcrumbSource,
      @NonNull AnalyticsEventLogger analyticsEventLogger) {
    this.analyticsConnectorDeferred = analyticsConnectorDeferred;
    this.breadcrumbSource = breadcrumbSource;
    this.breadcrumbHandlerList = new ArrayList<>();
    this.analyticsEventLogger = analyticsEventLogger;
    init();
  }

  public BreadcrumbSource getDeferredBreadcrumbSource() {
    return breadcrumbHandler -> {
      synchronized (this) {
        if (breadcrumbSource instanceof DisabledBreadcrumbSource) {
          breadcrumbHandlerList.add(breadcrumbHandler);
        }
        breadcrumbSource.registerBreadcrumbHandler(breadcrumbHandler);
      }
    };
  }

  public AnalyticsEventLogger getAnalyticsEventLogger() {
    return (name, params) -> analyticsEventLogger.logEvent(name, params);
  }

  private void init() {
    analyticsConnectorDeferred.whenAvailable(
        analyticsConnector -> {
          Logger.getLogger().d("AnalyticsConnector now available.");

          AnalyticsConnector connector = analyticsConnector.get();
          // If FA is available, create a logger to log events from the Crashlytics origin.
          final CrashlyticsOriginAnalyticsEventLogger directAnalyticsEventLogger =
              new CrashlyticsOriginAnalyticsEventLogger(connector);

          // Create a listener to register for events coming from FA, which supplies both
          // breadcrumbs
          // as well as Crashlytics-origin events through different streams.
          final CrashlyticsAnalyticsListener crashlyticsAnalyticsListener =
              new CrashlyticsAnalyticsListener();

          // Registering our listener with FA should return a "handle", in which case we know we've
          // registered successfully. Subsequent calls to register a listener will return null.
          final AnalyticsConnector.AnalyticsConnectorHandle analyticsConnectorHandle =
              subscribeToAnalyticsEvents(connector, crashlyticsAnalyticsListener);

          if (analyticsConnectorHandle != null) {
            Logger.getLogger().d("Registered Firebase Analytics listener.");
            // Create the event receiver which will supply breadcrumb events to Crashlytics
            final BreadcrumbAnalyticsEventReceiver breadcrumbReceiver =
                new BreadcrumbAnalyticsEventReceiver();
            // Logging events to FA is an asynchronous operation. This logger will send events to
            // FA and block until FA returns the same event back to us, from the Crashlytics origin.
            // However, in the case that data collection has been disabled on FA, we will not
            // receive
            // the event back (it will be silently dropped), so we set up a short timeout after
            // which
            // we will assume that FA data collection is disabled and move on.
            final BlockingAnalyticsEventLogger blockingAnalyticsEventLogger =
                new BlockingAnalyticsEventLogger(
                    directAnalyticsEventLogger,
                    APP_EXCEPTION_CALLBACK_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);

            synchronized (this) {
              // We need to re-register every handler registered in the other receiver. These
              // instructions are synchronized to ensure no handler is lost when registering the new
              // objects.
              for (BreadcrumbHandler handler : breadcrumbHandlerList) {
                breadcrumbReceiver.registerBreadcrumbHandler(handler);
              }
              // Set the appropriate event receivers to receive events from the FA listener
              crashlyticsAnalyticsListener.setBreadcrumbEventReceiver(breadcrumbReceiver);
              crashlyticsAnalyticsListener.setCrashlyticsOriginEventReceiver(
                  blockingAnalyticsEventLogger);

              // Set the breadcrumb event receiver as the breadcrumb source for Crashlytics.
              breadcrumbSource = breadcrumbReceiver;
              // Set the blocking analytics event logger for Crashlytics.
              analyticsEventLogger = blockingAnalyticsEventLogger;
            }
          } else {
            Logger.getLogger()
                .w(
                    "Could not register Firebase Analytics listener; a listener is already registered.");
            // FA is enabled, but the listener was not registered successfully.
            // We cannot listen for breadcrumbs. Since the default is already
            // `DisabledBreadcrumbSource` and `directAnalyticsEventLogger` there's nothing else to
            // do.
          }
        });
  }

  /**
   * Subscribes to Analytics events.
   *
   * <p>Should only be called from within the context of `whenAvailable` in the Deferred Connector.
   *
   * @param analyticsConnector Connector to Analytics.
   * @param listener Crashlytics listener to subscribe to analytics.
   * @return The Analytics handler
   */
  @DeferredApi
  private static AnalyticsConnector.AnalyticsConnectorHandle subscribeToAnalyticsEvents(
      @NonNull AnalyticsConnector analyticsConnector,
      @NonNull CrashlyticsAnalyticsListener listener) {

    AnalyticsConnector.AnalyticsConnectorHandle handle =
        analyticsConnector.registerAnalyticsConnectorListener(
            FIREBASE_CRASHLYTICS_ANALYTICS_ORIGIN, listener);

    if (handle == null) {
      Logger.getLogger()
          .d("Could not register AnalyticsConnectorListener with Crashlytics origin.");
      // Older versions of FA don't support CRASHLYTICS_ORIGIN. We can try using the old Firebase
      // Crash Reporting origin
      handle =
          analyticsConnector.registerAnalyticsConnectorListener(
              LEGACY_CRASH_ANALYTICS_ORIGIN, listener);

      // If FA allows us to connect with the legacy origin, but not the new one, nudge customers
      // to update their FA version.
      if (handle != null) {
        Logger.getLogger()
            .w(
                "A new version of the Google Analytics for Firebase SDK is now available. "
                    + "For improved performance and compatibility with Crashlytics, please "
                    + "update to the latest version.");
      }
    }

    return handle;
  }
}
