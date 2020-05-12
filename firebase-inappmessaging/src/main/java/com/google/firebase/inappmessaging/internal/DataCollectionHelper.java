// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.inappmessaging.internal;

import com.google.common.annotations.VisibleForTesting;
import com.google.firebase.DataCollectionDefaultChange;
import com.google.firebase.FirebaseApp;
import com.google.firebase.events.Subscriber;
import com.google.firebase.installations.FirebaseInstallationsApi;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;

/**
 * Determines whether auto-initialization of app instance ids and data collection are enabled.
 * Exposes methods to enable/disable the data collection, and stores this across app restarts
 *
 * @hide
 */
public class DataCollectionHelper {

  @VisibleForTesting
  static final String MANIFEST_METADATA_AUTO_INIT_ENABLED =
      "firebase_inapp_messaging_auto_data_collection_enabled";

  @VisibleForTesting static final String AUTO_INIT_PREFERENCES = "auto_init";

  private SharedPreferencesUtils sharedPreferencesUtils;
  private AtomicBoolean isGlobalAutomaticDataCollectionEnabled;

  @Inject
  public DataCollectionHelper(
      FirebaseApp firebaseApp,
      SharedPreferencesUtils sharedPreferencesUtils,
      Subscriber firebaseEventsSubscriber) {
    this.sharedPreferencesUtils = sharedPreferencesUtils;
    isGlobalAutomaticDataCollectionEnabled =
        new AtomicBoolean(firebaseApp.isDataCollectionDefaultEnabled());
    firebaseEventsSubscriber.subscribe(
        DataCollectionDefaultChange.class,
        event -> {
          // We don't need to store this value - on re-initialization, we always get the 'current'
          // state
          // off the firebaseApp
          DataCollectionDefaultChange change = event.getPayload();
          isGlobalAutomaticDataCollectionEnabled.set(change.enabled);
        });
  }

  /**
   * Determine whether automatic data collection is enabled or not
   *
   * @return true if auto initialization is required
   */
  public boolean isAutomaticDataCollectionEnabled() {

    // We follow this order of precedence:
    // P0 - the manual override in shared prefs
    // P1 - the product-level manifest override
    // P2 - the global-level value

    if (isProductManuallySet()) {
      return sharedPreferencesUtils.getBooleanPreference(AUTO_INIT_PREFERENCES, true);
    }
    if (isProductManifestSet()) {
      return sharedPreferencesUtils.getBooleanManifestValue(
          MANIFEST_METADATA_AUTO_INIT_ENABLED, true);
    }
    return isGlobalAutomaticDataCollectionEnabled.get();
  }

  /**
   * Enable or disable automatic data collection for Firebase In App Messaging.
   *
   * <p>
   *
   * <p>When enabled, generates a registration token on app startup if there is no valid one and
   * generates a new token when it is deleted (which prevents {@link
   * FirebaseInstallationsApi#delete()} from stopping the periodic sending of data). This setting is
   * persisted across app restarts and overrides the setting specified in your manifest.
   *
   * <p>
   *
   * <p>By default, auto-initialization is enabled. If you need to change the default, (for example,
   * because you want to prompt the user before generates/refreshes a registration token on app
   * startup), add to your application’s manifest:
   *
   * <p>
   *
   * <pre>{@code
   * <meta-data
   * android:name="firebase_inapp_messaging_auto_data_collection_enabled" android:value="false"
   * />
   * }</pre>
   *
   * <p>Note, this will require you to manually initialize Firebase In App Messaging, via:
   * {@code}FirebaseInAppMessaging.getInstance().setEnabled(true){/code}
   *
   * @param isEnabled Whether isEnabled
   */
  public void setAutomaticDataCollectionEnabled(boolean isEnabled) {
    // Update SharedPreferences, so that we preserve state across app restarts
    sharedPreferencesUtils.setBooleanPreference(AUTO_INIT_PREFERENCES, isEnabled);
  }

  /**
   * Enable, disable or clear automatic data collection for Firebase In-App Messaging.
   *
   * <p>When enabled, generates a registration token on app startup if there is no valid one and
   * generates a new token when it is deleted (which prevents {@link
   * FirebaseInstallationsApi#delete()} from stopping the periodic sending of data). This setting is
   * persisted across app restarts and overrides the setting specified in your manifest.
   *
   * <p>When null, the enablement of the auto-initialization depends on the manifest and then on the
   * global enablement setting in this order. If none of these settings are present then it is
   * enabled by default.
   *
   * <p>If you need to change the default, (for example, because you want to prompt the user before
   * generates/refreshes a registration token on app startup), add the following to your
   * application’s manifest:
   *
   * <pre>{@code
   * <meta-data android:name="firebase_inapp_messaging_auto_init_enabled" android:value="false" />
   * }</pre>
   *
   * <p>Note, this will require you to manually initialize Firebase In-App Messaging, via:
   *
   * <pre>{@code FirebaseInAppMessaging.getInstance().setAutomaticDataCollectionEnabled(true)}</pre>
   *
   * <p>Manual initialization will also be required in order to clear these settings and fall back
   * on other settings, via:
   *
   * <pre>{@code FirebaseInAppMessaging.getInstance().setAutomaticDataCollectionEnabled(null)}</pre>
   *
   * @param isEnabled Whether isEnabled
   */
  public void setAutomaticDataCollectionEnabled(Boolean isEnabled) {
    // Update SharedPreferences, so that we preserve state across app restarts
    if (isEnabled == null) {
      sharedPreferencesUtils.clearPreference(AUTO_INIT_PREFERENCES);
    } else {
      sharedPreferencesUtils.setBooleanPreference(
          AUTO_INIT_PREFERENCES, Boolean.TRUE.equals(isEnabled));
    }
  }

  private boolean readAutomaticDataCollectionEnabledFromPreferences() {
    return sharedPreferencesUtils.getBooleanPreference(AUTO_INIT_PREFERENCES, true);
  }

  private boolean isProductManuallySet() {
    return sharedPreferencesUtils.isPreferenceSet(AUTO_INIT_PREFERENCES);
  }

  private boolean isProductManifestSet() {
    return sharedPreferencesUtils.isManifestSet(MANIFEST_METADATA_AUTO_INIT_ENABLED);
  }
}
