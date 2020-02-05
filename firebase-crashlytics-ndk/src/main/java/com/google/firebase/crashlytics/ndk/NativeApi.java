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

package com.google.firebase.crashlytics.ndk;

import android.content.res.AssetManager;

/** Interface to methods that should be executed in native crash handling library. */
interface NativeApi {

  /**
   * Initialize crash handling for this session.
   *
   * @param dataPath the path where crash data should be written.
   * @return <code>true</code> if initialization was successful, <code>false</code> otherwise.
   */
  boolean initialize(String dataPath, AssetManager assetManager);
}
