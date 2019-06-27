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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import com.google.common.base.Preconditions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.abt.FirebaseABTesting.OriginService;
import com.google.firebase.abt.component.AbtComponent;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Unit tests for {@link FirebaseABTesting} without the Analytics SDK {@link AnalyticsConnector}.
 *
 * @author Miraziz Yusupov
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FirebaseABTWithoutAnalyticsTest {

  private static final String APP_ID = "1:14368190084:android:09cb977358c6f241";
  private static final String API_KEY = "api_key";

  private static final String VARIANT_ID_VALUE = "var1";
  private static final String TRIGGER_EVENT_NAME_VALUE = "trigger_event_value";
  private static final long TRIGGER_TIMEOUT_IN_MILLIS_VALUE = 1000L;
  private static final long TIME_TO_LIVE_IN_MILLIS_VALUE = 2000L;

  private FirebaseABTesting firebaseAbt;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    initializeFirebaseApp(RuntimeEnvironment.application);

    Preconditions.checkArgument(FirebaseApp.getInstance().get(AnalyticsConnector.class) == null);

    firebaseAbt =
        FirebaseApp.getInstance().get(AbtComponent.class).get(OriginService.REMOTE_CONFIG);
  }

  @Test
  public void replaceAllExperimentsWithoutAnalytics_experimentsListIsNull_throwsAbtException() {
    AbtException actualException =
        assertThrows(
            AbtException.class,
            () -> firebaseAbt.replaceAllExperiments(/*replacementExperiments=*/ null));
    assertThat(actualException).hasMessageThat().contains("Analytics");
  }

  @Test
  public void replaceAllExperimentsWithoutAnalytics_sendsValidExperimentList_throwsAbtException() {

    List<AbtExperimentInfo> experimentInfos = new ArrayList<>();
    experimentInfos.add(
        createExperimentInfo("expid1", /*experimentStartTimeInEpochMillis=*/ 1000L));
    experimentInfos.add(
        createExperimentInfo("expid2", /*experimentStartTimeInEpochMillis=*/ 2000L));

    List<Map<String, String>> experimentInfoMaps = new ArrayList<>();
    for (AbtExperimentInfo experimentInfo : experimentInfos) {
      experimentInfoMaps.add(experimentInfo.toStringMap());
    }

    AbtException actualException =
        assertThrows(
            AbtException.class, () -> firebaseAbt.replaceAllExperiments(experimentInfoMaps));
    assertThat(actualException).hasMessageThat().contains("Analytics");
  }

  @Test
  public void removeAllExperimentsWithoutAnalytics_throwsAbtException() {
    AbtException actualException =
        assertThrows(AbtException.class, () -> firebaseAbt.removeAllExperiments());
    assertThat(actualException).hasMessageThat().contains("Analytics");
  }

  private static AbtExperimentInfo createExperimentInfo(
      String experimentId, long experimentStartTimeInEpochMillis) {

    return new AbtExperimentInfo(
        experimentId,
        VARIANT_ID_VALUE,
        TRIGGER_EVENT_NAME_VALUE,
        new Date(experimentStartTimeInEpochMillis),
        TRIGGER_TIMEOUT_IN_MILLIS_VALUE,
        TIME_TO_LIVE_IN_MILLIS_VALUE);
  }

  private static void initializeFirebaseApp(Context context) {
    FirebaseApp.clearInstancesForTest();

    FirebaseApp.initializeApp(
        context, new FirebaseOptions.Builder().setApiKey(API_KEY).setApplicationId(APP_ID).build());
  }
}
