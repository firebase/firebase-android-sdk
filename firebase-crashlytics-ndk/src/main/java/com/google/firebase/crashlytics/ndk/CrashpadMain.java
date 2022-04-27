// Copyright 2020 Google LLC
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

import android.util.Log;

public class CrashpadMain {
  // The first argument will be the path of libcrashlytics-handler.so.
  // This path differs based on the version of Android.
  public static void main(String[] args) {
    try {
      String path = args[1];

      Log.d("FirebaseCrashlytics", "Path to shared objects is " + path);

      // System.load allows specifying the path.
      System.load(path + "libcrashlytics-handler.so");
    } catch (UnsatisfiedLinkError e) {
      throw new RuntimeException(e);
    }

    crashpadMain(args);
  }

  public static native void crashpadMain(String[] args);
}
