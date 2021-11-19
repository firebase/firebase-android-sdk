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

package com.google.firebase.crashlytics.internal.metadata;

import androidx.annotation.NonNull;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.common.CommonUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Handles any key/values for metadata. */
class KeysMap {

  // We use synchronized methods in this class rather than a ConcurrentHashMap because the
  // getKeys() method would need to return a defensive copy in either case. So using the standard
  // HashMap with synchronized access is more straightforward, and enables us to continue allowing
  // NULL values.
  private final Map<String, String> keys = new HashMap<>();
  private final int maxEntries;
  private final int maxEntryLength;

  public KeysMap(int maxEntries, int maxEntryLength) {
    this.maxEntries = maxEntries;
    this.maxEntryLength = maxEntryLength;
  }

  /** @return defensive, unmodifiable copy of the key/value pairs. */
  @NonNull
  public synchronized Map<String, String> getKeys() {
    return Collections.unmodifiableMap(new HashMap<String, String>(keys));
  }

  public synchronized boolean setKey(String key, String value) {
    String sanitizedKey = sanitizeKey(key);
    // The entry can be added if we're under the size limit or we're updating an existing entry
    if (keys.size() < maxEntries || keys.containsKey(sanitizedKey)) {
      String santitizedAttribute = sanitizeAttribute(value);
      if (CommonUtils.nullSafeEquals(keys.get(sanitizedKey), santitizedAttribute)) {
        return false;
      }
      keys.put(sanitizedKey, value == null ? "" : santitizedAttribute);
      return true;
    }
    Logger.getLogger()
        .w(
            "Ignored entry \""
                + key
                + "\" when adding custom keys. Maximum allowable: "
                + maxEntries);
    return false;
  }

  public synchronized void setKeys(Map<String, String> keysAndValues) {
    int nOverLimit = 0;
    for (Map.Entry<String, String> entry : keysAndValues.entrySet()) {
      String sanitizedKey = sanitizeKey(entry.getKey());
      // The entry can be added if we're under the size limit or we're updating an existing entry
      if (keys.size() < maxEntries || keys.containsKey(sanitizedKey)) {
        String value = entry.getValue();
        keys.put(sanitizedKey, value == null ? "" : sanitizeAttribute(value));
      } else {
        ++nOverLimit;
      }
    }
    if (nOverLimit > 0) {
      Logger.getLogger()
          .w(
              "Ignored "
                  + nOverLimit
                  + " entries when adding custom keys. "
                  + "Maximum allowable: "
                  + maxEntries);
    }
  }

  /** Checks that the key is not null then sanitizes it. */
  private String sanitizeKey(String key) {
    if (key == null) {
      throw new IllegalArgumentException("Custom attribute key must not be null.");
    }
    return sanitizeAttribute(key);
  }

  /** Trims the string and truncates it to maxEntryLength, or returns null if input is null. */
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
