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

package com.google.firebase.gradle.plugins;

public enum LibraryType {
  ANDROID("aar"),
  JAVA("jar");

  private final String format;

  LibraryType(String format) {
    this.format = format;
  }

  public String getFormat() {
    return format;
  }

  public String getComponentName() {
    // Due to the fact that multiple components are created for android libraries(1 per variant),
    // the "android" component contains artifacts from all 3 variants for Kotlin libraries, which is
    // invalid(bug in
    // https://github.com/wupdigital/android-maven-publish ?).
    // So we explicitly choose the "Release" variant for android libraries.
    if (this == ANDROID) {
      return "release";
    }
    return name().toLowerCase();
  }
}
