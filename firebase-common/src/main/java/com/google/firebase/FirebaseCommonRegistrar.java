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

package com.google.firebase;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.heartbeatinfo.DefaultHeartBeatInfo;
import com.google.firebase.platforminfo.DefaultUserAgentPublisher;
import com.google.firebase.platforminfo.KotlinDetector;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.ArrayList;
import java.util.List;

/** @hide */
public class FirebaseCommonRegistrar implements ComponentRegistrar {
  private static final String FIREBASE_ANDROID = "fire-android";
  private static final String FIREBASE_COMMON = "fire-core";
  private static final String DEVICE_NAME = "device-name";
  private static final String DEVICE_MODEL = "device-model";
  private static final String DEVICE_BRAND = "device-brand";
  private static final String TARGET_SDK = "android-target-sdk";
  private static final String MIN_SDK = "android-min-sdk";
  private static final String ANDROID_PLATFORM = "android-platform";
  private static final String ANDROID_INSTALLER = "android-installer";
  private static final String KOTLIN = "kotlin";

  @Override
  public List<Component<?>> getComponents() {
    List<Component<?>> result = new ArrayList<>();
    result.add(DefaultUserAgentPublisher.component());
    result.add(DefaultHeartBeatInfo.component());
    result.add(
        LibraryVersionComponent.create(FIREBASE_ANDROID, String.valueOf(Build.VERSION.SDK_INT)));
    result.add(LibraryVersionComponent.create(FIREBASE_COMMON, BuildConfig.VERSION_NAME));
    result.add(LibraryVersionComponent.create(DEVICE_NAME, safeValue(Build.PRODUCT)));
    result.add(LibraryVersionComponent.create(DEVICE_MODEL, safeValue(Build.DEVICE)));
    result.add(LibraryVersionComponent.create(DEVICE_BRAND, safeValue(Build.BRAND)));
    result.add(
        LibraryVersionComponent.fromContext(
            TARGET_SDK,
            ctx -> {
              ApplicationInfo info = ctx.getApplicationInfo();
              if (info != null) {
                return String.valueOf(info.targetSdkVersion);
              }
              return "";
            }));
    result.add(
        LibraryVersionComponent.fromContext(
            MIN_SDK,
            ctx -> {
              ApplicationInfo info = ctx.getApplicationInfo();
              if (info != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return String.valueOf(info.minSdkVersion);
              }
              return "";
            }));
    result.add(
        LibraryVersionComponent.fromContext(
            ANDROID_PLATFORM,
            ctx -> {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
                  && ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION)) {
                return "tv";
              }
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH
                  && ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
                return "watch";
              }
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                  && ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
                return "auto";
              }
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                  && ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_EMBEDDED)) {
                return "embedded";
              }
              return "";
            }));
    result.add(
        LibraryVersionComponent.fromContext(
            ANDROID_INSTALLER,
            ctx -> {
              String installer =
                  ctx.getPackageManager().getInstallerPackageName(ctx.getPackageName());
              return (installer != null) ? safeValue(installer) : "";
            }));

    String kotlinVersion = KotlinDetector.detectVersion();
    if (kotlinVersion != null) {
      result.add(LibraryVersionComponent.create(KOTLIN, kotlinVersion));
    }
    return result;
  }

  private static String safeValue(String value) {
    return value.replace(' ', '_').replace('/', '_');
  }
}
