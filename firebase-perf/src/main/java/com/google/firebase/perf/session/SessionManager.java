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
import androidx.annotation.VisibleForTesting;
import com.google.firebase.perf.logging.FirebaseSessionsEnforcementCheck;
import com.google.firebase.perf.session.gauges.GaugeManager;
import com.google.firebase.perf.v1.GaugeMetadata;
import com.google.firebase.perf.v1.GaugeMetric;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

/** Session manager to generate sessionIDs and broadcast to the application. */
@Keep // Needed because of b/117526359.
public class SessionManager {
  @SuppressLint("StaticFieldLeak")
  private static final SessionManager instance = new SessionManager();

  private final GaugeManager gaugeManager;
  private final Set<WeakReference<SessionAwareObject>> clients = new HashSet<>();

  private PerfSession perfSession;

  /** Returns the singleton instance of SessionManager. */
  public static SessionManager getInstance() {
    return instance;
  }

  /** Returns the currently active PerfSession. */
  public final PerfSession perfSession() {
    FirebaseSessionsEnforcementCheck.checkSession(perfSession, "PerfSession.perfSession()");

    return perfSession;
  }

  private SessionManager() {
    // session should quickly updated by session subscriber.
    this(GaugeManager.getInstance(), PerfSession.createWithId(null));
    FirebaseSessionsEnforcementCheck.checkSession(perfSession, "SessionManager()");
  }

  @VisibleForTesting
  public SessionManager(GaugeManager gaugeManager, PerfSession perfSession) {
    this.gaugeManager = gaugeManager;
    this.perfSession = perfSession;
  }

  /**
   * Finalizes gauge initialization during cold start. This must be called before app start finishes
   * (currently that is before onResume finishes) to ensure gauge collection starts on time.
   */
  public void setApplicationContext(final Context appContext) {
    gaugeManager.initializeGaugeMetadataManager(appContext);
  }

  /**
   * Checks if the current {@link PerfSession} is expired/timed out. If so, stop collecting gauges.
   *
   * @see PerfSession#isSessionRunningTooLong()
   */
  public void stopGaugeCollectionIfSessionRunningTooLong() {
    FirebaseSessionsEnforcementCheck.checkSession(
        perfSession, "SessionManager.stopGaugeCollectionIfSessionRunningTooLong");

    if (perfSession.isSessionRunningTooLong()) {
      gaugeManager.stopCollectingGauges();
    }
  }

  /**
   * Updates the currently associated {@link #perfSession} and broadcast the change.
   *
   * <p>Uses the provided PerfSession {@link PerfSession}, log the {@link GaugeMetadata} and
   * start/stop the collection of {@link GaugeMetric} depending upon Session verbosity.
   *
   * @see PerfSession#isVerbose()
   */
  public void updatePerfSession(PerfSession perfSession) {
    // Do not update the perf session if it is the exact same sessionId.
    if (Objects.equals(perfSession.sessionId(), this.perfSession.sessionId())) {
      return;
    }

    this.perfSession = perfSession;

    // Log gauge metadata.
    logGaugeMetadataIfCollectionEnabled();

    // Start of stop the gauge data collection.
    startOrStopCollectingGauges();

    synchronized (clients) {
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
  }

  /**
   * Initial start of gauge collection. This should be called in ContentProvider.attachInfo during
   * cold-start, because we want to start gauge collection as early as possible. This assumes {@link
   * PerfSession} was already initialized a moment ago by getInstance(). Unlike updatePerfSession,
   * this does not reset the perfSession.
   */
  public void initializeGaugeCollection() {
    startOrStopCollectingGauges();
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

  private void logGaugeMetadataIfCollectionEnabled() {
    FirebaseSessionsEnforcementCheck.checkSession(
        perfSession, "logGaugeMetadataIfCollectionEnabled");
    if (perfSession.isVerbose()) {
      gaugeManager.logGaugeMetadata(perfSession.sessionId());
    }
  }

  private void startOrStopCollectingGauges() {
    FirebaseSessionsEnforcementCheck.checkSession(perfSession, "startOrStopCollectingGauges");

    if (perfSession.isVerbose()) {
      gaugeManager.startCollectingGauges(perfSession);
    } else {
      gaugeManager.stopCollectingGauges();
    }
  }

  @VisibleForTesting
  public void setPerfSession(PerfSession perfSession) {
    this.perfSession = perfSession;
  }
}
