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

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Dependency {

  public abstract String groupId();

  public abstract String artifactId();

  public abstract String version();

  public abstract VersionBump versionBump();

  public static Dependency create(
      String groupId, String artifactId, String version, VersionBump versionBump) {
    return new AutoValue_Dependency(groupId, artifactId, version, versionBump);
  }

  // Null safe default constructor. Represents dependencies that have not yet been looked up in
  // repos.
  public static Dependency create(String groupId, String artifactId) {
    return new AutoValue_Dependency(groupId, artifactId, "0.0.0", VersionBump.NONE);
  }

  public String fullArtifactId() {
    return groupId() + ":" + artifactId();
  }

  public String toGradleString() {
    return groupId() + ":" + artifactId() + (version() == null ? "" : (":" + version()));
  }
}
