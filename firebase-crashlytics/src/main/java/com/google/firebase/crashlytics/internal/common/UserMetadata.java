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

package com.google.firebase.crashlytics.internal.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.crashlytics.internal.Logger;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Handles attributes set by the user. */
public class UserMetadata {
  static final int MAX_ATTRIBUTES = 64;
  static final int MAX_ATTRIBUTE_SIZE = 1024;

  private String userId = null;
  private final ConcurrentHashMap<String, String> attributes = new ConcurrentHashMap<>();

  public UserMetadata() {}

  @Nullable
  public String getUserId() {
    return userId;
  }

  public void setUserId(String identifier) {
    userId = sanitizeAttribute(identifier);
  }

  @NonNull
  public Map<String, String> getCustomKeys() {
    // Use of ConcurrentHashMap as the underlying attributes map guarantees safety should
    // attributes be added after this unmodifiable view is returned.
    return Collections.unmodifiableMap(attributes);
  }

  public void setCustomKey(String key, String value) {
    if (key == null) {
      throw new IllegalArgumentException("Custom attribute key must not be null.");
    }

    key = sanitizeAttribute(key);

    if (attributes.size() >= MAX_ATTRIBUTES && !attributes.containsKey(key)) {
      Logger.getLogger().d("Exceeded maximum number of custom attributes (" + MAX_ATTRIBUTES + ")");
      return;
    }

    value = (value == null) ? "" : sanitizeAttribute(value);
    attributes.put(key, value);
  }

  /** Trims the string and truncates it to MAX_ATTRIBUTE_SIZE. */
  private static String sanitizeAttribute(String input) {
    if (input != null) {
      input = input.trim();
      if (input.length() > MAX_ATTRIBUTE_SIZE) {
        input = input.substring(0, MAX_ATTRIBUTE_SIZE);
      }
    }
    return input;
  }
}
