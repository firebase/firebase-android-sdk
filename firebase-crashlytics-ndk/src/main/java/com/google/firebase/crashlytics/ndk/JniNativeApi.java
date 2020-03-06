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
import com.google.firebase.crashlytics.internal.Logger;

/** JNI implementation of the native API interface. */
@SuppressWarnings("PMD.AvoidUsingNativeCode")
class JniNativeApi implements NativeApi {

  private static final boolean LIB_CRASHLYTICS_LOADED;

  static {
    boolean loadSuccessful = false;
    try {
      // This is the recommended approach for loading the library
      // (http://developer.android.com/training/articles/perf-jni.html)
      System.loadLibrary("crashlytics");
      loadSuccessful = true;
    } catch (UnsatisfiedLinkError e) {
      // This can happen if the APK doesn't contain the correct binary for this architecture,
      // most likely because the user sideloaded the APK that was intended for a different
      // architecture. We can't reasonably recover, and Crashlytics may not be
      // initialized yet. So all we can do is write the error to logs
      Logger.getLogger()
          .e(
              "libcrashlytics could not be loaded. "
                  + "This APK may not have been compiled for this device's architecture. "
                  + "NDK crashes will not be reported to Crashlytics:\n"
                  + e.getLocalizedMessage());
    }
    LIB_CRASHLYTICS_LOADED = loadSuccessful;
  }

  @Override
  public boolean initialize(String dataPath, AssetManager assetManager) {
    return LIB_CRASHLYTICS_LOADED && nativeInit(dataPath, assetManager);
  }

  private native boolean nativeInit(String dataPath, Object assetManager);
}
