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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Handles attributes set by the user. */
public class UserMetadata {
  static final int MAX_ATTRIBUTES = 64;
  static final int MAX_ATTRIBUTE_SIZE = 1024;

  private String userId = null;
  private final Map<String, String> attributes = new HashMap<>();

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
    return Collections.unmodifiableMap(attributes);
  }

  public void setCustomKey(String key, String value) {
    setSyncCustomKeys(
        new HashMap<String, String>() {
          {
            put(sanitizeKey(key), sanitizeAttribute(value));
          }
        });
  }

  public void setCustomKeys(Map<String, String> keysAndValues) {
    setSyncCustomKeys(keysAndValues);
  }

  /** Gatekeeper function for access to attributes */
  private synchronized void setSyncCustomKeys(Map<String, String> keysAndValues) {
    // We want all access to the attributes hashmap to be locked so that there is no way to create
    // a race condition and add more than MAX_ATTRIBUTES keys.

    // Update any existing keys first, then add any additional keys
    Map<String, String> currentKeys = new HashMap<String, String>();
    Map<String, String> newKeys = new HashMap<String, String>();

    // Split into current and new keys
    for (Map.Entry<String, String> entry : keysAndValues.entrySet()) {
      String key = sanitizeKey(entry.getKey());
      String value = (entry.getValue() == null) ? "" : sanitizeAttribute(entry.getValue());
      if (attributes.containsKey(key)) {
        currentKeys.put(key, value);
      } else {
        newKeys.put(key, value);
      }
    }

    attributes.putAll(currentKeys);

    // Add new keys if there is space
    if (attributes.size() + newKeys.size() > MAX_ATTRIBUTES) {
      int keySlotsLeft = MAX_ATTRIBUTES - attributes.size();
      Logger.getLogger()
          .v("Exceeded maximum number of custom attributes (" + MAX_ATTRIBUTES + ").");
      List<String> newKeyList = new ArrayList<>(newKeys.keySet());
      newKeys.keySet().retainAll(newKeyList.subList(0, keySlotsLeft));
    }
    attributes.putAll(newKeys);
  }

  /** Checks that the key is not null then sanitizes it. */
  private static String sanitizeKey(String key) {
    if (key == null) {
      throw new IllegalArgumentException("Custom attribute key must not be null.");
    }
    return sanitizeAttribute(key);
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
