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

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.SessionVerbosity;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Details of a session including a unique Id and related information. */
public class PerfSession implements Parcelable {

  private final String sessionId;
  private final Timer creationTime;

  private boolean isGaugeAndEventCollectionEnabled = false;

  /*
   * Creates a PerfSession object and decides what metrics to collect.
   */
  public static PerfSession createWithId(@NonNull String sessionId) {
    String prunedSessionId = sessionId.replace("-", "");
    PerfSession session = new PerfSession(prunedSessionId, new Clock());
    session.setGaugeAndEventCollectionEnabled(shouldCollectGaugesAndEvents());

    return session;
  }

  /** Creates a PerfSession with the provided {@code sessionId} and {@code clock}. */
  @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
  public PerfSession(String sessionId, Clock clock) {
    this.sessionId = sessionId;
    creationTime = clock.getTime();
  }

  private PerfSession(@NonNull Parcel in) {
    super();
    sessionId = in.readString();
    isGaugeAndEventCollectionEnabled = in.readByte() != 0;
    creationTime = in.readParcelable(Timer.class.getClassLoader());
  }

  /** Returns the sessionId of the object. */
  public String sessionId() {
    return sessionId;
  }

  /**
   * Returns a timer object that has been seeded with the system time at which the session began.
   */
  public Timer getTimer() {
    return creationTime;
  }

  /*
   * Enables/Disables the gauge and event collection for the system.
   */
  public void setGaugeAndEventCollectionEnabled(boolean enabled) {
    isGaugeAndEventCollectionEnabled = enabled;
  }

  /*
   * Returns if gauge and event collection is enabled for the system.
   */
  public boolean isGaugeAndEventCollectionEnabled() {
    return isGaugeAndEventCollectionEnabled;
  }

  /** Returns if the current session is verbose or not. */
  public boolean isVerbose() {
    return isGaugeAndEventCollectionEnabled;
  }

  /** Checks if the current {@link com.google.firebase.perf.v1.PerfSession} is verbose or not. */
  @VisibleForTesting
  static boolean isVerbose(@NonNull com.google.firebase.perf.v1.PerfSession perfSession) {
    for (SessionVerbosity sessionVerbosity : perfSession.getSessionVerbosityList()) {
      if (sessionVerbosity == SessionVerbosity.GAUGES_AND_SYSTEM_EVENTS) {
        return true;
      }
    }

    return false;
  }

  /**
   * Checks if it has been more than {@link ConfigResolver#getSessionsMaxDurationMinutes()} time
   * since the creation time of the current session.
   */
  public boolean isSessionRunningTooLong() {
    return TimeUnit.MICROSECONDS.toMinutes(creationTime.getDurationMicros())
        > ConfigResolver.getInstance().getSessionsMaxDurationMinutes();
  }

  /** Creates and returns the proto object for PerfSession object. */
  public com.google.firebase.perf.v1.PerfSession build() {
    com.google.firebase.perf.v1.PerfSession.Builder sessionMetric =
        com.google.firebase.perf.v1.PerfSession.newBuilder().setSessionId(sessionId);

    // If gauge collection is enabled, enable gauge collection verbosity.
    if (isGaugeAndEventCollectionEnabled) {
      sessionMetric.addSessionVerbosity(SessionVerbosity.GAUGES_AND_SYSTEM_EVENTS);
    }
    return sessionMetric.build();
  }

  /**
   * Build an array of {@link com.google.firebase.perf.v1.PerfSession} from the provided list of
   * {@link PerfSession}. When packing the list of sessions - even if one of the sessions is more
   * verbose, that will go as the first element in the list. This is for the backend to reduce the
   * computation overhead of deciding if there is any verbose session directly by looking into the
   * very first session, that would otherwise require to visit each and every session.
   */
  @Nullable
  public static com.google.firebase.perf.v1.PerfSession[] buildAndSort(
      @NonNull List<PerfSession> sessions) {
    if (sessions.isEmpty()) {
      return null;
    }

    com.google.firebase.perf.v1.PerfSession[] perfSessions =
        new com.google.firebase.perf.v1.PerfSession[sessions.size()];
    com.google.firebase.perf.v1.PerfSession perfSessionAtIndexZero = sessions.get(0).build();

    boolean foundVerboseSession = false;

    for (int i = 1; i < sessions.size(); i++) {
      com.google.firebase.perf.v1.PerfSession perfSession = sessions.get(i).build();

      if (!foundVerboseSession && sessions.get(i).isVerbose()) {
        foundVerboseSession = true;
        perfSessions[0] = perfSession;
        perfSessions[i] = perfSessionAtIndexZero;
      } else {
        perfSessions[i] = perfSession;
      }
    }

    if (!foundVerboseSession) {
      perfSessions[0] = perfSessionAtIndexZero;
    }

    return perfSessions;
  }

  /** If true, Session Gauge collection is enabled. */
  public static boolean shouldCollectGaugesAndEvents() {
    ConfigResolver configResolver = ConfigResolver.getInstance();

    return configResolver.isPerformanceMonitoringEnabled()
        && Math.random() < configResolver.getSessionsSamplingRate();
  }

  /**
   * Describes the kinds of special objects contained in this Parcelable's marshalled
   * representation. Please refer to
   * https://developer.android.com/reference/android/os/Parcelable.html
   *
   * @return always returns 0.
   */
  public int describeContents() {
    return 0;
  }

  /**
   * Flatten this object into a Parcel. Please refer to
   * https://developer.android.com/reference/android/os/Parcelable.html
   *
   * @param out the Parcel in which the object should be written.
   * @param flags Additional flags about how the object should be written.
   */
  public void writeToParcel(@NonNull Parcel out, int flags) {
    out.writeString(sessionId);
    out.writeByte((byte) (isGaugeAndEventCollectionEnabled ? 1 : 0));
    out.writeParcelable(creationTime, 0);
  }

  /**
   * A public static CREATOR field that implements {@code Parcelable.Creator} and generates
   * instances of your Parcelable class from a Parcel.
   */
  public static final Parcelable.Creator<PerfSession> CREATOR =
      new Parcelable.Creator<PerfSession>() {
        public PerfSession createFromParcel(@NonNull Parcel in) {
          return new PerfSession(in);
        }

        public PerfSession[] newArray(int size) {
          return new PerfSession[size];
        }
      };
}
