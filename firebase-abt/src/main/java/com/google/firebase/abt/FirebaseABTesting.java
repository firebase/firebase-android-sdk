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

package com.google.firebase.abt;

import static com.google.firebase.abt.AbtExperimentInfo.validateAbtExperimentInfo;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.analytics.connector.AnalyticsConnector.ConditionalUserProperty;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages Firebase A/B Testing Experiments.
 *
 * <p>To register an ABT experiment in an App, the experiment's information needs to be sent to the
 * Google Analytics for Firebase SDK. To simplify those interactions with Analytics,
 * FirebaseABTesting's methods provide a simple interface for managing ABT experiments.
 *
 * <p>An instance of this class handles all experiments for a specific origin (an impact service
 * such as Firebase Remote Config) in a specific App.
 *
 * <p>The clients of this class are first party teams that use ABT experiments in their SDKs.
 *
 * @author Miraziz Yusupov
 */
public class FirebaseABTesting {

  @VisibleForTesting static final String ABT_PREFERENCES = "com.google.firebase.abt";

  @VisibleForTesting
  static final String ORIGIN_LAST_KNOWN_START_TIME_KEY_FORMAT = "%s_lastKnownExperimentStartTime";

  /** The App's Firebase Analytics client. */
  private final AnalyticsConnector analyticsConnector;

  /** The name of an ABT client. */
  private final String originService;

  /**
   * Select keys of fields in the experiment descriptions returned from the Firebase Remote Config
   * server.
   */
  @StringDef({OriginService.REMOTE_CONFIG, OriginService.INAPP_MESSAGING})
  @Retention(RetentionPolicy.SOURCE)
  public @interface OriginService {

    /** Must match the origin code in Google Analytics for Firebase. */
    String REMOTE_CONFIG = "frc";

    String INAPP_MESSAGING = "fiam";
  }

  /**
   * Maximum number of conditional user properties allowed for the origin service. Null until
   * retrieved from Analytics.
   */
  @Nullable private Integer maxUserProperties;

  /**
   * Creates an instance of the ABT class for the specified App and origin service.
   *
   * @param unusedAppContext {@link Context} of an App.
   * @param originService the name of an origin service.
   */
  public FirebaseABTesting(
      Context unusedAppContext,
      AnalyticsConnector analyticsConnector,
      @OriginService String originService) {
    this.analyticsConnector = analyticsConnector;
    this.originService = originService;

    this.maxUserProperties = null;
  }

  /**
   * Replaces the origin's list of experiments in the App with the experiments defined in {@code
   * replacementExperiments}, adhering to a "discard oldest" overflow policy.
   *
   * <p>Note: This is a blocking call and should only be called from a worker thread.
   *
   * <p>The maps of {@code replacementExperiments} must be in the format defined by the ABT service.
   * The current SDK's format for experiment maps is specified in {@link
   * AbtExperimentInfo#fromMap(Map)}.
   *
   * @param replacementExperiments list of experiment info {@link Map}s, where each map contains the
   *     identifiers and metadata of a distinct experiment that is currently running. If the value
   *     is null, this method is a no-op.
   * @throws IllegalArgumentException If {@code replacementExperiments} is null.
   * @throws AbtException If there is no Analytics SDK or if any experiment map in {@code
   *     replacementExperiments} could not be parsed.
   */
  @WorkerThread
  public void replaceAllExperiments(List<Map<String, String>> replacementExperiments)
      throws AbtException {

    throwAbtExceptionIfAnalyticsIsNull();

    if (replacementExperiments == null) {
      throw new IllegalArgumentException("The replacementExperiments list is null.");
    }

    replaceAllExperimentsWith(convertMapsToExperimentInfos(replacementExperiments));
  }

  /**
   * Clears the origin service's list of experiments in the App.
   *
   * <p>Note: This is a blocking call and therefore should be called from a worker thread.
   *
   * @throws AbtException If there is no Analytics SDK.
   */
  @WorkerThread
  public void removeAllExperiments() throws AbtException {

    throwAbtExceptionIfAnalyticsIsNull();

    removeExperiments(getAllExperimentsInAnalytics());
  }

  /**
   * Gets the origin service's list of experiments in the app.
   *
   * <p>Note: This is a blocking call and therefore should be called from a worker thread.
   *
   * @return the origin service's list of experiments in the app.
   * @throws AbtException If there is no Analytics SDK.
   */
  @WorkerThread
  public List<AbtExperimentInfo> getAllExperiments() throws AbtException {
    throwAbtExceptionIfAnalyticsIsNull();

    List<ConditionalUserProperty> experimentsInAnalytics = getAllExperimentsInAnalytics();
    List<AbtExperimentInfo> experimentInfos = new ArrayList<>();

    for (ConditionalUserProperty experimentInAnalytics : experimentsInAnalytics) {
      experimentInfos.add(AbtExperimentInfo.fromConditionalUserProperty(experimentInAnalytics));
    }

    return experimentInfos;
  }

  /**
   * Sets an experiment to be active in GA metrics reporting by setting a null triggering condition
   * on the provided experiment. This results in the experiment being active as if it was triggered
   * by the triggering condition event being seen in GA.
   *
   * <p>Note: This is a blocking call and therefore should be called from a worker thread.
   *
   * @param activeExperiment The {@link AbtExperimentInfo} that should be set as active in GA.
   * @throws AbtException If there is no Analytics SDK.
   */
  @WorkerThread
  public void reportActiveExperiment(AbtExperimentInfo activeExperiment) throws AbtException {
    throwAbtExceptionIfAnalyticsIsNull();
    validateAbtExperimentInfo(activeExperiment);
    ArrayList<AbtExperimentInfo> activeExperimentList = new ArrayList<>();

    // Remove trigger event if it exists, this sets the experiment to active.
    Map<String, String> activeExperimentMap = activeExperiment.toStringMap();
    activeExperimentMap.remove(AbtExperimentInfo.TRIGGER_EVENT_KEY);

    // Add experiment to GA
    activeExperimentList.add(AbtExperimentInfo.fromMap(activeExperimentMap));
    addExperiments(activeExperimentList);
  }

  /**
   * Cleans up all experiments which are active in GA but not currently running. This method is
   * meant to be used to ensure all running experiments should indeed be running.
   *
   * <p>Note: This is a blocking call and therefore should be called from a worker thread.
   *
   * @param runningExperiments the currently running {@link AbtExperimentInfo}s, any active
   *     experiment that is not in this list will be removed from GA reporting.
   * @throws AbtException If there is no Analytics SDK.
   */
  @WorkerThread
  public void validateRunningExperiments(List<AbtExperimentInfo> runningExperiments)
      throws AbtException {
    throwAbtExceptionIfAnalyticsIsNull();
    Set<String> runningExperimentIds = new HashSet<>();
    for (AbtExperimentInfo runningExperiment : runningExperiments) {
      runningExperimentIds.add(runningExperiment.getExperimentId());
    }
    List<ConditionalUserProperty> experimentsToRemove =
        getExperimentsToRemove(getAllExperimentsInAnalytics(), runningExperimentIds);
    removeExperiments(experimentsToRemove);
  }

  /**
   * Replaces the origin's list of experiments in the App with {@code replacementExperiments}. If
   * {@code replacementExperiments} is an empty list, then all the origin's experiments in the App
   * are removed.
   *
   * <p>The replacement is done as follows:
   *
   * <ol>
   *   <li>Any experiment in the origin's list that is not in {@code replacementExperiments} is
   *       removed.
   *   <li>Any experiment in {@code replacementExperiments} that is not already in the origin's list
   *       is added. If the origin's list has the maximum number of experiments allowed and an
   *       experiment needs to be added, the oldest experiment in the list is removed.
   * </ol>
   *
   * <p>Experiments in {@code replacementExperiments} that have previously been discarded will be
   * ignored. An experiment is assumed to be previously discarded if it's start time is before the
   * last start time seen by this instance and it does not exist in the origin's list.
   *
   * @param replacementExperiments list of {@link AbtExperimentInfo}s, each containing the
   *     identifiers and metadata of a distinct experiment that is currently running. Must contain
   *     at least one valid experiment.
   * @throws AbtException If there is no Analytics SDK.
   */
  private void replaceAllExperimentsWith(List<AbtExperimentInfo> replacementExperiments)
      throws AbtException {

    if (replacementExperiments.isEmpty()) {
      removeAllExperiments();
      return;
    }

    Set<String> replacementExperimentIds = new HashSet<>();
    for (AbtExperimentInfo replacementExperiment : replacementExperiments) {
      replacementExperimentIds.add(replacementExperiment.getExperimentId());
    }

    List<ConditionalUserProperty> experimentsInAnalytics = getAllExperimentsInAnalytics();
    Set<String> idsOfExperimentsInAnalytics = new HashSet<>();
    for (ConditionalUserProperty experimentInAnalytics : experimentsInAnalytics) {
      idsOfExperimentsInAnalytics.add(experimentInAnalytics.name);
    }

    List<ConditionalUserProperty> experimentsToRemove =
        getExperimentsToRemove(experimentsInAnalytics, replacementExperimentIds);
    removeExperiments(experimentsToRemove);

    List<AbtExperimentInfo> experimentsToAdd =
        getExperimentsToAdd(replacementExperiments, idsOfExperimentsInAnalytics);
    addExperiments(experimentsToAdd);
  }

  /** Returns this origin's experiments in Analytics that are no longer assigned to this App. */
  private ArrayList<ConditionalUserProperty> getExperimentsToRemove(
      List<ConditionalUserProperty> experimentsInAnalytics, Set<String> replacementExperimentIds) {

    ArrayList<ConditionalUserProperty> experimentsToRemove = new ArrayList<>();
    for (ConditionalUserProperty experimentInAnalytics : experimentsInAnalytics) {
      if (!replacementExperimentIds.contains(experimentInAnalytics.name)) {
        experimentsToRemove.add(experimentInAnalytics);
      }
    }
    return experimentsToRemove;
  }

  /**
   * Returns the new experiments in the specified {@link AbtExperimentInfo}s that need to be added
   * to this origin's list of experiments in Analytics.
   */
  private ArrayList<AbtExperimentInfo> getExperimentsToAdd(
      List<AbtExperimentInfo> replacementExperiments, Set<String> idsOfExperimentsInAnalytics) {

    ArrayList<AbtExperimentInfo> experimentsToAdd = new ArrayList<>();
    for (AbtExperimentInfo replacementExperiment : replacementExperiments) {
      if (!idsOfExperimentsInAnalytics.contains(replacementExperiment.getExperimentId())) {
        experimentsToAdd.add(replacementExperiment);
      }
    }
    return experimentsToAdd;
  }

  /** Adds the given experiments to the origin's list in Analytics. */
  private void addExperiments(List<AbtExperimentInfo> experimentsToAdd) {

    Deque<ConditionalUserProperty> dequeOfExperimentsInAnalytics =
        new ArrayDeque<>(getAllExperimentsInAnalytics());

    int fetchedMaxUserProperties = getMaxUserPropertiesInAnalytics();

    for (AbtExperimentInfo experimentToAdd : experimentsToAdd) {
      while (dequeOfExperimentsInAnalytics.size() >= fetchedMaxUserProperties) {
        removeExperimentFromAnalytics(dequeOfExperimentsInAnalytics.pollFirst().name);
      }

      ConditionalUserProperty experiment = experimentToAdd.toConditionalUserProperty(originService);
      addExperimentToAnalytics(experiment);
      dequeOfExperimentsInAnalytics.offer(experiment);
    }
  }

  private void removeExperiments(Collection<ConditionalUserProperty> experiments) {
    for (ConditionalUserProperty experiment : experiments) {
      removeExperimentFromAnalytics(experiment.name);
    }
  }

  /**
   * Returns the {@link List} of {@link AbtExperimentInfo} converted from the {@link List} of
   * experiment info {@link Map}s.
   */
  private static List<AbtExperimentInfo> convertMapsToExperimentInfos(
      List<Map<String, String>> replacementExperimentsMaps) throws AbtException {

    List<AbtExperimentInfo> replacementExperimentInfos = new ArrayList<>();
    for (Map<String, String> replacementExperimentMap : replacementExperimentsMaps) {
      replacementExperimentInfos.add(AbtExperimentInfo.fromMap(replacementExperimentMap));
    }
    return replacementExperimentInfos;
  }

  private void addExperimentToAnalytics(ConditionalUserProperty experiment) {
    analyticsConnector.setConditionalUserProperty(experiment);
  }

  private void throwAbtExceptionIfAnalyticsIsNull() throws AbtException {
    if (analyticsConnector == null) {
      throw new AbtException(
          "The Analytics SDK is not available. "
              + "Please check that the Analytics SDK is included in your app dependencies.");
    }
  }

  /**
   * The method takes a String instead of a {@link ConditionalUserProperty} to make it easier to
   * test. The method itself is tested to make it easier to figure out whether part of ABT is
   * breaking, or if the underlying Analytics clear method is failing.
   */
  private void removeExperimentFromAnalytics(String experimentId) {
    analyticsConnector.clearConditionalUserProperty(
        experimentId, /*clearEventName=*/ null, /*clearEventParams=*/ null);
  }

  @WorkerThread
  private int getMaxUserPropertiesInAnalytics() {
    if (maxUserProperties == null) {
      maxUserProperties = analyticsConnector.getMaxUserProperties(originService);
    }
    return maxUserProperties;
  }

  /**
   * Returns a list of all this origin's experiments in this App's Analytics SDK.
   *
   * <p>The list is sorted chronologically by the experiment start time, with the oldest experiment
   * at index 0.
   */
  @WorkerThread
  private List<ConditionalUserProperty> getAllExperimentsInAnalytics() {
    return analyticsConnector.getConditionalUserProperties(
        originService, /*propertyNamePrefix=*/ "");
  }
}
