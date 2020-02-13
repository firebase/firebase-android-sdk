// Copyright 2019 Google LLC
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import com.google.firebase.abt.FirebaseABTesting.OriginService;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.analytics.connector.AnalyticsConnector.ConditionalUserProperty;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Unit tests for {@link FirebaseABTesting}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FirebaseABTestingTest {

  private static final String ORIGIN_SERVICE = OriginService.REMOTE_CONFIG;

  private static final String VARIANT_ID_VALUE = "var1";
  private static final String TRIGGER_EVENT_NAME_VALUE = "trigger_event_value";
  private static final long TRIGGER_TIMEOUT_IN_MILLIS_VALUE = 1000L;
  private static final long TIME_TO_LIVE_IN_MILLIS_VALUE = 2000L;

  private static final String TEST_EXPERIMENT_1_ID = "1";
  private static final String TEST_EXPERIMENT_2_ID = "2";

  private static final AbtExperimentInfo TEST_ABT_EXPERIMENT_1 =
      createExperimentInfo(
          TEST_EXPERIMENT_1_ID,
          /*triggerEventName=*/ "",
          /*experimentStartTimeInEpochMillis=*/ 1000L);
  private static final AbtExperimentInfo TEST_ABT_EXPERIMENT_2 =
      createExperimentInfo(
          TEST_EXPERIMENT_2_ID, "trigger_event_2", /*experimentStartTimeInEpochMillis=*/ 2000L);

  private static final int MAX_ALLOWED_EXPERIMENTS_IN_ANALYTICS = 100;

  private FirebaseABTesting firebaseAbt;

  @Mock AnalyticsConnector mockAnalyticsConnector;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    firebaseAbt =
        new FirebaseABTesting(
            /*unusedAppContext=*/ null,
            /*analyticsConnector=*/ mockAnalyticsConnector,
            ORIGIN_SERVICE);

    when(mockAnalyticsConnector.getMaxUserProperties(ORIGIN_SERVICE))
        .thenReturn(MAX_ALLOWED_EXPERIMENTS_IN_ANALYTICS);
  }

  @Test
  public void replaceAllExperiments_noExperimentsInAnalytics_experimentsCorrectlySetInAnalytics()
      throws Exception {
    when(mockAnalyticsConnector.getConditionalUserProperties(ORIGIN_SERVICE, ""))
        .thenReturn(new ArrayList<>());

    firebaseAbt.replaceAllExperiments(
        Lists.newArrayList(
            TEST_ABT_EXPERIMENT_1.toStringMap(), TEST_ABT_EXPERIMENT_2.toStringMap()));

    ArgumentCaptor<ConditionalUserProperty> analyticsExperimentArgumentCaptor =
        ArgumentCaptor.forClass(ConditionalUserProperty.class);
    verify(mockAnalyticsConnector, never()).clearConditionalUserProperty(any(), any(), any());
    verify(mockAnalyticsConnector, times(2))
        .setConditionalUserProperty(analyticsExperimentArgumentCaptor.capture());

    List<ConditionalUserProperty> actualValues = analyticsExperimentArgumentCaptor.getAllValues();
    AbtExperimentInfo analyticsExperiment1 =
        AbtExperimentInfo.fromConditionalUserProperty(actualValues.get(0));
    AbtExperimentInfo analyticsExperiment2 =
        AbtExperimentInfo.fromConditionalUserProperty(actualValues.get(1));

    // Validates that TEST_ABT_EXPERIMENT_1 and TEST_ABT_EXPERIMENT_2 have been set in Analytics.
    assertThat(analyticsExperiment1.toStringMap()).isEqualTo(TEST_ABT_EXPERIMENT_1.toStringMap());
    assertThat(analyticsExperiment2.toStringMap()).isEqualTo(TEST_ABT_EXPERIMENT_2.toStringMap());
  }

  @Test
  public void replaceAllExperiments_existExperimentsInAnalytics_experimentsCorrectlySetInAnalytics()
      throws Exception {
    when(mockAnalyticsConnector.getConditionalUserProperties(ORIGIN_SERVICE, ""))
        .thenReturn(
            Lists.newArrayList(
                TEST_ABT_EXPERIMENT_1.toConditionalUserProperty(ORIGIN_SERVICE),
                TEST_ABT_EXPERIMENT_2.toConditionalUserProperty(ORIGIN_SERVICE)));

    AbtExperimentInfo newExperiment3 = createExperimentInfo("3", "", 1000L);
    AbtExperimentInfo newExperiment4 = createExperimentInfo("4", "trigger_event_4", 1000L);

    // Simulates the case where experiment 1 is assigned (as before), experiment 2 is no longer
    // assigned; experiment 3 and experiment 4 are newly assigned.
    firebaseAbt.replaceAllExperiments(
        Lists.newArrayList(
            TEST_ABT_EXPERIMENT_1.toStringMap(),
            newExperiment3.toStringMap(),
            newExperiment4.toStringMap()));

    // Validates that experiment 2 is cleared and experiment 3 and experiment 4 are set in
    // Analytics.
    ArgumentCaptor<ConditionalUserProperty> analyticsExperimentArgumentCaptor =
        ArgumentCaptor.forClass(ConditionalUserProperty.class);
    verify(mockAnalyticsConnector, times(1))
        .clearConditionalUserProperty(TEST_EXPERIMENT_2_ID, null, null);
    verify(mockAnalyticsConnector, times(2))
        .setConditionalUserProperty(analyticsExperimentArgumentCaptor.capture());

    List<ConditionalUserProperty> actualValues = analyticsExperimentArgumentCaptor.getAllValues();
    assertThat(AbtExperimentInfo.fromConditionalUserProperty(actualValues.get(0)).toStringMap())
        .isEqualTo(newExperiment3.toStringMap());
    assertThat(AbtExperimentInfo.fromConditionalUserProperty(actualValues.get(1)).toStringMap())
        .isEqualTo(newExperiment4.toStringMap());
  }

  @Test
  public void replaceAllExperiments_totalExperimentsExceedsAnalyticsLimit_oldExperimentsDiscarded()
      throws Exception {
    // Set max allowed experiments in Analytics to 3.
    when(mockAnalyticsConnector.getMaxUserProperties(ORIGIN_SERVICE)).thenReturn(3);
    when(mockAnalyticsConnector.getConditionalUserProperties(ORIGIN_SERVICE, ""))
        .thenReturn(
            Lists.newArrayList(
                TEST_ABT_EXPERIMENT_1.toConditionalUserProperty(ORIGIN_SERVICE),
                TEST_ABT_EXPERIMENT_2.toConditionalUserProperty(ORIGIN_SERVICE)));

    AbtExperimentInfo newExperiment3 = createExperimentInfo("3", "", 1000L);
    AbtExperimentInfo newExperiment4 = createExperimentInfo("4", "trigger_event_4", 1000L);

    // Simulates the case where experiment 1 and 2 are assigned (as before), experiment 3 and
    // experiment 4 are newly assigned.
    firebaseAbt.replaceAllExperiments(
        Lists.newArrayList(
            TEST_ABT_EXPERIMENT_1.toStringMap(),
            TEST_ABT_EXPERIMENT_2.toStringMap(),
            newExperiment3.toStringMap(),
            newExperiment4.toStringMap()));

    // Validates that experiment 1 is cleared (discarded) and experiment 3 and experiment 4 are set
    // in Analytics.
    ArgumentCaptor<ConditionalUserProperty> analyticsExperimentArgumentCaptor =
        ArgumentCaptor.forClass(ConditionalUserProperty.class);
    verify(mockAnalyticsConnector, times(1))
        .clearConditionalUserProperty(TEST_EXPERIMENT_1_ID, null, null);
    verify(mockAnalyticsConnector, times(2))
        .setConditionalUserProperty(analyticsExperimentArgumentCaptor.capture());

    List<ConditionalUserProperty> actualValues = analyticsExperimentArgumentCaptor.getAllValues();
    assertThat(AbtExperimentInfo.fromConditionalUserProperty(actualValues.get(0)).toStringMap())
        .isEqualTo(newExperiment3.toStringMap());
    assertThat(AbtExperimentInfo.fromConditionalUserProperty(actualValues.get(1)).toStringMap())
        .isEqualTo(newExperiment4.toStringMap());
  }

  @Test
  public void replaceAllExperiments_analyticsSdkUnavailable_throwsAbtException() {
    firebaseAbt =
        new FirebaseABTesting(
            /*unusedAppContext=*/ null, /*analyticsConnector=*/ null, ORIGIN_SERVICE);

    AbtException actualException =
        assertThrows(
            AbtException.class,
            () -> firebaseAbt.replaceAllExperiments(/*replacementExperiments=*/ null));

    assertThat(actualException).hasMessageThat().contains("The Analytics SDK is not available");
    verify(mockAnalyticsConnector, never()).setConditionalUserProperty(any());
    verify(mockAnalyticsConnector, never()).clearConditionalUserProperty(any(), any(), any());
  }

  @Test
  public void replaceAllExperiments_experimentsParamNull_throwsAbtException() {
    IllegalArgumentException actualException =
        assertThrows(
            IllegalArgumentException.class,
            () -> firebaseAbt.replaceAllExperiments(/*replacementExperiments=*/ null));

    assertThat(actualException)
        .hasMessageThat()
        .contains("The replacementExperiments list is null");
  }

  @Test
  public void removeAllExperiments_noExperimentsInAnalytics_noExperimentsCleared()
      throws Exception {
    when(mockAnalyticsConnector.getConditionalUserProperties(ORIGIN_SERVICE, ""))
        .thenReturn(new ArrayList<>());

    firebaseAbt.removeAllExperiments();

    verify(mockAnalyticsConnector, never()).clearConditionalUserProperty(any(), any(), any());
  }

  @Test
  public void removeAllExperiments_existExperimentsInAnalytics_experimentsClearedFromAnalytics()
      throws Exception {
    when(mockAnalyticsConnector.getConditionalUserProperties(ORIGIN_SERVICE, ""))
        .thenReturn(
            Lists.newArrayList(
                TEST_ABT_EXPERIMENT_1.toConditionalUserProperty(ORIGIN_SERVICE),
                TEST_ABT_EXPERIMENT_2.toConditionalUserProperty(ORIGIN_SERVICE)));

    firebaseAbt.removeAllExperiments();

    verify(mockAnalyticsConnector).clearConditionalUserProperty(TEST_EXPERIMENT_1_ID, null, null);
    verify(mockAnalyticsConnector).clearConditionalUserProperty(TEST_EXPERIMENT_2_ID, null, null);
  }

  @Test
  public void removeAllExperiments_analyticsSdkUnavailable_throwsAbtException() {
    firebaseAbt =
        new FirebaseABTesting(
            /*unusedAppContext=*/ null, /*analyticsConnector=*/ null, ORIGIN_SERVICE);

    AbtException actualException =
        assertThrows(AbtException.class, () -> firebaseAbt.removeAllExperiments());

    assertThat(actualException).hasMessageThat().contains("he Analytics SDK is not available");
  }

  @Test
  public void getAllExperiments_noExperimentsInAnalytics_returnEmpty() throws Exception {
    when(mockAnalyticsConnector.getConditionalUserProperties(ORIGIN_SERVICE, ""))
        .thenReturn(new ArrayList<>());

    assertThat(firebaseAbt.getAllExperiments()).isEmpty();
  }

  @Test
  public void getAllExperiments_existExperimentsInAnalytics_returnAllExperiments()
      throws Exception {
    when(mockAnalyticsConnector.getConditionalUserProperties(ORIGIN_SERVICE, ""))
        .thenReturn(
            Lists.newArrayList(
                TEST_ABT_EXPERIMENT_1.toConditionalUserProperty(ORIGIN_SERVICE),
                TEST_ABT_EXPERIMENT_2.toConditionalUserProperty(ORIGIN_SERVICE)));

    List<AbtExperimentInfo> abtExperimentInfoList = firebaseAbt.getAllExperiments();

    assertThat(abtExperimentInfoList).hasSize(2);
    assertThat(abtExperimentInfoList.get(0).toStringMap())
        .isEqualTo(TEST_ABT_EXPERIMENT_1.toStringMap());
    assertThat(abtExperimentInfoList.get(1).toStringMap())
        .isEqualTo(TEST_ABT_EXPERIMENT_2.toStringMap());
  }

  @Test
  public void getAllExperiments_analyticsSdkUnavailable_throwsAbtException() {
    firebaseAbt =
        new FirebaseABTesting(
            /*unusedAppContext=*/ null, /*analyticsConnector=*/ null, ORIGIN_SERVICE);

    AbtException actualException =
        assertThrows(AbtException.class, () -> firebaseAbt.getAllExperiments());

    assertThat(actualException).hasMessageThat().contains("he Analytics SDK is not available");
  }

  @Test
  public void
      validateRunningExperiments_inactiveExperimentsInAnalytics_cleansUpInactiveExperiments()
          throws Exception {
    // Two experiments running
    when(mockAnalyticsConnector.getConditionalUserProperties(ORIGIN_SERVICE, ""))
        .thenReturn(
            Lists.newArrayList(
                TEST_ABT_EXPERIMENT_1.toConditionalUserProperty(ORIGIN_SERVICE),
                TEST_ABT_EXPERIMENT_2.toConditionalUserProperty(ORIGIN_SERVICE)));

    // Update to just one experiment running
    firebaseAbt.validateRunningExperiments(Lists.newArrayList(TEST_ABT_EXPERIMENT_1));

    // Verify the not running experiment is cleared
    verify(mockAnalyticsConnector).clearConditionalUserProperty(TEST_EXPERIMENT_2_ID, null, null);
  }

  @Test
  public void validateRunningExperiments_noinactiveExperimentsInAnalytics_cleansUpNothing()
      throws Exception {
    // Two experiments running
    when(mockAnalyticsConnector.getConditionalUserProperties(ORIGIN_SERVICE, ""))
        .thenReturn(
            Lists.newArrayList(
                TEST_ABT_EXPERIMENT_1.toConditionalUserProperty(ORIGIN_SERVICE),
                TEST_ABT_EXPERIMENT_2.toConditionalUserProperty(ORIGIN_SERVICE)));

    // Update still says the same two experiments are running
    firebaseAbt.validateRunningExperiments(
        Lists.newArrayList(TEST_ABT_EXPERIMENT_1, TEST_ABT_EXPERIMENT_2));

    // Verify nothing cleared
    verify(mockAnalyticsConnector, never()).clearConditionalUserProperty(any(), any(), any());
  }

  @Test
  public void reportActiveExperiment_setsNullTriggerCondition() throws Exception {

    // Set trigger event on exp 1
    Map<String, String> EXP1_MAP = TEST_ABT_EXPERIMENT_1.toStringMap();
    EXP1_MAP.put(AbtExperimentInfo.TRIGGER_EVENT_KEY, "walrus_event");

    // Report experiment as active
    firebaseAbt.reportActiveExperiment(AbtExperimentInfo.fromMap(EXP1_MAP));

    // capture conditional user property set in analytics
    ArgumentCaptor<ConditionalUserProperty> argumentCaptor =
        ArgumentCaptor.forClass(ConditionalUserProperty.class);
    verify(mockAnalyticsConnector).setConditionalUserProperty(argumentCaptor.capture());
    ConditionalUserProperty conditionalUserProperty = argumentCaptor.getValue();

    // verify property has a null trigger event (i.e is active)
    assertThat(conditionalUserProperty.triggerEventName).isNull();
  }

  private static AbtExperimentInfo createExperimentInfo(
      String experimentId, String triggerEventName, long experimentStartTimeInEpochMillis) {

    return new AbtExperimentInfo(
        experimentId,
        VARIANT_ID_VALUE,
        triggerEventName,
        new Date(experimentStartTimeInEpochMillis),
        TRIGGER_TIMEOUT_IN_MILLIS_VALUE,
        TIME_TO_LIVE_IN_MILLIS_VALUE);
  }
}
