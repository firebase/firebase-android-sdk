// Copyright 2022 Google LLC
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

package com.google.firebase.crashlytics.internal.common;

/** Information about a particular build id which represents a single .so within the project. */
public class BuildIdInfo {
  private final String libraryName;
  private final String arch;
  private final String buildId;

  /**
   * @param libraryName The file name of the .so file
   * @param arch the architecture for which the .so was built
   * @param buildId the build id of the .so file as found in the ELF header section
   */
  public BuildIdInfo(String libraryName, String arch, String buildId) {
    this.libraryName = libraryName;
    this.arch = arch;
    this.buildId = buildId;
  }

  public String getLibraryName() {
    return libraryName;
  }

  public String getArch() {
    return arch;
  }

  public String getBuildId() {
    return buildId;
  }
}
