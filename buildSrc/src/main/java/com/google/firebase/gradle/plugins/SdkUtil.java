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

package com.google.firebase.gradle.plugins;

import com.android.build.gradle.LibraryExtension;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

public final class SdkUtil {
  public static File getSdkDir(Project project) {
    Properties properties = new Properties();
    File localProperties = project.getRootProject().file("local.properties");
    if (localProperties.exists()) {
      try (FileInputStream fis = new FileInputStream(localProperties)) {
        properties.load(fis);
      } catch (IOException ex) {
        throw new GradleException("Could not load local.properties", ex);
      }
    }

    String sdkDir = properties.getProperty("sdk.dir");
    if (sdkDir != null) {
      return project.file(sdkDir);
    }
    String androidHome = System.getenv("ANDROID_HOME");
    if (androidHome == null) {
      throw new GradleException("No sdk.dir or ANDROID_HOME set.");
    }
    return project.file(androidHome);
  }

  public static File getAndroidJar(Project project) {
    LibraryExtension android = project.getExtensions().findByType(LibraryExtension.class);
    if (android == null) {
      return null;
    }
    return new File(
        getSdkDir(project),
        String.format("/platforms/%s/android.jar", android.getCompileSdkVersion()));
  }
}
