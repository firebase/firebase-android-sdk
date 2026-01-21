/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.buildtools;

import java.io.File;

/**
 * Information about a build configuration of an app, which defines the app's identity and where
 * the buildtools can write build files.
 */
public class AppBuildInfo {

  private final String packageName;
  private final String googleAppId;
  private final File buildDir;

  /**
   * @param packageName The package name of the app
   * @param googleAppId App ID from google-services.json
   * @param buildDir Crashlytics-specific working directory (such as build/crashlytics), where transient build
   *                 artifacts can be written.
   */
  public AppBuildInfo(String packageName, String googleAppId, File buildDir) {
    this.packageName = packageName;
    this.googleAppId = googleAppId;
    this.buildDir = buildDir;
  }

  public String getPackageName() {
    return packageName;
  }

  public String getGoogleAppId() {
    return googleAppId;
  }

  /**
   *  @return The Crashlytics-specific build subdirectory, in which Crashlytics can write transient files.
   *  Note the directory is not guaranteed to exist. Use
   *  {@link com.google.firebase.crashlytics.buildtools.utils.FileUtils#verifyDirectory(File)} to create it, if
   *  necessary.
   */
  public File getBuildDir() {
    return buildDir;
  }
}
