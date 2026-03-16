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

package com.google.firebase.appdistribution.gradle;

import groovy.transform.builder.Builder;

/**
 * Options object for configuring build.gradle.
 */
@Builder
class BuildFileUploadOptions {
  private final String artifactType;
  private final String releaseNotes;
  private final String testers;
  private final String groups;

  public String getArtifactType() {
    return artifactType;
  }

  public String getReleaseNotes() {
    return releaseNotes;
  }

  public String getTesters() {
    return testers;
  }

  public String getGroups() {
    return groups;
  }

  private BuildFileUploadOptions(
      String artifactType, String releaseNotes, String testers, String groups) {
    this.artifactType = artifactType;
    this.releaseNotes = releaseNotes;
    this.testers = testers;
    this.groups = groups;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String artifactType;
    private String releaseNotes;
    private String testers;
    private String groups;

    public Builder artifactType(String artifactType) {
      this.artifactType = artifactType;
      return this;
    }

    public Builder releaseNotes(String releaseNotes) {
      this.releaseNotes = releaseNotes;
      return this;
    }

    public Builder testers(String testers) {
      this.testers = testers;
      return this;
    }

    public Builder groups(String groups) {
      this.groups = groups;
      return this;
    }

    public BuildFileUploadOptions build() {
      return new BuildFileUploadOptions(artifactType, releaseNotes, testers, groups);
    }
  }
}
