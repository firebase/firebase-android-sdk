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

/** Handles custom attributes internal to the SDK, eg. for custom Unity metadata. */
public class InternalKeys {
    static final int MAX_INTERNAL_KEYS = 64;
    static final int MAX_INTERNAL_KEY_SIZE = 8192;

    private final Map<String, String> internal_keys = new HashMap<>();

    public InternalKeys() {}

    @NonNull
    public Map<String, String> getInternalKeys() {
        return Collections.unmodifiableMap(internal_keys);
    }

    public void setInternalKey(String key, String value) {
        setSyncInternalKeys(
                new HashMap<String, String>() {
                    {
                        put(sanitizeKey(key), sanitizeAttribute(value));
                    }
                });
    }

    /** Gatekeeper function for access to attributes */
    private synchronized void setSyncInternalKeys(Map<String, String> keysAndValues) {
        // We want all access to the attributes hashmap to be locked so that there is no way to create
        // a race condition and add more than MAX_ATTRIBUTES keys.

        // Update any existing keys first, then add any additional keys
        Map<String, String> currentKeys = new HashMap<String, String>();
        Map<String, String> newKeys = new HashMap<String, String>();

        // Split into current and new keys
        for (Map.Entry<String, String> entry : keysAndValues.entrySet()) {
            String key = sanitizeKey(entry.getKey());
            String value = (entry.getValue() == null) ? "" : sanitizeAttribute(entry.getValue());
            if (internal_keys.containsKey(key)) {
                currentKeys.put(key, value);
            } else {
                newKeys.put(key, value);
            }
        }

        internal_keys.putAll(currentKeys);

        // Add new keys if there is space
        if (internal_keys.size() + newKeys.size() > MAX_INTERNAL_KEYS) {
            int keySlotsLeft = MAX_INTERNAL_KEYS - internal_keys.size();
            Logger.getLogger()
                    .v("Exceeded maximum number of internal keys (" + MAX_INTERNAL_KEYS + ").");
            List<String> newKeyList = new ArrayList<>(newKeys.keySet());
            newKeys.keySet().retainAll(newKeyList.subList(0, keySlotsLeft));
        }
        internal_keys.putAll(newKeys);
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
            if (input.length() > MAX_INTERNAL_KEY_SIZE) {
                input = input.substring(0, MAX_INTERNAL_KEY_SIZE);
            }
        }
        return input;
    }
}
