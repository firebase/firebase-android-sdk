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

import android.text.TextUtils;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.abt.FirebaseABTesting.OriginService;
import com.google.firebase.analytics.connector.AnalyticsConnector.ConditionalUserProperty;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A set of values describing an ABT experiment. The values are sent to the Analytics SDK for
 * tracking when an experiment is applied on an App instance.
 *
 * <p>The experiment info is expected to be in a {@code {@link Map}<String, String>} format. All
 * such maps must contain all the keys in {@link #ALL_REQUIRED_KEYS}; if a key is missing, an {@link
 * AbtException} will be thrown. Any keys not defined in {@link #ALL_REQUIRED_KEYS} will be ignored.
 *
 * <p>Changes in the values returned by the ABT server and client SDKs must be reflected here
 */
public class AbtExperimentInfo {

  /**
   * The experiment id key.
   *
   * <p>An experiment id is unique within a Firebase project and is assigned by the ABT service.
   */
  @VisibleForTesting static final String EXPERIMENT_ID_KEY = "experimentId";

  /**
   * The variant id key.
   *
   * <p>A variant id determines which variant of the experiment an App instance belongs to and is
   * assigned by the ABT service.
   */
  @VisibleForTesting static final String VARIANT_ID_KEY = "variantId";

  /**
   * The trigger event key.
   *
   * <p>The ABT server does not pass this key if the value is empty, so it is optional.
   *
   * <p>The occurrence of a trigger event activates the experiment for an App instance.
   */
  @VisibleForTesting static final String TRIGGER_EVENT_KEY = "triggerEvent";

  /**
   * The experiment start time key.
   *
   * <p>The experiment start time is the point in time when the experiment was started in the
   * Firebase console. The start time must be in an ISO 8601 compliant format.
   */
  @VisibleForTesting static final String EXPERIMENT_START_TIME_KEY = "experimentStartTime";

  /**
   * The trigger timeout key.
   *
   * <p>A trigger timeout defines how long an experiment can run in an App instance without being
   * triggered. The timeout must be in milliseconds and convertible into a {@code long}.
   */
  @VisibleForTesting static final String TRIGGER_TIMEOUT_KEY = "triggerTimeoutMillis";

  /**
   * The time to live key.
   *
   * <p>A time to live defines how long an experiment can run in an App instance. The time must be
   * in milliseconds and convertible into a {@code long}.
   */
  @VisibleForTesting static final String TIME_TO_LIVE_KEY = "timeToLiveMillis";

  /** The set of all keys required by the ABT SDK to define an experiment. */
  private static final String[] ALL_REQUIRED_KEYS = {
    EXPERIMENT_ID_KEY,
    EXPERIMENT_START_TIME_KEY,
    TIME_TO_LIVE_KEY,
    TRIGGER_TIMEOUT_KEY,
    VARIANT_ID_KEY,
  };

  /**
   * The String format of a protobuf Timestamp; the format is ISO 8601 compliant.
   *
   * <p>The protobuf Timestamp field gets converted to an ISO 8601 string when returned as JSON. For
   * example, the Firebase Remote Config backend sends experiment start time as a Timestamp field,
   * which gets converted to an ISO 8601 string when sent as JSON.
   */
  @VisibleForTesting
  static final DateFormat protoTimestampStringParser =
      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);

  /** The experiment id as defined by the ABT backend. */
  private final String experimentId;

  /** The id of the variant of this experiment the current App instance has been assigned to. */
  private final String variantId;

  /** The name of the event that will trigger the activation of this experiment. */
  private final String triggerEventName;

  /** The start time of this experiment. */
  private final Date experimentStartTime;

  /** The amount of time, in milliseconds, before the trigger for this experiment expires. */
  private final long triggerTimeoutInMillis;

  /** The amount of time, in milliseconds, before the experiment expires for this App instance. */
  private final long timeToLiveInMillis;

  /** Creates an instance of {@link AbtExperimentInfo} with all the required keys. */
  public AbtExperimentInfo(
      String experimentId,
      String variantId,
      String triggerEventName,
      Date experimentStartTime,
      long triggerTimeoutInMillis,
      long timeToLiveInMillis) {

    this.experimentId = experimentId;
    this.variantId = variantId;
    this.triggerEventName = triggerEventName;
    this.experimentStartTime = experimentStartTime;
    this.triggerTimeoutInMillis = triggerTimeoutInMillis;
    this.timeToLiveInMillis = timeToLiveInMillis;
  }

  /**
   * Converts a map of strings containing an ABT experiment's tracking information into an instance
   * of {@link AbtExperimentInfo}.
   *
   * @param experimentInfoMap A {@link Map} that contains all the keys specified in {@link
   *     #ALL_REQUIRED_KEYS}. The values of each key must be convertible to the appropriate type,
   *     e.g., the value for {@link #EXPERIMENT_START_TIME_KEY} must be an ISO 8601 Date string.
   * @return An {@link AbtExperimentInfo} with the values of the experiment in {@code
   *     experimentInfoMap}.
   * @throws AbtException If one of the keys is missing, or any of the values cannot be converted to
   *     their appropriate type.
   */
  static AbtExperimentInfo fromMap(Map<String, String> experimentInfoMap) throws AbtException {

    validateExperimentInfoMap(experimentInfoMap);

    try {
      Date experimentStartTime =
          protoTimestampStringParser.parse(experimentInfoMap.get(EXPERIMENT_START_TIME_KEY));
      long triggerTimeoutInMillis = Long.parseLong(experimentInfoMap.get(TRIGGER_TIMEOUT_KEY));
      long timeToLiveInMillis = Long.parseLong(experimentInfoMap.get(TIME_TO_LIVE_KEY));

      return new AbtExperimentInfo(
          experimentInfoMap.get(EXPERIMENT_ID_KEY),
          experimentInfoMap.get(VARIANT_ID_KEY),
          experimentInfoMap.containsKey(TRIGGER_EVENT_KEY)
              ? experimentInfoMap.get(TRIGGER_EVENT_KEY)
              : "",
          experimentStartTime,
          triggerTimeoutInMillis,
          timeToLiveInMillis);
    } catch (ParseException e) {
      throw new AbtException(
          "Could not process experiment: parsing experiment start time failed.", e);
    } catch (NumberFormatException e) {
      throw new AbtException(
          "Could not process experiment: one of the durations could not be converted into a long.",
          e);
    }
  }

  /** Returns the id of this experiment. */
  String getExperimentId() {
    return experimentId;
  }

  /** Returns the id of the variant this App instance got assigned to. */
  String getVariantId() {
    return variantId;
  }

  /** Returns the name of the event that will trigger the activation of this experiment. */
  String getTriggerEventName() {
    return triggerEventName;
  }

  /** Returns the time the experiment was started, in millis since epoch. */
  long getStartTimeInMillisSinceEpoch() {
    return experimentStartTime.getTime();
  }

  /** Returns the amount of time before the trigger event expires for this experiment. */
  long getTriggerTimeoutInMillis() {
    return triggerTimeoutInMillis;
  }

  /** Returns the amount of time before the experiment expires in this App instance. */
  long getTimeToLiveInMillis() {
    return timeToLiveInMillis;
  }

  /**
   * Verifies that {@code experimentInfoMap} contains all the keys in {@link #ALL_REQUIRED_KEYS}.
   *
   * @throws AbtException If {@code experimentInfoMap} is missing a key.
   */
  private static void validateExperimentInfoMap(Map<String, String> experimentInfoMap)
      throws AbtException {

    List<String> missingKeys = new ArrayList<>();
    for (String key : ALL_REQUIRED_KEYS) {
      if (!experimentInfoMap.containsKey(key)) {
        missingKeys.add(key);
      }
    }

    if (!missingKeys.isEmpty()) {
      throw new AbtException(
          String.format(
              "The following keys are missing from the experiment info map: %s", missingKeys));
    }
  }

  static void validateAbtExperimentInfo(AbtExperimentInfo experimentInfo) throws AbtException {
    validateExperimentInfoMap(experimentInfo.toStringMap());
  }

  /**
   * Used for testing {@code FirebaseABTesting#replaceAllExperiments(List)} without leaking the
   * implementation details of this class.
   */
  @VisibleForTesting
  Map<String, String> toStringMap() {

    Map<String, String> experimentInfoMap = new HashMap<>();

    experimentInfoMap.put(EXPERIMENT_ID_KEY, experimentId);
    experimentInfoMap.put(VARIANT_ID_KEY, variantId);
    experimentInfoMap.put(TRIGGER_EVENT_KEY, triggerEventName);
    experimentInfoMap.put(
        EXPERIMENT_START_TIME_KEY, protoTimestampStringParser.format(experimentStartTime));
    experimentInfoMap.put(TRIGGER_TIMEOUT_KEY, Long.toString(triggerTimeoutInMillis));
    experimentInfoMap.put(TIME_TO_LIVE_KEY, Long.toString(timeToLiveInMillis));

    return experimentInfoMap;
  }

  /**
   * Returns the {@link ConditionalUserProperty} created from the specified {@link
   * AbtExperimentInfo}.
   */
  ConditionalUserProperty toConditionalUserProperty(@OriginService String originService) {
    ConditionalUserProperty conditionalUserProperty = new ConditionalUserProperty();

    conditionalUserProperty.origin = originService;
    conditionalUserProperty.creationTimestamp = getStartTimeInMillisSinceEpoch();
    conditionalUserProperty.name = experimentId;
    conditionalUserProperty.value = variantId;

    // For a conditional user property to be immediately activated/triggered, its trigger
    // event needs to be null, not just an empty string.
    conditionalUserProperty.triggerEventName =
        TextUtils.isEmpty(triggerEventName) ? null : triggerEventName;
    conditionalUserProperty.triggerTimeout = triggerTimeoutInMillis;
    conditionalUserProperty.timeToLive = timeToLiveInMillis;

    return conditionalUserProperty;
  }

  /**
   * Returns the {@link AbtExperimentInfo} created from the specified {@link
   * ConditionalUserProperty}.
   *
   * @param conditionalUserProperty A {@link ConditionalUserProperty} that contains an ABT
   *     experiment's information.
   * @return the converted {@link AbtExperimentInfo} from {@param conditionalUserProperty}.
   */
  static AbtExperimentInfo fromConditionalUserProperty(
      ConditionalUserProperty conditionalUserProperty) {

    // Trigger event defaults to empty string if absent.
    String triggerEventName = "";
    if (conditionalUserProperty.triggerEventName != null) {
      triggerEventName = conditionalUserProperty.triggerEventName;
    }

    return new AbtExperimentInfo(
        conditionalUserProperty.name,
        String.valueOf(conditionalUserProperty.value),
        triggerEventName,
        new Date(conditionalUserProperty.creationTimestamp),
        conditionalUserProperty.triggerTimeout,
        conditionalUserProperty.timeToLive);
  }
}
