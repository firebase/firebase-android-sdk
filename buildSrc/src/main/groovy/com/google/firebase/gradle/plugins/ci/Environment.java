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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Environment {

  public static String expand(String value) {
    Matcher m = ENV_PATTERN.matcher(value);
    while (m.find()) {
      value = value.replace(m.group(), env(m.group(1)));
    }

    return value;
  }

  private static String env(String varName) {
    return Optional.ofNullable(System.getenv(varName)).orElse("");
  }

  private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\(([A-Za-z0-9_-]+)\\)");
}
