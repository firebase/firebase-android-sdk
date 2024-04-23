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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.perf.BuildConfig;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.util.ImmutableBundle;
import com.google.firebase.perf.util.Optional;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link ConfigResolver}. */
@RunWith(RobolectricTestRunner.class)
public class ConfigResolverTest extends FirebasePerformanceTestBase {

  private static final String FIREBASE_PERFORMANCE_COLLECTION_ENABLED_CACHE_KEY = "isEnabled";

  private static final String FIREBASE_PERFORMANCE_DISABLED_VERSIONS_FRC_KEY =
      "fpr_disabled_android_versions";
  private static final String FIREBASE_PERFORMANCE_DISABLED_VERSIONS_CACHE_KEY =
      "com.google.firebase.perf.SdkDisabledVersions";

  private static final String FIREBASE_PERFORMANCE_SDK_ENABLED_FRC_KEY = "fpr_enabled";
  private static final String FIREBASE_PERFORMANCE_SDK_ENABLED_CACHE_KEY =
      "com.google.firebase.perf.SdkEnabled";

  // Session sampling rate flag
  private static final String SESSION_SAMPLING_RATE_FRC_KEY = "fpr_vc_session_sampling_rate";
  private static final String SESSION_SAMPLING_RATE_CACHE_KEY =
      "com.google.firebase.perf.SessionSamplingRate";

  // Trace sampling rate flag
  private static final String TRACE_SAMPLING_RATE_FRC_KEY = "fpr_vc_trace_sampling_rate";
  private static final String TRACE_SAMPLING_RATE_CACHE_KEY =
      "com.google.firebase.perf.TraceSamplingRate";

  // Network request sampling rate flag
  private static final String NETWORK_REQUEST_SAMPLING_RATE_FRC_KEY =
      "fpr_vc_network_request_sampling_rate";
  private static final String NETWORK_REQUEST_SAMPLING_RATE_CACHE_KEY =
      "com.google.firebase.perf.NetworkRequestSamplingRate";

  // Rate limiting flags
  private static final String TRACE_EVENT_COUNT_FG_FRC_KEY = "fpr_rl_trace_event_count_fg";
  private static final String TRACE_EVENT_COUNT_FG_CACHE_KEY =
      "com.google.firebase.perf.TraceEventCountForeground";

  private static final String TRACE_EVENT_COUNT_BG_FRC_KEY = "fpr_rl_trace_event_count_bg";
  private static final String TRACE_EVENT_COUNT_BG_CACHE_KEY =
      "com.google.firebase.perf.TraceEventCountBackground";

  private static final String NETWORK_EVENT_COUNT_FG_FRC_KEY = "fpr_rl_network_event_count_fg";
  private static final String NETWORK_EVENT_COUNT_FG_CACHE_KEY =
      "com.google.firebase.perf.NetworkEventCountForeground";

  private static final String NETWORK_EVENT_COUNT_BG_FRC_KEY = "fpr_rl_network_event_count_bg";
  private static final String NETWORK_EVENT_COUNT_BG_CACHE_KEY =
      "com.google.firebase.perf.NetworkEventCountBackground";

  private static final String TIME_LIMIT_SEC_FG_FRC_KEY = "fpr_rl_time_limit_sec";
  private static final String TIME_LIMIT_SEC_FG_CACHE_KEY = "com.google.firebase.perf.TimeLimitSec";

  // Session gauges flags
  private static final String SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_METADATA_KEY =
      "sessions_cpu_capture_frequency_fg_ms";
  private static final String SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_FRC_KEY =
      "fpr_session_gauge_cpu_capture_frequency_fg_ms";
  private static final String SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY =
      "com.google.firebase.perf.SessionsCpuCaptureFrequencyForegroundMs";

  private static final String SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_METADATA_KEY =
      "sessions_cpu_capture_frequency_bg_ms";
  private static final String SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_FRC_KEY =
      "fpr_session_gauge_cpu_capture_frequency_bg_ms";
  private static final String SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY =
      "com.google.firebase.perf.SessionsCpuCaptureFrequencyBackgroundMs";

  private static final String SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_METADATA_KEY =
      "sessions_memory_capture_frequency_fg_ms";
  private static final String SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_FRC_KEY =
      "fpr_session_gauge_memory_capture_frequency_fg_ms";
  private static final String SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY =
      "com.google.firebase.perf.SessionsMemoryCaptureFrequencyForegroundMs";

  private static final String SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_METADATA_KEY =
      "sessions_memory_capture_frequency_bg_ms";
  private static final String SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_FRC_KEY =
      "fpr_session_gauge_memory_capture_frequency_bg_ms";
  private static final String SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY =
      "com.google.firebase.perf.SessionsMemoryCaptureFrequencyBackgroundMs";

  private static final String SESSIONS_MAX_DURATION_MIN_METADATA_KEY =
      "sessions_max_length_minutes";
  private static final String SESSIONS_MAX_DURATION_MIN_FRC_KEY = "fpr_session_max_duration_min";
  private static final String SESSIONS_MAX_DURATION_MIN_CACHE_KEY =
      "com.google.firebase.perf.SessionsMaxDurationMinutes";

  // Fragment trace sampling rate flags
  private static final String FRAGMENT_SAMPLING_RATE_FRC_KEY = "fpr_vc_fragment_sampling_rate";
  private static final String FRAGMENT_SAMPLING_RATE_CACHE_KEY =
      "com.google.firebase.perf.FragmentSamplingRate";

  private ConfigResolver testConfigResolver;

  @Mock private RemoteConfigManager mockRemoteConfigManager;
  @Mock private DeviceCacheManager mockDeviceCacheManager;

  @Before
  public void setUp() {
    initMocks(this);
    testConfigResolver = new ConfigResolver(mockRemoteConfigManager, null, mockDeviceCacheManager);
  }

  @Test
  public void setContentProviderContext_contextIsProvided_deviceCacheManagerIsCalled() {
    testConfigResolver.setContentProviderContext(ApplicationProvider.getApplicationContext());

    verify(mockDeviceCacheManager, times(1))
        .setContext(eq(ApplicationProvider.getApplicationContext()));
  }

  @Test
  public void setApplicationContext_contextIsProvided_deviceCacheManagerIsCalled() {
    testConfigResolver.setApplicationContext(ApplicationProvider.getApplicationContext());

    verify(mockDeviceCacheManager, times(1))
        .setContext(eq(ApplicationProvider.getApplicationContext()));
  }

  @Test
  public void getIsServiceCollectionEnabled_sdkDisabledVersionAndSdkEnabled_returnsTrue() {
    when(mockRemoteConfigManager.isLastFetchFailed()).thenReturn(false);
    when(mockRemoteConfigManager.getString(FIREBASE_PERFORMANCE_DISABLED_VERSIONS_FRC_KEY))
        .thenReturn(Optional.of(""));
    when(mockRemoteConfigManager.getBoolean(FIREBASE_PERFORMANCE_SDK_ENABLED_FRC_KEY))
        .thenReturn(Optional.of(true));

    assertThat(testConfigResolver.getIsServiceCollectionEnabled()).isTrue();
  }

  @Test
  public void getIsServiceCollectionEnabled_sdkDisabled_returnsFalse() {
    when(mockRemoteConfigManager.isLastFetchFailed()).thenReturn(false);
    when(mockRemoteConfigManager.getString(FIREBASE_PERFORMANCE_DISABLED_VERSIONS_FRC_KEY))
        .thenReturn(Optional.of(""));
    when(mockRemoteConfigManager.getBoolean(FIREBASE_PERFORMANCE_SDK_ENABLED_FRC_KEY))
        .thenReturn(Optional.of(false));

    assertThat(testConfigResolver.getIsServiceCollectionEnabled()).isFalse();
  }

  @Test
  public void getIsServiceCollectionEnabled_lastFetchSucceededFromFrc_returnFrcValue() {
    // As long as SDK version is not disabled, it depends on SDK enabled flag only for
    // enabled/disabled.
    when(mockRemoteConfigManager.getString(FIREBASE_PERFORMANCE_DISABLED_VERSIONS_FRC_KEY))
        .thenReturn(Optional.of(""));

    // Mock that SDK enabled flag from FRC is false, and fetch is successful.
    when(mockRemoteConfigManager.isLastFetchFailed()).thenReturn(false);
    when(mockRemoteConfigManager.getBoolean(FIREBASE_PERFORMANCE_SDK_ENABLED_FRC_KEY))
        .thenReturn(Optional.of(false));

    // Assert that final result is false, and FRC value is cached.
    assertThat(testConfigResolver.getIsServiceCollectionEnabled()).isFalse();
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(FIREBASE_PERFORMANCE_SDK_ENABLED_CACHE_KEY), eq(false));
  }

  @Test
  public void getIsServiceCollectionEnabled_lastFlagFetchFailedFromFrc_returnFalse() {
    // As long as SDK version is not disabled, it depends on SDK enabled flag only for
    // enabled/disabled.
    when(mockRemoteConfigManager.getString(FIREBASE_PERFORMANCE_DISABLED_VERSIONS_FRC_KEY))
        .thenReturn(Optional.of(""));

    // Mock that SDK enabled flag value from FRC is true, and fetch is failed.
    when(mockRemoteConfigManager.isLastFetchFailed()).thenReturn(true);
    when(mockRemoteConfigManager.getBoolean(FIREBASE_PERFORMANCE_SDK_ENABLED_FRC_KEY))
        .thenReturn(Optional.of(true));

    // Mock device caching value to be false.
    when(mockDeviceCacheManager.getBoolean(FIREBASE_PERFORMANCE_SDK_ENABLED_CACHE_KEY))
        .thenReturn(Optional.of(false));

    // Assert that final result is false, and no value is cached.
    assertThat(testConfigResolver.getIsServiceCollectionEnabled()).isFalse();
    verify(mockDeviceCacheManager, never()).setValue(any(), anyBoolean());
  }

  @Test
  public void getIsServiceCollectionEnabled_sdkEnabledFlagNoFrc_returnCacheValue() {
    // As long as SDK version is not disabled, it depends on SDK enabled flag only for
    // enabled/disabled.
    when(mockRemoteConfigManager.getString(FIREBASE_PERFORMANCE_DISABLED_VERSIONS_FRC_KEY))
        .thenReturn(Optional.of(""));

    // Mock that SDK enabled flag value from FRC doesn't exist.
    when(mockRemoteConfigManager.getBoolean(FIREBASE_PERFORMANCE_SDK_ENABLED_FRC_KEY))
        .thenReturn(Optional.absent());

    // Mock device caching value to be false.
    when(mockDeviceCacheManager.getBoolean(FIREBASE_PERFORMANCE_SDK_ENABLED_CACHE_KEY))
        .thenReturn(Optional.of(false));

    // Assert that final result is false, and no value is cached.
    assertThat(testConfigResolver.getIsServiceCollectionEnabled()).isFalse();
    verify(mockDeviceCacheManager, never()).setValue(any(), anyBoolean());
  }

  @Test
  public void getIsServiceCollectionEnabled_sdkEnabledFlagNoFrc_returnDefaultValue() {
    // As long as SDK version is not disabled, it depends on SDK enabled flag only for
    // enabled/disabled.
    when(mockRemoteConfigManager.getString(FIREBASE_PERFORMANCE_DISABLED_VERSIONS_FRC_KEY))
        .thenReturn(Optional.of(""));

    // Mock that SDK enabled flag value from FRC doesn't exist.
    when(mockRemoteConfigManager.getBoolean(FIREBASE_PERFORMANCE_SDK_ENABLED_FRC_KEY))
        .thenReturn(Optional.absent());

    // Mock device caching value to be false.
    when(mockDeviceCacheManager.getBoolean(FIREBASE_PERFORMANCE_SDK_ENABLED_CACHE_KEY))
        .thenReturn(Optional.absent());

    // Assert that final result is true, and no value is cached.
    assertThat(testConfigResolver.getIsServiceCollectionEnabled()).isTrue();
    verify(mockDeviceCacheManager, never()).setValue(any(), anyBoolean());
  }

  @Test
  public void getIsServiceCollectionEnabled_sdkDisabledVersionFlagNoFrc_returnDefaultValue() {
    // As long as SDK enabled flag is enabled, it depends on SDK disabled version only for
    // enabled/disabled.
    when(mockRemoteConfigManager.getBoolean(FIREBASE_PERFORMANCE_SDK_ENABLED_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getBoolean(FIREBASE_PERFORMANCE_SDK_ENABLED_CACHE_KEY))
        .thenReturn(Optional.absent());

    // Mock that Fireperf SDK version is not set by FRC.
    when(mockRemoteConfigManager.getString(FIREBASE_PERFORMANCE_DISABLED_VERSIONS_FRC_KEY))
        .thenReturn(Optional.absent());

    // Mock device caching layer to return default value
    when(mockDeviceCacheManager.getString(FIREBASE_PERFORMANCE_DISABLED_VERSIONS_CACHE_KEY))
        .thenReturn(Optional.absent());

    // Assert that final result is true, and no value is cached.
    assertThat(testConfigResolver.getIsServiceCollectionEnabled()).isTrue();
    verify(mockDeviceCacheManager, never()).setValue(any(), anyString());
  }

  @Test
  public void
      getIsPerformanceCollectionConfigValueAvailable_noDeviceCacheNoRemoteConfig_returnsFalse() {
    when(mockDeviceCacheManager.getBoolean(FIREBASE_PERFORMANCE_COLLECTION_ENABLED_CACHE_KEY))
        .thenReturn(Optional.absent());
    when(mockRemoteConfigManager.isLastFetchFailed()).thenReturn(false);
    when(mockRemoteConfigManager.getBoolean(FIREBASE_PERFORMANCE_SDK_ENABLED_FRC_KEY))
        .thenReturn(Optional.absent());
    assertThat(testConfigResolver.isCollectionEnabledConfigValueAvailable()).isFalse();
  }

  @Test
  public void
      getIsPerformanceCollectionConfigValueAvailable_noDeviceCacheHasRemoteConfigValueFalse_returnsTrue() {
    when(mockDeviceCacheManager.getBoolean(FIREBASE_PERFORMANCE_COLLECTION_ENABLED_CACHE_KEY))
        .thenReturn(Optional.absent());
    when(mockRemoteConfigManager.isLastFetchFailed()).thenReturn(false);
    when(mockRemoteConfigManager.getBoolean(FIREBASE_PERFORMANCE_SDK_ENABLED_FRC_KEY))
        .thenReturn(Optional.of(false));
    assertThat(testConfigResolver.isCollectionEnabledConfigValueAvailable()).isTrue();
  }

  @Test
  public void
      getIsPerformanceCollectionConfigValueAvailable_HasDeviceCacheNoRemoteConfigValue_returnsTrue() {
    when(mockDeviceCacheManager.getBoolean(FIREBASE_PERFORMANCE_COLLECTION_ENABLED_CACHE_KEY))
        .thenReturn(Optional.of(false));
    when(mockRemoteConfigManager.isLastFetchFailed()).thenReturn(false);
    when(mockRemoteConfigManager.getBoolean(FIREBASE_PERFORMANCE_SDK_ENABLED_FRC_KEY))
        .thenReturn(Optional.absent());
    assertThat(testConfigResolver.isCollectionEnabledConfigValueAvailable()).isTrue();
  }

  @Test
  public void
      getIsPerformanceCollectionConfigValueAvailable_HasDeviceCacheFalseHasRemoteConfigValueFalse_returnsTrue() {
    when(mockDeviceCacheManager.getBoolean(FIREBASE_PERFORMANCE_COLLECTION_ENABLED_CACHE_KEY))
        .thenReturn(Optional.of(false));
    when(mockRemoteConfigManager.isLastFetchFailed()).thenReturn(false);
    when(mockRemoteConfigManager.getBoolean(FIREBASE_PERFORMANCE_SDK_ENABLED_FRC_KEY))
        .thenReturn(Optional.of(false));
    assertThat(testConfigResolver.isCollectionEnabledConfigValueAvailable()).isTrue();
  }

  @Test
  public void
      getIsPerformanceCollectionConfigValueAvailable_noDeviceCacheHasRemoteConfigValueTrue_returnsTrue() {
    when(mockDeviceCacheManager.getBoolean(FIREBASE_PERFORMANCE_COLLECTION_ENABLED_CACHE_KEY))
        .thenReturn(Optional.of(false));
    when(mockRemoteConfigManager.isLastFetchFailed()).thenReturn(false);
    when(mockRemoteConfigManager.getBoolean(FIREBASE_PERFORMANCE_SDK_ENABLED_FRC_KEY))
        .thenReturn(Optional.of(true));
    assertThat(testConfigResolver.isCollectionEnabledConfigValueAvailable()).isTrue();
  }

  @Test
  public void
      getIsPerformanceCollectionConfigValueAvailable_hasDeviceCacheHasRemoteConfigValueFalse_returnsTrue() {
    when(mockDeviceCacheManager.getBoolean(FIREBASE_PERFORMANCE_COLLECTION_ENABLED_CACHE_KEY))
        .thenReturn(Optional.of(true));
    when(mockRemoteConfigManager.isLastFetchFailed()).thenReturn(false);
    when(mockRemoteConfigManager.getBoolean(FIREBASE_PERFORMANCE_SDK_ENABLED_FRC_KEY))
        .thenReturn(Optional.of(false));
    assertThat(testConfigResolver.isCollectionEnabledConfigValueAvailable()).isTrue();
  }

  @Test
  public void
      getIsPerformanceCollectionConfigValueAvailable_hasDeviceCacheHasRemoteConfig_returnsTrue() {
    when(mockDeviceCacheManager.getBoolean(FIREBASE_PERFORMANCE_COLLECTION_ENABLED_CACHE_KEY))
        .thenReturn(Optional.of(true));
    when(mockRemoteConfigManager.isLastFetchFailed()).thenReturn(false);
    when(mockRemoteConfigManager.getBoolean(FIREBASE_PERFORMANCE_SDK_ENABLED_FRC_KEY))
        .thenReturn(Optional.of(true));
    assertThat(testConfigResolver.isCollectionEnabledConfigValueAvailable()).isTrue();
  }

  @Test
  public void getIsPerformanceCollectionEnabled_notDeviceCacheOrMetadata_returnsNull() {
    when(mockDeviceCacheManager.getBoolean(FIREBASE_PERFORMANCE_COLLECTION_ENABLED_CACHE_KEY))
        .thenReturn(Optional.absent());
    assertThat(testConfigResolver.getIsPerformanceCollectionEnabled()).isNull();
  }

  @Test
  public void getIsPerformanceCollectionEnabled_collectionDeactivated_returnsFalse() {
    Bundle bundle = new Bundle();
    bundle.putBoolean("firebase_performance_collection_deactivated", true);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getIsPerformanceCollectionEnabled()).isFalse();
  }

  @Test
  public void getIsPerformanceCollectionEnabled_deviceCacheIsTrue_returnsTrue() {
    when(mockDeviceCacheManager.containsKey("isEnabled")).thenReturn(true);
    when(mockDeviceCacheManager.getBoolean("isEnabled")).thenReturn(Optional.of(true));

    // When no metadata is set.
    assertThat(testConfigResolver.getIsPerformanceCollectionEnabled()).isTrue();

    // When metadata is false.
    Bundle bundle = new Bundle();
    bundle.putBoolean("firebase_performance_collection_enabled", false);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getIsPerformanceCollectionEnabled()).isTrue();
  }

  @Test
  public void getIsPerformanceCollectionEnabled_noDeviceCacheAndMetadataIsTrue_returnsTrue() {
    // Arrange metadata.
    Bundle bundle = new Bundle();
    bundle.putBoolean("firebase_performance_collection_enabled", true);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    when(mockDeviceCacheManager.getBoolean(FIREBASE_PERFORMANCE_COLLECTION_ENABLED_CACHE_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getIsPerformanceCollectionEnabled()).isTrue();
  }

  @Test
  public void getIsPerformanceCollectionEnabled_noDeviceCacheAndMetadataIsFalse_returnsFalse() {
    // Arrange metadata.
    Bundle bundle = new Bundle();
    bundle.putBoolean("firebase_performance_collection_enabled", false);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    when(mockDeviceCacheManager.getBoolean(FIREBASE_PERFORMANCE_COLLECTION_ENABLED_CACHE_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getIsPerformanceCollectionEnabled()).isFalse();
  }

  @Test
  public void setIsPerformanceCollectionEnabled_collectionDeactivated_deviceCacheMangerNotCalled() {
    Bundle bundle = new Bundle();
    bundle.putBoolean("firebase_performance_collection_deactivated", true);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    testConfigResolver.setIsPerformanceCollectionEnabled(true);

    verify(mockDeviceCacheManager, never()).setValue(any(), anyBoolean());
  }

  @Test
  public void setIsPerformanceCollectionEnabled_defaultSetting_deviceCacheMangerIsCalled() {
    testConfigResolver.setIsPerformanceCollectionEnabled(false);

    verify(mockDeviceCacheManager, times(1)).setValue(any(), anyBoolean());
  }

  @Test
  public void getIsPerformanceCollectionDeactivated_flagNotSet_returnsFalse() {
    assertThat(testConfigResolver.getIsPerformanceCollectionDeactivated()).isFalse();
  }

  @Test
  public void getIsPerformanceCollectionDeactivated_flagIsTrue_returnsTrue() {
    Bundle bundle = new Bundle();
    bundle.putBoolean("firebase_performance_collection_deactivated", true);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getIsPerformanceCollectionDeactivated()).isTrue();
  }

  @Test
  public void getIsPerformanceCollectionDeactivated_flagIsFalse_returnsFalse() {
    Bundle bundle = new Bundle();
    bundle.putBoolean("firebase_performance_collection_deactivated", false);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getIsPerformanceCollectionDeactivated()).isFalse();
  }

  @Test
  public void getSessionsCpuCaptureFrequencyForegroundMs_validMetadata_returnsMetadata() {
    // #1 pass: Validate that method returns Remote Config Value when there is no metadata value.
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.of(400L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyForegroundMs()).isEqualTo(400L);

    // #2 pass: Validate that method returns Metadata value which takes higher precedence.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_METADATA_KEY, 200);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyForegroundMs()).isEqualTo(200L);

    // #3 pass: Validate that method returns another valid Metadata value which is 0.
    bundle.putInt(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_METADATA_KEY, 0);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyForegroundMs()).isEqualTo(0L);
  }

  @Test
  public void getSessionsCpuCaptureFrequencyForegroundMs_validMetadata_notSaveMetadataInCache() {
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_METADATA_KEY, 20);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    testConfigResolver.getSessionsCpuCaptureFrequencyForegroundMs();

    verify(mockDeviceCacheManager, never()).setValue(any(), any());
  }

  @Test
  public void
      getSessionsCpuCaptureFrequencyForegroundMs_invalidAndroidMetadataBundle_returnDefaultValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyForegroundMs()).isEqualTo(100L);

    // Android Metadata bundle value is negative.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_METADATA_KEY, -200);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyForegroundMs()).isEqualTo(100L);
  }

  @Test
  public void
      getSessionsCpuCaptureFrequencyForegroundMs_remoteConfigFetchFailed_returnDefaultRCValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY))
        .thenReturn(Optional.absent());
    when(mockRemoteConfigManager.isLastFetchFailed()).thenReturn(true);

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyForegroundMs()).isEqualTo(300L);
  }

  @Test
  public void
      getSessionsCpuCaptureFrequencyForegroundMs_invalidAndroidMetadataBundle_returnRemoteConfigValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.of(200L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyForegroundMs()).isEqualTo(200L);

    // Android Metadata bundle value is negative.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_METADATA_KEY, -200);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyForegroundMs()).isEqualTo(200L);
  }

  @Test
  public void getSessionsCpuCaptureFrequencyForegroundMs_invalidMetadataBundle_returnCacheValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY))
        .thenReturn(Optional.of(200L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyForegroundMs()).isEqualTo(200L);

    // Android Metadata bundle value is too high.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_METADATA_KEY, -200);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyForegroundMs()).isEqualTo(200L);
  }

  @Test
  public void
      getSessionsCpuCaptureFrequencyForegroundMs_validRemoteConfig_returnRemoteConfigValue() {
    // Case #1.
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.of(1000000L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyForegroundMs()).isEqualTo(1000000L);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY), eq(1000000L));

    // Case #2.
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.of(0L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyForegroundMs()).isEqualTo(0L);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY), eq(0L));

    // Case #3.
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.of(100L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyForegroundMs()).isEqualTo(100L);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY), eq(100L));

    // Case #4.
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.of(123456L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyForegroundMs()).isEqualTo(123456L);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY), eq(123456L));
  }

  @Test
  public void getSessionsCpuCaptureFrequencyForegroundMs_invalidRemoteConfig_returnDefaultValue() {
    when(mockDeviceCacheManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY))
        .thenReturn(Optional.absent());

    // Firebase Remote Config value is negative.
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.of(-200L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyForegroundMs()).isEqualTo(100L);
    verify(mockDeviceCacheManager, never()).setValue(any(), any());
  }

  @Test
  public void getSessionsCpuCaptureFrequencyForegroundMs_invalidRemoteConfig_returnCacheValue() {
    when(mockDeviceCacheManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY))
        .thenReturn(Optional.of(1L));

    // Firebase Remote Config value is negative.
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.of(-300L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyForegroundMs()).isEqualTo(1L);
    verify(mockDeviceCacheManager, never()).setValue(any(), any());
  }

  @Test
  public void getSessionsCpuCaptureFrequencyForegroundMs_validCache_returnCacheValue() {
    when(mockDeviceCacheManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY))
        .thenReturn(Optional.of(200L));

    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyForegroundMs()).isEqualTo(200L);
  }

  @Test
  public void getSessionsCpuCaptureFrequencyForegroundMs_invalidCache_returnDefaultValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.absent());

    // Firebase Remote Config value is negative.
    when(mockDeviceCacheManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY))
        .thenReturn(Optional.of(-200L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyForegroundMs()).isEqualTo(100L);
  }

  @Test
  public void
      getSessionsCpuCaptureFrequencyForegroundMs_metadataAndRemoteConfigAndCacheAreSet_metadataHasHighestConfigPrecedence() {
    // Set cache value.
    when(mockDeviceCacheManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY))
        .thenReturn(Optional.of(200L));

    // Set remote config value.
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.of(300L));

    // Set Android Manifest value.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_METADATA_KEY, 400);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyForegroundMs()).isEqualTo(400L);
  }

  @Test
  public void
      getSessionsCpuCaptureFrequencyForegroundMs_remoteConfigAndCacheAreSet_remoteConfigHasHighestConfigPrecedence() {
    // Set cache value.
    when(mockDeviceCacheManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY))
        .thenReturn(Optional.of(200L));

    // Set remote config value.
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.of(300L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyForegroundMs()).isEqualTo(300L);
  }

  @Test
  public void getSessionsCpuCaptureFrequencyBackgroundMs_validMetadata_returnsMetadata() {
    // #1 pass: Validate that method returns Remote Config Value when there is no metadata value.
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.of(400L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyBackgroundMs()).isEqualTo(400L);

    // #2 pass: Validate that method returns Metadata value which takes higher precedence.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_METADATA_KEY, 200);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyBackgroundMs()).isEqualTo(200L);

    // #3 pass: Validate that method returns another valid Metadata value which is 0.
    bundle.putInt(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_METADATA_KEY, 0);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyBackgroundMs()).isEqualTo(0L);
  }

  @Test
  public void getSessionsCpuCaptureFrequencyBackgroundMs_validMetadata_notSaveMetadataInCache() {
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_METADATA_KEY, 20);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    testConfigResolver.getSessionsCpuCaptureFrequencyBackgroundMs();

    verify(mockDeviceCacheManager, never()).setValue(any(), any());
  }

  @Test
  public void
      getSessionsCpuCaptureFrequencyBackgroundMs_invalidAndroidMetadataBundle_returnDefaultValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyBackgroundMs()).isEqualTo(0L);

    // Android Metadata bundle value is negative.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_METADATA_KEY, -200);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyBackgroundMs()).isEqualTo(0L);
  }

  @Test
  public void
      getSessionsCpuCaptureFrequencyBackgroundMs_invalidAndroidMetadataBundle_returnRemoteConfigValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.of(200L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyBackgroundMs()).isEqualTo(200L);

    // Android Metadata bundle value is negative.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_METADATA_KEY, -200);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyBackgroundMs()).isEqualTo(200L);
  }

  @Test
  public void getSessionsCpuCaptureFrequencyBackgroundMs_invalidMetadataBundle_returnCacheValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY))
        .thenReturn(Optional.of(200L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyBackgroundMs()).isEqualTo(200L);

    // Android Metadata bundle value is too high.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_METADATA_KEY, -200);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyBackgroundMs()).isEqualTo(200L);
  }

  @Test
  public void
      getSessionsCpuCaptureFrequencyBackgroundMs_validRemoteConfig_returnRemoteConfigValue() {
    // Case #1.
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.of(1000000L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyBackgroundMs()).isEqualTo(1000000L);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY), eq(1000000L));

    // Case #2.
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.of(0L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyBackgroundMs()).isEqualTo(0L);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY), eq(0L));

    // Case #3.
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.of(100L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyBackgroundMs()).isEqualTo(100L);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY), eq(100L));

    // Case #4.
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.of(123456L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyBackgroundMs()).isEqualTo(123456L);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY), eq(123456L));
  }

  @Test
  public void getSessionsCpuCaptureFrequencyBackgroundMs_invalidRemoteConfig_returnDefaultValue() {
    when(mockDeviceCacheManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY))
        .thenReturn(Optional.absent());

    // Firebase Remote Config value is negative.
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.of(-200L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyBackgroundMs()).isEqualTo(0L);
    verify(mockDeviceCacheManager, never()).setValue(any(), any());
  }

  @Test
  public void getSessionsCpuCaptureFrequencyBackgroundMs_invalidRemoteConfig_returnCacheValue() {
    when(mockDeviceCacheManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY))
        .thenReturn(Optional.of(1L));

    // Firebase Remote Config value is negative.
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.of(-300L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyBackgroundMs()).isEqualTo(1L);
    verify(mockDeviceCacheManager, never()).setValue(any(), any());
  }

  @Test
  public void getSessionsCpuCaptureFrequencyBackgroundMs_validCache_returnCacheValue() {
    when(mockDeviceCacheManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY))
        .thenReturn(Optional.of(200L));

    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyBackgroundMs()).isEqualTo(200L);
  }

  @Test
  public void getSessionsCpuCaptureFrequencyBackgroundMs_invalidCache_returnDefaultValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.absent());

    // Firebase Remote Config value is negative.
    when(mockDeviceCacheManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY))
        .thenReturn(Optional.of(-200L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyBackgroundMs()).isEqualTo(0L);
  }

  @Test
  public void
      getSessionsCpuCaptureFrequencyBackgroundMs_metadataAndRemoteConfigAndCacheAreSet_metadataHasHighestConfigPrecedence() {
    // Set cache value.
    when(mockDeviceCacheManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY))
        .thenReturn(Optional.of(200L));

    // Set remote config value.
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.of(300L));

    // Set Android Manifest value.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_METADATA_KEY, 400);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyBackgroundMs()).isEqualTo(400L);
  }

  @Test
  public void
      getSessionsCpuCaptureFrequencyBackgroundMs_remoteConfigAndCacheAreSet_remoteConfigHasHighestConfigPrecedence() {
    // Set cache value.
    when(mockDeviceCacheManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY))
        .thenReturn(Optional.of(200L));

    // Set remote config value.
    when(mockRemoteConfigManager.getLong(SESSIONS_CPU_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.of(300L));

    assertThat(testConfigResolver.getSessionsCpuCaptureFrequencyBackgroundMs()).isEqualTo(300L);
  }

  @Test
  public void getSessionsMemoryCaptureFrequencyForegroundMs_validMetadata_returnsMetadata() {
    // #1 pass: Validate that method returns Remote Config Value when there is no metadata value.
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.of(400L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyForegroundMs()).isEqualTo(400L);

    // #2 pass: Validate that method returns Metadata value which takes higher precedence.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_METADATA_KEY, 200);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyForegroundMs()).isEqualTo(200L);

    // #3 pass: Validate that method returns another valid Metadata value which is 0.
    bundle.putInt(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_METADATA_KEY, 0);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyForegroundMs()).isEqualTo(0L);
  }

  @Test
  public void getSessionsMemoryCaptureFrequencyForegroundMs_validMetadata_notSaveMetadataInCache() {
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_METADATA_KEY, 20);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    testConfigResolver.getSessionsMemoryCaptureFrequencyForegroundMs();

    verify(mockDeviceCacheManager, never()).setValue(any(), any());
  }

  @Test
  public void
      getSessionsMemoryCaptureFrequencyForegroundMs_invalidAndroidMetadataBundle_returnDefaultValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyForegroundMs()).isEqualTo(100L);

    // Android Metadata bundle value is negative.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_METADATA_KEY, -200);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyForegroundMs()).isEqualTo(100L);
  }

  @Test
  public void
      getSessionsMemoryCaptureFrequencyForegroundMs_remoteConfigFetchFailed_returnDefaultRCValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY))
        .thenReturn(Optional.absent());
    when(mockRemoteConfigManager.isLastFetchFailed()).thenReturn(true);

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyForegroundMs()).isEqualTo(300L);
  }

  @Test
  public void
      getSessionsMemoryCaptureFrequencyForegroundMs_invalidAndroidMetadataBundle_returnRemoteConfigValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.of(200L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyForegroundMs()).isEqualTo(200L);

    // Android Metadata bundle value is negative.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_METADATA_KEY, -200);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyForegroundMs()).isEqualTo(200L);
  }

  @Test
  public void
      getSessionsMemoryCaptureFrequencyForegroundMs_invalidMetadataBundle_returnCacheValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY))
        .thenReturn(Optional.of(200L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyForegroundMs()).isEqualTo(200L);

    // Android Metadata bundle value is too high.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_METADATA_KEY, -200);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyForegroundMs()).isEqualTo(200L);
  }

  @Test
  public void
      getSessionsMemoryCaptureFrequencyForegroundMs_validRemoteConfig_returnRemoteConfigValue() {
    // Case #1.
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.of(1000000L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyForegroundMs())
        .isEqualTo(1000000L);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY), eq(1000000L));

    // Case #2.
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.of(0L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyForegroundMs()).isEqualTo(0L);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY), eq(0L));

    // Case #3.
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.of(100L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyForegroundMs()).isEqualTo(100L);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY), eq(100L));

    // Case #4.
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.of(123456L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyForegroundMs())
        .isEqualTo(123456L);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY), eq(123456L));
  }

  @Test
  public void
      getSessionsMemoryCaptureFrequencyForegroundMs_invalidRemoteConfig_returnDefaultValue() {
    when(mockDeviceCacheManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY))
        .thenReturn(Optional.absent());

    // Firebase Remote Config value is negative.
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.of(-200L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyForegroundMs()).isEqualTo(100L);
    verify(mockDeviceCacheManager, never()).setValue(any(), any());
  }

  @Test
  public void getSessionsMemoryCaptureFrequencyForegroundMs_invalidRemoteConfig_returnCacheValue() {
    when(mockDeviceCacheManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY))
        .thenReturn(Optional.of(1L));

    // Firebase Remote Config value is negative.
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.of(-300L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyForegroundMs()).isEqualTo(1L);
    verify(mockDeviceCacheManager, never()).setValue(any(), any());
  }

  @Test
  public void getSessionsMemoryCaptureFrequencyForegroundMs_validCache_returnCacheValue() {
    when(mockDeviceCacheManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY))
        .thenReturn(Optional.of(200L));

    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyForegroundMs()).isEqualTo(200L);
  }

  @Test
  public void getSessionsMemoryCaptureFrequencyForegroundMs_invalidCache_returnDefaultValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.absent());

    // Firebase Remote Config value is negative.
    when(mockDeviceCacheManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY))
        .thenReturn(Optional.of(-200L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyForegroundMs()).isEqualTo(100L);
  }

  @Test
  public void
      getSessionsMemoryCaptureFrequencyForegroundMs_metadataAndRemoteConfigAndCacheAreSet_metadataHasHighestConfigPrecedence() {
    // Set cache value.
    when(mockDeviceCacheManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY))
        .thenReturn(Optional.of(200L));

    // Set remote config value.
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.of(300L));

    // Set Android Manifest value.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_METADATA_KEY, 400);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyForegroundMs()).isEqualTo(400L);
  }

  @Test
  public void
      getSessionsMemoryCaptureFrequencyForegroundMs_remoteConfigAndCacheAreSet_remoteConfigHasHighestConfigPrecedence() {
    // Set cache value.
    when(mockDeviceCacheManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_CACHE_KEY))
        .thenReturn(Optional.of(200L));

    // Set remote config value.
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_FG_MS_FRC_KEY))
        .thenReturn(Optional.of(300L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyForegroundMs()).isEqualTo(300L);
  }

  @Test
  public void getSessionsMemoryCaptureFrequencyBackgroundMs_validMetadata_returnsMetadata() {
    // #1 pass: Validate that method returns Remote Config Value when there is no metadata value.
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.of(400L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyBackgroundMs()).isEqualTo(400L);

    // #2 pass: Validate that method returns Metadata value which takes higher precedence.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_METADATA_KEY, 200);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyBackgroundMs()).isEqualTo(200L);

    // #3 pass: Validate that method returns another valid Metadata value which is 0.
    bundle.putInt(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_METADATA_KEY, 0);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyBackgroundMs()).isEqualTo(0L);
  }

  @Test
  public void getSessionsMemoryCaptureFrequencyBackgroundMs_validMetadata_notSaveMetadataInCache() {
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_METADATA_KEY, 20);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    testConfigResolver.getSessionsMemoryCaptureFrequencyBackgroundMs();

    verify(mockDeviceCacheManager, never()).setValue(any(), any());
  }

  @Test
  public void
      getSessionsMemoryCaptureFrequencyBackgroundMs_invalidAndroidMetadataBundle_returnDefaultValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyBackgroundMs()).isEqualTo(0L);

    // Android Metadata bundle value is negative.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_METADATA_KEY, -200);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyBackgroundMs()).isEqualTo(0L);
  }

  @Test
  public void
      getSessionsMemoryCaptureFrequencyBackgroundMs_invalidAndroidMetadataBundle_returnRemoteConfigValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.of(200L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyBackgroundMs()).isEqualTo(200L);

    // Android Metadata bundle value is negative.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_METADATA_KEY, -200);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyBackgroundMs()).isEqualTo(200L);
  }

  @Test
  public void
      getSessionsMemoryCaptureFrequencyBackgroundMs_invalidMetadataBundle_returnCacheValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY))
        .thenReturn(Optional.of(200L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyBackgroundMs()).isEqualTo(200L);

    // Android Metadata bundle value is too high.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_METADATA_KEY, -200);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyBackgroundMs()).isEqualTo(200L);
  }

  @Test
  public void
      getSessionsMemoryCaptureFrequencyBackgroundMs_validRemoteConfig_returnRemoteConfigValue() {
    // Case #1.
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.of(1000000L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyBackgroundMs())
        .isEqualTo(1000000L);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY), eq(1000000L));

    // Case #2.
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.of(0L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyBackgroundMs()).isEqualTo(0L);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY), eq(0L));

    // Case #3.
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.of(100L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyBackgroundMs()).isEqualTo(100L);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY), eq(100L));

    // Case #4.
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.of(123456L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyBackgroundMs())
        .isEqualTo(123456L);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY), eq(123456L));
  }

  @Test
  public void
      getSessionsMemoryCaptureFrequencyBackgroundMs_invalidRemoteConfig_returnDefaultValue() {
    when(mockDeviceCacheManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY))
        .thenReturn(Optional.absent());

    // Firebase Remote Config value is negative.
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.of(-200L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyBackgroundMs()).isEqualTo(0L);
    verify(mockDeviceCacheManager, never()).setValue(any(), any());
  }

  @Test
  public void getSessionsMemoryCaptureFrequencyBackgroundMs_invalidRemoteConfig_returnCacheValue() {
    when(mockDeviceCacheManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY))
        .thenReturn(Optional.of(1L));

    // Firebase Remote Config value is negative.
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.of(-300L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyBackgroundMs()).isEqualTo(1L);
    verify(mockDeviceCacheManager, never()).setValue(any(), any());
  }

  @Test
  public void getSessionsMemoryCaptureFrequencyBackgroundMs_validCache_returnCacheValue() {
    when(mockDeviceCacheManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY))
        .thenReturn(Optional.of(200L));

    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyBackgroundMs()).isEqualTo(200L);
  }

  @Test
  public void getSessionsMemoryCaptureFrequencyBackgroundMs_invalidCache_returnDefaultValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.absent());

    // Firebase Remote Config value is negative.
    when(mockDeviceCacheManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY))
        .thenReturn(Optional.of(-200L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyBackgroundMs()).isEqualTo(0L);
  }

  @Test
  public void
      getSessionsMemoryCaptureFrequencyBackgroundMs_metadataAndRemoteConfigAndCacheAreSet_metadataHasHighestConfigPrecedence() {
    // Set cache value.
    when(mockDeviceCacheManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY))
        .thenReturn(Optional.of(200L));

    // Set remote config value.
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.of(300L));

    // Set Android Manifest value.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_METADATA_KEY, 400);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyBackgroundMs()).isEqualTo(400L);
  }

  @Test
  public void
      getSessionsMemoryCaptureFrequencyBackgroundMs_remoteConfigAndCacheAreSet_remoteConfigHasHighestConfigPrecedence() {
    // Set cache value.
    when(mockDeviceCacheManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_CACHE_KEY))
        .thenReturn(Optional.of(200L));

    // Set remote config value.
    when(mockRemoteConfigManager.getLong(SESSIONS_MEMORY_CAPTURE_FREQUENCY_BG_MS_FRC_KEY))
        .thenReturn(Optional.of(300L));

    assertThat(testConfigResolver.getSessionsMemoryCaptureFrequencyBackgroundMs()).isEqualTo(300L);
  }

  @Test
  public void getSessionsMaxDurationMinutes_validMetadata_returnsMetadata() {
    // #1 pass: Validate that method returns Remote Config Value when there is no metadata value.
    when(mockRemoteConfigManager.getLong(SESSIONS_MAX_DURATION_MIN_FRC_KEY))
        .thenReturn(Optional.of(400L));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(400L);

    // #2 pass: Validate that method returns Metadata value which takes higher precedence.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_MAX_DURATION_MIN_METADATA_KEY, 200);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(200L);
  }

  @Test
  public void getSessionsMaxDurationMinutes_validMetadata_notSaveMetadataInCache() {
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_MAX_DURATION_MIN_METADATA_KEY, 20);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    testConfigResolver.getSessionsMaxDurationMinutes();

    verify(mockDeviceCacheManager, never()).setValue(any(), any());
  }

  @Test
  public void getSessionsMaxDurationMinutes_invalidAndroidMetadataBundle_returnDefaultValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_MAX_DURATION_MIN_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(SESSIONS_MAX_DURATION_MIN_CACHE_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(240L);

    // Android Metadata bundle value is negative.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_MAX_DURATION_MIN_METADATA_KEY, -200);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(240L);

    // Android Metadata bundle value is 0.
    bundle.putInt(SESSIONS_MAX_DURATION_MIN_METADATA_KEY, 0);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(240L);
  }

  @Test
  public void getSessionsMaxDurationMinutes_invalidAndroidMetadataBundle_returnRemoteConfigValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_MAX_DURATION_MIN_FRC_KEY))
        .thenReturn(Optional.of(200L));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(200L);

    // Android Metadata bundle value is negative.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_MAX_DURATION_MIN_METADATA_KEY, -200);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(200L);

    // Android Metadata bundle value is 0.
    bundle.putInt(SESSIONS_MAX_DURATION_MIN_METADATA_KEY, 0);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(200L);
  }

  @Test
  public void getSessionsMaxDurationMinutes_invalidMetadataBundle_returnCacheValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_MAX_DURATION_MIN_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(SESSIONS_MAX_DURATION_MIN_CACHE_KEY))
        .thenReturn(Optional.of(200L));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(200L);

    // Android Metadata bundle value is too high.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_MAX_DURATION_MIN_METADATA_KEY, -200);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(200L);

    // Android Metadata bundle value is 0.
    bundle.putInt(SESSIONS_MAX_DURATION_MIN_METADATA_KEY, 0);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(200L);
  }

  @Test
  public void getSessionsMaxDurationMinutes_validRemoteConfig_returnRemoteConfigValue() {
    // Case #1.
    when(mockRemoteConfigManager.getLong(SESSIONS_MAX_DURATION_MIN_FRC_KEY))
        .thenReturn(Optional.of(1000000L));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(1000000L);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSIONS_MAX_DURATION_MIN_CACHE_KEY), eq(1000000L));

    // Case #2.
    when(mockRemoteConfigManager.getLong(SESSIONS_MAX_DURATION_MIN_FRC_KEY))
        .thenReturn(Optional.of(10L));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(10L);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSIONS_MAX_DURATION_MIN_CACHE_KEY), eq(10L));

    // Case #3.
    when(mockRemoteConfigManager.getLong(SESSIONS_MAX_DURATION_MIN_FRC_KEY))
        .thenReturn(Optional.of(240L));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(240L);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSIONS_MAX_DURATION_MIN_CACHE_KEY), eq(240L));

    // Case #4.
    when(mockRemoteConfigManager.getLong(SESSIONS_MAX_DURATION_MIN_FRC_KEY))
        .thenReturn(Optional.of(123456L));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(123456L);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSIONS_MAX_DURATION_MIN_CACHE_KEY), eq(123456L));
  }

  @Test
  public void getSessionsMaxDurationMinutes_invalidRemoteConfig_returnDefaultValue() {
    when(mockDeviceCacheManager.getLong(SESSIONS_MAX_DURATION_MIN_CACHE_KEY))
        .thenReturn(Optional.absent());

    // Firebase Remote Config value is negative.
    when(mockRemoteConfigManager.getLong(SESSIONS_MAX_DURATION_MIN_FRC_KEY))
        .thenReturn(Optional.of(-200L));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(240L);
    verify(mockDeviceCacheManager, never()).setValue(any(), any());

    // Android Metadata bundle value is 0.
    when(mockRemoteConfigManager.getLong(SESSIONS_MAX_DURATION_MIN_FRC_KEY))
        .thenReturn(Optional.of(0L));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(240L);
    verify(mockDeviceCacheManager, never()).setValue(any(), any());
  }

  @Test
  public void getSessionsMaxDurationMinutes_invalidRemoteConfig_returnCacheValue() {
    when(mockDeviceCacheManager.getLong(SESSIONS_MAX_DURATION_MIN_CACHE_KEY))
        .thenReturn(Optional.of(1L));

    // Firebase Remote Config value is negative.
    when(mockRemoteConfigManager.getLong(SESSIONS_MAX_DURATION_MIN_FRC_KEY))
        .thenReturn(Optional.of(-300L));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(1L);
    verify(mockDeviceCacheManager, never()).setValue(any(), any());

    // Android Metadata bundle value is 0.
    when(mockRemoteConfigManager.getLong(SESSIONS_MAX_DURATION_MIN_FRC_KEY))
        .thenReturn(Optional.of(0L));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(1L);
    verify(mockDeviceCacheManager, never()).setValue(any(), any());
  }

  @Test
  public void getSessionsMaxDurationMinutes_validCache_returnCacheValue() {
    when(mockDeviceCacheManager.getLong(SESSIONS_MAX_DURATION_MIN_CACHE_KEY))
        .thenReturn(Optional.of(200L));

    when(mockRemoteConfigManager.getLong(SESSIONS_MAX_DURATION_MIN_FRC_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(200L);
  }

  @Test
  public void getSessionsMaxDurationMinutes_invalidCache_returnDefaultValue() {
    when(mockRemoteConfigManager.getLong(SESSIONS_MAX_DURATION_MIN_FRC_KEY))
        .thenReturn(Optional.absent());

    // Firebase Remote Config value is negative.
    when(mockDeviceCacheManager.getLong(SESSIONS_MAX_DURATION_MIN_CACHE_KEY))
        .thenReturn(Optional.of(-200L));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(240L);

    // Firebase Remote Config value is 0.
    when(mockDeviceCacheManager.getLong(SESSIONS_MAX_DURATION_MIN_CACHE_KEY))
        .thenReturn(Optional.of(0L));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(240L);
  }

  @Test
  public void
      getSessionsMaxDurationMinutes_metadataAndRemoteConfigAndCacheAreSet_metadataHasHighestConfigPrecedence() {
    // Set cache value.
    when(mockDeviceCacheManager.getLong(SESSIONS_MAX_DURATION_MIN_CACHE_KEY))
        .thenReturn(Optional.of(200L));

    // Set remote config value.
    when(mockRemoteConfigManager.getLong(SESSIONS_MAX_DURATION_MIN_FRC_KEY))
        .thenReturn(Optional.of(300L));

    // Set Android Manifest value.
    Bundle bundle = new Bundle();
    bundle.putInt(SESSIONS_MAX_DURATION_MIN_METADATA_KEY, 400);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(400L);
  }

  @Test
  public void
      getSessionsMaxDurationMinutes_remoteConfigAndCacheAreSet_remoteConfigHasHighestConfigPrecedence() {
    // Set cache value.
    when(mockDeviceCacheManager.getLong(SESSIONS_MAX_DURATION_MIN_CACHE_KEY))
        .thenReturn(Optional.of(200L));

    // Set remote config value.
    when(mockRemoteConfigManager.getLong(SESSIONS_MAX_DURATION_MIN_FRC_KEY))
        .thenReturn(Optional.of(300L));

    assertThat(testConfigResolver.getSessionsMaxDurationMinutes()).isEqualTo(300L);
  }

  @Test
  public void getTraceSamplingRate_noRemoteConfig_returnsDefault() {
    when(mockRemoteConfigManager.getDouble(TRACE_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getDouble(TRACE_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.of(1.00));

    assertThat(testConfigResolver.getTraceSamplingRate()).isEqualTo(1.00);
  }

  @Test
  public void getTraceSamplingRate_remoteConfigFetchFailed_returnsRCFailureDefault() {
    when(mockRemoteConfigManager.getDouble(TRACE_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getDouble(TRACE_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.absent());
    when(mockRemoteConfigManager.isLastFetchFailed()).thenReturn(true);

    assertThat(testConfigResolver.getTraceSamplingRate()).isEqualTo(1.00 / 1000);
  }

  @Test
  public void getTraceSamplingRate_noRemoteConfigHasCache_returnsCache() {
    when(mockRemoteConfigManager.getDouble(TRACE_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getDouble(TRACE_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.of(0.01));

    assertThat(testConfigResolver.getTraceSamplingRate()).isEqualTo(0.01);
  }

  @Test
  public void getTraceSamplingRate_validRemoteConfig_returnsRemoteConfigValue() {
    when(mockRemoteConfigManager.getDouble(TRACE_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(0.01));
    when(mockDeviceCacheManager.getDouble(TRACE_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.of(0.02));

    assertThat(testConfigResolver.getTraceSamplingRate()).isEqualTo(0.01);
    verify(mockDeviceCacheManager, times(1)).setValue(eq(TRACE_SAMPLING_RATE_CACHE_KEY), eq(0.01));
  }

  @Test
  public void getTraceSamplingRate_remoteConfigValueTooHigh_returnsDefaultValue() {
    when(mockRemoteConfigManager.getDouble(TRACE_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(1.01));
    when(mockDeviceCacheManager.getDouble(TRACE_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getTraceSamplingRate()).isEqualTo(1.00);
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getTraceSamplingRate_remoteConfigValueTooLow_returnsDefaultValue() {
    when(mockRemoteConfigManager.getDouble(TRACE_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(-0.01));
    when(mockDeviceCacheManager.getDouble(TRACE_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getTraceSamplingRate()).isEqualTo(1.00);
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getTraceSamplingRate_10digitRemoteConfig_returnsRemoteConfigValue() {
    when(mockRemoteConfigManager.getDouble(TRACE_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(0.0000000001));

    assertThat(testConfigResolver.getTraceSamplingRate()).isEqualTo(0.0000000001);
  }

  @Test
  public void getNetworkRequestSamplingRate_noRemoteConfig_returnsDefault() {
    when(mockRemoteConfigManager.getDouble(NETWORK_REQUEST_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getDouble(NETWORK_REQUEST_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getNetworkRequestSamplingRate()).isEqualTo(1.00);
  }

  @Test
  public void getNetworkRequestSamplingRate_remoteConfigFetchFailed_returnsRCFailureDefault() {
    when(mockRemoteConfigManager.getDouble(NETWORK_REQUEST_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getDouble(NETWORK_REQUEST_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.absent());
    when(mockRemoteConfigManager.isLastFetchFailed()).thenReturn(true);

    assertThat(testConfigResolver.getNetworkRequestSamplingRate()).isEqualTo(1.00 / 1000);
  }

  @Test
  public void getNetworkRequestSamplingRate_noRemoteConfigHasCache_returnsCache() {
    when(mockRemoteConfigManager.getDouble(NETWORK_REQUEST_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getDouble(NETWORK_REQUEST_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.of(0.01));

    assertThat(testConfigResolver.getNetworkRequestSamplingRate()).isEqualTo(0.01);
  }

  @Test
  public void getNetworkRequestSamplingRate_validRemoteConfig_returnsRemoteConfigValue() {
    when(mockRemoteConfigManager.getDouble(NETWORK_REQUEST_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(0.01));
    when(mockDeviceCacheManager.getDouble(NETWORK_REQUEST_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getNetworkRequestSamplingRate()).isEqualTo(0.01);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(NETWORK_REQUEST_SAMPLING_RATE_CACHE_KEY), eq(0.01));
  }

  @Test
  public void getNetworkRequestSamplingRate_remoteConfigValueTooHigh_returnsDefaultValue() {
    when(mockRemoteConfigManager.getDouble(NETWORK_REQUEST_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(1.01));
    when(mockDeviceCacheManager.getDouble(NETWORK_REQUEST_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getNetworkRequestSamplingRate()).isEqualTo(1.00);
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getNetworkRequestSamplingRate_remoteConfigValueTooLow_returnsDefaultValue() {
    when(mockDeviceCacheManager.getDouble(NETWORK_REQUEST_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.absent());
    when(mockRemoteConfigManager.getDouble(NETWORK_REQUEST_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(-0.01));

    assertThat(testConfigResolver.getNetworkRequestSamplingRate()).isEqualTo(1.00);
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getNetworkRequestSamplingRate_10digitRemoteConfig_returnsRemoteConfigValue() {
    when(mockRemoteConfigManager.getDouble(NETWORK_REQUEST_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(0.0000000001));

    assertThat(testConfigResolver.getNetworkRequestSamplingRate()).isEqualTo(0.0000000001);
  }

  @Test
  public void getSessionsSamplingRate_validMetadata_returnsMetadata() {
    // #1 pass: Validate that method returns Remote Config Value when there is no metadata value.
    when(mockRemoteConfigManager.getDouble(SESSION_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(0.01));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.01);

    // #2 pass: Validate that method returns Metadata value which takes higher precedence.
    Bundle bundle = new Bundle();
    bundle.putDouble("sessions_sampling_percentage", 20.0);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.2);
  }

  @Test
  public void getSessionsSamplingRate_validMetadata_notSaveMetadataInCache() {
    Bundle bundle = new Bundle();
    bundle.putDouble("sessions_sampling_percentage", 20.0);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.2);

    verify(mockDeviceCacheManager, never()).setValue(any(), any());
  }

  @Test
  public void getSessionsSamplingRate_invalidAndroidMetadataBundle_returnDefaultValue() {
    when(mockRemoteConfigManager.getDouble(SESSION_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getDouble(SESSION_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.01);

    // Case #1: Android Metadata bundle value is too high.
    Bundle bundle = new Bundle();
    bundle.putDouble("sessions_sampling_percentage", 200.00);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.01);

    // Case #2: Android Metadata bundle value is too low.
    bundle = new Bundle();
    bundle.putDouble("sessions_sampling_percentage", -1.00);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.01);
  }

  @Test
  public void getSessionsSamplingRate_invalidAndroidMetadataBundle_returnRemoteConfigValue() {
    when(mockRemoteConfigManager.getDouble(SESSION_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(0.25));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.25);

    // Case #1: Android Metadata bundle value is too high.
    Bundle bundle = new Bundle();
    bundle.putDouble("sessions_sampling_percentage", 200.00);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.25);

    // Case #2: Android Metadata bundle value is too low.
    bundle = new Bundle();
    bundle.putDouble("sessions_sampling_percentage", -1.00);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.25);
  }

  @Test
  public void getSessionsSamplingRate_invalidMetadataBundle_returnCacheValue() {
    when(mockRemoteConfigManager.getDouble(SESSION_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getDouble(SESSION_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.of(1.0));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(1.0);

    // Case #1: Android Metadata bundle value is too high.
    Bundle bundle = new Bundle();
    bundle.putDouble("sessions_sampling_percentage", 200.00);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(1.0);

    // Case #2: Android Metadata bundle value is too low.
    bundle = new Bundle();
    bundle.putDouble("sessions_sampling_percentage", -1.00);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(1.0);
  }

  @Test
  public void getSessionsSamplingRate_validRemoteConfig_returnRemoteConfigValue() {
    when(mockRemoteConfigManager.getDouble(SESSION_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(0.25));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.25);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSION_SAMPLING_RATE_CACHE_KEY), eq(0.25));

    when(mockRemoteConfigManager.getDouble(SESSION_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(0.0));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.0);
    verify(mockDeviceCacheManager, times(1)).setValue(eq(SESSION_SAMPLING_RATE_CACHE_KEY), eq(0.0));

    when(mockRemoteConfigManager.getDouble(SESSION_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(0.00005));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.00005);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSION_SAMPLING_RATE_CACHE_KEY), eq(0.00005));

    when(mockRemoteConfigManager.getDouble(SESSION_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(0.0000000001));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.0000000001);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(SESSION_SAMPLING_RATE_CACHE_KEY), eq(0.0000000001));
  }

  @Test
  public void getSessionsSamplingRate_invalidRemoteConfig_returnDefaultValue() {
    // Mock behavior that device cache doesn't have session sampling rate value.
    when(mockDeviceCacheManager.getDouble(SESSION_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.absent());

    // Case #1: Firebase Remote Config value is too high.
    when(mockRemoteConfigManager.getDouble(SESSION_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(1.01));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.01);
    verify(mockDeviceCacheManager, never()).setValue(any(), any());

    // Case #2: Firebase Remote Config value is too low.
    when(mockRemoteConfigManager.getDouble(SESSION_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(-0.1));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.01);
    verify(mockDeviceCacheManager, never()).setValue(any(), any());
  }

  @Test
  public void getSessionsSamplingRate_invalidRemoteConfig_returnCacheValue() {
    // Mock behavior that device cache doesn't have session sampling rate value.
    when(mockDeviceCacheManager.getDouble(SESSION_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.of(0.25));

    // Case #1: Firebase Remote Config value is too high.
    when(mockRemoteConfigManager.getDouble(SESSION_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(1.01));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.25);
    verify(mockDeviceCacheManager, never()).setValue(any(), any());

    // Case #2: Firebase Remote Config value is too low.
    when(mockRemoteConfigManager.getDouble(SESSION_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(-0.1));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.25);
    verify(mockDeviceCacheManager, never()).setValue(any(), any());
  }

  @Test
  public void getSessionsSamplingRate_remoteConfigFetchFailed_returnsRCFailureDefault() {
    when(mockDeviceCacheManager.getDouble(SESSION_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.absent());
    when(mockRemoteConfigManager.getDouble(SESSION_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockRemoteConfigManager.isLastFetchFailed()).thenReturn(true);

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.01 / 1000);
  }

  @Test
  public void getSessionsSamplingRate_validCache_returnCacheValue() {
    when(mockDeviceCacheManager.getDouble(SESSION_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.of(1.0));

    when(mockRemoteConfigManager.getDouble(SESSION_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(1.0);
  }

  @Test
  public void getSessionsSamplingRate_invalidCache_returnDefaultValue() {
    // Mock behavior that remote config doesn't have session sampling rate value.
    when(mockRemoteConfigManager.getDouble(SESSION_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.absent());

    // Case #1: Device Cache value is too high.
    when(mockDeviceCacheManager.getDouble(SESSION_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.of(10.0));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.01);

    // Case #2: Device Cache value is too low.
    when(mockDeviceCacheManager.getDouble(SESSION_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.of(-1.0));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.01);
  }

  @Test
  public void
      getSessionsSamplingRate_metadataAndRemoteConfigAndCacheAreSet_metadataHasHighestConfigPrecedence() {
    // Set cache value.
    when(mockDeviceCacheManager.getDouble(SESSION_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.of(0.2));

    // Set remote config value.
    when(mockRemoteConfigManager.getDouble(SESSION_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(0.3));

    // Set Android Manifest value.
    Bundle bundle = new Bundle();
    bundle.putDouble("sessions_sampling_percentage", 4.0);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.04);
  }

  @Test
  public void
      getSessionsSamplingRate_remoteConfigAndCacheAreSet_remoteConfigHasHighestConfigPrecedence() {
    // Set cache value.
    when(mockDeviceCacheManager.getDouble(SESSION_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.of(0.2));

    // Set remote config value.
    when(mockRemoteConfigManager.getDouble(SESSION_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(0.3));

    assertThat(testConfigResolver.getSessionsSamplingRate()).isEqualTo(0.3);
  }

  @Test
  public void getNetworkEventCountBackground_remoteConfigExists_returnsRemoteConfig() {
    when(mockRemoteConfigManager.getLong(NETWORK_EVENT_COUNT_BG_FRC_KEY))
        .thenReturn(Optional.of(1000L));

    assertThat(testConfigResolver.getNetworkEventCountBackground()).isEqualTo(1000L);
    verify(mockDeviceCacheManager, times(1)).setValue(anyString(), anyLong());
  }

  @Test
  public void getNetworkEventCountBackground_inValidRemoteConfig_returnsDefault() {
    when(mockRemoteConfigManager.getLong(NETWORK_EVENT_COUNT_BG_FRC_KEY))
        .thenReturn(Optional.of(-1L));
    when(mockDeviceCacheManager.getLong(NETWORK_EVENT_COUNT_BG_CACHE_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getNetworkEventCountBackground()).isEqualTo(70L);

    verify(mockRemoteConfigManager, times(1)).getLong(anyString());
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getNetworkEventCountBackground_noRemoteConfig_returnsCache() {
    when(mockRemoteConfigManager.getLong(NETWORK_EVENT_COUNT_BG_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(NETWORK_EVENT_COUNT_BG_CACHE_KEY))
        .thenReturn(Optional.of(100L));

    assertThat(testConfigResolver.getNetworkEventCountBackground()).isEqualTo(100L);

    verify(mockRemoteConfigManager, times(1)).getLong(anyString());
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getNetworkEventCountBackground_noRemoteConfigOrCache_returnsDefault() {
    when(mockRemoteConfigManager.getLong(NETWORK_EVENT_COUNT_BG_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(NETWORK_EVENT_COUNT_BG_CACHE_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getNetworkEventCountBackground()).isEqualTo(70L);

    verify(mockRemoteConfigManager, times(1)).getLong(anyString());
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getNetworkEventCountBackground_invalidCache_returnsDefault() {
    when(mockRemoteConfigManager.getLong(NETWORK_EVENT_COUNT_BG_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(NETWORK_EVENT_COUNT_BG_CACHE_KEY))
        .thenReturn(Optional.of(-1L));

    assertThat(testConfigResolver.getNetworkEventCountBackground()).isEqualTo(70L);

    verify(mockRemoteConfigManager, times(1)).getLong(anyString());
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getNetworkEventCountForeground_remoteConfigExists_returnsRemoteConfig() {
    when(mockRemoteConfigManager.getLong(NETWORK_EVENT_COUNT_FG_FRC_KEY))
        .thenReturn(Optional.of(1000L));

    assertThat(testConfigResolver.getNetworkEventCountForeground()).isEqualTo(1000L);
    verify(mockDeviceCacheManager, times(1)).setValue(anyString(), anyLong());
  }

  @Test
  public void getNetworkEventCountForeground_inValidRemoteConfig_returnsDefault() {
    when(mockRemoteConfigManager.getLong(NETWORK_EVENT_COUNT_FG_FRC_KEY))
        .thenReturn(Optional.of(-1L));
    when(mockDeviceCacheManager.getLong(NETWORK_EVENT_COUNT_FG_CACHE_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getNetworkEventCountForeground()).isEqualTo(700L);

    verify(mockRemoteConfigManager, times(1)).getLong(anyString());
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getNetworkEventCountForeground_noRemoteConfig_returnsCache() {
    when(mockRemoteConfigManager.getLong(NETWORK_EVENT_COUNT_FG_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(NETWORK_EVENT_COUNT_FG_CACHE_KEY))
        .thenReturn(Optional.of(100L));

    assertThat(testConfigResolver.getNetworkEventCountForeground()).isEqualTo(100L);

    verify(mockRemoteConfigManager, times(1)).getLong(anyString());
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getNetworkEventCountForeground_noRemoteConfigOrCache_returnsDefault() {
    when(mockRemoteConfigManager.getLong(NETWORK_EVENT_COUNT_FG_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(NETWORK_EVENT_COUNT_FG_CACHE_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getNetworkEventCountForeground()).isEqualTo(700L);

    verify(mockRemoteConfigManager, times(1)).getLong(anyString());
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getNetworkEventCountForeground_invalidCache_returnsDefault() {
    when(mockRemoteConfigManager.getLong(NETWORK_EVENT_COUNT_FG_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(NETWORK_EVENT_COUNT_FG_CACHE_KEY))
        .thenReturn(Optional.of(-1L));

    assertThat(testConfigResolver.getNetworkEventCountForeground()).isEqualTo(700L);

    verify(mockRemoteConfigManager, times(1)).getLong(anyString());
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getTraceEventCountBackground_remoteConfigExists_returnsRemoteConfig() {
    when(mockRemoteConfigManager.getLong(TRACE_EVENT_COUNT_BG_FRC_KEY))
        .thenReturn(Optional.of(1000L));

    assertThat(testConfigResolver.getTraceEventCountBackground()).isEqualTo(1000L);
    verify(mockDeviceCacheManager, times(1)).setValue(anyString(), anyLong());
  }

  @Test
  public void getTraceEventCountBackground_inValidRemoteConfig_returnsDefault() {
    when(mockRemoteConfigManager.getLong(TRACE_EVENT_COUNT_BG_FRC_KEY))
        .thenReturn(Optional.of(-1L));
    when(mockDeviceCacheManager.getLong(TRACE_EVENT_COUNT_BG_CACHE_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getTraceEventCountBackground()).isEqualTo(30L);

    verify(mockRemoteConfigManager, times(1)).getLong(anyString());
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getTraceEventCountBackground_noRemoteConfig_returnsCache() {
    when(mockRemoteConfigManager.getLong(TRACE_EVENT_COUNT_BG_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(TRACE_EVENT_COUNT_BG_CACHE_KEY))
        .thenReturn(Optional.of(100L));

    assertThat(testConfigResolver.getTraceEventCountBackground()).isEqualTo(100L);

    verify(mockRemoteConfigManager, times(1)).getLong(anyString());
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getTraceEventCountBackground_noRemoteConfigOrCache_returnsDefault() {
    when(mockRemoteConfigManager.getLong(TRACE_EVENT_COUNT_BG_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(TRACE_EVENT_COUNT_BG_CACHE_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getTraceEventCountBackground()).isEqualTo(30L);

    verify(mockRemoteConfigManager, times(1)).getLong(anyString());
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getTraceEventCountBackground_invalidCache_returnsDefault() {
    when(mockRemoteConfigManager.getLong(TRACE_EVENT_COUNT_BG_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(TRACE_EVENT_COUNT_BG_CACHE_KEY))
        .thenReturn(Optional.of(-1L));

    assertThat(testConfigResolver.getTraceEventCountBackground()).isEqualTo(30L);

    verify(mockRemoteConfigManager, times(1)).getLong(anyString());
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getTraceEventCountForeground_remoteConfigExists_returnsRemoteConfig() {
    when(mockRemoteConfigManager.getLong(TRACE_EVENT_COUNT_FG_FRC_KEY))
        .thenReturn(Optional.of(1000L));

    assertThat(testConfigResolver.getTraceEventCountForeground()).isEqualTo(1000L);
    verify(mockDeviceCacheManager, times(1)).setValue(anyString(), anyLong());
  }

  @Test
  public void getTraceEventCountForeground_inValidRemoteConfig_returnsDefault() {
    when(mockRemoteConfigManager.getLong(TRACE_EVENT_COUNT_FG_FRC_KEY))
        .thenReturn(Optional.of(-1L));
    when(mockDeviceCacheManager.getLong(TRACE_EVENT_COUNT_FG_CACHE_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getTraceEventCountForeground()).isEqualTo(300L);

    verify(mockRemoteConfigManager, times(1)).getLong(anyString());
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getTraceEventCountForeground_noRemoteConfig_returnsCache() {
    when(mockRemoteConfigManager.getLong(TRACE_EVENT_COUNT_FG_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(TRACE_EVENT_COUNT_FG_CACHE_KEY))
        .thenReturn(Optional.of(100L));

    assertThat(testConfigResolver.getTraceEventCountForeground()).isEqualTo(100L);

    verify(mockRemoteConfigManager, times(1)).getLong(anyString());
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getTraceEventCountForeground_noRemoteConfigOrCache_returnsDefault() {
    when(mockRemoteConfigManager.getLong(TRACE_EVENT_COUNT_FG_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(TRACE_EVENT_COUNT_FG_CACHE_KEY))
        .thenReturn(Optional.of(300L));

    assertThat(testConfigResolver.getTraceEventCountForeground()).isEqualTo(300L);

    verify(mockRemoteConfigManager, times(1)).getLong(anyString());
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getTraceEventCountForeground_invalidCache_returnsDefault() {
    when(mockRemoteConfigManager.getLong(TRACE_EVENT_COUNT_FG_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(TRACE_EVENT_COUNT_FG_CACHE_KEY))
        .thenReturn(Optional.of(-1L));

    assertThat(testConfigResolver.getTraceEventCountForeground()).isEqualTo(300L);

    verify(mockRemoteConfigManager, times(1)).getLong(anyString());
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getRateLimitSec_remoteConfigExists_returnsRemoteConfig() {
    when(mockRemoteConfigManager.getLong(TIME_LIMIT_SEC_FG_FRC_KEY)).thenReturn(Optional.of(1000L));

    assertThat(testConfigResolver.getRateLimitSec()).isEqualTo(1000L);
    verify(mockDeviceCacheManager, times(1)).setValue(anyString(), anyLong());
  }

  @Test
  public void getRateLimitSec_inValidRemoteConfig_returnsDefault() {
    when(mockRemoteConfigManager.getLong(TIME_LIMIT_SEC_FG_FRC_KEY)).thenReturn(Optional.of(-1L));
    when(mockDeviceCacheManager.getLong(TIME_LIMIT_SEC_FG_CACHE_KEY)).thenReturn(Optional.of(600L));

    assertThat(testConfigResolver.getRateLimitSec()).isEqualTo(600L);

    verify(mockRemoteConfigManager, times(1)).getLong(anyString());
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getRateLimitSec_noRemoteConfig_returnsCache() {
    when(mockRemoteConfigManager.getLong(TIME_LIMIT_SEC_FG_FRC_KEY)).thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(TIME_LIMIT_SEC_FG_CACHE_KEY)).thenReturn(Optional.of(100L));

    assertThat(testConfigResolver.getRateLimitSec()).isEqualTo(100L);

    verify(mockRemoteConfigManager, times(1)).getLong(anyString());
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getRateLimitSec_noRemoteConfigOrCache_returnsDefault() {
    when(mockRemoteConfigManager.getLong(TIME_LIMIT_SEC_FG_FRC_KEY)).thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(TIME_LIMIT_SEC_FG_CACHE_KEY)).thenReturn(Optional.of(600L));

    assertThat(testConfigResolver.getRateLimitSec()).isEqualTo(600L);

    verify(mockRemoteConfigManager, times(1)).getLong(anyString());
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  @Test
  public void getRateLimitSec_invalidCache_returnsDefault() {
    when(mockRemoteConfigManager.getLong(TIME_LIMIT_SEC_FG_FRC_KEY)).thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getLong(TIME_LIMIT_SEC_FG_CACHE_KEY)).thenReturn(Optional.of(-1L));

    assertThat(testConfigResolver.getRateLimitSec()).isEqualTo(600L);

    verify(mockRemoteConfigManager, times(1)).getLong(anyString());
    verify(mockDeviceCacheManager, never()).setValue(anyString(), anyLong());
  }

  // TODO: Commenting out the Flaky test.
  //  This test is failing sometimes because when ":test" is run ReflectionHelpers is not able to
  //  reflectively modify the static fields for "BuildConfig.class". Running ":testDebugUnitTest"
  //  and ":testReleaseUnitTest" is succeeding though.
  //  NOTE: This is true for all usages of ReflectionHelpers in this class however, other tests are
  //  passing because we are just relying on default values (see firebase-per.gradle) in those
  // tests.
  //  @Test
  //  public void getAndCacheLogSourceName_enforceDefaultValue_returnsDefault() {
  //    ReflectionHelpers.setStaticField(BuildConfig.class, "TRANSPORT_LOG_SRC",
  // "FIREPERF_AUTOPUSH");
  //    ReflectionHelpers.setStaticField(
  //        BuildConfig.class, "ENFORCE_DEFAULT_LOG_SRC", Boolean.valueOf(true));
  //
  //    assertThat(testConfigResolver.getAndCacheLogSourceName()).isEqualTo("FIREPERF_AUTOPUSH");
  //    // Skip remote config and device cache if default value is enforced.
  //    verify(mockRemoteConfigManager, never())
  //        .getRemoteConfigValueOrDefault(eq("fpr_log_source"), anyLong());
  //    verify(mockDeviceCacheManager, never())
  //        .setValue(eq("com.google.firebase.perf.LogSourceName"), anyString());
  //  }

  @Test
  public void getFragmentSamplingRate_validMetadata_returnsMetadata() {
    // #1 pass: Validate that method returns Remote Config Value when there is no metadata value.
    when(mockRemoteConfigManager.getDouble(FRAGMENT_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(0.01));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(0.01);

    // #2 pass: Validate that method returns Metadata value which takes higher precedence.
    Bundle bundle = new Bundle();
    bundle.putDouble("fragment_sampling_percentage", 20.0);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(0.2);
  }

  @Test
  public void getFragmentSamplingRate_validMetadata_notSaveMetadataInCache() {
    Bundle bundle = new Bundle();
    bundle.putDouble("fragment_sampling_percentage", 20.0);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(0.2);

    verify(mockDeviceCacheManager, never()).setValue(any(), any());
  }

  @Test
  public void getFragmentSamplingRate_invalidAndroidMetadataBundle_returnDefaultValue() {
    double defaultValue = ConfigurationConstants.FragmentSamplingRate.getInstance().getDefault();
    when(mockRemoteConfigManager.getDouble(FRAGMENT_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getDouble(FRAGMENT_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(defaultValue);

    // Case #1: Android Metadata bundle value is too high.
    Bundle bundle = new Bundle();
    bundle.putDouble("fragment_sampling_percentage", 200.00);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(defaultValue);

    // Case #2: Android Metadata bundle value is too low.
    bundle = new Bundle();
    bundle.putDouble("fragment_sampling_percentage", -1.00);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(defaultValue);
  }

  @Test
  public void getFragmentSamplingRate_invalidAndroidMetadataBundle_returnRemoteConfigValue() {
    when(mockRemoteConfigManager.getDouble(FRAGMENT_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(0.25));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(0.25);

    // Case #1: Android Metadata bundle value is too high.
    Bundle bundle = new Bundle();
    bundle.putDouble("fragment_sampling_percentage", 200.00);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(0.25);

    // Case #2: Android Metadata bundle value is too low.
    bundle = new Bundle();
    bundle.putDouble("fragment_sampling_percentage", -1.00);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(0.25);
  }

  @Test
  public void getFragmentSamplingRate_invalidMetadataBundle_returnCacheValue() {
    when(mockRemoteConfigManager.getDouble(FRAGMENT_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.absent());
    when(mockDeviceCacheManager.getDouble(FRAGMENT_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.of(1.0));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(1.0);

    // Case #1: Android Metadata bundle value is too high.
    Bundle bundle = new Bundle();
    bundle.putDouble("fragment_sampling_percentage", 200.00);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(1.0);

    // Case #2: Android Metadata bundle value is too low.
    bundle = new Bundle();
    bundle.putDouble("fragment_sampling_percentage", -1.00);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(1.0);
  }

  @Test
  public void getFragmentSamplingRate_validRemoteConfig_returnRemoteConfigValue() {
    when(mockRemoteConfigManager.getDouble(FRAGMENT_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(0.25));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(0.25);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(FRAGMENT_SAMPLING_RATE_CACHE_KEY), eq(0.25));

    when(mockRemoteConfigManager.getDouble(FRAGMENT_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(0.0));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(0.0);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(FRAGMENT_SAMPLING_RATE_CACHE_KEY), eq(0.0));

    when(mockRemoteConfigManager.getDouble(FRAGMENT_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(0.00005));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(0.00005);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(FRAGMENT_SAMPLING_RATE_CACHE_KEY), eq(0.00005));

    when(mockRemoteConfigManager.getDouble(FRAGMENT_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(0.0000000001));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(0.0000000001);
    verify(mockDeviceCacheManager, times(1))
        .setValue(eq(FRAGMENT_SAMPLING_RATE_CACHE_KEY), eq(0.0000000001));
  }

  @Test
  public void getFragmentSamplingRate_invalidRemoteConfig_returnDefaultValue() {
    double defaultValue = ConfigurationConstants.FragmentSamplingRate.getInstance().getDefault();
    // Mock behavior that device cache doesn't have session sampling rate value.
    when(mockDeviceCacheManager.getDouble(FRAGMENT_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.absent());

    // Case #1: Firebase Remote Config value is too high.
    when(mockRemoteConfigManager.getDouble(FRAGMENT_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(1.01));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(defaultValue);
    verify(mockDeviceCacheManager, never()).setValue(any(), any());

    // Case #2: Firebase Remote Config value is too low.
    when(mockRemoteConfigManager.getDouble(FRAGMENT_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(-0.1));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(defaultValue);
    verify(mockDeviceCacheManager, never()).setValue(any(), any());
  }

  @Test
  public void getFragmentSamplingRate_invalidRemoteConfig_returnCacheValue() {
    // Mock behavior that device cache doesn't have session sampling rate value.
    when(mockDeviceCacheManager.getDouble(FRAGMENT_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.of(0.25));

    // Case #1: Firebase Remote Config value is too high.
    when(mockRemoteConfigManager.getDouble(FRAGMENT_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(1.01));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(0.25);
    verify(mockDeviceCacheManager, never()).setValue(any(), any());

    // Case #2: Firebase Remote Config value is too low.
    when(mockRemoteConfigManager.getDouble(FRAGMENT_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(-0.1));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(0.25);
    verify(mockDeviceCacheManager, never()).setValue(any(), any());
  }

  @Test
  public void getFragmentSamplingRate_validCache_returnCacheValue() {
    when(mockDeviceCacheManager.getDouble(FRAGMENT_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.of(1.0));

    when(mockRemoteConfigManager.getDouble(FRAGMENT_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.absent());

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(1.0);
  }

  @Test
  public void getFragmentSamplingRate_invalidCache_returnDefaultValue() {
    double defaultValue = ConfigurationConstants.FragmentSamplingRate.getInstance().getDefault();
    // Mock behavior that remote config doesn't have session sampling rate value.
    when(mockRemoteConfigManager.getDouble(FRAGMENT_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.absent());

    // Case #1: Device Cache value is too high.
    when(mockDeviceCacheManager.getDouble(FRAGMENT_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.of(10.0));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(defaultValue);

    // Case #2: Device Cache value is too low.
    when(mockDeviceCacheManager.getDouble(FRAGMENT_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.of(-1.0));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(defaultValue);
  }

  @Test
  public void
      getFragmentSamplingRate_metadataAndRemoteConfigAndCacheAreSet_metadataHasHighestConfigPrecedence() {
    // Set cache value.
    when(mockDeviceCacheManager.getDouble(FRAGMENT_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.of(0.2));

    // Set remote config value.
    when(mockRemoteConfigManager.getDouble(FRAGMENT_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(0.3));

    // Set Android Manifest value.
    Bundle bundle = new Bundle();
    bundle.putDouble("fragment_sampling_percentage", 4.0);
    testConfigResolver.setMetadataBundle(new ImmutableBundle(bundle));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(0.04);
  }

  @Test
  public void
      getFragmentSamplingRate_remoteConfigAndCacheAreSet_remoteConfigHasHighestConfigPrecedence() {
    // Set cache value.
    when(mockDeviceCacheManager.getDouble(FRAGMENT_SAMPLING_RATE_CACHE_KEY))
        .thenReturn(Optional.of(0.2));

    // Set remote config value.
    when(mockRemoteConfigManager.getDouble(FRAGMENT_SAMPLING_RATE_FRC_KEY))
        .thenReturn(Optional.of(0.3));

    assertThat(testConfigResolver.getFragmentSamplingRate()).isEqualTo(0.3);
  }

  private static void setStaticFinalField(Class clazz, String fieldName, Object value) {
    try {
      Field field = clazz.getDeclaredField(fieldName);
      if (field != null) {
        field.setAccessible(true);

        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

        field.set(null, value);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
