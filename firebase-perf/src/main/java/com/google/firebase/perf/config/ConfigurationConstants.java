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

import com.google.firebase.perf.BuildConfig;
import com.google.firebase.perf.util.Constants;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Stores all configuration flag names and their constant values. */
final class ConfigurationConstants {

  protected static final class CollectionDeactivated extends ConfigurationFlag<Boolean> {
    private static CollectionDeactivated instance;

    private CollectionDeactivated() {
      super();
    }

    protected static synchronized CollectionDeactivated getInstance() {
      if (instance == null) {
        instance = new CollectionDeactivated();
      }
      return instance;
    }

    @Override
    protected Boolean getDefault() {
      return false;
    }

    @Override
    protected String getMetadataFlag() {
      return "firebase_performance_collection_deactivated";
    }
  }

  protected static final class CollectionEnabled extends ConfigurationFlag<Boolean> {
    private static CollectionEnabled instance;

    private CollectionEnabled() {
      super();
    }

    protected static synchronized CollectionEnabled getInstance() {
      if (instance == null) {
        instance = new CollectionEnabled();
      }
      return instance;
    }

    @Override
    protected Boolean getDefault() {
      return true;
    }

    @Override
    protected String getMetadataFlag() {
      return "firebase_performance_collection_enabled";
    }

    @Override
    protected String getDeviceCacheFlag() {
      return Constants.ENABLE_DISABLE;
    }
  }

  protected static final class SdkEnabled extends ConfigurationFlag<Boolean> {
    private static SdkEnabled instance;

    protected static synchronized SdkEnabled getInstance() {
      if (instance == null) {
        instance = new SdkEnabled();
      }
      return instance;
    }

    @Override
    protected Boolean getDefault() {
      return true;
    }

    @Override
    protected String getRemoteConfigFlag() {
      return "fpr_enabled";
    }

    @Override
    protected String getDeviceCacheFlag() {
      return "com.google.firebase.perf.SdkEnabled";
    }
  }

  protected static final class SdkDisabledVersions extends ConfigurationFlag<String> {
    private static SdkDisabledVersions instance;

    protected static synchronized SdkDisabledVersions getInstance() {
      if (instance == null) {
        instance = new SdkDisabledVersions();
      }
      return instance;
    }

    @Override
    protected String getDefault() {
      return "";
    }

    @Override
    protected String getRemoteConfigFlag() {
      return "fpr_disabled_android_versions";
    }

    @Override
    protected String getDeviceCacheFlag() {
      return "com.google.firebase.perf.SdkDisabledVersions";
    }
  }

  protected static final class TraceSamplingRate extends ConfigurationFlag<Double> {
    private static TraceSamplingRate instance;

    private TraceSamplingRate() {
      super();
    }

    protected static synchronized TraceSamplingRate getInstance() {
      if (instance == null) {
        instance = new TraceSamplingRate();
      }
      return instance;
    }

    @Override
    protected Double getDefault() {
      // Sampling rate range is [0.00f, 1.00f]. By default, sampling rate is 1.00f, which is 100%.
      // 0.00f means 0%, Fireperf will not capture any event for trace from the device,
      // 1.00f means 100%, Fireperf will capture all events for trace from the device.
      return 1.0;
    }

    @Override
    protected Double getDefaultOnRcFetchFail() {
      // Reduce the typical default by 3 orders of magnitude.
      return getDefault() / 1000;
    }

    @Override
    protected String getRemoteConfigFlag() {
      return "fpr_vc_trace_sampling_rate";
    }

    @Override
    protected String getDeviceCacheFlag() {
      return "com.google.firebase.perf.TraceSamplingRate";
    }
  }

  protected static final class NetworkRequestSamplingRate extends ConfigurationFlag<Double> {
    private static NetworkRequestSamplingRate instance;

    private NetworkRequestSamplingRate() {
      super();
    }

    protected static synchronized NetworkRequestSamplingRate getInstance() {
      if (instance == null) {
        instance = new NetworkRequestSamplingRate();
      }
      return instance;
    }

    @Override
    protected Double getDefault() {
      // Sampling rate range is [0.00f, 1.00f]. By default, sampling rate is 1.00f, which is 100%.
      // 0.00f means 0%, Fireperf will not capture any event for trace from the device,
      // 1.00f means 100%, Fireperf will capture all events for trace from the device.
      return 1.0;
    }

    @Override
    protected Double getDefaultOnRcFetchFail() {
      // Reduce the typical default by 3 orders of magnitude.
      return getDefault() / 1000;
    }

    @Override
    protected String getRemoteConfigFlag() {
      return "fpr_vc_network_request_sampling_rate";
    }

    @Override
    protected String getDeviceCacheFlag() {
      return "com.google.firebase.perf.NetworkRequestSamplingRate";
    }
  }

  protected static final class SessionsCpuCaptureFrequencyForegroundMs
      extends ConfigurationFlag<Long> {
    private static SessionsCpuCaptureFrequencyForegroundMs instance;

    private SessionsCpuCaptureFrequencyForegroundMs() {
      super();
    }

    public static synchronized SessionsCpuCaptureFrequencyForegroundMs getInstance() {
      if (instance == null) {
        instance = new SessionsCpuCaptureFrequencyForegroundMs();
      }
      return instance;
    }

    @Override
    protected Long getDefault() {
      // Capture frequency range is [0, 1000]. By default, capture frequency when the app is in
      // foreground is 100, which is 100ms.
      return 100L;
    }

    @Override
    protected Long getDefaultOnRcFetchFail() {
      // Increase the typical default by factor of 3.
      return getDefault() * 3;
    }

    @Override
    protected String getRemoteConfigFlag() {
      return "fpr_session_gauge_cpu_capture_frequency_fg_ms";
    }

    @Override
    protected String getMetadataFlag() {
      return "sessions_cpu_capture_frequency_fg_ms";
    }

    @Override
    protected String getDeviceCacheFlag() {
      return "com.google.firebase.perf.SessionsCpuCaptureFrequencyForegroundMs";
    }
  }

  protected static final class SessionsCpuCaptureFrequencyBackgroundMs
      extends ConfigurationFlag<Long> {
    private static SessionsCpuCaptureFrequencyBackgroundMs instance;

    private SessionsCpuCaptureFrequencyBackgroundMs() {
      super();
    }

    public static synchronized SessionsCpuCaptureFrequencyBackgroundMs getInstance() {
      if (instance == null) {
        instance = new SessionsCpuCaptureFrequencyBackgroundMs();
      }
      return instance;
    }

    @Override
    protected Long getDefault() {
      // Capture frequency range is [0, 1000]. By default, capture frequency when the app is in
      // background is 0 (Meaning do not capture).
      return 0L;
    }

    @Override
    protected String getRemoteConfigFlag() {
      return "fpr_session_gauge_cpu_capture_frequency_bg_ms";
    }

    @Override
    protected String getMetadataFlag() {
      return "sessions_cpu_capture_frequency_bg_ms";
    }

    @Override
    protected String getDeviceCacheFlag() {
      return "com.google.firebase.perf.SessionsCpuCaptureFrequencyBackgroundMs";
    }
  }

  protected static final class SessionsMemoryCaptureFrequencyForegroundMs
      extends ConfigurationFlag<Long> {
    private static SessionsMemoryCaptureFrequencyForegroundMs instance;

    private SessionsMemoryCaptureFrequencyForegroundMs() {
      super();
    }

    public static synchronized SessionsMemoryCaptureFrequencyForegroundMs getInstance() {
      if (instance == null) {
        instance = new SessionsMemoryCaptureFrequencyForegroundMs();
      }
      return instance;
    }

    @Override
    protected Long getDefault() {
      // Capture frequency range is [0, 1000]. By default, capture frequency when the app is in
      // foreground is 100, which is 100ms.
      return 100L;
    }

    @Override
    protected Long getDefaultOnRcFetchFail() {
      // Increase the typical default by factor of 3.
      return getDefault() * 3;
    }

    @Override
    protected String getRemoteConfigFlag() {
      return "fpr_session_gauge_memory_capture_frequency_fg_ms";
    }

    @Override
    protected String getMetadataFlag() {
      return "sessions_memory_capture_frequency_fg_ms";
    }

    @Override
    protected String getDeviceCacheFlag() {
      return "com.google.firebase.perf.SessionsMemoryCaptureFrequencyForegroundMs";
    }
  }

  protected static final class SessionsMemoryCaptureFrequencyBackgroundMs
      extends ConfigurationFlag<Long> {
    private static SessionsMemoryCaptureFrequencyBackgroundMs instance;

    private SessionsMemoryCaptureFrequencyBackgroundMs() {
      super();
    }

    public static synchronized SessionsMemoryCaptureFrequencyBackgroundMs getInstance() {
      if (instance == null) {
        instance = new SessionsMemoryCaptureFrequencyBackgroundMs();
      }
      return instance;
    }

    @Override
    protected Long getDefault() {
      // Capture frequency range is [0, 1000]. By default, capture frequency when the app is in
      // background is 0 (Meaning do not capture).
      return 0L;
    }

    @Override
    protected String getRemoteConfigFlag() {
      return "fpr_session_gauge_memory_capture_frequency_bg_ms";
    }

    @Override
    protected String getMetadataFlag() {
      return "sessions_memory_capture_frequency_bg_ms";
    }

    @Override
    protected String getDeviceCacheFlag() {
      return "com.google.firebase.perf.SessionsMemoryCaptureFrequencyBackgroundMs";
    }
  }

  protected static final class SessionsMaxDurationMinutes extends ConfigurationFlag<Long> {
    private static SessionsMaxDurationMinutes instance;

    private SessionsMaxDurationMinutes() {
      super();
    }

    public static synchronized SessionsMaxDurationMinutes getInstance() {
      if (instance == null) {
        instance = new SessionsMaxDurationMinutes();
      }
      return instance;
    }

    @Override
    protected Long getDefault() {
      // Default value of max duration for sessions is 240 minutes.
      return 240L;
    }

    @Override
    protected String getRemoteConfigFlag() {
      return "fpr_session_max_duration_min";
    }

    @Override
    protected String getMetadataFlag() {
      return "sessions_max_length_minutes";
    }

    @Override
    protected String getDeviceCacheFlag() {
      return "com.google.firebase.perf.SessionsMaxDurationMinutes";
    }
  }

  protected static final class TraceEventCountForeground extends ConfigurationFlag<Long> {
    private static TraceEventCountForeground instance;

    private TraceEventCountForeground() {
      super();
    }

    public static synchronized TraceEventCountForeground getInstance() {
      if (instance == null) {
        instance = new TraceEventCountForeground();
      }
      return instance;
    }

    @Override
    protected Long getDefault() {
      return 300L;
    }

    @Override
    protected String getRemoteConfigFlag() {
      return "fpr_rl_trace_event_count_fg";
    }

    @Override
    protected String getDeviceCacheFlag() {
      return "com.google.firebase.perf.TraceEventCountForeground";
    }
  }

  protected static final class TraceEventCountBackground extends ConfigurationFlag<Long> {
    private static TraceEventCountBackground instance;

    private TraceEventCountBackground() {
      super();
    }

    public static synchronized TraceEventCountBackground getInstance() {
      if (instance == null) {
        instance = new TraceEventCountBackground();
      }
      return instance;
    }

    @Override
    protected Long getDefault() {
      return 30L;
    }

    @Override
    protected String getRemoteConfigFlag() {
      return "fpr_rl_trace_event_count_bg";
    }

    @Override
    protected String getDeviceCacheFlag() {
      return "com.google.firebase.perf.TraceEventCountBackground";
    }
  }

  protected static final class NetworkEventCountForeground extends ConfigurationFlag<Long> {
    private static NetworkEventCountForeground instance;

    private NetworkEventCountForeground() {
      super();
    }

    public static synchronized NetworkEventCountForeground getInstance() {
      if (instance == null) {
        instance = new NetworkEventCountForeground();
      }
      return instance;
    }

    @Override
    protected Long getDefault() {
      return 700L;
    }

    @Override
    protected String getRemoteConfigFlag() {
      return "fpr_rl_network_event_count_fg";
    }

    @Override
    protected String getDeviceCacheFlag() {
      return "com.google.firebase.perf.NetworkEventCountForeground";
    }
  }

  protected static final class NetworkEventCountBackground extends ConfigurationFlag<Long> {
    private static NetworkEventCountBackground instance;

    private NetworkEventCountBackground() {
      super();
    }

    public static synchronized NetworkEventCountBackground getInstance() {
      if (instance == null) {
        instance = new NetworkEventCountBackground();
      }
      return instance;
    }

    @Override
    protected Long getDefault() {
      return 70L;
    }

    @Override
    protected String getRemoteConfigFlag() {
      return "fpr_rl_network_event_count_bg";
    }

    @Override
    protected String getDeviceCacheFlag() {
      return "com.google.firebase.perf.NetworkEventCountBackground";
    }
  }

  protected static final class RateLimitSec extends ConfigurationFlag<Long> {
    private static RateLimitSec instance;

    private RateLimitSec() {
      super();
    }

    public static synchronized RateLimitSec getInstance() {
      if (instance == null) {
        instance = new RateLimitSec();
      }
      return instance;
    }

    @Override
    protected Long getDefault() {
      return 600L;
    }

    @Override
    protected String getRemoteConfigFlag() {
      return "fpr_rl_time_limit_sec";
    }

    @Override
    protected String getDeviceCacheFlag() {
      return "com.google.firebase.perf.TimeLimitSec";
    }
  }

  protected static final class SessionsSamplingRate extends ConfigurationFlag<Double> {
    private static SessionsSamplingRate instance;

    private SessionsSamplingRate() {
      super();
    }

    public static synchronized SessionsSamplingRate getInstance() {
      if (instance == null) {
        instance = new SessionsSamplingRate();
      }
      return instance;
    }

    @Override
    protected Double getDefault() {
      return 0.01;
    }

    @Override
    protected Double getDefaultOnRcFetchFail() {
      // Reduce the typical default by 3 orders of magnitude.
      return getDefault() / 1000;
    }

    @Override
    protected String getDeviceCacheFlag() {
      return "com.google.firebase.perf.SessionSamplingRate";
    }

    @Override
    protected String getRemoteConfigFlag() {
      return "fpr_vc_session_sampling_rate";
    }

    @Override
    protected String getMetadataFlag() {
      return "sessions_sampling_percentage";
    }
  }

  protected static final class LogSourceName extends ConfigurationFlag<String> {

    private static LogSourceName instance;

    // Maps Firebase Performance backend log source number value to String value. Current log source
    // on Remote Config Template is defined as number type as is currently required by Web SDK.
    //
    // Android Transport client only takes String value. This map converts long value to String
    // value for creating Transport. Reference: go/clientanalytics.proto
    private static final Map<Long, String> LOG_SOURCE_MAP =
        Collections.unmodifiableMap(
            new HashMap<Long, String>() {
              {
                put(461L, "FIREPERF_AUTOPUSH");
                put(462L, "FIREPERF");
                put(675L, "FIREPERF_INTERNAL_LOW");
                put(676L, "FIREPERF_INTERNAL_HIGH");
              }
            });

    private LogSourceName() {
      super();
    }

    public static synchronized LogSourceName getInstance() {
      if (instance == null) {
        instance = new LogSourceName();
      }
      return instance;
    }

    protected static String getLogSourceName(long logSource) {
      return LOG_SOURCE_MAP.get(logSource);
    }

    protected static boolean isLogSourceKnown(long logSource) {
      return LOG_SOURCE_MAP.containsKey(logSource);
    }

    @Override
    protected String getDefault() {
      return BuildConfig.TRANSPORT_LOG_SRC;
    }

    @Override
    protected String getRemoteConfigFlag() {
      return "fpr_log_source";
    }

    @Override
    protected String getDeviceCacheFlag() {
      return "com.google.firebase.perf.LogSourceName";
    }
  }

  protected static final class FragmentSamplingRate extends ConfigurationFlag<Double> {
    private static FragmentSamplingRate instance;

    private FragmentSamplingRate() {
      super();
    }

    protected static synchronized FragmentSamplingRate getInstance() {
      if (instance == null) {
        instance = new FragmentSamplingRate();
      }
      return instance;
    }

    @Override
    protected Double getDefault() {
      // Sampling rate range is [0.00f, 1.00f]. By default, sampling rate is 0.00f, which is 0%.
      // 0.00f means 0%, Fireperf will not capture any event for fragment trace from the device,
      // 1.00f means 100%, Fireperf will capture all events for fragment trace from the device.
      return 0.00;
    }

    @Override
    protected String getRemoteConfigFlag() {
      return "fpr_vc_fragment_sampling_rate";
    }

    @Override
    protected String getDeviceCacheFlag() {
      return "com.google.firebase.perf.FragmentSamplingRate";
    }

    @Override
    protected String getMetadataFlag() {
      return "fragment_sampling_percentage";
    }
  }

  protected static final class ExperimentTTID extends ConfigurationFlag<Boolean> {
    private static ExperimentTTID instance;

    private ExperimentTTID() {
      super();
    }

    protected static synchronized ExperimentTTID getInstance() {
      if (instance == null) {
        instance = new ExperimentTTID();
      }
      return instance;
    }

    @Override
    protected Boolean getDefault() {
      return false;
    }

    @Override
    protected String getRemoteConfigFlag() {
      return "fpr_experiment_app_start_ttid";
    }

    @Override
    protected String getDeviceCacheFlag() {
      return "com.google.firebase.perf.ExperimentTTID";
    }

    @Override
    protected String getMetadataFlag() {
      return "experiment_app_start_ttid";
    }
  }
}
