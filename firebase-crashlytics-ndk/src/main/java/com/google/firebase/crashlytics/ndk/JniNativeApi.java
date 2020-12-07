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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.os.Build;
import android.text.TextUtils;
import com.google.firebase.crashlytics.internal.Logger;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** JNI implementation of the native API interface. */
@SuppressWarnings("PMD.AvoidUsingNativeCode")
class JniNativeApi implements NativeApi {

  private static final boolean LIB_CRASHLYTICS_LOADED;
  private Context context;

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

  public JniNativeApi(Context context) {
    this.context = context;
  }

  public static boolean isAtLeastLollipop() {
    return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public static void addSplitSourceDirs(List<String> zipPaths, ApplicationInfo applicationInfo) {
    if (applicationInfo.splitSourceDirs != null) {
      Collections.addAll(zipPaths, applicationInfo.splitSourceDirs);
    }
  }

  public String[] makePackagePaths(String arch) {
    try {
      PackageManager pm = context.getPackageManager();
      PackageInfo pi =
          pm.getPackageInfo(
              context.getPackageName(),
              PackageManager.GET_SHARED_LIBRARY_FILES | PackageManager.MATCH_UNINSTALLED_PACKAGES);

      List<String> zipPaths = new ArrayList<>(10);
      zipPaths.add(pi.applicationInfo.sourceDir);

      if (isAtLeastLollipop()) {
        addSplitSourceDirs(zipPaths, pi.applicationInfo);
      }

      if (pi.applicationInfo.sharedLibraryFiles != null) {
        Collections.addAll(zipPaths, pi.applicationInfo.sharedLibraryFiles);
      }

      List<String> libPaths = new ArrayList<>(10);
      File parent = new File(pi.applicationInfo.nativeLibraryDir).getParentFile();
      if (parent != null) {
        libPaths.add(new File(parent, arch).getPath());

        // arch is the currently loaded library's ABI name. This is the name of the library
        // directory in an APK, but may differ from the library directory extracted to the
        // filesystem. ARM family abi names have a suffix specifying the architecture
        // version, but may be extracted to directories named "arm64" or "arm".
        // crbug.com/930342
        if (arch.startsWith("arm64")) {
          libPaths.add(new File(parent, "arm64").getPath());
        } else if (arch.startsWith("arm")) {
          libPaths.add(new File(parent, "arm").getPath());
        }
      }
      for (String zip : zipPaths) {
        if (zip.endsWith(".apk")) {
          libPaths.add(zip + "!/lib/" + arch);
        }
      }
      libPaths.add(System.getProperty("java.library.path"));
      libPaths.add(pi.applicationInfo.nativeLibraryDir);

      return new String[] {
        TextUtils.join(File.pathSeparator, zipPaths), TextUtils.join(File.pathSeparator, libPaths)
      };
    } catch (NameNotFoundException e) {
      Logger.getLogger().e("Unable to compose package paths", e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean initialize(String dataPath, AssetManager assetManager) {
    String[] paths = makePackagePaths(Build.CPU_ABI);

    if (paths.length < 2) {
      return false;
    }

    String classpath = paths[0];
    String libspath = paths[1];

    return LIB_CRASHLYTICS_LOADED
        && nativeInit(new String[] {classpath, libspath, dataPath}, assetManager);
  }

  private native boolean nativeInit(String[] paths, Object assetManager);
}
