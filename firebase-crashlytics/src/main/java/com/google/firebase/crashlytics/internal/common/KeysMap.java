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

package com.google.firebase.crashlytics.internal.common;

import androidx.annotation.NonNull;
import com.google.firebase.crashlytics.internal.Logger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Handles any key/values for metadata. */
public class KeysMap {
  private final Map<String, String> keys = new HashMap<>();
  private int maxEntries;
  private int maxEntryLength;

  public KeysMap(int maxEntries, int maxEntryLength) {
    this.maxEntries = maxEntries;
    this.maxEntryLength = maxEntryLength;
  }

  @NonNull
  public Map<String, String> getKeys() {
    return Collections.unmodifiableMap(keys);
  }

  public void setKey(String key, String value) {
    setSyncKeys(
        new HashMap<String, String>() {
          {
            put(sanitizeKey(key), sanitizeAttribute(value));
          }
        });
  }

  public void setKeys(Map<String, String> keysAndValues) {
    setSyncKeys(keysAndValues);
  }

  /** Gatekeeper function for access to attributes or internalKeys */
  private synchronized void setSyncKeys(Map<String, String> keysAndValues) {
    // We want all access to the keys hashmap to be locked so that there is no way to create
    // a race condition and add more than maxEntries keys.

    // Update any existing keys first, then add any additional keys
    Map<String, String> currentKeys = new HashMap<String, String>();
    Map<String, String> newKeys = new HashMap<String, String>();

    // Split into current and new keys
    for (Map.Entry<String, String> entry : keysAndValues.entrySet()) {
      String key = sanitizeKey(entry.getKey());
      String value = (entry.getValue() == null) ? "" : sanitizeAttribute(entry.getValue());
      if (keys.containsKey(key)) {
        currentKeys.put(key, value);
      } else {
        newKeys.put(key, value);
      }
    }

    keys.putAll(currentKeys);

    // Add new keys if there is space
    if (keys.size() + newKeys.size() > maxEntries) {
      int keySlotsLeft = maxEntries - keys.size();
      Logger.getLogger().v("Exceeded maximum number of custom attributes (" + maxEntries + ").");
      List<String> newKeyList = new ArrayList<>(newKeys.keySet());
      newKeys.keySet().retainAll(newKeyList.subList(0, keySlotsLeft));
    }
    keys.putAll(newKeys);
  }

  /** Checks that the key is not null then sanitizes it. */
  private String sanitizeKey(String key) {
    if (key == null) {
      throw new IllegalArgumentException("Custom attribute key must not be null.");
    }
    return sanitizeAttribute(key);
  }

  /** Trims the string and truncates it to maxEntryLength. */
  public String sanitizeAttribute(String input) {
    if (input != null) {
      input = input.trim();
      if (input.length() > maxEntryLength) {
        input = input.substring(0, maxEntryLength);
      }
    }
    return input;
  }
}
