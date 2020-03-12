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

package com.google.firebase.segmentation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Util methods used for {@link FirebaseSegmentation}
 *
 * @hide
 */
class Utils {

  private static final Pattern APP_ID_PATTERN =
      Pattern.compile("^[^:]+:([0-9]+):(android|ios|web):([0-9a-f]+)");

  static long getProjectNumberFromAppId(String appId) {
    Matcher matcher = APP_ID_PATTERN.matcher(appId);
    if (matcher.matches()) {
      return Long.valueOf(matcher.group(1));
    }
    throw new IllegalArgumentException("Invalid app id " + appId);
  }
}
