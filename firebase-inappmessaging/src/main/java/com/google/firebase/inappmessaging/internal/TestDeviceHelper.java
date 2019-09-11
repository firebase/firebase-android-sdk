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
import com.google.internal.firebase.inappmessaging.v1.CampaignProto;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsResponse;
import java.util.List;
import javax.inject.Inject;

/**
 * Determines whether the app is a fresh install or the device is in test mode. Exposes methods to
 * check for install freshness and test device status as well as a method to update these fields by
 * processing a campaign fetch response.
 *
 * @hide
 */
public class TestDeviceHelper {

  @VisibleForTesting static final String TEST_DEVICE_PREFERENCES = "test_device";
  @VisibleForTesting static final String FRESH_INSTALL_PREFERENCES = "fresh_install";
  @VisibleForTesting static final int MAX_FETCH_COUNT = 5;

  private final SharedPreferencesUtils sharedPreferencesUtils;
  private boolean isTestDevice;
  private boolean isFreshInstall;
  private int fetchCount = 0;

  @Inject
  public TestDeviceHelper(SharedPreferencesUtils sharedPreferencesUtils) {
    this.sharedPreferencesUtils = sharedPreferencesUtils;
    this.isFreshInstall = readFreshInstallStatusFromPreferences();
    this.isTestDevice = readTestDeviceStatusFromPreferences();
  }

  /**
   * Determine whether device is set as a test device.
   *
   * @return true if device is in test mode
   */
  public boolean isDeviceInTestMode() {
    return isTestDevice;
  }

  /**
   * Determine whether app was just installed.
   *
   * @return true if this is a fresh install
   */
  public boolean isAppInstallFresh() {
    return isFreshInstall;
  }

  /**
   * Updates test device status based on a response from the FIAM server.
   *
   * @param response campaign fetch response from the FIAM server.
   */
  public void processCampaignFetch(FetchEligibleCampaignsResponse response) {
    // We only care about this logic if we are not already a test device.
    if (!isTestDevice) {
      updateFreshInstallStatus();
      List<CampaignProto.ThickContent> messages = response.getMessagesList();
      for (CampaignProto.ThickContent message : messages) {
        if (message.getIsTestCampaign()) {
          setTestDeviceStatus(true);
          Logging.logi("Setting this device as a test device");
          return;
        }
      }
    }
  }

  /** Increments the fetch count which is used to determine if an app install is fresh. */
  private void updateFreshInstallStatus() {
    // We only care about this logic if we are a fresh install.
    if (isFreshInstall) {
      fetchCount += 1;
      if (fetchCount >= MAX_FETCH_COUNT) {
        setFreshInstallStatus(false);
      }
    }
  }

  /**
   * Sets the test device status and saves it into the app stored preferences.
   *
   * @param isEnabled whether or not the device should be a test device
   */
  private void setTestDeviceStatus(boolean isEnabled) {
    isTestDevice = isEnabled;
    // Update SharedPreferences, so that we preserve state across app restarts
    sharedPreferencesUtils.setBooleanPreference(TEST_DEVICE_PREFERENCES, isEnabled);
  }

  /**
   * Sets the app fresh install state and saves it into the app stored preferences
   *
   * @param isEnabled whether or not the app is a fresh install.
   */
  private void setFreshInstallStatus(boolean isEnabled) {
    isFreshInstall = isEnabled;
    // Update SharedPreferences, so that we preserve state across app restarts
    sharedPreferencesUtils.setBooleanPreference(FRESH_INSTALL_PREFERENCES, isEnabled);
  }

  /**
   * Reads the test device status from the apps stored preferences. Defaults to false because apps
   * do not start in test mode.
   *
   * @return true if device is in test mode.
   */
  private boolean readTestDeviceStatusFromPreferences() {
    return sharedPreferencesUtils.getAndSetBooleanPreference(TEST_DEVICE_PREFERENCES, false);
  }

  /**
   * Reads the fresh install status from the apps stored preferences. Defaults to true because apps
   * start out as fresh installs.
   *
   * @return true if the app is a fresh install.
   */
  private boolean readFreshInstallStatusFromPreferences() {
    return sharedPreferencesUtils.getAndSetBooleanPreference(FRESH_INSTALL_PREFERENCES, true);
  }
}
