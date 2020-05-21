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

package com.google.firebase.gradle.plugins.ci;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Contains plugin configuration properties. */
public class ContinuousIntegrationExtension {

  /** List of paths that the plugin should ignore when querying the Git commit. */
  private List<Pattern> ignorePaths = new ArrayList<>();

  public List<Pattern> getIgnorePaths() {
    return ignorePaths;
  }

  public void setIgnorePaths(List<Pattern> ignorePaths) {
    this.ignorePaths = ignorePaths;
  }
}
