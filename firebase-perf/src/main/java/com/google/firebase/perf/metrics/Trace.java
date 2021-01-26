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

package com.google.firebase.perf.metrics;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.util.VisibleForTesting;
import com.google.firebase.perf.FirebasePerformanceAttributable;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.internal.AppStateMonitor;
import com.google.firebase.perf.internal.AppStateUpdateHandler;
import com.google.firebase.perf.internal.GaugeManager;
import com.google.firebase.perf.internal.PerfMetricValidator;
import com.google.firebase.perf.internal.PerfSession;
import com.google.firebase.perf.internal.SessionAwareObject;
import com.google.firebase.perf.internal.SessionManager;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Timer;
import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Trace allows you to set beginning and end of a certain action in your app. */
public class Trace extends AppStateUpdateHandler
    implements Parcelable, FirebasePerformanceAttributable, SessionAwareObject {

  private static final AndroidLogger logger = AndroidLogger.getInstance();
  private static final Map<String, Trace> sTraces = new ConcurrentHashMap<>();

  private final Trace parent;
  private final GaugeManager gaugeManager;
  private final String name;

  // TODO(b/177317027): Consider using a Set to avoid adding same PerfSession object
  private final List<PerfSession> sessions;

  private final List<Trace> subtraces;
  private final Map<String, Counter> counters;
  private final Clock clock;
  private final TransportManager transportManager;
  private final Map<String, String> attributes;
  private Timer startTime;
  private Timer endTime;

  private final WeakReference<SessionAwareObject> weakReference = new WeakReference<>(this);

  /** @hide */
  @Override
  /** @hide */
  public void updateSession(PerfSession session) {
    // Note(b/152218504): Being defensive to fix the NPE
    if (session == null) {
      logger.info("Unable to add new SessionId to the Trace. Continuing without it.");
      return;
    }

    if (hasStarted() && !isStopped()) {
      sessions.add(session);
    }
  }

  /**
   * Creates a Trace object with given name.
   *
   * @param name name of the trace object
   * @hide
   */
  /** @hide */
  @NonNull
  public static Trace create(@NonNull String name) {
    // Make a copy of input name so it does not hold onto the reference which is thread-safer.
    return new Trace(name);
  }

  private Trace(@NonNull String name) {
    this(
        name,
        TransportManager.getInstance(),
        new Clock(),
        AppStateMonitor.getInstance(),
        GaugeManager.getInstance());
  }

  /**
   * Creates a Trace object with given name.
   *
   * @param parent Parent of this trace
   * @param name Name of the trace
   * @param startTime Start time of the trace
   * @param endTime End time of the trace
   * @param subtraces List of subtraces
   * @param counters List of counters
   * @param attributes The map of custom attributes
   * @hide
   */
  /** @hide */
  private Trace(
      @NonNull Trace parent,
      @NonNull String name,
      Timer startTime,
      Timer endTime,
      @Nullable List<Trace> subtraces,
      @Nullable Map<String, Counter> counters,
      @Nullable Map<String, String> attributes) {
    this.parent = parent;
    this.name = name.trim();
    this.startTime = startTime;
    this.endTime = endTime;
    this.subtraces = subtraces != null ? subtraces : new ArrayList<>();
    this.counters = counters != null ? counters : new ConcurrentHashMap<>();
    this.attributes = attributes != null ? attributes : new ConcurrentHashMap<>();
    clock = parent.clock;
    transportManager = parent.transportManager;
    sessions = Collections.synchronizedList(new ArrayList<>());
    gaugeManager = this.parent.gaugeManager;
  }

  /**
   * Creates a Trace object with the given name. TransportManager and Clock instances are for
   * testing.
   *
   * @hide
   */
  /** @hide */
  public Trace(
      @NonNull String name,
      @NonNull TransportManager transportManager,
      @NonNull Clock clock,
      @NonNull AppStateMonitor appStateMonitor) {
    this(name, transportManager, clock, appStateMonitor, GaugeManager.getInstance());
  }

  /**
   * Creates a Trace object with the given name. TransportManager, Clock and GaugeManager instances
   * are for testing.
   *
   * @hide
   */
  /** @hide */
  public Trace(
      @NonNull String name,
      @NonNull TransportManager transportManager,
      @NonNull Clock clock,
      @NonNull AppStateMonitor appStateMonitor,
      @NonNull GaugeManager gaugeManager) {
    super(appStateMonitor);
    parent = null;
    this.name = name.trim();
    subtraces = new ArrayList<>();
    counters = new ConcurrentHashMap<>();
    attributes = new ConcurrentHashMap<>();
    this.clock = clock;
    this.transportManager = transportManager;
    sessions = Collections.synchronizedList(new ArrayList<>());
    this.gaugeManager = gaugeManager;
  }

  private Trace(@NonNull Parcel in, boolean isDataOnly) {
    super(isDataOnly ? null : AppStateMonitor.getInstance());
    parent = in.readParcelable(Trace.class.getClassLoader());
    name = in.readString();
    subtraces = new ArrayList<>();
    in.readList(subtraces, Trace.class.getClassLoader());
    counters = new ConcurrentHashMap<>();
    attributes = new ConcurrentHashMap<>();
    in.readMap(counters, Counter.class.getClassLoader());
    startTime = in.readParcelable(Timer.class.getClassLoader());
    endTime = in.readParcelable(Timer.class.getClassLoader());
    sessions = Collections.synchronizedList(new ArrayList<PerfSession>());
    in.readList(sessions, PerfSession.class.getClassLoader());
    if (isDataOnly) {
      transportManager = null;
      clock = null;
      gaugeManager = null;
    } else {
      // These three references are not in the parcel, need to recreate them.
      transportManager = TransportManager.getInstance();
      clock = new Clock();
      gaugeManager = GaugeManager.getInstance();
    }
  }

  /** Starts this trace. */
  @Keep
  public void start() {
    if (!ConfigResolver.getInstance().isPerformanceMonitoringEnabled()) {
      logger.info("Trace feature is disabled.");
      return;
    }

    String err = PerfMetricValidator.validateTraceName(name);

    if (err != null) {
      logger.error("Cannot start trace '%s'. Trace name is invalid.(%s)", name, err);
      return;
    }

    if (startTime != null) {
      logger.error("Trace '%s' has already started, should not start again!", name);
      return;
    }

    startTime = clock.getTime();

    registerForAppState();

    SessionManager sessionManager = SessionManager.getInstance();
    PerfSession perfSession = sessionManager.perfSession();
    SessionManager.getInstance().registerForSessionUpdates(weakReference);

    updateSession(perfSession);

    if (perfSession.isGaugeAndEventCollectionEnabled()) {
      gaugeManager.collectGaugeMetricOnce(perfSession.getTimer());
    }
  }

  /** Stops this trace. */
  @Keep
  public void stop() {
    if (!hasStarted()) {
      logger.error("Trace '%s' has not been started so unable to stop!", name);
      return;
    }
    if (isStopped()) {
      logger.error("Trace '%s' has already stopped, should not stop again!", name);
      return;
    }

    SessionManager.getInstance().unregisterForSessionUpdates(weakReference);

    unregisterForAppState();
    endTime = clock.getTime();
    if (parent == null) {
      setEndTimeOfLastStage(endTime);
      if (!name.isEmpty()) {
        transportManager.log(new TraceMetricBuilder(this).build(), getAppState());

        if (SessionManager.getInstance().perfSession().isGaugeAndEventCollectionEnabled()) {
          gaugeManager.collectGaugeMetricOnce(
              SessionManager.getInstance().perfSession().getTimer());
        }
      } else {
        logger.error("Trace name is empty, no log is sent to server");
      }
    }
  }

  /**
   * Set the end time of the last stage if there is any
   *
   * @param endTime time in millis to be set as end time of the last stage
   */
  private void setEndTimeOfLastStage(Timer endTime) {
    if (subtraces.isEmpty()) {
      return;
    }
    int lastLocation = subtraces.size() - 1;
    Trace lastStage = subtraces.get(lastLocation);
    // The end time of a stage can only be set once and can not be changed afterwards.
    if (lastStage.endTime == null) {
      lastStage.endTime = endTime;
    }
  }

  /**
   * Start a stage. If a stage is already running, it is stopped.
   *
   * @param name Name to be given to the stage.
   * @hide
   */
  /** @hide */
  void startStage(@NonNull String name) {
    Timer currentTime = clock.getTime();
    setEndTimeOfLastStage(currentTime);
    subtraces.add(new Trace(this, name, currentTime, null, null, null, null));
  }

  /**
   * Stop currently running stage.
   *
   * @hide
   */
  /** @hide */
  void stopStage() {
    setEndTimeOfLastStage(clock.getTime());
  }

  @NonNull
  private Counter obtainOrCreateCounterByName(@NonNull String counterName) {
    Counter counter = counters.get(counterName);
    if (counter == null) {
      counter = new Counter(counterName);
      counters.put(counterName, counter);
    }
    return counter;
  }

  /**
   * Atomically increments the metric with the given name in this trace by the incrementBy value. If
   * the metric does not exist, a new one will be created. If the trace has not been started or has
   * already been stopped, returns immediately without taking action.
   *
   * @param metricName Name of the metric to be incremented. Requires no leading or trailing
   *     whitespace, no leading underscore [_] character, max length of 100 characters.
   * @param incrementBy Amount by which the metric has to be incremented.
   */
  @Keep
  public void incrementMetric(@NonNull String metricName, long incrementBy) {
    String err = PerfMetricValidator.validateMetricName(metricName);
    if (err != null) {
      logger.error("Cannot increment metric '%s'. Metric name is invalid.(%s)", metricName, err);
      return;
    }
    if (!hasStarted()) {
      logger.warn(
          "Cannot increment metric '%s' for trace '%s' because it's not started", metricName, name);
      return;
    }
    if (isStopped()) {
      logger.warn(
          "Cannot increment metric '%s' for trace '%s' because it's been stopped",
          metricName, name);
      return;
    }
    // Make a copy of input metricName so it does not hold onto the reference which is
    // thread-safer
    Counter counter = obtainOrCreateCounterByName(metricName.trim());
    counter.increment(incrementBy);
    logger.debug(
        "Incrementing metric '%s' to %d on trace '%s'", metricName, counter.getCount(), name);
  }

  /**
   * Gets the value of the metric with the given name in the current trace. If a metric with the
   * given name doesn't exist, it is NOT created and a 0 is returned. This method is atomic.
   *
   * @param metricName Name of the metric to get. Requires no leading or trailing whitespace, no
   *     leading underscore '_' character, max length is 100 characters.
   * @return Value of the metric or 0 if it hasn't yet been set.
   */
  @Keep
  public long getLongMetric(@NonNull String metricName) {
    Counter counter = null;
    if (metricName != null) {
      counter = counters.get(metricName.trim());
    }
    if (counter == null) {
      return 0;
    }
    return counter.getCount();
  }

  /**
   * Sets the value of the metric with the given name in this trace to the value provided. If a
   * metric with the given name doesn't exist, a new one will be created. If the trace has not been
   * started or has already been stopped, returns immediately without taking action. This method is
   * atomic.
   *
   * @param metricName Name of the metric to set. Requires no leading or trailing whitespace, no
   *     leading underscore '_' character, max length is 100 characters.
   * @param value The value to which the metric should be set to.
   */
  @Keep
  public void putMetric(@NonNull String metricName, long value) {
    String err = PerfMetricValidator.validateMetricName(metricName);
    if (err != null) {
      logger.error(
          "Cannot set value for metric '%s'. Metric name is invalid.(%s)", metricName, err);
      return;
    }
    if (!hasStarted()) {
      logger.warn(
          "Cannot set value for metric '%s' for trace '%s' because it's not started",
          metricName, name);
      return;
    }
    if (isStopped()) {
      logger.warn(
          "Cannot set value for metric '%s' for trace '%s' because it's been stopped",
          metricName, name);
      return;
    }
    Counter counter = obtainOrCreateCounterByName(metricName.trim());
    counter.setCount(value);
    logger.debug("Setting metric '%s' to '%s' on trace '%s'", metricName, value, this.name);
  }

  /**
   * Creates a global {@link Trace} instance with given name. If an instance with this name was
   * obtained earlier, the same instance in returned.
   *
   * @param traceName Name of the global trace.
   * @return The instance of the {@link Trace}.
   * @hide
   */
  /** @hide */
  @NonNull
  static synchronized Trace getTrace(@NonNull String traceName) {
    Trace trace = sTraces.get(traceName);
    if (trace == null) {
      trace = new Trace(traceName);
      sTraces.put(traceName, trace);
    }
    return trace;
  }

  /** @hide */
  /** @hide */
  @VisibleForTesting
  @NonNull
  static synchronized Trace getTrace(
      @NonNull String traceName,
      @NonNull TransportManager transportManager,
      @NonNull Clock clock,
      @NonNull AppStateMonitor appStateMonitor) {
    Trace trace = sTraces.get(traceName);
    if (trace == null) {
      trace =
          new Trace(
              traceName, transportManager, clock, appStateMonitor, GaugeManager.getInstance());
      sTraces.put(traceName, trace);
    }
    return trace;
  }

  /**
   * Start the global {@link Trace} with given name. If getTrace() method of the traceName is not
   * called before, no Trace object is created and a null Trace object is returned.
   *
   * @param traceName Name of the Trace to be started.
   * @return The instance of the {@link Trace} started.
   * @hide
   */
  /** @hide */
  @Nullable
  static Trace startTrace(@NonNull String traceName) {
    Trace trace = sTraces.get(traceName);
    if (trace != null) {
      trace.start();
    }
    return trace;
  }

  /**
   * Stops the global {@link Trace}. If getTrace() method of the traceName is not called before, no
   * Trace object is created and a null Trace object is returned. After stopTrace(), the Trace is
   * not globally tracked any more.
   *
   * @param traceName Name of the global trace to be stopped
   * @return The instance of the {@link Trace} stopped.
   * @hide
   */
  /** @hide */
  @Nullable
  static Trace stopTrace(@NonNull String traceName) {
    Trace trace = sTraces.get(traceName);
    if (trace != null) {
      trace.stop();
      sTraces.remove(traceName);
    }
    return trace;
  }

  /**
   * Log a message if Trace is not stopped when finalize() is called.
   *
   * @hide
   */
  /** @hide */
  @Override
  protected void finalize() throws Throwable {
    try {
      // If trace is started but not stopped when it reaches finalize(), log a warning msg.
      if (isActive()) {
        logger.warn("Trace '%s' is started but not stopped when it is destructed!", name);
        incrementTsnsCount(1);
      }
    } finally {
      super.finalize();
    }
  }

  /** @hide */
  /** @hide */
  @VisibleForTesting
  @NonNull
  String getName() {
    return name;
  }

  /** @hide */
  /** @hide */
  @VisibleForTesting
  @NonNull
  Map<String, Counter> getCounters() {
    return counters;
  }

  /** @hide */
  /** @hide */
  @VisibleForTesting
  Timer getStartTime() {
    return startTime;
  }

  /** @hide */
  /** @hide */
  @VisibleForTesting
  Timer getEndTime() {
    return endTime;
  }

  /** @hide */
  /** @hide */
  @VisibleForTesting
  @NonNull
  List<Trace> getSubtraces() {
    return subtraces;
  }

  /**
   * non-zero endTime indicates Trace's stop() method has been called already.
   *
   * @return true if trace is stopped. false if not stopped.
   * @hide
   */
  /** @hide */
  @VisibleForTesting
  boolean isStopped() {
    return endTime != null;
  }

  /**
   * non-zero startTime indicates that Trace's start() method has been called
   *
   * @return true if trace has started, false if it has not started
   * @hide
   */
  /** @hide */
  @VisibleForTesting
  boolean hasStarted() {
    return startTime != null;
  }

  /**
   * Returns whether the trace is active.
   *
   * @return true if trace has been started but not stopped.
   * @hide
   */
  /** @hide */
  @VisibleForTesting
  boolean isActive() {
    return hasStarted() && !isStopped();
  }

  /**
   * Flatten this object into a Parcel. Please refer to
   * https://developer.android.com/reference/android/os/Parcelable.html
   *
   * @param out the Parcel in which the object should be written.
   * @param flags Additional flags about how the object should be written.
   */
  @Keep
  public void writeToParcel(@NonNull Parcel out, int flags) {
    out.writeParcelable(parent, 0);
    out.writeString(name);
    out.writeList(subtraces);
    out.writeMap(counters);
    out.writeParcelable(startTime, 0);
    out.writeParcelable(endTime, 0);
    synchronized (sessions) {
      out.writeList(sessions);
    }
  }

  /**
   * A public static CREATOR field that implements {@code Parcelable.Creator} and generates
   * instances of your Parcelable class from a Parcel.
   */
  @Keep
  public static final Parcelable.Creator<Trace> CREATOR =
      new Parcelable.Creator<Trace>() {
        public Trace createFromParcel(@NonNull Parcel in) {
          return new Trace(in, false);
        }

        public Trace[] newArray(int size) {
          return new Trace[size];
        }
      };

  /**
   * Sets a String value for the specified attribute. Updates the value of the attribute if the
   * attribute already exists. If the trace has been stopped, this method returns without adding the
   * attribute. The maximum number of attributes that can be added to a Trace are {@link
   * #MAX_TRACE_CUSTOM_ATTRIBUTES}.
   *
   * @param attribute Name of the attribute
   * @param value Value of the attribute
   */
  @Override
  @Keep
  public void putAttribute(@NonNull String attribute, @NonNull String value) {
    boolean noError = true;
    try {
      attribute = attribute.trim();
      value = value.trim();
      checkAttribute(attribute, value);
      logger.debug("Setting attribute '%s' to '%s' on trace '%s'", attribute, value, this.name);
    } catch (Exception e) {
      logger.error(
          "Can not set attribute '%s' with value '%s' (%s)", attribute, value, e.getMessage());
      noError = false;
    }
    if (noError) {
      attributes.put(attribute, value);
    }
  }

  private void checkAttribute(@NonNull String key, @NonNull String value) {
    if (isStopped()) {
      throw new IllegalArgumentException(
          String.format(Locale.ENGLISH, "Trace '%s' has been stopped", name));
    }
    if (!attributes.containsKey(key)
        && attributes.size() >= Constants.MAX_TRACE_CUSTOM_ATTRIBUTES) {
      throw new IllegalArgumentException(
          String.format(
              Locale.ENGLISH,
              "Exceeds max limit of number of attributes - %d",
              Constants.MAX_TRACE_CUSTOM_ATTRIBUTES));
    }
    String err = PerfMetricValidator.validateAttribute(new AbstractMap.SimpleEntry<>(key, value));
    if (err != null) {
      throw new IllegalArgumentException(err);
    }
  }

  /**
   * Removes an already added attribute from the Traces. If the trace has been stopped, this method
   * returns without removing the attribute.
   *
   * @param attribute Name of the attribute to be removed from the running Traces.
   */
  @Override
  @Keep
  public void removeAttribute(@NonNull String attribute) {
    if (isStopped()) {
      logger.error("Can't remove a attribute from a Trace that's stopped.");
      return;
    }
    attributes.remove(attribute);
  }

  /**
   * Returns the value of an attribute.
   *
   * @param attribute name of the attribute to fetch the value for
   * @return the value of the attribute if it exists or null otherwise.
   */
  @Override
  @Nullable
  @Keep
  public String getAttribute(@NonNull String attribute) {
    return attributes.get(attribute);
  }

  /**
   * Returns the map of all the attributes added to this trace.
   *
   * @return map of attributes and its values currently added to this Trace
   */
  @Override
  @NonNull
  @Keep
  public Map<String, String> getAttributes() {
    return new HashMap<>(attributes);
  }

  /**
   * Describes the kinds of special objects contained in this Parcelable's marshalled
   * representation.
   *
   * @see <a href="https://developer.android.com/reference/android/os/Parcelable.html">
   *     https://developer.android.com/reference/android/os/Parcelable.html</a>
   * @return always returns 0.
   */
  @Keep
  public int describeContents() {
    return 0;
  }

  /** @hide */
  /** @hide */
  @VisibleForTesting
  static final Parcelable.Creator<Trace> CREATOR_DATAONLY =
      new Parcelable.Creator<Trace>() {
        public Trace createFromParcel(Parcel in) {
          return new Trace(in, true);
        }

        public Trace[] newArray(int size) {
          return new Trace[size];
        }
      };

  /** @hide */
  /** @hide */
  @VisibleForTesting
  List<PerfSession> getSessions() {
    synchronized (sessions) {
      ArrayList<PerfSession> sessionsListCopy = new ArrayList<>();
      // To be extra safe, filter out nulls before returning the list
      // (b/171730176)
      for (PerfSession session : sessions) {
        if (session != null) {
          sessionsListCopy.add(session);
        }
      }
      return Collections.unmodifiableList(sessionsListCopy);
    }
  }
}
