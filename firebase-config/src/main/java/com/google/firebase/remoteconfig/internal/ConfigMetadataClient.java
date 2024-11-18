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

import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.LAST_FETCH_STATUS_FAILURE;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.LAST_FETCH_STATUS_NO_FETCH_YET;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.LAST_FETCH_STATUS_SUCCESS;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.LAST_FETCH_STATUS_THROTTLED;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.TAG;
import static com.google.firebase.remoteconfig.RemoteConfigComponent.CONNECTION_TIMEOUT_IN_SECONDS;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.CUSTOM_SIGNALS;
import static com.google.firebase.remoteconfig.internal.ConfigFetchHandler.DEFAULT_MINIMUM_FETCH_INTERVAL_IN_SECONDS;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigInfo;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import java.lang.annotation.Retention;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Client for handling Firebase Remote Config (FRC) metadata that is saved to disk and persisted
 * across App life cycles.
 *
 * @author Miraziz Yusupov
 */
public class ConfigMetadataClient {
  @Retention(SOURCE)
  @IntDef({
    LAST_FETCH_STATUS_SUCCESS,
    LAST_FETCH_STATUS_NO_FETCH_YET,
    LAST_FETCH_STATUS_FAILURE,
    LAST_FETCH_STATUS_THROTTLED
  })
  @interface LastFetchStatus {}

  /** Indicates that there have been no successful fetch attempts yet. */
  @VisibleForTesting public static final long LAST_FETCH_TIME_IN_MILLIS_NO_FETCH_YET = -1L;

  static final Date LAST_FETCH_TIME_NO_FETCH_YET = new Date(LAST_FETCH_TIME_IN_MILLIS_NO_FETCH_YET);

  static final int NO_FAILED_REALTIME_STREAMS = 0;
  @VisibleForTesting static final int NO_FAILED_FETCHES = 0;
  private static final long NO_BACKOFF_TIME_IN_MILLIS = -1L;
  @VisibleForTesting static final Date NO_BACKOFF_TIME = new Date(NO_BACKOFF_TIME_IN_MILLIS);

  private static final String FETCH_TIMEOUT_IN_SECONDS_KEY = "fetch_timeout_in_seconds";
  private static final String MINIMUM_FETCH_INTERVAL_IN_SECONDS_KEY =
      "minimum_fetch_interval_in_seconds";
  private static final String LAST_FETCH_STATUS_KEY = "last_fetch_status";
  private static final String LAST_SUCCESSFUL_FETCH_TIME_IN_MILLIS_KEY =
      "last_fetch_time_in_millis";
  private static final String LAST_FETCH_ETAG_KEY = "last_fetch_etag";
  private static final String BACKOFF_END_TIME_IN_MILLIS_KEY = "backoff_end_time_in_millis";
  private static final String NUM_FAILED_FETCHES_KEY = "num_failed_fetches";
  private static final String LAST_TEMPLATE_VERSION = "last_template_version";

  /** Realtime backoff key names. */
  private static final String NUM_FAILED_REALTIME_STREAMS_KEY = "num_failed_realtime_streams";

  private static final String REALTIME_BACKOFF_END_TIME_IN_MILLIS_KEY =
      "realtime_backoff_end_time_in_millis";

  /** Constants for custom signal limits. */
  private static final int CUSTOM_SIGNALS_MAX_KEY_LENGTH = 250;

  private static final int CUSTOM_SIGNALS_MAX_STRING_VALUE_LENGTH = 500;
  private static final int CUSTOM_SIGNALS_MAX_COUNT = 100;

  private final SharedPreferences frcMetadata;

  private final Object frcInfoLock;
  private final Object backoffMetadataLock;
  private final Object realtimeBackoffMetadataLock;
  private final Object customSignalsLock;

  public ConfigMetadataClient(SharedPreferences frcMetadata) {
    this.frcMetadata = frcMetadata;
    this.frcInfoLock = new Object();
    this.backoffMetadataLock = new Object();
    this.realtimeBackoffMetadataLock = new Object();
    this.customSignalsLock = new Object();
  }

  public long getFetchTimeoutInSeconds() {
    return frcMetadata.getLong(FETCH_TIMEOUT_IN_SECONDS_KEY, CONNECTION_TIMEOUT_IN_SECONDS);
  }

  public long getMinimumFetchIntervalInSeconds() {
    return frcMetadata.getLong(
        MINIMUM_FETCH_INTERVAL_IN_SECONDS_KEY, DEFAULT_MINIMUM_FETCH_INTERVAL_IN_SECONDS);
  }

  @LastFetchStatus
  int getLastFetchStatus() {
    return frcMetadata.getInt(LAST_FETCH_STATUS_KEY, LAST_FETCH_STATUS_NO_FETCH_YET);
  }

  Date getLastSuccessfulFetchTime() {
    return new Date(
        frcMetadata.getLong(
            LAST_SUCCESSFUL_FETCH_TIME_IN_MILLIS_KEY, LAST_FETCH_TIME_IN_MILLIS_NO_FETCH_YET));
  }

  @Nullable
  String getLastFetchETag() {
    return frcMetadata.getString(LAST_FETCH_ETAG_KEY, null);
  }

  long getLastTemplateVersion() {
    return frcMetadata.getLong(LAST_TEMPLATE_VERSION, 0);
  }

  public FirebaseRemoteConfigInfo getInfo() {
    // A lock is used here to prevent the setters in this class from changing the state of
    // frcMetadata during a getInfo call.
    synchronized (frcInfoLock) {
      long lastSuccessfulFetchTimeInMillis =
          frcMetadata.getLong(
              LAST_SUCCESSFUL_FETCH_TIME_IN_MILLIS_KEY, LAST_FETCH_TIME_IN_MILLIS_NO_FETCH_YET);
      @LastFetchStatus
      int lastFetchStatus =
          frcMetadata.getInt(LAST_FETCH_STATUS_KEY, LAST_FETCH_STATUS_NO_FETCH_YET);

      FirebaseRemoteConfigSettings settings =
          new FirebaseRemoteConfigSettings.Builder()
              .setFetchTimeoutInSeconds(
                  frcMetadata.getLong(FETCH_TIMEOUT_IN_SECONDS_KEY, CONNECTION_TIMEOUT_IN_SECONDS))
              .setMinimumFetchIntervalInSeconds(
                  frcMetadata.getLong(
                      MINIMUM_FETCH_INTERVAL_IN_SECONDS_KEY,
                      DEFAULT_MINIMUM_FETCH_INTERVAL_IN_SECONDS))
              .build();

      return FirebaseRemoteConfigInfoImpl.newBuilder()
          .withLastFetchStatus(lastFetchStatus)
          .withLastSuccessfulFetchTimeInMillis(lastSuccessfulFetchTimeInMillis)
          .withConfigSettings(settings)
          .build();
    }
  }

  /**
   * Clears all metadata values from memory and disk.
   *
   * <p>The method is blocking and returns only when the values in disk are also cleared.
   */
  @WorkerThread
  public void clear() {
    synchronized (frcInfoLock) {
      frcMetadata.edit().clear().commit();
    }
  }

  /**
   * Updates the stored settings with the given {@link FirebaseRemoteConfigSettings}, and blocks
   * until the changes are persisted to disk.
   *
   * @param settings the new settings to apply.
   */
  @WorkerThread
  public void setConfigSettings(FirebaseRemoteConfigSettings settings) {
    synchronized (frcInfoLock) {
      frcMetadata
          .edit()
          .putLong(FETCH_TIMEOUT_IN_SECONDS_KEY, settings.getFetchTimeoutInSeconds())
          .putLong(
              MINIMUM_FETCH_INTERVAL_IN_SECONDS_KEY, settings.getMinimumFetchIntervalInSeconds())
          .commit();
    }
  }

  /**
   * Updates the stored settings with the given {@link FirebaseRemoteConfigSettings} and returns
   * before waiting on the disk write to complete.
   *
   * @param settings the new settings to apply.
   */
  public void setConfigSettingsWithoutWaitingOnDiskWrite(FirebaseRemoteConfigSettings settings) {
    synchronized (frcInfoLock) {
      frcMetadata
          .edit()
          .putLong(FETCH_TIMEOUT_IN_SECONDS_KEY, settings.getFetchTimeoutInSeconds())
          .putLong(
              MINIMUM_FETCH_INTERVAL_IN_SECONDS_KEY, settings.getMinimumFetchIntervalInSeconds())
          .apply();
    }
  }

  void updateLastFetchAsSuccessfulAt(Date fetchTime) {
    synchronized (frcInfoLock) {
      frcMetadata
          .edit()
          .putInt(LAST_FETCH_STATUS_KEY, LAST_FETCH_STATUS_SUCCESS)
          .putLong(LAST_SUCCESSFUL_FETCH_TIME_IN_MILLIS_KEY, fetchTime.getTime())
          .apply();
    }
  }

  void updateLastFetchAsFailed() {
    synchronized (frcInfoLock) {
      frcMetadata.edit().putInt(LAST_FETCH_STATUS_KEY, LAST_FETCH_STATUS_FAILURE).apply();
    }
  }

  void updateLastFetchAsThrottled() {
    synchronized (frcInfoLock) {
      frcMetadata.edit().putInt(LAST_FETCH_STATUS_KEY, LAST_FETCH_STATUS_THROTTLED).apply();
    }
  }

  void setLastFetchETag(String eTag) {
    synchronized (frcInfoLock) {
      frcMetadata.edit().putString(LAST_FETCH_ETAG_KEY, eTag).apply();
    }
  }

  void setLastTemplateVersion(long templateVersion) {
    synchronized (frcInfoLock) {
      frcMetadata.edit().putLong(LAST_TEMPLATE_VERSION, templateVersion).apply();
    }
  }

  // -----------------------------------------------------------------
  // Exponential backoff logic.
  // -----------------------------------------------------------------

  BackoffMetadata getBackoffMetadata() {
    synchronized (backoffMetadataLock) {
      return new BackoffMetadata(
          frcMetadata.getInt(NUM_FAILED_FETCHES_KEY, NO_FAILED_FETCHES),
          new Date(frcMetadata.getLong(BACKOFF_END_TIME_IN_MILLIS_KEY, NO_BACKOFF_TIME_IN_MILLIS)));
    }
  }

  void setBackoffMetadata(int numFailedFetches, Date backoffEndTime) {
    synchronized (backoffMetadataLock) {
      frcMetadata
          .edit()
          .putInt(NUM_FAILED_FETCHES_KEY, numFailedFetches)
          .putLong(BACKOFF_END_TIME_IN_MILLIS_KEY, backoffEndTime.getTime())
          .apply();
    }
  }

  public void setCustomSignals(Map<String, Object> newCustomSignals) {
    synchronized (customSignalsLock) {
      // Retrieve existing custom signals
      Map<String, Object> existingCustomSignals = getCustomSignals();

      for (Map.Entry<String, Object> entry : newCustomSignals.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();

        // Validate value type, and key and value length
        if (value != null && !(value instanceof String || value instanceof Long)) {
          Log.w(TAG, "Invalid custom signal: Custom signal values must be of type String or Long.");
          return;
        }
        if (key.length() > CUSTOM_SIGNALS_MAX_KEY_LENGTH
            || (value instanceof String
                && ((String) value).length() > CUSTOM_SIGNALS_MAX_STRING_VALUE_LENGTH)) {
          Log.w(
              TAG,
              String.format(
                  "Invalid custom signal: Custom signal keys must be %d characters or less, and string values must be %d characters or less.",
                  CUSTOM_SIGNALS_MAX_KEY_LENGTH, CUSTOM_SIGNALS_MAX_STRING_VALUE_LENGTH));
          return;
        }

        // Merge new signals with existing ones, overwriting existing keys.
        // Also, remove entries where the new value is null.
        if (value != null) {
          existingCustomSignals.put(key, value);
        } else {
          existingCustomSignals.remove(key);
        }
      }

      // Check if the map has actually changed and the size limit
      if (existingCustomSignals.equals(getCustomSignals())) {
        return;
      }
      if (existingCustomSignals.size() > CUSTOM_SIGNALS_MAX_COUNT) {
        Log.w(
            TAG,
            String.format(
                "Invalid custom signal: Too many custom signals provided. The maximum allowed is %d.",
                CUSTOM_SIGNALS_MAX_COUNT));
        return;
      }

      frcMetadata
          .edit()
          .putString(CUSTOM_SIGNALS, new JSONObject(existingCustomSignals).toString())
          .commit();
    }
  }

  public Map<String, Object> getCustomSignals() {
    String jsonString = frcMetadata.getString(CUSTOM_SIGNALS, "{}");
    try {
      JSONObject existingCustomSignalsJson = new JSONObject(jsonString);
      Map<String, Object> custom_signals = new HashMap<>();
      Iterator<String> keys = existingCustomSignalsJson.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        Object value = existingCustomSignalsJson.get(key);
        if (value instanceof Integer) {
          value = existingCustomSignalsJson.getLong(key);
        }
        custom_signals.put(key, value);
      }
      return custom_signals;
    } catch (JSONException e) {
      return new HashMap<>();
    }
  }

  void resetBackoff() {
    setBackoffMetadata(NO_FAILED_FETCHES, NO_BACKOFF_TIME);
  }

  /**
   * Container for backoff metadata values such as the number of failed fetches and the backoff end
   * time.
   *
   * <p>The purpose of this class is to avoid race conditions when retrieving backoff metadata
   * values separately.
   */
  static class BackoffMetadata {
    private int numFailedFetches;
    private Date backoffEndTime;

    BackoffMetadata(int numFailedFetches, Date backoffEndTime) {
      this.numFailedFetches = numFailedFetches;
      this.backoffEndTime = backoffEndTime;
    }

    int getNumFailedFetches() {
      return numFailedFetches;
    }

    Date getBackoffEndTime() {
      return backoffEndTime;
    }
  }

  // -----------------------------------------------------------------
  // Realtime exponential backoff logic.
  // -----------------------------------------------------------------

  @VisibleForTesting
  public RealtimeBackoffMetadata getRealtimeBackoffMetadata() {
    synchronized (realtimeBackoffMetadataLock) {
      return new RealtimeBackoffMetadata(
          frcMetadata.getInt(NUM_FAILED_REALTIME_STREAMS_KEY, NO_FAILED_REALTIME_STREAMS),
          new Date(
              frcMetadata.getLong(
                  REALTIME_BACKOFF_END_TIME_IN_MILLIS_KEY, NO_BACKOFF_TIME_IN_MILLIS)));
    }
  }

  void setRealtimeBackoffMetadata(int numFailedStreams, Date backoffEndTime) {
    synchronized (realtimeBackoffMetadataLock) {
      frcMetadata
          .edit()
          .putInt(NUM_FAILED_REALTIME_STREAMS_KEY, numFailedStreams)
          .putLong(REALTIME_BACKOFF_END_TIME_IN_MILLIS_KEY, backoffEndTime.getTime())
          .apply();
    }
  }

  void resetRealtimeBackoff() {
    setRealtimeBackoffMetadata(NO_FAILED_REALTIME_STREAMS, NO_BACKOFF_TIME);
  }

  /**
   * Container for backoff metadata values such as the number of failed streams and the backoff end
   * time.
   *
   * <p>The purpose of this class is to avoid race conditions when retrieving backoff metadata
   * values separately.
   */
  @VisibleForTesting
  public static class RealtimeBackoffMetadata {
    private int numFailedStreams;
    private Date backoffEndTime;

    @VisibleForTesting
    public RealtimeBackoffMetadata(int numFailedStreams, Date backoffEndTime) {
      this.numFailedStreams = numFailedStreams;
      this.backoffEndTime = backoffEndTime;
    }

    int getNumFailedStreams() {
      return numFailedStreams;
    }

    Date getBackoffEndTime() {
      return backoffEndTime;
    }
  }
}
