// Copyright 2019 Google LLC
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

package com.google.apksize;

import android.content.Context;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

public class Config implements SampleCode {
  private static final String MESSAGE_TAG = "message";
  private static final String FLAG_TAG = "flag";
  private static final String NUMBER_TAG = "number";

  @Override
  public void runSample(Context context) {
    FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
    FirebaseRemoteConfigSettings settings =
        new FirebaseRemoteConfigSettings.Builder()
            .setDeveloperModeEnabled(BuildConfig.DEBUG)
            .setMinimumFetchIntervalInSeconds(3600)
            .build();
    remoteConfig.setConfigSettings(settings);
    remoteConfig.setDefaults(R.xml.remote_config_defaults);

    remoteConfig.getString(MESSAGE_TAG);
    remoteConfig.getBoolean(FLAG_TAG);
    remoteConfig.getDouble(NUMBER_TAG);
  }
}
