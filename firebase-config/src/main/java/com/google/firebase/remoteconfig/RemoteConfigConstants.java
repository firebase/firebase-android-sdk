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

import static com.google.firebase.remoteconfig.RemoteConfigConstants.ExperimentDescriptionFieldKey.EXPERIMENT_ID;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.ExperimentDescriptionFieldKey.VARIANT_ID;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.ANALYTICS_USER_PROPERTIES;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.APP_ID;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.APP_VERSION;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.COUNTRY_CODE;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.INSTANCE_ID;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.INSTANCE_ID_TOKEN;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.LANGUAGE_CODE;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.PACKAGE_NAME;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.PLATFORM_VERSION;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.SDK_VERSION;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.RequestFieldKey.TIME_ZONE;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.ResponseFieldKey.ENTRIES;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.ResponseFieldKey.EXPERIMENT_DESCRIPTIONS;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.ResponseFieldKey.STATE;

import androidx.annotation.StringDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Constants used throughout the Firebase Remote Config SDK.
 *
 * @author Lucas Png
 * @hide
 */
public class RemoteConfigConstants {
  public static final String FETCH_REGEX_URL =
      "https://firebaseremoteconfig.googleapis.com/v1/projects/%s/namespaces/%s:fetch";

  /**
   * Keys of fields in the Fetch request body that the client sends to the Firebase Remote Config
   * server.
   */
  @StringDef({
    INSTANCE_ID,
    INSTANCE_ID_TOKEN,
    APP_ID,
    COUNTRY_CODE,
    LANGUAGE_CODE,
    PLATFORM_VERSION,
    TIME_ZONE,
    APP_VERSION,
    PACKAGE_NAME,
    SDK_VERSION,
    ANALYTICS_USER_PROPERTIES
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface RequestFieldKey {
    String INSTANCE_ID = "appInstanceId";
    String INSTANCE_ID_TOKEN = "appInstanceIdToken";
    String APP_ID = "appId";
    String COUNTRY_CODE = "countryCode";
    String LANGUAGE_CODE = "languageCode";
    String PLATFORM_VERSION = "platformVersion";
    String TIME_ZONE = "timeZone";
    String APP_VERSION = "appVersion";
    String PACKAGE_NAME = "packageName";
    String SDK_VERSION = "sdkVersion";
    String ANALYTICS_USER_PROPERTIES = "analyticsUserProperties";
  }

  /** Keys of fields in the Fetch response body from the Firebase Remote Config server. */
  @StringDef({ENTRIES, EXPERIMENT_DESCRIPTIONS, STATE})
  @Retention(RetentionPolicy.SOURCE)
  public @interface ResponseFieldKey {
    String ENTRIES = "entries";
    String EXPERIMENT_DESCRIPTIONS = "experimentDescriptions";
    String STATE = "state";
  }

  /**
   * Select keys of fields in the experiment descriptions returned from the Firebase Remote Config
   * server.
   */
  @StringDef({EXPERIMENT_ID, VARIANT_ID})
  @Retention(RetentionPolicy.SOURCE)
  public @interface ExperimentDescriptionFieldKey {
    String EXPERIMENT_ID = "experimentId";
    String VARIANT_ID = "variantId";
  }

  private RemoteConfigConstants() {}
}
