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

import androidx.annotation.StringDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Constants used throughout the Firebase Remote Config SDK.
 *
 * @author Lucas Png
 * @hide
 */
public final class RemoteConfigConstants {
  public static final String FETCH_REGEX_URL =
      "https://firebaseremoteconfig.googleapis.com/v1/projects/%s/namespaces/%s:fetch";

  /**
   * Keys of fields in the Fetch request body that the client sends to the Firebase Remote Config
   * server.
   *
   * <p>{@code INSTANCE_ID} and {@code INSTANCE_ID_TOKEN} are legacy names for the fields that used
   * to be populated by the IID SDK. The fields have been replaced by the installation ID and
   * installation auth token, respectively, which are fetched from the FIS SDK.
   */
  @StringDef({
    RequestFieldKey.INSTANCE_ID,
    RequestFieldKey.INSTANCE_ID_TOKEN,
    RequestFieldKey.APP_ID,
    RequestFieldKey.COUNTRY_CODE,
    RequestFieldKey.LANGUAGE_CODE,
    RequestFieldKey.PLATFORM_VERSION,
    RequestFieldKey.TIME_ZONE,
    RequestFieldKey.APP_VERSION,
    RequestFieldKey.PACKAGE_NAME,
    RequestFieldKey.SDK_VERSION,
    RequestFieldKey.ANALYTICS_USER_PROPERTIES
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
  @StringDef({
    ResponseFieldKey.ENTRIES,
    ResponseFieldKey.EXPERIMENT_DESCRIPTIONS,
    ResponseFieldKey.STATE
  })
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
  @StringDef({
    ExperimentDescriptionFieldKey.EXPERIMENT_ID,
    ExperimentDescriptionFieldKey.VARIANT_ID
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface ExperimentDescriptionFieldKey {
    String EXPERIMENT_ID = "experimentId";
    String VARIANT_ID = "variantId";
  }

  private RemoteConfigConstants() {}
}
