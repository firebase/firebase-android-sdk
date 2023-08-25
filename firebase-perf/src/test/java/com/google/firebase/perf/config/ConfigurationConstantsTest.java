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

package com.google.firebase.perf.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.firebase.perf.config.ConfigurationConstants.CollectionDeactivated;
import com.google.firebase.perf.config.ConfigurationConstants.CollectionEnabled;
import com.google.firebase.perf.config.ConfigurationConstants.FragmentSamplingRate;
import com.google.firebase.perf.config.ConfigurationConstants.LogSourceName;
import com.google.firebase.perf.config.ConfigurationConstants.NetworkEventCountBackground;
import com.google.firebase.perf.config.ConfigurationConstants.NetworkEventCountForeground;
import com.google.firebase.perf.config.ConfigurationConstants.NetworkRequestSamplingRate;
import com.google.firebase.perf.config.ConfigurationConstants.RateLimitSec;
import com.google.firebase.perf.config.ConfigurationConstants.SdkDisabledVersions;
import com.google.firebase.perf.config.ConfigurationConstants.SdkEnabled;
import com.google.firebase.perf.config.ConfigurationConstants.SessionsCpuCaptureFrequencyBackgroundMs;
import com.google.firebase.perf.config.ConfigurationConstants.SessionsCpuCaptureFrequencyForegroundMs;
import com.google.firebase.perf.config.ConfigurationConstants.SessionsMaxDurationMinutes;
import com.google.firebase.perf.config.ConfigurationConstants.SessionsMemoryCaptureFrequencyBackgroundMs;
import com.google.firebase.perf.config.ConfigurationConstants.SessionsMemoryCaptureFrequencyForegroundMs;
import com.google.firebase.perf.config.ConfigurationConstants.SessionsSamplingRate;
import com.google.firebase.perf.config.ConfigurationConstants.TraceEventCountBackground;
import com.google.firebase.perf.config.ConfigurationConstants.TraceEventCountForeground;
import com.google.firebase.perf.config.ConfigurationConstants.TraceSamplingRate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link ConfigurationConstants}. */
@RunWith(RobolectricTestRunner.class)
public class ConfigurationConstantsTest {

  @Test
  public void getInstance_CollectionDeactivated_validateConstants() {
    CollectionDeactivated configFlag = CollectionDeactivated.getInstance();

    assertThat(configFlag.getDefault()).isFalse();
    assertThat(configFlag.getDeviceCacheFlag()).isNull();
    assertThat(configFlag.getRemoteConfigFlag()).isNull();
    assertThat(configFlag.getMetadataFlag())
        .isEqualTo("firebase_performance_collection_deactivated");
  }

  @Test
  public void getInstance_CollectionEnabled_validateConstants() {
    CollectionEnabled configFlag = CollectionEnabled.getInstance();

    assertThat(configFlag.getDefault()).isTrue();
    assertThat(configFlag.getRemoteConfigFlag()).isNull();
    assertThat(configFlag.getMetadataFlag()).isEqualTo("firebase_performance_collection_enabled");
    assertThat(configFlag.getDeviceCacheFlag()).isEqualTo("isEnabled");
  }

  @Test
  public void getInstance_SdkEnabled_validateConstants() {
    SdkEnabled configFlag = SdkEnabled.getInstance();

    assertThat(configFlag.getDefault()).isTrue();
    assertThat(configFlag.getDeviceCacheFlag()).isEqualTo("com.google.firebase.perf.SdkEnabled");
    assertThat(configFlag.getMetadataFlag()).isNull();
    assertThat(configFlag.getRemoteConfigFlag()).isEqualTo("fpr_enabled");
  }

  @Test
  public void getInstance_SdkDisabledVersion_validateConstants() {
    SdkDisabledVersions configFlag = SdkDisabledVersions.getInstance();

    assertThat(configFlag.getDefault()).isEmpty();
    assertThat(configFlag.getDeviceCacheFlag())
        .isEqualTo("com.google.firebase.perf.SdkDisabledVersions");
    assertThat(configFlag.getMetadataFlag()).isNull();
    assertThat(configFlag.getRemoteConfigFlag()).isEqualTo("fpr_disabled_android_versions");
  }

  @Test
  public void getInstance_SessionsCpuCaptureFrequencyForegroundMs_validateConstants() {
    SessionsCpuCaptureFrequencyForegroundMs configFlag =
        SessionsCpuCaptureFrequencyForegroundMs.getInstance();

    assertThat(configFlag.getDefault()).isEqualTo(100L);
    assertThat(configFlag.getDefaultOnRcFetchFail()).isEqualTo(300L);
    assertThat(configFlag.getDeviceCacheFlag())
        .isEqualTo("com.google.firebase.perf.SessionsCpuCaptureFrequencyForegroundMs");
    assertThat(configFlag.getRemoteConfigFlag())
        .isEqualTo("fpr_session_gauge_cpu_capture_frequency_fg_ms");
    assertThat(configFlag.getMetadataFlag()).isEqualTo("sessions_cpu_capture_frequency_fg_ms");
  }

  @Test
  public void getInstance_SessionsCpuCaptureFrequencyBackgroundMs_validateConstants() {
    SessionsCpuCaptureFrequencyBackgroundMs configFlag =
        SessionsCpuCaptureFrequencyBackgroundMs.getInstance();

    assertThat(configFlag.getDefault()).isEqualTo(0L);
    assertThat(configFlag.getDeviceCacheFlag())
        .isEqualTo("com.google.firebase.perf.SessionsCpuCaptureFrequencyBackgroundMs");
    assertThat(configFlag.getRemoteConfigFlag())
        .isEqualTo("fpr_session_gauge_cpu_capture_frequency_bg_ms");
    assertThat(configFlag.getMetadataFlag()).isEqualTo("sessions_cpu_capture_frequency_bg_ms");
  }

  @Test
  public void getInstance_SessionsMemoryCaptureFrequencyForegroundMs_validateConstants() {
    SessionsMemoryCaptureFrequencyForegroundMs configFlag =
        SessionsMemoryCaptureFrequencyForegroundMs.getInstance();

    assertThat(configFlag.getDefault()).isEqualTo(100L);
    assertThat(configFlag.getDefaultOnRcFetchFail()).isEqualTo(300L);
    assertThat(configFlag.getDeviceCacheFlag())
        .isEqualTo("com.google.firebase.perf.SessionsMemoryCaptureFrequencyForegroundMs");
    assertThat(configFlag.getRemoteConfigFlag())
        .isEqualTo("fpr_session_gauge_memory_capture_frequency_fg_ms");
    assertThat(configFlag.getMetadataFlag()).isEqualTo("sessions_memory_capture_frequency_fg_ms");
  }

  @Test
  public void getInstance_SessionsMemoryCaptureFrequencyBackgroundMs_validateConstants() {
    SessionsMemoryCaptureFrequencyBackgroundMs configFlag =
        SessionsMemoryCaptureFrequencyBackgroundMs.getInstance();

    assertThat(configFlag.getDefault()).isEqualTo(0L);
    assertThat(configFlag.getDeviceCacheFlag())
        .isEqualTo("com.google.firebase.perf.SessionsMemoryCaptureFrequencyBackgroundMs");
    assertThat(configFlag.getRemoteConfigFlag())
        .isEqualTo("fpr_session_gauge_memory_capture_frequency_bg_ms");
    assertThat(configFlag.getMetadataFlag()).isEqualTo("sessions_memory_capture_frequency_bg_ms");
  }

  @Test
  public void getInstance_SessionsMaxDuration_validateConstants() {
    SessionsMaxDurationMinutes configFlag = SessionsMaxDurationMinutes.getInstance();

    assertThat(configFlag.getDefault()).isEqualTo(240L);
    assertThat(configFlag.getDeviceCacheFlag())
        .isEqualTo("com.google.firebase.perf.SessionsMaxDurationMinutes");
    assertThat(configFlag.getRemoteConfigFlag()).isEqualTo("fpr_session_max_duration_min");
    assertThat(configFlag.getMetadataFlag()).isEqualTo("sessions_max_length_minutes");
  }

  @Test
  public void getInstance_TraceSamplingRate_validateConstants() {
    TraceSamplingRate configFlag = TraceSamplingRate.getInstance();

    assertThat(configFlag.getDefault()).isEqualTo(1.00);
    assertThat(configFlag.getDefaultOnRcFetchFail()).isEqualTo(0.001);
    assertThat(configFlag.getDeviceCacheFlag())
        .isEqualTo("com.google.firebase.perf.TraceSamplingRate");
    assertThat(configFlag.getRemoteConfigFlag()).isEqualTo("fpr_vc_trace_sampling_rate");
    assertThat(configFlag.getMetadataFlag()).isNull();
  }

  @Test
  public void getInstance_NetworkRequestSamplingRate_validateConstants() {
    NetworkRequestSamplingRate configFlag = NetworkRequestSamplingRate.getInstance();

    assertThat(configFlag.getDefault()).isEqualTo(1.00);
    assertThat(configFlag.getDefaultOnRcFetchFail()).isEqualTo(0.001);
    assertThat(configFlag.getDeviceCacheFlag())
        .isEqualTo("com.google.firebase.perf.NetworkRequestSamplingRate");
    assertThat(configFlag.getRemoteConfigFlag()).isEqualTo("fpr_vc_network_request_sampling_rate");
    assertThat(configFlag.getMetadataFlag()).isNull();
  }

  @Test
  public void getInstance_SessionsSamplingRate_validateConstants() {
    SessionsSamplingRate configFlag = SessionsSamplingRate.getInstance();

    assertThat(configFlag.getDefault()).isEqualTo(0.01);
    assertThat(configFlag.getDefaultOnRcFetchFail()).isEqualTo(0.00001);
    assertThat(configFlag.getDeviceCacheFlag())
        .isEqualTo("com.google.firebase.perf.SessionSamplingRate");
    assertThat(configFlag.getRemoteConfigFlag()).isEqualTo("fpr_vc_session_sampling_rate");
    assertThat(configFlag.getMetadataFlag()).isEqualTo("sessions_sampling_percentage");
  }

  @Test
  public void getInstance_TraceEventCountForeground_validateConstants() {
    TraceEventCountForeground configFlag = TraceEventCountForeground.getInstance();

    assertThat(configFlag.getDefault()).isEqualTo(300L);
    assertThat(configFlag.getMetadataFlag()).isNull();
    assertThat(configFlag.getRemoteConfigFlag()).isEqualTo("fpr_rl_trace_event_count_fg");
    assertThat(configFlag.getDeviceCacheFlag())
        .isEqualTo("com.google.firebase.perf.TraceEventCountForeground");
  }

  @Test
  public void getInstance_TraceEventCountBackground_validateConstants() {
    TraceEventCountBackground configFlag = TraceEventCountBackground.getInstance();

    assertThat(configFlag.getDefault()).isEqualTo(30L);
    assertThat(configFlag.getMetadataFlag()).isNull();
    assertThat(configFlag.getRemoteConfigFlag()).isEqualTo("fpr_rl_trace_event_count_bg");
    assertThat(configFlag.getDeviceCacheFlag())
        .isEqualTo("com.google.firebase.perf.TraceEventCountBackground");
  }

  @Test
  public void getInstance_NetworkEventCountForeground_validateConstants() {
    NetworkEventCountForeground configFlag = NetworkEventCountForeground.getInstance();

    assertThat(configFlag.getDefault()).isEqualTo(700L);
    assertThat(configFlag.getMetadataFlag()).isNull();
    assertThat(configFlag.getRemoteConfigFlag()).isEqualTo("fpr_rl_network_event_count_fg");
    assertThat(configFlag.getDeviceCacheFlag())
        .isEqualTo("com.google.firebase.perf.NetworkEventCountForeground");
  }

  @Test
  public void getInstance_NetworkEventCountBackground_validateConstants() {
    NetworkEventCountBackground configFlag = NetworkEventCountBackground.getInstance();

    assertThat(configFlag.getDefault()).isEqualTo(70L);
    assertThat(configFlag.getMetadataFlag()).isNull();
    assertThat(configFlag.getRemoteConfigFlag()).isEqualTo("fpr_rl_network_event_count_bg");
    assertThat(configFlag.getDeviceCacheFlag())
        .isEqualTo("com.google.firebase.perf.NetworkEventCountBackground");
  }

  @Test
  public void getInstance_RateLimitSec_validateConstants() {
    RateLimitSec configFlag = RateLimitSec.getInstance();

    assertThat(configFlag.getDefault()).isEqualTo(600L);
    assertThat(configFlag.getMetadataFlag()).isNull();
    assertThat(configFlag.getRemoteConfigFlag()).isEqualTo("fpr_rl_time_limit_sec");
    assertThat(configFlag.getDeviceCacheFlag()).isEqualTo("com.google.firebase.perf.TimeLimitSec");
  }

  @Test
  public void getInstance_LogSourceName_validateConstants() {
    LogSourceName configFlag = LogSourceName.getInstance();

    assertThat(configFlag.getDefault()).isEqualTo("FIREPERF");
    assertThat(configFlag.getDeviceCacheFlag()).isEqualTo("com.google.firebase.perf.LogSourceName");
    assertThat(configFlag.getRemoteConfigFlag()).isEqualTo("fpr_log_source");
    assertThat(configFlag.getMetadataFlag()).isNull();

    assertThat(LogSourceName.isLogSourceKnown(461L)).isTrue();
    assertThat(LogSourceName.isLogSourceKnown(462L)).isTrue();
    assertThat(LogSourceName.isLogSourceKnown(675L)).isTrue();
    assertThat(LogSourceName.isLogSourceKnown(676L)).isTrue();

    assertThat(LogSourceName.getLogSourceName(461L)).isEqualTo("FIREPERF_AUTOPUSH");
    assertThat(LogSourceName.getLogSourceName(462L)).isEqualTo("FIREPERF");
    assertThat(LogSourceName.getLogSourceName(675L)).isEqualTo("FIREPERF_INTERNAL_LOW");
    assertThat(LogSourceName.getLogSourceName(676L)).isEqualTo("FIREPERF_INTERNAL_HIGH");
  }

  @Test
  public void getInstance_FragmentSamplingRate_validateConstants() {
    FragmentSamplingRate configFlag = FragmentSamplingRate.getInstance();

    assertThat(configFlag.getDefault()).isEqualTo(0.0);
    assertThat(configFlag.getDeviceCacheFlag())
        .isEqualTo("com.google.firebase.perf.FragmentSamplingRate");
    assertThat(configFlag.getRemoteConfigFlag()).isEqualTo("fpr_vc_fragment_sampling_rate");
    assertThat(configFlag.getMetadataFlag()).isEqualTo("fragment_sampling_percentage");
  }
}
