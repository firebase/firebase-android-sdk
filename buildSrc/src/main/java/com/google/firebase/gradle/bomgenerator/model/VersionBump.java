// Copyright 2021 Google LLC
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

package com.google.firebase.gradle.bomgenerator.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum VersionBump {
  MAJOR,
  MINOR,
  PATCH,
  NONE;

  private static final Pattern SEMVER_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+).*");

  // Assumes list of versions passed in is sorted, newest versions last.
  public static VersionBump getBumpBetweenVersion(
      String newestVersion, String secondNewestVersion) {
    Matcher newestVersionMatcher = SEMVER_PATTERN.matcher(newestVersion);
    Matcher secondNewestVersionMatcher = SEMVER_PATTERN.matcher(secondNewestVersion);
    if (!(newestVersionMatcher.matches() && secondNewestVersionMatcher.matches())) {
      throw new RuntimeException(
          "Could not figure out version bump between "
              + secondNewestVersion
              + " and "
              + newestVersion
              + ".");
    }
    if (Integer.parseInt(newestVersionMatcher.group(1))
        > Integer.parseInt(secondNewestVersionMatcher.group(1))) {
      return MAJOR;
    }
    if (Integer.parseInt(newestVersionMatcher.group(2))
        > Integer.parseInt(secondNewestVersionMatcher.group(2))) {
      return MINOR;
    }
    if (Integer.parseInt(newestVersionMatcher.group(3))
        > Integer.parseInt(secondNewestVersionMatcher.group(3))) {
      return PATCH;
    }
    return NONE;
  }

  public static String bumpVersionBy(String version, VersionBump bump) {
    Matcher versionMatcher = SEMVER_PATTERN.matcher(version);
    if (!versionMatcher.matches()) {
      throw new RuntimeException("Could not bump " + version + " as it is not a valid version.");
    }
    switch (bump) {
      case NONE:
        return version;
      case MAJOR:
        return Integer.toString(Integer.parseInt(versionMatcher.group(1)) + 1).toString() + ".0.0";
      case MINOR:
        return versionMatcher.group(1)
            + "."
            + Integer.toString(Integer.parseInt(versionMatcher.group(2)) + 1)
            + ".0";
      case PATCH:
        return versionMatcher.group(1)
            + "."
            + versionMatcher.group(2)
            + "."
            + Integer.toString(Integer.parseInt(versionMatcher.group(3)) + 1);
      default:
        throw new RuntimeException("Should be impossible");
    }
  }
}
