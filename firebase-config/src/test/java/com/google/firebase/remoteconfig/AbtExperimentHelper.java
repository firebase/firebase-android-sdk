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

package com.google.firebase.remoteconfig;

import java.sql.Date;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Set of utility methods for dealing with Firebase A/B Testing (ABT) experiments in tests.
 *
 * @author Miraziz Yusupov
 */
public class AbtExperimentHelper {
  private static final String EXPERIMENT_ID_KEY = "experimentId";
  private static final String EXPERIMENT_VARIANT_ID_KEY = "variantId";
  private static final String EXPERIMENT_START_TIME_KEY = "experimentStartTime";
  private static final String EXPERIMENT_TRIGGER_EVENT_KEY = "triggerEvent";
  private static final String EXPERIMENT_TRIGGER_TIMEOUT_KEY = "triggerTimeoutMillis";
  private static final String EXPERIMENT_TIME_TO_LIVE_KEY = "timeToLiveMillis";

  /**
   * Returns a {@link JSONArray} containing a list of {@link JSONObject}s representing ABT
   * experiments.
   */
  static JSONArray createAbtExperiments(JSONObject... abtExperiments) throws JSONException {
    return new JSONArray(abtExperiments);
  }

  /**
   * Returns a {@link JSONObject} representing an ABT Experiment with the given experiment id and
   * variant id.
   */
  static JSONObject createAbtExperiment(String experimentId) throws JSONException {
    JSONObject abtExperiment = new JSONObject();
    abtExperiment.put(EXPERIMENT_ID_KEY, experimentId);
    abtExperiment.put(EXPERIMENT_VARIANT_ID_KEY, "var1");
    abtExperiment.put(EXPERIMENT_START_TIME_KEY, new Date(1L));
    abtExperiment.put(EXPERIMENT_TRIGGER_EVENT_KEY, "trigger event");
    abtExperiment.put(EXPERIMENT_TRIGGER_TIMEOUT_KEY, 5000L);
    abtExperiment.put(EXPERIMENT_TIME_TO_LIVE_KEY, 10000L);
    return abtExperiment;
  }
}
