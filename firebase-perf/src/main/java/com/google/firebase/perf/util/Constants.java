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

package com.google.firebase.perf.util;

import androidx.annotation.NonNull;

/** Class for constants */
public class Constants {

  public static final String PREFS_NAME = "FirebasePerfSharedPrefs";
  public static final String ENABLE_DISABLE = "isEnabled";

  public static final double MIN_SAMPLING_RATE = 0.0;
  public static final double MAX_SAMPLING_RATE = 1.0;

  // Max length of URL.
  public static final int MAX_URL_LENGTH = 2000;

  // Max hostname length in URL.
  public static final int MAX_HOST_LENGTH = 255;
  public static final int MAX_CONTENT_TYPE_LENGTH = 128;
  public static final int MAX_TRACE_CUSTOM_ATTRIBUTES = 5;

  // Trace limits
  public static final int MAX_TRACE_ID_LENGTH = 100;
  public static final int MAX_COUNTER_ID_LENGTH = 100;
  public static final int MAX_ATTRIBUTE_KEY_LENGTH = 40;
  public static final int MAX_ATTRIBUTE_VALUE_LENGTH = 100;

  public static final int MAX_SUBTRACE_DEEP = 1;

  /** Default rate of Token Bucket rate limiting algorithm. Number of event logs per minute. */
  public static final int RATE_PER_MINUTE = 100;
  /**
   * The bucket capacity of Token Bucket rate limiting algorithm. Number of event logs burst
   * allowed.
   */
  public static final int BURST_CAPACITY = 500;

  /** Screen trace name is the prefix plus activity class name. */
  public static final String SCREEN_TRACE_PREFIX = "_st_";

  /** Attribute key for the parent fragment of a fragment screen trace. */
  public static final String PARENT_FRAGMENT_ATTRIBUTE_KEY = "Parent_fragment";

  /** Attribute key for the hosting activity of a fragment screen trace. */
  public static final String ACTIVITY_ATTRIBUTE_KEY = "Hosting_activity";

  /** Attribute value for when the current fragment does not have a parent fragment. */
  public static final String PARENT_FRAGMENT_ATTRIBUTE_VALUE_NONE = "No parent";

  /** frames longer than 16 ms are slow frames */
  public static final int SLOW_FRAME_TIME = 16;

  /** frames longer than 700 ms are frozen frames */
  public static final int FROZEN_FRAME_TIME = 700;

  /** An enum to list internal trace names */
  public enum TraceNames {
    APP_START_TRACE_NAME("_as"),
    ON_CREATE_TRACE_NAME("_astui"), // Time To UI Initialition
    ON_START_TRACE_NAME("_astfd"), // Time To First Draw Done
    ON_RESUME_TRACE_NAME("_asti"), // Time To Interaction.
    FOREGROUND_TRACE_NAME("_fs"),
    BACKGROUND_TRACE_NAME("_bs");

    private String mName;

    TraceNames(@NonNull String name) {
      mName = name;
    }

    @Override
    public String toString() {
      return mName;
    }
  }

  /** An enum to list internal counter names */
  public enum CounterNames {
    TRACE_EVENT_RATE_LIMITED("_fstec"),
    NETWORK_TRACE_EVENT_RATE_LIMITED("_fsntc"),
    TRACE_STARTED_NOT_STOPPED("_tsns"),
    FRAMES_TOTAL("_fr_tot"),
    FRAMES_SLOW("_fr_slo"),
    FRAMES_FROZEN("_fr_fzn");

    private String mName;

    CounterNames(@NonNull String name) {
      mName = name;
    }

    @Override
    public String toString() {
      return mName;
    }
  }
}
