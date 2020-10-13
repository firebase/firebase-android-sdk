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
import static com.google.firebase.remoteconfig.internal.ConfigMetadataClient.LAST_FETCH_TIME_IN_MILLIS_NO_FETCH_YET;
import static com.google.firebase.remoteconfig.internal.ConfigMetadataClient.LAST_FETCH_TIME_NO_FETCH_YET;
import static com.google.firebase.remoteconfig.internal.ConfigMetadataClient.NO_BACKOFF_TIME;
import static com.google.firebase.remoteconfig.internal.ConfigMetadataClient.NO_FAILED_FETCHES;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.common.base.Preconditions;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigInfo;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.remoteconfig.internal.ConfigMetadataClient.BackoffMetadata;
import com.google.firebase.remoteconfig.internal.ConfigMetadataClient.LastFetchStatus;
import java.util.Date;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Unit tests for the {@link ConfigMetadataClient}.
 *
 * @author Miraziz Yusupov
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ConfigMetadataClientTest {
  private ConfigMetadataClient metadataClient;
  private FirebaseRemoteConfigSettings.Builder settingsBuilder;

  @Before
  public void setUp() {
    SharedPreferences metadata =
        RuntimeEnvironment.application.getSharedPreferences("TEST_FILE_NAME", Context.MODE_PRIVATE);

    metadata.edit().clear().commit();

    metadataClient = new ConfigMetadataClient(metadata);

    settingsBuilder = new FirebaseRemoteConfigSettings.Builder();
  }

  @Test
  public void getFetchTimeoutInSeconds_isNotSet_returnsDefault() {
    assertThat(metadataClient.getFetchTimeoutInSeconds()).isEqualTo(CONNECTION_TIMEOUT_IN_SECONDS);
  }

  @Test
  public void getFetchTimeoutInSeconds_isSetTo10Seconds_returns10Seconds() {
    long expectedFetchTimeout = 10L;
    metadataClient.setConfigSettings(
        settingsBuilder.setFetchTimeoutInSeconds(expectedFetchTimeout).build());

    long fetchTimeout = metadataClient.getFetchTimeoutInSeconds();

    assertThat(fetchTimeout).isEqualTo(expectedFetchTimeout);
  }

  @Test
  public void getMinimumFetchIntervalInSeconds_isNotSet_returnsDefault() {
    assertThat(metadataClient.getMinimumFetchIntervalInSeconds())
        .isEqualTo(DEFAULT_MINIMUM_FETCH_INTERVAL_IN_SECONDS);
  }

  @Test
  public void getMinimumFetchIntervalInSeconds_isSetTo10Seconds_returns10Seconds() {
    long expectedMinimumFetchInterval = 10L;
    metadataClient.setConfigSettings(
        settingsBuilder.setMinimumFetchIntervalInSeconds(expectedMinimumFetchInterval).build());

    long minimumFetchInterval = metadataClient.getMinimumFetchIntervalInSeconds();

    assertThat(minimumFetchInterval).isEqualTo(expectedMinimumFetchInterval);
  }

  @Test
  public void getLastFetchStatus_isNotSet_returnsZero() {
    assertThat(metadataClient.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_NO_FETCH_YET);
  }

  @Test
  public void getLastFetchStatus_isSetToSuccess_returnsSuccess() {
    metadataClient.updateLastFetchAsSuccessfulAt(new Date(100L));

    @LastFetchStatus int lastFetchStatus = metadataClient.getLastFetchStatus();

    assertThat(lastFetchStatus).isEqualTo(LAST_FETCH_STATUS_SUCCESS);
  }

  @Test
  public void getLastSuccessfulFetchTime_isNotSet_returnsZero() {
    assertThat(metadataClient.getLastSuccessfulFetchTime()).isEqualTo(LAST_FETCH_TIME_NO_FETCH_YET);
  }

  @Test
  public void getLastSuccessfulFetchTime_isSet_returnsTime() {
    Date fetchTime = new Date(1000L);
    metadataClient.updateLastFetchAsSuccessfulAt(fetchTime);

    Date lastSuccessfulFetchTime = metadataClient.getLastSuccessfulFetchTime();

    assertThat(lastSuccessfulFetchTime).isEqualTo(fetchTime);
  }

  @Test
  public void getLastFetchETag_isNotSet_returnsEmptyString() {
    assertThat(metadataClient.getLastFetchETag()).isNull();
  }

  @Test
  public void getLastFetchETag_isSet_returnsETag() {
    String expectedETag = "an etag";
    metadataClient.setLastFetchETag(expectedETag);

    String eTag = metadataClient.getLastFetchETag();

    assertThat(eTag).isEqualTo(expectedETag);
  }

  @Test
  public void getBackoffMetadata_isNotSet_returnsNoFailedFetchesAndNotThrottled() {
    BackoffMetadata defaultBackoffMetadata = metadataClient.getBackoffMetadata();

    assertThat(defaultBackoffMetadata.getNumFailedFetches()).isEqualTo(NO_FAILED_FETCHES);
    assertThat(defaultBackoffMetadata.getBackoffEndTime()).isEqualTo(NO_BACKOFF_TIME);
  }

  @Test
  public void getBackoffMetadata_hasValues_returnsValues() {
    int numFailedFetches = 5;
    Date backoffEndTime = new Date(1000L);
    metadataClient.setBackoffMetadata(numFailedFetches, backoffEndTime);

    BackoffMetadata backoffMetadata = metadataClient.getBackoffMetadata();

    assertThat(backoffMetadata.getNumFailedFetches()).isEqualTo(numFailedFetches);
    assertThat(backoffMetadata.getBackoffEndTime()).isEqualTo(backoffEndTime);
  }

  @Test
  public void resetBackoff_hasValues_clearsAllValues() {
    metadataClient.setBackoffMetadata(/*numFailedFetches=*/ 5, /*backoffEndTime=*/ new Date(1000L));

    BackoffMetadata backoffMetadata = metadataClient.getBackoffMetadata();
    Preconditions.checkArgument(backoffMetadata.getNumFailedFetches() != NO_FAILED_FETCHES);
    Preconditions.checkArgument(!backoffMetadata.getBackoffEndTime().equals(NO_BACKOFF_TIME));

    metadataClient.resetBackoff();

    BackoffMetadata resetMetadata = metadataClient.getBackoffMetadata();
    assertThat(resetMetadata.getNumFailedFetches()).isEqualTo(NO_FAILED_FETCHES);
    assertThat(resetMetadata.getBackoffEndTime()).isEqualTo(NO_BACKOFF_TIME);
  }

  @Test
  public void getInfo_hasNoSetValues_returnsDefaults() {
    FirebaseRemoteConfigInfo info = metadataClient.getInfo();

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
    metadataClient.updateLastFetchAsSuccessfulAt(lastSuccessfulFetchTime);
    metadataClient.updateLastFetchAsFailed();

    long fetchTimeout = 666L;
    long minimumFetchInterval = 666L;
    metadataClient.setConfigSettings(
        new FirebaseRemoteConfigSettings.Builder()
            .setFetchTimeoutInSeconds(fetchTimeout)
            .setMinimumFetchIntervalInSeconds(minimumFetchInterval)
            .build());

    FirebaseRemoteConfigInfo info = metadataClient.getInfo();

    assertThat(info.getFetchTimeMillis()).isEqualTo(lastSuccessfulFetchTime.getTime());
    assertThat(info.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_FAILURE);
    assertThat(info.getConfigSettings().getFetchTimeoutInSeconds()).isEqualTo(fetchTimeout);
    assertThat(info.getConfigSettings().getMinimumFetchIntervalInSeconds())
        .isEqualTo(minimumFetchInterval);
  }

  @Test
  public void getInfo_firstAndOnlyFetchFails_failStatusAndNoFetchYetTime() {
    metadataClient.updateLastFetchAsFailed();

    FirebaseRemoteConfigInfo info = metadataClient.getInfo();

    assertThat(info.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_FAILURE);
    assertThat(info.getFetchTimeMillis()).isEqualTo(LAST_FETCH_TIME_IN_MILLIS_NO_FETCH_YET);
  }

  @Test
  public void getInfo_fetchSucceeds_successStatusAndFetchTimeUpdated() {
    Date fetchTime = new Date(100L);
    metadataClient.updateLastFetchAsSuccessfulAt(fetchTime);

    FirebaseRemoteConfigInfo info = metadataClient.getInfo();

    assertThat(info.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_SUCCESS);
    assertThat(info.getFetchTimeMillis()).isEqualTo(fetchTime.getTime());
  }

  @Test
  public void getInfo_firstFetchSucceedsSecondFetchFails_failStatusAndFirstFetchTime() {
    Date fetchTime = new Date(100L);
    metadataClient.updateLastFetchAsSuccessfulAt(fetchTime);

    metadataClient.updateLastFetchAsFailed();

    FirebaseRemoteConfigInfo info = metadataClient.getInfo();

    assertThat(info.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_FAILURE);
    assertThat(info.getFetchTimeMillis()).isEqualTo(fetchTime.getTime());
  }

  @Test
  public void getInfo_twoFetchesSucceed_successStatusAndSecondFetchTime() {
    Date fetchTime = new Date(100L);
    metadataClient.updateLastFetchAsSuccessfulAt(fetchTime);

    Date secondFetchTime = new Date(200L);
    metadataClient.updateLastFetchAsSuccessfulAt(secondFetchTime);

    FirebaseRemoteConfigInfo info = metadataClient.getInfo();

    assertThat(info.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_SUCCESS);
    assertThat(info.getFetchTimeMillis()).isEqualTo(secondFetchTime.getTime());
  }

  @Test
  public void getInfo_hitsThrottleLimit_throttledStatus() {
    Date fetchTime = new Date(100L);
    metadataClient.updateLastFetchAsSuccessfulAt(fetchTime);

    metadataClient.updateLastFetchAsThrottled();

    FirebaseRemoteConfigInfo info = metadataClient.getInfo();

    assertThat(info.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_THROTTLED);
    assertThat(info.getFetchTimeMillis()).isEqualTo(fetchTime.getTime());
  }

  @Test
  public void clear_hasSetValues_clearsAll() {
    Date lastSuccessfulFetchTime = new Date(1000L);
    metadataClient.updateLastFetchAsSuccessfulAt(lastSuccessfulFetchTime);

    long fetchTimeout = 666L;
    long minimumFetchInterval = 666L;
    metadataClient.setConfigSettings(
        new FirebaseRemoteConfigSettings.Builder()
            .setFetchTimeoutInSeconds(fetchTimeout)
            .setMinimumFetchIntervalInSeconds(minimumFetchInterval)
            .build());

    metadataClient.clear();

    FirebaseRemoteConfigInfo info = metadataClient.getInfo();
    assertThat(info.getFetchTimeMillis()).isEqualTo(LAST_FETCH_TIME_IN_MILLIS_NO_FETCH_YET);
    assertThat(info.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_NO_FETCH_YET);
    assertThat(info.getConfigSettings().getFetchTimeoutInSeconds())
        .isEqualTo(CONNECTION_TIMEOUT_IN_SECONDS);
    assertThat(info.getConfigSettings().getMinimumFetchIntervalInSeconds())
        .isEqualTo(DEFAULT_MINIMUM_FETCH_INTERVAL_IN_SECONDS);
  }
}
