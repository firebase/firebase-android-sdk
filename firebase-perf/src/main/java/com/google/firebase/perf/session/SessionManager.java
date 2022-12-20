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

package com.google.firebase.perf.session;

import android.annotation.SuppressLint;
import android.content.Context;
import androidx.annotation.Keep;
import com.google.android.gms.common.util.VisibleForTesting;
import com.google.firebase.perf.application.AppStateMonitor;
import com.google.firebase.perf.application.AppStateUpdateHandler;
import com.google.firebase.perf.session.gauges.GaugeManager;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.GaugeMetadata;
import com.google.firebase.perf.v1.GaugeMetric;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Session manager to generate sessionIDs and broadcast to the application. */
@Keep // Needed because of b/117526359.
public class SessionManager extends AppStateUpdateHandler {

  @SuppressLint("StaticFieldLeak")
  private static final SessionManager instance = new SessionManager();

  private final GaugeManager gaugeManager;
  private final AppStateMonitor appStateMonitor;
  private final Set<WeakReference<SessionAwareObject>> clients = new HashSet<>();

  private PerfSession perfSession;
  private Future syncInitFuture;

  /** Returns the singleton instance of SessionManager. */
  public static SessionManager getInstance() {
    return instance;
  }

  /** Returns the currently active PerfSession. */
  public final PerfSession perfSession() {
    return perfSession;
  }

  private SessionManager() {
    this(GaugeManager.getInstance(), PerfSession.create(), AppStateMonitor.getInstance());
  }

  @VisibleForTesting
  public SessionManager(
      GaugeManager gaugeManager, PerfSession perfSession, AppStateMonitor appStateMonitor) {
    this.gaugeManager = gaugeManager;
    this.perfSession = perfSession;
    this.appStateMonitor = appStateMonitor;
    registerForAppState();
  }

  /**
   * Finalizes gauge initialization during app start. This must be called before app start finishes
   * (currently that is before onResume finishes), because {@link #perfSession} can be changed by
   * {@link #onUpdateAppState(ApplicationProcessState)} once {@link AppStateMonitor#isColdStart()}
   * becomes false.
   */
  public void setApplicationContext(final Context appContext) {
    // Get PerfSession in main thread first, because it is possible that app changes fg/bg state
    // which creates a new perfSession, before the following is executed in background thread
    final PerfSession appStartSession = perfSession;
    // TODO(b/258263016): Migrate to go/firebase-android-executors
    @SuppressLint("ThreadPoolCreation")
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    syncInitFuture =
        executorService.submit(
            () -> {
              gaugeManager.initializeGaugeMetadataManager(appContext);
              if (appStartSession.isGaugeAndEventCollectionEnabled()) {
                gaugeManager.logGaugeMetadata(
                    appStartSession.sessionId(), ApplicationProcessState.FOREGROUND);
              }
            });
  }

  @Override
  public void onUpdateAppState(ApplicationProcessState newAppState) {
    super.onUpdateAppState(newAppState);

    if (appStateMonitor.isColdStart()) {
      // We want the Session to remain unchanged if this is a cold start of the app since we already
      // update the PerfSession in FirebasePerfProvider#onAttachInfo().
      return;
    }

    if (newAppState == ApplicationProcessState.FOREGROUND) {
      updatePerfSession(newAppState);

    } else if (!updatePerfSessionIfExpired()) {
      startOrStopCollectingGauges(newAppState);
    }
  }

  /**
   * Updates the current {@link PerfSession} if it has expired/timed out.
   *
   * @return {@code true} if {@link PerfSession} is updated, {@code false} otherwise.
   * @see PerfSession#isExpired()
   */
  public boolean updatePerfSessionIfExpired() {
    if (perfSession.isExpired()) {
      updatePerfSession(appStateMonitor.getAppState());
      return true;
    }

    return false;
  }

  /**
   * Updates the currently associated {@link #perfSession} and broadcast the event.
   *
   * <p>Creation of new {@link PerfSession} will log the {@link GaugeMetadata} and start/stop the
   * collection of {@link GaugeMetric} depending upon Session verbosity.
   *
   * @see PerfSession#isVerbose()
   */
  public void updatePerfSession(ApplicationProcessState currentAppState) {
    synchronized (clients) {
      perfSession = PerfSession.create();

      for (Iterator<WeakReference<SessionAwareObject>> i = clients.iterator(); i.hasNext(); ) {
        SessionAwareObject callback = i.next().get();
        if (callback != null) {
          callback.updateSession(perfSession);
        } else {
          // The object pointing by WeakReference has already been garbage collected.
          // Remove it from the Set.
          i.remove();
        }
      }
    }

    logGaugeMetadataIfCollectionEnabled(currentAppState);
    startOrStopCollectingGauges(currentAppState);
  }

  /**
   * Initial start of gauge collection. This should be called in ContentProvider.attachInfo during
   * cold-start, because we want to start gauge collection as early as possible. This assumes {@link
   * PerfSession} was already initialized a moment ago by getInstance(). Unlike updatePerfSession,
   * this does not reset the perfSession.
   */
  public void initializeGaugeCollection() {
    logGaugeMetadataIfCollectionEnabled(ApplicationProcessState.FOREGROUND);
    startOrStopCollectingGauges(ApplicationProcessState.FOREGROUND);
  }

  /**
   * Registers an object to receive updates about changes in the globally active {@link
   * PerfSession}.
   *
   * @param client The object that wants to receive updates.
   */
  public void registerForSessionUpdates(WeakReference<SessionAwareObject> client) {
    synchronized (clients) {
      clients.add(client);
    }
  }

  /**
   * Unregisters an object to receive updates about changes in the globally active {@link
   * PerfSession}.
   *
   * @param client The object that wants to stop receiving updates.
   */
  public void unregisterForSessionUpdates(WeakReference<SessionAwareObject> client) {
    synchronized (clients) {
      clients.remove(client);
    }
  }

  private void logGaugeMetadataIfCollectionEnabled(ApplicationProcessState appState) {
    if (perfSession.isGaugeAndEventCollectionEnabled()) {
      gaugeManager.logGaugeMetadata(perfSession.sessionId(), appState);
    }
  }

  private void startOrStopCollectingGauges(ApplicationProcessState appState) {
    if (perfSession.isGaugeAndEventCollectionEnabled()) {
      gaugeManager.startCollectingGauges(perfSession, appState);
    } else {
      gaugeManager.stopCollectingGauges();
    }
  }

  @VisibleForTesting
  public void setPerfSession(PerfSession perfSession) {
    this.perfSession = perfSession;
  }

  @VisibleForTesting
  public Future getSyncInitFuture() {
    return this.syncInitFuture;
  }
}
