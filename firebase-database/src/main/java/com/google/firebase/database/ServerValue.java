// Copyright 2018 Google LLC
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

package com.google.firebase.database;

// Server values

import androidx.annotation.NonNull;
import com.google.firebase.annotations.PublicApi;
import com.google.firebase.database.core.ServerValues;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Contains placeholder values to use when writing data to the Firebase Database. */
@PublicApi
public class ServerValue {

  /**
   * A placeholder value for auto-populating the current timestamp (time since the Unix epoch, in
   * milliseconds) by the Firebase Database servers.
   */
  @NonNull @PublicApi
  public static final Map<String, String> TIMESTAMP = createServerValuePlaceholder("timestamp");

  private static Map<String, String> createServerValuePlaceholder(String key) {
    Map<String, String> result = new HashMap<String, String>();
    result.put(ServerValues.NAME_SUBKEY_SERVERVALUE, key);
    return Collections.unmodifiableMap(result);
  }
}
