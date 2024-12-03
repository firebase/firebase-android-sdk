// Copyright 2018 Google LLC
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

package com.google.firebase.remoteconfig.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.LAST_FETCH_STATUS_FAILURE;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.LAST_FETCH_STATUS_NO_FETCH_YET;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.LAST_FETCH_STATUS_SUCCESS;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.LAST_FETCH_STATUS_THROTTLED;
import static com.google.firebase.remoteconfig.RemoteConfigComponent.CONNECTION_TIMEOUT_IN_SECONDS;
import static com.google.firebase.remoteconfig.internal.ConfigFetchHandler.DEFAULT_MINIMUM_FETCH_INTERVAL_IN_SECONDS;
import static com.google.firebase.remoteconfig.internal.ConfigSharedPrefsClient.LAST_FETCH_TIME_IN_MILLIS_NO_FETCH_YET;
import static com.google.firebase.remoteconfig.internal.ConfigSharedPrefsClient.LAST_FETCH_TIME_NO_FETCH_YET;
import static com.google.firebase.remoteconfig.internal.ConfigSharedPrefsClient.NO_BACKOFF_TIME;
import static com.google.firebase.remoteconfig.internal.ConfigSharedPrefsClient.NO_FAILED_FETCHES;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigInfo;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.remoteconfig.internal.ConfigSharedPrefsClient.BackoffMetadata;
import com.google.firebase.remoteconfig.internal.ConfigSharedPrefsClient.LastFetchStatus;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Unit tests for the {@link ConfigSharedPrefsClient}.
 *
 * @author Miraziz Yusupov
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ConfigSharedPrefsClientTest {
  private ConfigSharedPrefsClient sharedPrefsClient;
  private FirebaseRemoteConfigSettings.Builder settingsBuilder;

  @Before
  public void setUp() {
    SharedPreferences sharedPrefs =
        ApplicationProvider.getApplicationContext()
            .getSharedPreferences("TEST_FILE_NAME", Context.MODE_PRIVATE);

    sharedPrefs.edit().clear().commit();

    sharedPrefsClient = new ConfigSharedPrefsClient(sharedPrefs);

    settingsBuilder = new FirebaseRemoteConfigSettings.Builder();
  }

  @Test
  public void getFetchTimeoutInSeconds_isNotSet_returnsDefault() {
    assertThat(sharedPrefsClient.getFetchTimeoutInSeconds())
        .isEqualTo(CONNECTION_TIMEOUT_IN_SECONDS);
  }

  @Test
  public void getFetchTimeoutInSeconds_isSetTo10Seconds_returns10Seconds() {
    long expectedFetchTimeout = 10L;
    sharedPrefsClient.setConfigSettings(
        settingsBuilder.setFetchTimeoutInSeconds(expectedFetchTimeout).build());

    long fetchTimeout = sharedPrefsClient.getFetchTimeoutInSeconds();

    assertThat(fetchTimeout).isEqualTo(expectedFetchTimeout);
  }

  @Test
  public void getMinimumFetchIntervalInSeconds_isNotSet_returnsDefault() {
    assertThat(sharedPrefsClient.getMinimumFetchIntervalInSeconds())
        .isEqualTo(DEFAULT_MINIMUM_FETCH_INTERVAL_IN_SECONDS);
  }

  @Test
  public void getMinimumFetchIntervalInSeconds_isSetTo10Seconds_returns10Seconds() {
    long expectedMinimumFetchInterval = 10L;
    sharedPrefsClient.setConfigSettings(
        settingsBuilder.setMinimumFetchIntervalInSeconds(expectedMinimumFetchInterval).build());

    long minimumFetchInterval = sharedPrefsClient.getMinimumFetchIntervalInSeconds();

    assertThat(minimumFetchInterval).isEqualTo(expectedMinimumFetchInterval);
  }

  @Test
  public void getLastFetchStatus_isNotSet_returnsZero() {
    assertThat(sharedPrefsClient.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_NO_FETCH_YET);
  }

  @Test
  public void getLastFetchStatus_isSetToSuccess_returnsSuccess() {
    sharedPrefsClient.updateLastFetchAsSuccessfulAt(new Date(100L));

    @LastFetchStatus int lastFetchStatus = sharedPrefsClient.getLastFetchStatus();

    assertThat(lastFetchStatus).isEqualTo(LAST_FETCH_STATUS_SUCCESS);
  }

  @Test
  public void getLastSuccessfulFetchTime_isNotSet_returnsZero() {
    assertThat(sharedPrefsClient.getLastSuccessfulFetchTime())
        .isEqualTo(LAST_FETCH_TIME_NO_FETCH_YET);
  }

  @Test
  public void getLastSuccessfulFetchTime_isSet_returnsTime() {
    Date fetchTime = new Date(1000L);
    sharedPrefsClient.updateLastFetchAsSuccessfulAt(fetchTime);

    Date lastSuccessfulFetchTime = sharedPrefsClient.getLastSuccessfulFetchTime();

    assertThat(lastSuccessfulFetchTime).isEqualTo(fetchTime);
  }

  @Test
  public void getLastFetchETag_isNotSet_returnsEmptyString() {
    assertThat(sharedPrefsClient.getLastFetchETag()).isNull();
  }

  @Test
  public void getLastFetchETag_isSet_returnsETag() {
    String expectedETag = "an etag";
    sharedPrefsClient.setLastFetchETag(expectedETag);

    String eTag = sharedPrefsClient.getLastFetchETag();

    assertThat(eTag).isEqualTo(expectedETag);
  }

  @Test
  public void getLastTemplateVersion_isNotSet_returnsDefault() {
    assertThat(sharedPrefsClient.getLastTemplateVersion()).isEqualTo(0);
  }

  @Test
  public void getLastTemplateVersion_isSet_returnsTemplateVersion() {
    sharedPrefsClient.setLastTemplateVersion(1);
    assertThat(sharedPrefsClient.getLastTemplateVersion()).isEqualTo(1);
  }

  @Test
  public void getRealtimeBackoffMetadata_isNotSet_returnsNoFailedStreamsAndNotThrottled() {
    ConfigSharedPrefsClient.RealtimeBackoffMetadata defaultRealtimeBackoffMetadata =
        sharedPrefsClient.getRealtimeBackoffMetadata();

    assertThat(defaultRealtimeBackoffMetadata.getNumFailedStreams()).isEqualTo(NO_FAILED_FETCHES);
    assertThat(defaultRealtimeBackoffMetadata.getBackoffEndTime()).isEqualTo(NO_BACKOFF_TIME);
  }

  @Test
  public void getRealtimeBackoffMetadata_hasValues_returnsValues() {
    int numFailedStreams = 5;
    Date backoffEndTime = new Date(1000L);
    sharedPrefsClient.setRealtimeBackoffMetadata(numFailedStreams, backoffEndTime);

    ConfigSharedPrefsClient.RealtimeBackoffMetadata backoffMetadata =
        sharedPrefsClient.getRealtimeBackoffMetadata();

    assertThat(backoffMetadata.getNumFailedStreams()).isEqualTo(numFailedStreams);
    assertThat(backoffMetadata.getBackoffEndTime()).isEqualTo(backoffEndTime);
  }

  @Test
  public void resetRealtimeBackoff_hasValues_clearsAllValues() {
    sharedPrefsClient.setRealtimeBackoffMetadata(
        /* numFailedStreams= */ 5, /* backoffEndTime= */ new Date(1000L));

    ConfigSharedPrefsClient.RealtimeBackoffMetadata realtimeBackoffMetadata =
        sharedPrefsClient.getRealtimeBackoffMetadata();
    Preconditions.checkArgument(realtimeBackoffMetadata.getNumFailedStreams() != NO_FAILED_FETCHES);
    Preconditions.checkArgument(
        !realtimeBackoffMetadata.getBackoffEndTime().equals(NO_BACKOFF_TIME));

    sharedPrefsClient.resetRealtimeBackoff();

    ConfigSharedPrefsClient.RealtimeBackoffMetadata resetMetadata =
        sharedPrefsClient.getRealtimeBackoffMetadata();
    assertThat(resetMetadata.getNumFailedStreams()).isEqualTo(NO_FAILED_FETCHES);
    assertThat(resetMetadata.getBackoffEndTime()).isEqualTo(NO_BACKOFF_TIME);
  }

  @Test
  public void getBackoffMetadata_isNotSet_returnsNoFailedFetchesAndNotThrottled() {
    BackoffMetadata defaultBackoffMetadata = sharedPrefsClient.getBackoffMetadata();

    assertThat(defaultBackoffMetadata.getNumFailedFetches()).isEqualTo(NO_FAILED_FETCHES);
    assertThat(defaultBackoffMetadata.getBackoffEndTime()).isEqualTo(NO_BACKOFF_TIME);
  }

  @Test
  public void getBackoffMetadata_hasValues_returnsValues() {
    int numFailedFetches = 5;
    Date backoffEndTime = new Date(1000L);
    sharedPrefsClient.setBackoffMetadata(numFailedFetches, backoffEndTime);

    BackoffMetadata backoffMetadata = sharedPrefsClient.getBackoffMetadata();

    assertThat(backoffMetadata.getNumFailedFetches()).isEqualTo(numFailedFetches);
    assertThat(backoffMetadata.getBackoffEndTime()).isEqualTo(backoffEndTime);
  }

  @Test
  public void resetBackoff_hasValues_clearsAllValues() {
    sharedPrefsClient.setBackoffMetadata(
        /* numFailedFetches= */ 5, /* backoffEndTime= */ new Date(1000L));

    BackoffMetadata backoffMetadata = sharedPrefsClient.getBackoffMetadata();
    Preconditions.checkArgument(backoffMetadata.getNumFailedFetches() != NO_FAILED_FETCHES);
    Preconditions.checkArgument(!backoffMetadata.getBackoffEndTime().equals(NO_BACKOFF_TIME));

    sharedPrefsClient.resetBackoff();

    BackoffMetadata resetMetadata = sharedPrefsClient.getBackoffMetadata();
    assertThat(resetMetadata.getNumFailedFetches()).isEqualTo(NO_FAILED_FETCHES);
    assertThat(resetMetadata.getBackoffEndTime()).isEqualTo(NO_BACKOFF_TIME);
  }

  @Test
  public void getInfo_hasNoSetValues_returnsDefaults() {
    FirebaseRemoteConfigInfo info = sharedPrefsClient.getInfo();

    assertThat(info.getFetchTimeMillis()).isEqualTo(LAST_FETCH_TIME_IN_MILLIS_NO_FETCH_YET);
    assertThat(info.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_NO_FETCH_YET);
    assertThat(info.getConfigSettings().getFetchTimeoutInSeconds())
        .isEqualTo(CONNECTION_TIMEOUT_IN_SECONDS);
    assertThat(info.getConfigSettings().getMinimumFetchIntervalInSeconds())
        .isEqualTo(DEFAULT_MINIMUM_FETCH_INTERVAL_IN_SECONDS);
  }

  @Test
  public void getInfo_hasSetValues_returnsValues() {
    Date lastSuccessfulFetchTime = new Date(1000L);
    sharedPrefsClient.updateLastFetchAsSuccessfulAt(lastSuccessfulFetchTime);
    sharedPrefsClient.updateLastFetchAsFailed();

    long fetchTimeout = 666L;
    long minimumFetchInterval = 666L;
    sharedPrefsClient.setConfigSettings(
        new FirebaseRemoteConfigSettings.Builder()
            .setFetchTimeoutInSeconds(fetchTimeout)
            .setMinimumFetchIntervalInSeconds(minimumFetchInterval)
            .build());

    FirebaseRemoteConfigInfo info = sharedPrefsClient.getInfo();

    assertThat(info.getFetchTimeMillis()).isEqualTo(lastSuccessfulFetchTime.getTime());
    assertThat(info.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_FAILURE);
    assertThat(info.getConfigSettings().getFetchTimeoutInSeconds()).isEqualTo(fetchTimeout);
    assertThat(info.getConfigSettings().getMinimumFetchIntervalInSeconds())
        .isEqualTo(minimumFetchInterval);
  }

  @Test
  public void getInfo_firstAndOnlyFetchFails_failStatusAndNoFetchYetTime() {
    sharedPrefsClient.updateLastFetchAsFailed();

    FirebaseRemoteConfigInfo info = sharedPrefsClient.getInfo();

    assertThat(info.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_FAILURE);
    assertThat(info.getFetchTimeMillis()).isEqualTo(LAST_FETCH_TIME_IN_MILLIS_NO_FETCH_YET);
  }

  @Test
  public void getInfo_fetchSucceeds_successStatusAndFetchTimeUpdated() {
    Date fetchTime = new Date(100L);
    sharedPrefsClient.updateLastFetchAsSuccessfulAt(fetchTime);

    FirebaseRemoteConfigInfo info = sharedPrefsClient.getInfo();

    assertThat(info.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_SUCCESS);
    assertThat(info.getFetchTimeMillis()).isEqualTo(fetchTime.getTime());
  }

  @Test
  public void getInfo_firstFetchSucceedsSecondFetchFails_failStatusAndFirstFetchTime() {
    Date fetchTime = new Date(100L);
    sharedPrefsClient.updateLastFetchAsSuccessfulAt(fetchTime);

    sharedPrefsClient.updateLastFetchAsFailed();

    FirebaseRemoteConfigInfo info = sharedPrefsClient.getInfo();

    assertThat(info.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_FAILURE);
    assertThat(info.getFetchTimeMillis()).isEqualTo(fetchTime.getTime());
  }

  @Test
  public void getInfo_twoFetchesSucceed_successStatusAndSecondFetchTime() {
    Date fetchTime = new Date(100L);
    sharedPrefsClient.updateLastFetchAsSuccessfulAt(fetchTime);

    Date secondFetchTime = new Date(200L);
    sharedPrefsClient.updateLastFetchAsSuccessfulAt(secondFetchTime);

    FirebaseRemoteConfigInfo info = sharedPrefsClient.getInfo();

    assertThat(info.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_SUCCESS);
    assertThat(info.getFetchTimeMillis()).isEqualTo(secondFetchTime.getTime());
  }

  @Test
  public void getInfo_hitsThrottleLimit_throttledStatus() {
    Date fetchTime = new Date(100L);
    sharedPrefsClient.updateLastFetchAsSuccessfulAt(fetchTime);

    sharedPrefsClient.updateLastFetchAsThrottled();

    FirebaseRemoteConfigInfo info = sharedPrefsClient.getInfo();

    assertThat(info.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_THROTTLED);
    assertThat(info.getFetchTimeMillis()).isEqualTo(fetchTime.getTime());
  }

  @Test
  public void clear_hasSetValues_clearsAll() {
    Date lastSuccessfulFetchTime = new Date(1000L);
    sharedPrefsClient.updateLastFetchAsSuccessfulAt(lastSuccessfulFetchTime);

    long fetchTimeout = 666L;
    long minimumFetchInterval = 666L;
    sharedPrefsClient.setConfigSettings(
        new FirebaseRemoteConfigSettings.Builder()
            .setFetchTimeoutInSeconds(fetchTimeout)
            .setMinimumFetchIntervalInSeconds(minimumFetchInterval)
            .build());

    sharedPrefsClient.clear();

    FirebaseRemoteConfigInfo info = sharedPrefsClient.getInfo();
    assertThat(info.getFetchTimeMillis()).isEqualTo(LAST_FETCH_TIME_IN_MILLIS_NO_FETCH_YET);
    assertThat(info.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_NO_FETCH_YET);
    assertThat(info.getConfigSettings().getFetchTimeoutInSeconds())
        .isEqualTo(CONNECTION_TIMEOUT_IN_SECONDS);
    assertThat(info.getConfigSettings().getMinimumFetchIntervalInSeconds())
        .isEqualTo(DEFAULT_MINIMUM_FETCH_INTERVAL_IN_SECONDS);
  }

  @Test
  public void getCustomSignals_isNotSet_returnsEmptyMap() {
    assertThat(sharedPrefsClient.getCustomSignals()).isEqualTo(Collections.emptyMap());
  }

  @Test
  public void getCustomSignals_isSet_returnsCustomSignals() {
    Map<String, String> SAMPLE_CUSTOM_SIGNALS =
        ImmutableMap.of(
            "subscription", "premium",
            "age", "20");
    sharedPrefsClient.setCustomSignals(SAMPLE_CUSTOM_SIGNALS);
    assertThat(sharedPrefsClient.getCustomSignals()).isEqualTo(SAMPLE_CUSTOM_SIGNALS);
  }

  @Test
  public void setCustomSignals_multipleTimes_addsNewSignals() {
    Map<String, String> signals1 = ImmutableMap.of("subscription", "premium");
    Map<String, String> signals2 = ImmutableMap.of("age", "20", "subscription", "basic");
    sharedPrefsClient.setCustomSignals(signals1);
    sharedPrefsClient.setCustomSignals(signals2);
    Map<String, String> expectedSignals = ImmutableMap.of("subscription", "basic", "age", "20");
    assertThat(sharedPrefsClient.getCustomSignals()).isEqualTo(expectedSignals);
  }

  @Test
  public void setCustomSignals_nullValue_removesSignal() {
    Map<String, String> signals1 = ImmutableMap.of("subscription", "premium", "age", "20");
    sharedPrefsClient.setCustomSignals(signals1);
    Map<String, String> signals2 = new HashMap<>();
    signals2.put("age", null);
    sharedPrefsClient.setCustomSignals(signals2);
    Map<String, Object> expectedSignals = ImmutableMap.of("subscription", "premium");
    assertThat(sharedPrefsClient.getCustomSignals()).isEqualTo(expectedSignals);
  }
}
